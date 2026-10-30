package com.nexus.enchants.util;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

/**
 * Command output formatting.
 *
 * <p>Output uses legacy colour codes because they are identical in the console
 * and in game, and because the plugin must not depend on an Adventure
 * serializer version that a server implementation may not expose.</p>
 */
public final class Text {

    /** Prefix used by every plugin message. */
    public static final String PREFIX = "&8[&bNexusEnchants&8] &r";

    private Text() {
    }

    /**
     * Translates colour codes.
     *
     * @param raw text with {@code &} colour codes
     * @return the translated text
     */
    public static String color(String raw) {
        return raw == null ? "" : ChatColor.translateAlternateColorCodes('&', raw);
    }

    /**
     * Sends a prefixed message.
     *
     * @param sender  receiver
     * @param message text with {@code &} colour codes
     */
    public static void send(CommandSender sender, String message) {
        sender.sendMessage(color(PREFIX + message));
    }

    /**
     * Sends a message without the prefix, used for tables and headers.
     *
     * @param sender  receiver
     * @param message text with {@code &} colour codes
     */
    public static void raw(CommandSender sender, String message) {
        sender.sendMessage(color(message));
    }

    /**
     * Sends an error message.
     *
     * @param sender  receiver
     * @param message text with {@code &} colour codes
     */
    public static void error(CommandSender sender, String message) {
        send(sender, "&c" + message);
    }

    /**
     * Formats a boolean as a coloured yes/no.
     *
     * @param value value to format
     * @return the formatted text
     */
    public static String bool(boolean value) {
        return value ? "&ayes&r" : "&cno&r";
    }

    /**
     * Formats a probability as a percentage with two decimals.
     *
     * @param probability probability in the range {@code 0.0 - 1.0}
     * @return the formatted percentage
     */
    public static String percent(double probability) {
        return String.format(java.util.Locale.ROOT, "%.2f%%", probability * 100.0D);
    }

    /**
     * Formats an emerald amount.
     *
     * @param amount amount to format
     * @return the formatted amount
     */
    public static String emeralds(int amount) {
        return "&a" + amount + "&r emerald" + (amount == 1 ? "" : "s");
    }
}
