package com.nexus.enchants.commands;

import com.nexus.enchants.database.Migration;
import com.nexus.enchants.database.MigrationManager;
import com.nexus.enchants.permissions.Permissions;
import com.nexus.enchants.util.Text;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.CommandSender;

/**
 * Diagnostics and database inspection commands.
 *
 * <p>Nothing here queries JDBC on the server thread. {@code database status} reads
 * cached state, and {@code database migrations} runs its query on the database
 * worker pool and sends the answer back through the server scheduler.</p>
 */
public final class DiagnosticsCommands {

    /**
     * Creates the command group.
     */
    public DiagnosticsCommands() {
    }

    /**
     * Returns the subcommands of this group.
     *
     * @return immutable list
     */
    public List<SubCommand> commands() {
        return List.of(new DatabaseCommand(), new DiagnosticsCommand());
    }

    private static void sendLater(CommandContext context, CommandSender sender, List<String> lines) {
        try {
            context.plugin().getServer().getScheduler().runTask(context.plugin(),
                    () -> lines.forEach(line -> Text.raw(sender, line)));
        } catch (RuntimeException exception) {
            // The plugin is disabling: there is nobody left to read the output.
            context.plugin().getLogger().fine("Dropped asynchronous command output: " + exception.getMessage());
        }
    }

    private static final class DatabaseCommand implements SubCommand {

        @Override
        public String name() {
            return "database";
        }

        @Override
        public List<String> aliases() {
            return List.of("db");
        }

        @Override
        public String permission() {
            return Permissions.DATABASE;
        }

        @Override
        public String usage() {
            return "/vbe database <status|migrations>";
        }

        @Override
        public String description() {
            return "Shows the database state and migration history.";
        }

        @Override
        public boolean execute(CommandContext context, CommandSender sender, String[] args) {
            if (args.length != 1) {
                Text.error(sender, "Usage: " + usage());
                return true;
            }
            String action = args[0].toLowerCase(java.util.Locale.ROOT);
            if ("status".equals(action)) {
                Text.raw(sender, "&8&m                                                        ");
                Text.raw(sender, "&bDatabase status");
                Text.raw(sender, "&7Type: &f" + context.database().type());
                Text.raw(sender, "&7Ready: " + Text.bool(context.database().isReady()));
                Text.raw(sender, "&7Schema version: &f" + context.database().schemaVersion());
                Text.raw(sender, "&7Detail: &f" + context.database().statusSummary());
                Text.raw(sender, "&7Worker threads: &f" + context.database().executor().workerCount()
                        + "&7, queued &f" + context.database().executor().queued()
                        + "&7 of &f" + context.database().executor().queueCapacity());
                Text.raw(sender, "&7Tasks: &f" + context.database().executor().submittedCount()
                        + "&7 submitted, &a" + context.database().executor().completedCount()
                        + "&7 completed, &c" + context.database().executor().failedCount()
                        + "&7 failed, &e" + context.database().executor().rejectedCount() + "&7 rejected");
                if (context.database().lastFailure() != null) {
                    Text.raw(sender, "&cLast failure: " + context.database().lastFailure());
                }
                Text.raw(sender, "&8&m                                                        ");
                return true;
            }
            if ("migrations".equals(action)) {
                requestMigrations(context, sender);
                return true;
            }
            Text.error(sender, "Unknown action '" + args[0] + "'. Usage: " + usage());
            return true;
        }

        private void requestMigrations(CommandContext context, CommandSender sender) {
            if (!context.database().isReady()) {
                Text.error(sender, "The database is not ready; nothing to list.");
                return;
            }
            MigrationManager manager = context.database().migrationManager();
            if (manager == null) {
                Text.error(sender, "No migration manager is available.");
                return;
            }
            Text.send(sender, "Reading the migration history asynchronously...");
            boolean accepted = context.database().executor().submit("command-migrations", () -> {
                List<String> lines = new ArrayList<>();
                lines.add("&8&m                                                        ");
                lines.add("&bDatabase migrations");
                try (Connection connection = context.database().connection()) {
                    for (var applied : manager.applied(connection)) {
                        lines.add("&aV" + String.format("%03d", applied.version()) + " &7"
                                + applied.description() + " &8(installed "
                                + applied.installedAt() + ")");
                    }
                    List<Migration> pending = manager.pending(connection);
                    if (pending.isEmpty()) {
                        lines.add("&7No pending migrations.");
                    } else {
                        for (var migration : pending) {
                            lines.add("&ePending V" + String.format("%03d", migration.version()) + " &7"
                                    + migration.description());
                        }
                    }
                } catch (SQLException | RuntimeException exception) {
                    lines.add("&cQuery failed: " + exception.getMessage());
                }
                lines.add("&8&m                                                        ");
                sendLater(context, sender, lines);
            });
            if (!accepted) {
                Text.error(sender, "The database queue is full; try again in a moment.");
            }
        }

        @Override
        public List<String> complete(CommandContext context, CommandSender sender, String[] args) {
            if (args.length == 2) {
                return List.of("status", "migrations");
            }
            return List.of();
        }
    }

    private static final class DiagnosticsCommand implements SubCommand {

        @Override
        public String name() {
            return "diagnostics";
        }

        @Override
        public List<String> aliases() {
            return List.of("diag");
        }

        @Override
        public String permission() {
            return Permissions.DIAGNOSTICS;
        }

        @Override
        public String usage() {
            return "/vbe diagnostics";
        }

        @Override
        public String description() {
            return "Prints the full runtime diagnostic report.";
        }

        @Override
        public boolean execute(CommandContext context, CommandSender sender, String[] args) {
            for (String line : context.diagnostics().report()) {
                Text.raw(sender, line);
            }
            return true;
        }
    }
}
