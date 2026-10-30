package com.nexus.enchants.bootstrap;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal typed service container.
 *
 * <p>Services are constructed once in {@link PluginBootstrap} and looked up by
 * their interface, which keeps command and listener classes free of
 * construction order concerns.</p>
 */
public final class ServiceRegistry {

    private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();

    /**
     * Creates an empty registry.
     */
    public ServiceRegistry() {
    }

    /**
     * Registers a service.
     *
     * @param type     service type
     * @param instance service instance
     * @param <T>      service type
     * @throws IllegalStateException when the type is already registered
     */
    public <T> void register(Class<T> type, T instance) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(instance, "instance");
        Object previous = services.putIfAbsent(type, instance);
        if (previous != null) {
            throw new IllegalStateException("Service already registered: " + type.getName());
        }
    }

    /**
     * Returns a registered service.
     *
     * @param type service type
     * @param <T>  service type
     * @return the service
     * @throws IllegalStateException when the service is missing
     */
    public <T> T require(Class<T> type) {
        return find(type).orElseThrow(() -> new IllegalStateException("Missing service: " + type.getName()));
    }

    /**
     * Returns a registered service if present.
     *
     * @param type service type
     * @param <T>  service type
     * @return the service
     */
    public <T> Optional<T> find(Class<T> type) {
        return Optional.ofNullable(type.cast(services.get(type)));
    }

    /**
     * Removes every service.
     */
    public void clear() {
        services.clear();
    }

    /**
     * Returns how many services are registered.
     *
     * @return the service count
     */
    public int size() {
        return services.size();
    }
}
