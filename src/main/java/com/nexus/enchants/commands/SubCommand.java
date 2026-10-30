package com.nexus.enchants.commands;

import java.util.List;
import org.bukkit.command.CommandSender;

/**
 * One {@code /vbe} subcommand.
 */
public interface SubCommand {

    /**
     * Returns the primary name.
     *
     * @return the name
     */
    String name();

    /**
     * Returns alternative names.
     *
     * @return the aliases, empty by default
     */
    default List<String> aliases() {
        return List.of();
    }

    /**
     * Returns the required permission.
     *
     * @return the permission node, or {@code null} when the command is public
     */
    default String permission() {
        return null;
    }

    /**
     * Returns the usage line.
     *
     * @return the usage
     */
    String usage();

    /**
     * Returns a one line description.
     *
     * @return the description
     */
    String description();

    /**
     * Executes the command.
     *
     * @param context command context with the live services
     * @param sender  sender
     * @param args    arguments after the subcommand name
     * @return {@code true} when the command was handled
     */
    boolean execute(CommandContext context, CommandSender sender, String[] args);

    /**
     * Completes arguments.
     *
     * @param context command context
     * @param sender  sender
     * @param args    arguments typed so far, including the subcommand name at index 0
     * @return suggestions
     */
    default List<String> complete(CommandContext context, CommandSender sender, String[] args) {
        return List.of();
    }
}
