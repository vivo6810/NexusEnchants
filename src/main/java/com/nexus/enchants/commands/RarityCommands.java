package com.nexus.enchants.commands;

import com.nexus.enchants.config.ConfigSnapshot;
import com.nexus.enchants.permissions.Permissions;
import com.nexus.enchants.sources.AcquisitionSource;
import com.nexus.enchants.util.EnchantmentResolver;
import com.nexus.enchants.util.EnchantmentWeight;
import com.nexus.enchants.util.Text;
import com.nexus.enchants.util.WeightedTable;
import com.nexus.enchants.villager.VillagerRarityManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;

/**
 * Rarity inspection, editing and simulation behind {@code /vbe rarity}.
 */
public final class RarityCommands {

    private static final List<String> MODES = List.of("villager", "enchanting");

    /**
     * Creates the command group.
     */
    public RarityCommands() {
    }

    /**
     * Returns the subcommands of this group.
     *
     * @return immutable list
     */
    public List<SubCommand> commands() {
        return List.of(new RarityCommand());
    }

    private static final class RarityCommand implements SubCommand {

        @Override
        public String name() {
            return "rarity";
        }

        @Override
        public String permission() {
            return Permissions.RARITY;
        }

        @Override
        public String usage() {
            return "/vbe rarity <list|villager|enchanting|set|test> ...";
        }

        @Override
        public String description() {
            return "Inspects, changes and simulates rarity weights.";
        }

        @Override
        public boolean execute(CommandContext context, CommandSender sender, String[] args) {
            if (args.length == 0) {
                Text.error(sender, "Usage: " + usage());
                return true;
            }
            String action = args[0].toLowerCase(Locale.ROOT);
            return switch (action) {
                case "list" -> list(context, sender);
                case "villager" -> describeVillager(context, sender, args);
                case "enchanting" -> describeEnchanting(context, sender, args);
                case "set" -> set(context, sender, args);
                case "test" -> test(context, sender, args);
                default -> {
                    Text.error(sender, "Unknown action '" + args[0] + "'. Usage: " + usage());
                    yield true;
                }
            };
        }

        private boolean list(CommandContext context, CommandSender sender) {
            ConfigSnapshot snapshot = context.config().current();
            Text.raw(sender, "&8&m                                                        ");
            Text.raw(sender, "&bEnchantment weights &8(librarian / enchanting table)");
            List<String> keys = new ArrayList<>(snapshot.villagerRarity().weights().keySet());
            for (String key : snapshot.enchanting().weights().keySet()) {
                if (!keys.contains(key)) {
                    keys.add(key);
                }
            }
            keys.sort(String::compareTo);
            for (String key : keys) {
                EnchantmentWeight villager = snapshot.villagerRarity().weightOf(key);
                EnchantmentWeight enchanting = snapshot.enchanting().weightOf(key);
                Text.raw(sender, "&7" + key + " &8- &avillager &f" + villager.effectiveWeight()
                        + " &8(&7" + Text.percent(context.villagerRarity().probabilityOf(key)) + "&8)"
                        + " &8| &benchanting &f" + enchanting.effectiveWeight());
            }
            Text.raw(sender, "&8&m                                                        ");
            return true;
        }

        private boolean describeVillager(CommandContext context, CommandSender sender, String[] args) {
            if (args.length != 2) {
                Text.error(sender, "Usage: /vbe rarity villager <enchantment>");
                return true;
            }
            Optional<Enchantment> enchantment = resolve(sender, args[1]);
            if (enchantment.isEmpty()) {
                return true;
            }
            String key = EnchantmentResolver.keyOf(enchantment.get());
            ConfigSnapshot snapshot = context.config().current();
            EnchantmentWeight weight = snapshot.villagerRarity().weightOf(key);
            Text.raw(sender, "&8&m                                                        ");
            Text.raw(sender, "&b" + key + " &7- librarian");
            Text.raw(sender, "&7Enabled: " + Text.bool(weight.enabled()) + "&7, weight: &f" + weight.weight());
            Text.raw(sender, "&7Probability: &f" + Text.percent(context.villagerRarity().probabilityOf(key)));
            Text.raw(sender, "&7Treasure: " + Text.bool(enchantment.get().isTreasure())
                    + "&7, allowed: " + Text.bool(!enchantment.get().isTreasure()
                            || snapshot.villagerRarity().treasureEnabled()));
            appendLevels(context, sender, enchantment.get());
            Text.raw(sender, "&8&m                                                        ");
            return true;
        }

        private boolean describeEnchanting(CommandContext context, CommandSender sender, String[] args) {
            if (args.length != 2) {
                Text.error(sender, "Usage: /vbe rarity enchanting <enchantment>");
                return true;
            }
            Optional<Enchantment> enchantment = resolve(sender, args[1]);
            if (enchantment.isEmpty()) {
                return true;
            }
            String key = EnchantmentResolver.keyOf(enchantment.get());
            ConfigSnapshot snapshot = context.config().current();
            EnchantmentWeight weight = snapshot.enchanting().weightOf(key);
            Text.raw(sender, "&8&m                                                        ");
            Text.raw(sender, "&b" + key + " &7- enchanting table");
            Text.raw(sender, "&7Enabled: " + Text.bool(weight.enabled()) + "&7, weight: &f" + weight.weight());
            Text.raw(sender, "&7Excluded: " + Text.bool(snapshot.enchanting().excludedEnchantments().contains(key))
                    + "&7, source allowed: "
                    + Text.bool(snapshot.sources().allows(key, AcquisitionSource.ENCHANTING_TABLE)));
            Text.raw(sender, "&7Probability (ignoring item type): &f"
                    + Text.percent(context.combinations().primaryProbability(key, null)));
            appendLevels(context, sender, enchantment.get());
            Text.raw(sender, "&8&m                                                        ");
            return true;
        }

