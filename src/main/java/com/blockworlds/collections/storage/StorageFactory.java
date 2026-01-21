package com.blockworlds.collections.storage;

import com.blockworlds.collections.Collections;

/**
 * Factory for creating Storage implementations based on configuration.
 */
public final class StorageFactory {

    private StorageFactory() {
        // Utility class
    }

    /**
     * Create a Storage implementation based on the configured database type.
     *
     * @param plugin The plugin instance
     * @return The appropriate Storage implementation
     */
    public static Storage createStorage(Collections plugin) {
        String type = plugin.getConfigManager().getDatabaseType();

        return switch (type.toLowerCase()) {
            case "mysql" -> new MySQLStorage(plugin);
            case "sqlite" -> new SQLiteStorage(plugin);
            default -> {
                plugin.getLogger().warning("Unknown database type: " + type + ", defaulting to SQLite");
                yield new SQLiteStorage(plugin);
            }
        };
    }
}
