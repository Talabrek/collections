package com.example.collections;

import org.bukkit.plugin.java.JavaPlugin;

public final class CollectionsPlugin extends JavaPlugin {

    private static CollectionsPlugin instance;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        registerCommands();
        registerListeners();

        getLogger().info("Collections enabled!");
    }

    private void registerCommands() {
        // Register Brigadier commands here
    }

    private void registerListeners() {
        // Register event listeners here
    }

    @Override
    public void onDisable() {
        getLogger().info("Collections disabled!");
    }

    public static CollectionsPlugin getInstance() {
        return instance;
    }
}
