package com.nexus.enchants.permissions;

/**
 * Permission nodes declared in plugin.yml.
 *
 * <p>They are plain strings on purpose: LuckPerms and every other permission
 * plugin resolves them by name, and no runtime dependency on a permission
 * implementation is needed.</p>
 */
public final class Permissions {

    /** Grants every other node through inheritance. */
    public static final String ADMIN = "nexusenchants.admin";
    /** Allows {@code /vbe reload}. */
    public static final String RELOAD = "nexusenchants.reload";
    /** Allows reading and changing prices. */
    public static final String PRICES = "nexusenchants.prices";
    /** Allows reading and changing rarity weights. */
    public static final String RARITY = "nexusenchants.rarity";
    /** Allows diagnostics and simulation commands. */
    public static final String DIAGNOSTICS = "nexusenchants.diagnostics";
    /** Allows database inspection commands. */
    public static final String DATABASE = "nexusenchants.database";

    private Permissions() {
    }
}
