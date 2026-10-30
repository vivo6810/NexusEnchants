package com.nexus.enchants.commands;

import com.nexus.enchants.permissions.Permissions;
import com.nexus.enchants.util.Text;
import java.util.List;
import org.bukkit.command.CommandSender;

/**
 * Lists every subcommand with its usage.
 */
public final class HelpCommand implements SubCommand {

    private final List<SubCommand> commands;

    /**
     * Creates the command.
     *
     * @param commands every registered subcommand, in display order
     */
    public HelpCommand(List<SubCommand> commands) {
        this.commands = List.copyOf(commands);
    }

    @Override
    public String name() {
        return "help";
    }

    @Override
    public List<String> aliases() {
        return List.of("?");
    }

    @Override
    public String usage() {
        return "/vbe help";
    }

    @Override
    public String description() {
        return "Shows this command list.";
    }

    @Override
    public boolean execute(CommandContext context, CommandSender sender, String[] args) {
        Text.raw(sender, "&8&m                                                        ");
        Text.raw(sender, "&bNexusEnchants &7- enchantment economy control");
        Text.raw(sender, "&8&m                                                        ");
        for (SubCommand command : commands) {
            String permission = command.permission();
            if (permission != null && !sender.hasPermission(permission)
                    && !sender.hasPermission(Permissions.ADMIN)) {
                continue;
            }
            Text.raw(sender, "&e" + command.usage() + " &8- &7" + command.description());
        }
        Text.raw(sender, "&8&m                                                        ");
        return true;
    }
}