        private void appendLevels(CommandContext context, CommandSender sender, Enchantment enchantment) {
            WeightedTable<Integer> table = context
                    .enchantingLevels()
                    .tableFor(enchantment, Map.of());
            if (table.isEmpty()) {
                Text.raw(sender, "&7Levels: &cnone configured");
                return;
            }
            StringBuilder builder = new StringBuilder("&7Levels: ");
            boolean first = true;
            for (WeightedTable.Entry<Integer> entry : table.entries()) {
                if (entry.weight() <= 0L) {
                    continue;
                }
                if (!first) {
                    builder.append("&7, ");
                }
                builder.append("&f").append(entry.value()).append(" &8(")
                        .append(Text.percent(table.probabilityOf(entry.value()))).append("&8)");
                first = false;
            }
            Text.raw(sender, builder.toString());
        }

        private boolean set(CommandContext context, CommandSender sender, String[] args) {
            if (args.length != 4) {
                Text.error(sender, "Usage: /vbe rarity set <villager|enchanting> <enchantment> <weight>");
                return true;
            }
            String mode = args[1].toLowerCase(Locale.ROOT);
            if (!MODES.contains(mode)) {
                Text.error(sender, "Mode must be 'villager' or 'enchanting'.");
                return true;
            }
            Optional<Enchantment> enchantment = resolve(sender, args[2]);
            if (enchantment.isEmpty()) {
                return true;
            }
            int weight;
            try {
                weight = Integer.parseInt(args[3]);
            } catch (NumberFormatException exception) {
                Text.error(sender, "'" + args[3] + "' is not a number.");
                return true;
            }
            if (weight < 0 || weight > 1000) {
                Text.error(sender, "Weight must be between 0 and 1000.");
                return true;
            }
            String key = EnchantmentResolver.keyOf(enchantment.get());
            boolean enabled = weight > 0;
            try {
                if ("villager".equals(mode)) {
                    context.config().writeVillagerWeight(key, weight, enabled);
                } else {
                    context.config().writeEnchantingWeight(key, weight, enabled);
                }
                Text.send(sender, "Set " + mode + " weight of " + key + " to " + weight
                        + (enabled ? "." : " (disabled)."));
            } catch (IOException exception) {
                Text.error(sender, "Could not write the rarity file: " + exception.getMessage());
            }
            return true;
        }

        private boolean test(CommandContext context, CommandSender sender, String[] args) {
            if (args.length != 2) {
                Text.error(sender, "Usage: /vbe rarity test <enchantment>");
                return true;
            }
            Optional<Enchantment> enchantment = resolve(sender, args[1]);
            if (enchantment.isEmpty()) {
                return true;
            }
            String key = EnchantmentResolver.keyOf(enchantment.get());
            final int iterations = 20_000;
            int hits = 0;
            int failures = 0;
            for (int index = 0; index < iterations; index++) {
                Optional<VillagerRarityManager.Selection> selection = context.villagerRarity().select();
                if (selection.isEmpty()) {
                    failures++;
                    continue;
                }
                if (selection.get().enchantmentKey().equals(key)) {
                    hits++;
                }
            }
            double observed = (double) hits / (double) iterations;
            double expected = context.villagerRarity().probabilityOf(key);
            Text.raw(sender, "&8&m                                                        ");
            Text.raw(sender, "&bSimulation &7(" + iterations + " librarian rolls)");
            Text.raw(sender, "&7Observed &f" + key + "&7: " + hits + " &8(" + Text.percent(observed) + "&8)");
            Text.raw(sender, "&7Expected: " + Text.percent(expected));
            if (failures > 0) {
                Text.raw(sender, "&cRolls without a result: " + failures
                        + " (the table is empty or every entry is filtered)");
            }
            Text.raw(sender, "&8&m                                                        ");
            return true;
        }

        @Override
        public List<String> complete(CommandContext context, CommandSender sender, String[] args) {
            if (args.length == 2) {
                return List.of("list", "villager", "enchanting", "set", "test");
            }
            if (args.length == 3) {
                String action = args[1].toLowerCase(Locale.ROOT);
                if ("set".equals(action)) {
                    return MODES;
                }
                return CommandDispatcher.enchantmentSuggestions();
            }
            if (args.length == 4 && "set".equals(args[1].toLowerCase(Locale.ROOT))) {
                return CommandDispatcher.enchantmentSuggestions();
            }
            if (args.length == 5 && "set".equals(args[1].toLowerCase(Locale.ROOT))) {
                return List.of("0", "1", "2", "5", "10", "12", "40");
            }
            return List.of();
        }

        private Optional<Enchantment> resolve(CommandSender sender, String raw) {
            Optional<Enchantment> resolved = EnchantmentResolver.resolve(raw);
            if (resolved.isEmpty()) {
                Text.error(sender, "Unknown enchantment '" + raw + "'.");
            }
            return resolved;
        }
    }
}
