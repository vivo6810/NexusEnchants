package com.nexus.enchants.commands;

import com.nexus.enchants.permissions.Permissions;
import com.nexus.enchants.util.EnchantmentResolver;
import com.nexus.enchants.util.Text;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

/**
 * Root {@code /vbe} handler.
 *
 * <p>Dispatches to a {@link SubCommand}, enforces permissions before any work
 * happens and provides tab completion from the registered commands.</p>
 */
public final class CommandDispatcher implements TabExecutor {

    private final Map<String, SubCommand> commands = new LinkedHashMap<>();
    private final List<SubCommand> ordered;
    private final CommandContext context;

    /**
     * Creates the dispatcher.
     *
     * @param context    command context
     * @param subCommands registered subcommands, in help order
     */
    public CommandDispatcher(CommandContext context, List<SubCommand> subCommands) {
        this.context = context;
        this.ordered = List.copyOf(subCommands);
        for (SubCommand command : ordered) {
            commands.put(command.name().toLowerCase(Locale.ROOT), command);
            for (String alias : command.aliases()) {
                commands.put(alias.toLowerCase(Locale.ROOT), command);
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        SubCommand target;
        String[] effectiveArgs;
        if (args.length == 0) {
            target = commands.get("help");
            effectiveArgs = new String[0];
        } else {
            target = commands.get(args[0].toLowerCase(Locale.ROOT));
            if (target == null) {
                Text.error(sender, "Unknown subcommand '" + args[0] + "'. Use /" + label + " help.");
                return true;
            }
            effectiveArgs = new String[args.length - 1];
            System.arraycopy(args, 1, effectiveArgs, 0, effectiveArgs.length);
        }
        if (target == null) {
            Text.error(sender, "This build does not register a help command.");
            return true;
        }
        String permission = target.permission();
        if (permission != null && !sender.hasPermission(permission) && !sender.hasPermission(Permissions.ADMIN)) {
            Text.error(sender, "You do not have permission to use this command (" + permission + ").");
            return true;
        }
        try {
            return target.execute(context, sender, effectiveArgs);
        } catch (RuntimeException exception) {
            context.plugin().getLogger().severe("Command /" + label + " " + target.name()
                    + " failed: " + exception.getMessage());
            Text.error(sender, "The command failed: " + exception.getMessage()
                    + ". See the server log for details.");
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length <= 1) {
            List<String> names = new ArrayList<>();
            for (SubCommand subCommand : ordered) {
                String permission = subCommand.permission();
                if (permission != null && !sender.hasPermission(permission)
                        && !sender.hasPermission(Permissions.ADMIN)) {
                    continue;
                }
                names.add(subCommand.name());
            }
            return filter(names, args.length == 0 ? "" : args[0]);
        }
        SubCommand target = commands.get(args[0].toLowerCase(Locale.ROOT));
        if (target == null) {
            return List.of();
        }
        String permission = target.permission();
        if (permission != null && !sender.hasPermission(permission) && !sender.hasPermission(Permissions.ADMIN)) {
            return List.of();
        }
        List<String> suggestions = target.complete(context, sender, args);
        return filter(suggestions, args[args.length - 1]);
    }

    private List<String> filter(List<String> candidates, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return candidates;
        }
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(lower)) {
                matches.add(candidate);
            }
        }
        return matches;
    }

    /**
     * Returns every registered subcommand in help order.
     *
     * @return immutable list
     */
    public List<SubCommand> subCommands() {
        return ordered;
    }

    /**
     * Returns the completion candidates for enchantment names.
     *
     * @return sorted canonical keys plus their short names
     */
    public static List<String> enchantmentSuggestions() {
        List<String> suggestions = new ArrayList<>();
        for (String key : EnchantmentResolver.allKeys()) {
            suggestions.add(key);
            int colon = key.indexOf(':');
            if (colon >= 0 && "minecraft".equals(key.substring(0, colon))) {
                suggestions.add(key.substring(colon + 1).toUpperCase(Locale.ROOT));
            }
        }
        return suggestions;
    }
}
