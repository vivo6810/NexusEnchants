package com.nexus.enchants.commands;

import com.nexus.enchants.config.ConfigSnapshot;
import com.nexus.enchants.economy.BookPricingManager;
import com.nexus.enchants.economy.PriceNotConfiguredException;
import com.nexus.enchants.enchanting.EnchantmentLevelManager;
import com.nexus.enchants.permissions.Permissions;
import com.nexus.enchants.util.EnchantmentResolver;
import com.nexus.enchants.util.PriceMath;
import com.nexus.enchants.util.Text;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;

/**
 * Price inspection and editing: {@code price} and {@code setprice}.
 */
public final class PricingCommands {

    /**
     * Creates the command group.
     */
    public PricingCommands() {
    }

    /**
     * Returns the subcommands of this group.
     *
     * @return immutable list
     */
    public List<SubCommand> commands() {
        return List.of(new PriceCommand(), new SetPriceCommand());
    }

    private static Optional<Enchantment> resolve(CommandSender sender, String raw) {
        Optional<Enchantment> resolved = EnchantmentResolver.resolve(raw);
        if (resolved.isEmpty()) {
            Text.error(sender, "Unknown enchantment '" + raw + "'. Try 'minecraft:mending' or 'MENDING'.");
        }
        return resolved;
    }

    private static Optional<Integer> parseLevel(CommandSender sender, String raw, Enchantment enchantment) {
        int level;
        try {
            level = Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            Text.error(sender, "'" + raw + "' is not a number.");
            return Optional.empty();
        }
        int[] range = EnchantmentLevelManager.legalRange(enchantment);
        if (level < 1 || level > range[1]) {
            Text.error(sender, "Level must be between 1 and " + range[1] + " for "
                    + EnchantmentResolver.keyOf(enchantment) + ".");
            return Optional.empty();
        }
        return Optional.of(level);
    }

    private static final class PriceCommand implements SubCommand {

        @Override
        public String name() {
            return "price";
        }

        @Override
        public String permission() {
            return Permissions.PRICES;
        }

        @Override
        public String usage() {
            return "/vbe price <enchantment> <level>";
        }

        @Override
        public String description() {
            return "Shows the configured and effective price of a book.";
        }

        @Override
        public boolean execute(CommandContext context, CommandSender sender, String[] args) {
            if (args.length != 2) {
                Text.error(sender, "Usage: " + usage());
                return true;
            }
            Optional<Enchantment> enchantment = resolve(sender, args[0]);
            if (enchantment.isEmpty()) {
                return true;
            }
            Optional<Integer> level = parseLevel(sender, args[1], enchantment.get());
            if (level.isEmpty()) {
                return true;
            }
            String key = EnchantmentResolver.keyOf(enchantment.get());
            ConfigSnapshot snapshot = context.config().current();
            boolean explicit = snapshot.pricing().explicitPrice(key, level.get()).isPresent();
            try {
                BookPricingManager.PricedTrade priced = context.pricing().price(key, level.get(), 0);
                Text.raw(sender, "&8&m                                                        ");
                Text.raw(sender, "&b" + key + " &7level &f" + level.get());
                Text.raw(sender, "&7Source: " + (explicit ? "&apricing.yml" : "&efallback"));
                Text.raw(sender, "&7Base price: " + Text.emeralds(priced.basePrice()));
                Text.raw(sender, "&7Effective price: " + Text.emeralds(priced.finalPrice())
                        + " &8(" + priced.reason() + ")");
                Text.raw(sender, "&7Payment: " + priced.plan().slotAmounts().stream()
                        .map(amount -> amount + " emerald(s)")
                        .reduce((left, right) -> left + " + " + right).orElse("none"));
                Text.raw(sender, "&7Discount policy: &f" + snapshot.discounts().policy()
                        + " &8(vanilla suppressed: " + Text.bool(priced.suppressDiscounts()) + "&8)");
                Text.raw(sender, "&8&m                                                        ");
            } catch (PriceNotConfiguredException exception) {
                Text.error(sender, "No price and no fallback configured for " + key + " level " + level.get() + ".");
            } catch (RuntimeException exception) {
                Text.error(sender, "Cannot price this book: " + exception.getMessage());
            }
            return true;
        }

