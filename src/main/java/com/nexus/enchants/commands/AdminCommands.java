package com.nexus.enchants.commands;

import com.nexus.enchants.config.ConfigSnapshot;
import com.nexus.enchants.permissions.Permissions;
import com.nexus.enchants.util.Text;
import java.util.List;
import org.bukkit.command.CommandSender;

/**
 * Administrative commands: {@code info} and {@code reload}.
 */
public final class AdminCommands {

    /**
     * Creates the command group.
     */
    public AdminCommands() {
    }

    /**
     * Returns the subcommands of this group.
     *
     * @return immutable list
     */
    public List<SubCommand> commands() {
        return List.of(new InfoCommand(), new ReloadCommand());
    }

    private static final class InfoCommand implements SubCommand {

        @Override
        public String name() {
            return "info";
        }

        @Override
        public String usage() {
            return "/vbe info";
        }

        @Override
        public String description() {
            return "Shows the active configuration summary.";
        }

        @Override
        public boolean execute(CommandContext context, CommandSender sender, String[] args) {
            ConfigSnapshot snapshot = context.config().current();
            Text.raw(sender, "&8&m                                                        ");
            Text.raw(sender, "&bNexusEnchants &7version &f"
                    + context.plugin().getPluginMeta().getVersion());
            Text.raw(sender, "&7Control enabled: " + Text.bool(snapshot.settings().enabled()));
            Text.raw(sender, "&7Priced enchantment/level pairs: &f"
                    + countedPairs(snapshot));
            Text.raw(sender, "&7Price limits: &f" + snapshot.pricing().minimumPrice() + " - "
                    + snapshot.pricing().maximumPrice() + "&7, fallback "
                    + Text.bool(snapshot.pricing().fallbackEnabled()) + "&7 ("
                    + snapshot.pricing().fallbackPrice() + ")");
            Text.raw(sender, "&7Librarian rarity: " + Text.bool(snapshot.villagerRarity().active())
                    + "&7, enchanting rarity: " + Text.bool(snapshot.enchanting().active()));
            Text.raw(sender, "&7Discount policy: &f" + snapshot.discounts().policy()
                    + "&7, cap &f" + snapshot.discounts().maximumDiscountPercentage() + "%");
            Text.raw(sender, "&7Sources enforced: " + Text.bool(snapshot.sources().enabled()));
            Text.raw(sender, "&7Database: &f" + context.database().type() + "&7, ready "
                    + Text.bool(context.database().isReady()) + "&7, schema &f"
                    + context.database().schemaVersion());
            Text.raw(sender, "&7Managed villagers cached: &f" + context.cache().size());
            Text.raw(sender, "&7Configuration findings: &c" + snapshot.errors().size()
                    + " error(s)&7, &e" + snapshot.warnings().size() + " warning(s)");
            Text.raw(sender, "&8&m                                                        ");
            return true;
        }

        private int countedPairs(ConfigSnapshot snapshot) {
            int total = 0;
            for (var levels : snapshot.pricing().prices().values()) {
                total += levels.size();
            }
            return total;
        }
    }

    private static final class ReloadCommand implements SubCommand {

        @Override
        public String name() {
            return "reload";
        }

        @Override
        public List<String> aliases() {
            return List.of("rl");
        }

        @Override
        public String permission() {
            return Permissions.RELOAD;
        }

        @Override
        public String usage() {
            return "/vbe reload";
        }

        @Override
        public String description() {
            return "Reloads every YAML file and re-applies prices.";
        }

        @Override
        public boolean execute(CommandContext context, CommandSender sender, String[] args) {
            long started = System.nanoTime();
            ReloadReport report = context.reloadSupplier().get();
            long millis = (System.nanoTime() - started) / 1_000_000L;
            if (!report.success()) {
                Text.error(sender, "Reload finished with " + report.errors() + " error(s) in " + millis + " ms.");
            } else {
                Text.send(sender, "Configuration reloaded in " + millis + " ms with "
                        + report.warnings() + " warning(s).");
            }
            for (String problem : report.problems()) {
                Text.raw(sender, "&c- " + problem);
            }
            return true;
        }
    }
}
