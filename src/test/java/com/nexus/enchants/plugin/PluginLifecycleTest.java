package com.nexus.enchants.plugin;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nexus.enchants.NexusEnchants;
import com.nexus.enchants.api.NexusEnchantsApi;
import com.nexus.enchants.commands.ReloadReport;
import com.nexus.enchants.sources.AcquisitionSource;
import com.nexus.enchants.util.EnchantmentResolver;
import java.util.UUID;
import org.bukkit.command.PluginCommand;
import org.bukkit.enchantments.Enchantment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Startup, shutdown, reload and command registration inside MockBukkit.
 *
 * <p>MockBukkit models the Bukkit API, not the Minecraft client. These tests prove
 * the plugin enables without a server, registers its command and permissions and
 * shuts down cleanly; they do not prove the vanilla trade screen behaves.</p>
 */
class PluginLifecycleTest {

    private ServerMock server;
    private NexusEnchants plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(NexusEnchants.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("The plugin enables and its services start")
    void enable() {
        assertTrue(plugin.isEnabled());
        assertNotNull(plugin.bootstrap());
        assertTrue(plugin.bootstrap().isStarted());
        assertNotNull(plugin.bootstrap().configManager());
        assertNotNull(plugin.bootstrap().database());
        assertNotNull(plugin.bootstrap().api());
        assertNotNull(plugin.bootstrap().tradeCache());
        assertNotNull(plugin.bootstrap().enchantingManager());
    }

    @Test
    @DisplayName("The vbe command is registered with its alias")
    void commandRegistered() {
        PluginCommand command = server.getPluginCommand("vbe");
        assertNotNull(command, "the vbe command must be registered");
        assertEquals("NexusEnchants", command.getPlugin().getName());
        assertEquals("/vbe help", command.getUsage());
        assertTrue(command.getAliases().contains("nexusenchants"));
    }

    @Test
    @DisplayName("/vbe help runs for the console and answers")
    void helpFromConsole() {
        ConsoleCommandSenderMock console = (ConsoleCommandSenderMock) server.getConsoleSender();
        assertTrue(server.dispatchCommand(console, "vbe help"));
        assertNotNull(console.nextComponentMessage(), "the help output must be sent");
    }

    @Test
    @DisplayName("/vbe info runs for an operator")
    void infoFromOperator() {
        PlayerMock player = server.addPlayer("Operator");
        player.setOp(true);
        assertTrue(server.dispatchCommand(player, "vbe info"));
        assertNotNull(player.nextComponentMessage());
    }

    @Test
    @DisplayName("A player without the permission is refused")
    void permissionDenied() {
        PlayerMock player = server.addPlayer("Plain");
        assertFalse(player.hasPermission("nexusenchants.reload"));
        server.dispatchCommand(player, "vbe reload");
        assertNotNull(player.nextComponentMessage(), "a permission denial must be reported");
    }

    @Test
    @DisplayName("An unknown subcommand is reported instead of failing silently")
    void unknownSubcommand() {
        PlayerMock player = server.addPlayer("Operator2");
        player.setOp(true);
        assertTrue(server.dispatchCommand(player, "vbe nonsense"));
        assertNotNull(player.nextComponentMessage());
    }

    @Test
    @DisplayName("Tab completion answers without throwing")
    void tabCompletion() {
        PlayerMock player = server.addPlayer("Operator3");
        player.setOp(true);
        assertDoesNotThrow(() -> server.getCommandMap().tabComplete(player, "vbe ra"));
    }

    @Test
    @DisplayName("Repeated reloads keep the plugin running")
    void repeatedReload() {
        for (int index = 0; index < 3; index++) {
            ReloadReport report = plugin.bootstrap().reload();
            assertNotNull(report);
            assertEquals(0, report.errors(), "the shipped defaults must reload without errors");
            assertTrue(plugin.isEnabled());
            assertTrue(plugin.bootstrap().isStarted());
        }
    }

    @Test
    @DisplayName("Disabling and re-enabling the plugin is safe")
    void disableAndReEnable() {
        server.getPluginManager().disablePlugin(plugin);
        assertFalse(plugin.isEnabled());

        server.getPluginManager().enablePlugin(plugin);
        assertTrue(plugin.isEnabled());
        assertNotNull(plugin.bootstrap());
        assertTrue(plugin.bootstrap().isStarted());
    }

    @Test
    @DisplayName("The public API exposes the live configuration")
    void api() {
        NexusEnchantsApi api = plugin.bootstrap().api();
        Enchantment mending = EnchantmentResolver.resolve("MENDING").orElseThrow();

        assertEquals(NexusEnchantsApi.API_VERSION, api.apiVersion());
        assertEquals(70, api.getBasePrice(mending, 1));
        assertEquals(70, api.getEffectivePrice(mending, 1, 0));
        assertTrue(api.getVillagerProbability(mending) > 0.0D);
        assertFalse(api.isSourceAllowed(mending, AcquisitionSource.ENCHANTING_TABLE));
        assertTrue(api.isSourceAllowed(mending, AcquisitionSource.VILLAGER));
        assertFalse(api.isVillagerManaged(UUID.randomUUID()));
    }
}