        @Override
        public List<String> complete(CommandContext context, CommandSender sender, String[] args) {
            if (args.length == 2) {
                return CommandDispatcher.enchantmentSuggestions();
            }
            if (args.length == 3) {
                Optional<Enchantment> enchantment = EnchantmentResolver.resolve(args[1]);
                if (enchantment.isEmpty()) {
                    return List.of("1", "2", "3");
                }
                int highest = enchantment.get().getMaxLevel();
                return java.util.stream.IntStream.rangeClosed(1, highest)
                        .mapToObj(Integer::toString)
                        .toList();
            }
            return List.of();
        }
    }

    private static final class SetPriceCommand implements SubCommand {

        @Override
        public String name() {
            return "setprice";
        }

        @Override
        public List<String> aliases() {
            return List.of("set");
        }

        @Override
        public String permission() {
            return Permissions.PRICES;
        }

        @Override
        public String usage() {
            return "/vbe setprice <enchantment> <level> <price>";
        }

        @Override
        public String description() {
            return "Writes a price to pricing.yml and reloads.";
        }

        @Override
        public boolean execute(CommandContext context, CommandSender sender, String[] args) {
            if (args.length != 3) {
                Text.error(sender, "Usage: " + usage());
                return true;
            }
            Optional<Enchantment> enchantment = resolve(sender, args[0]);
            if (enchantment.isEmpty()) {
                return true;
            }
            Optional<Integer> level = parseLevel(sender, args[1], enchantment.get());
            if (level.isEmpty()) {
                return true;
            }
            int price;
            try {
                price = Integer.parseInt(args[2]);
            } catch (NumberFormatException exception) {
                Text.error(sender, "'" + args[2] + "' is not a number.");
                return true;
            }
            ConfigSnapshot snapshot = context.config().current();
            int minimum = snapshot.pricing().minimumPrice();
            int maximum = snapshot.pricing().maximumPrice();
            if (price < minimum || price > maximum) {
                Text.error(sender, "Price must be between " + minimum + " and " + maximum
                        + " emeralds (one merchant trade holds at most "
                        + PriceMath.MAX_REPRESENTABLE_PRICE + ").");
                return true;
            }
            if (price > PriceMath.MAX_REPRESENTABLE_PRICE) {
                Text.error(sender, "A single trade cannot require more than "
                        + PriceMath.MAX_REPRESENTABLE_PRICE + " emeralds.");
                return true;
            }
            String key = EnchantmentResolver.keyOf(enchantment.get());
            try {
                ConfigSnapshot updated = context.config().writePrice(key, level.get(), price);
                Text.send(sender, "Set " + key + " level " + level.get() + " to " + price + " emerald(s).");
                if (!updated.errors().isEmpty()) {
                    Text.error(sender, "The reload reported " + updated.errors().size()
                            + " error(s); run /vbe diagnostics.");
                }
            } catch (IOException exception) {
                Text.error(sender, "Could not write pricing.yml: " + exception.getMessage());
            }
            return true;
        }

        @Override
        public List<String> complete(CommandContext context, CommandSender sender, String[] args) {
            if (args.length == 2) {
                return CommandDispatcher.enchantmentSuggestions();
            }
            if (args.length == 3) {
                Optional<Enchantment> enchantment = EnchantmentResolver.resolve(args[1]);
                if (enchantment.isEmpty()) {
                    return List.of();
                }
                return java.util.stream.IntStream.rangeClosed(1, enchantment.get().getMaxLevel())
                        .mapToObj(Integer::toString)
                        .toList();
            }
            if (args.length == 4) {
                return List.of("10", "20", "40", "70");
            }
            return List.of();
        }
    }
}
