package com.nexus.enchants;

import com.nexus.enchants.bootstrap.PluginBootstrap;
import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * NexusEnchants entry point.
 *
 * <p>The plugin class only owns the life cycle. Every service, listener, command
 * and task is created by {@link PluginBootstrap}, which also guarantees a clean
 * shutdown if startup fails halfway through.</p>
 */
public final class NexusEnchants extends JavaPlugin {

    private PluginBootstrap bootstrap;

    /**
     * Creates the plugin.
     */
    public NexusEnchants() {
    }

    @Override
    public void onEnable() {
        bootstrap = new PluginBootstrap(this);
        try {
            bootstrap.start();
        } catch (RuntimeException exception) {
            getLogger().log(Level.SEVERE, "NexusEnchants failed to start and will be disabled", exception);
            bootstrap.stop();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (bootstrap != null) {
            bootstrap.stop();
            bootstrap = null;
        }
    }

    /**
     * Returns the bootstrap that owns the services.
     *
     * @return the bootstrap, or {@code null} before startup and after shutdown
     */
    public PluginBootstrap bootstrap() {
        return bootstrap;
    }

    /**
     * Returns the plugin version.
     *
     * <p>Reads the implementation version from the jar manifest, so it also works
     * on a test server that does not expose plugin metadata.</p>
     *
     * @return the version, or {@code dev} when the manifest has none
     */
    public String pluginVersion() {
        Package pluginPackage = getClass().getPackage();
        if (pluginPackage != null) {
            String version = pluginPackage.getImplementationVersion();
            if (version != null && !version.isBlank()) {
                return version;
            }
        }
        return "dev";
    }
}
