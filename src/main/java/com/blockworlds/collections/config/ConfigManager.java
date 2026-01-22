package com.blockworlds.collections.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages plugin configuration and messages with MiniMessage support.
 */
public class ConfigManager {

    private final Plugin plugin;
    private final MiniMessage miniMessage;

    // Cached configuration values
    private int collectionCooldownMs;
    private int maxCollectiblesPerZone;
    private int defaultRespawnDelaySeconds;
    private int validityCheckIntervalMinutes;
    private int spawnCheckIntervalSeconds;
    private boolean debugMode;

    // Particle settings
    private int particleDistanceBlocks;
    private int particleIntervalTicks;

    // Goggle settings
    private boolean gogglesEnabled;
    private boolean gogglesSoulbound;
    private boolean recipesEnabled;
    private boolean unlockOnFirstCollect;

    // Radar settings
    private boolean radarEnabled;
    private int radarRangeBlocks;
    private int radarUpdateIntervalTicks;

    // Database settings
    private String databaseType;
    private String databasePath;

    // Spawn finder settings
    private int spawnGridSpacing;
    private int spawnInitialRadius;
    private int spawnMaxRadius;
    private int spawnMaxAttemptsPerPass;
    private boolean spawnAllowConditionRelaxation;
    private boolean spawnDebug;
    private int despawnAfterMinutes;

    // Notification settings
    private String progressNotificationStyle;
    private String progressNotificationFormat;
    private String completionNotificationStyle;
    private String completionTitle;
    private String completionSubtitle;
    private double completionFadeIn;
    private double completionStay;
    private double completionFadeOut;

    // Milestone notification settings
    private boolean milestonesEnabled;
    private String milestone25Style;
    private String milestone25Format;
    private String milestone25Sound;
    private boolean milestone25Particles;
    private String milestone50Style;
    private String milestone50Title;
    private String milestone50Format;
    private String milestone50Sound;
    private boolean milestone50Particles;
    private String milestone75Style;
    private String milestone75Title;
    private String milestone75Format;
    private String milestone75Sound;
    private boolean milestone75Particles;

    // Cached messages
    private final Map<String, String> messages;

    // Cached sounds (stored as string names for version compatibility)
    private final Map<String, String> sounds;

    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        this.messages = new HashMap<>();
        this.sounds = new HashMap<>();

        reload();
    }

    /**
     * Reload all configuration values from disk.
     */
    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        // General settings
        collectionCooldownMs = config.getInt("settings.collection-cooldown-ms", 500);
        maxCollectiblesPerZone = config.getInt("settings.max-collectibles-per-zone", 5);
        defaultRespawnDelaySeconds = config.getInt("settings.default-respawn-delay-seconds", 60);
        validityCheckIntervalMinutes = config.getInt("settings.validity-check-interval-minutes", 5);
        spawnCheckIntervalSeconds = config.getInt("settings.spawn-check-interval-seconds", 30);
        debugMode = config.getBoolean("settings.debug", false);

        // Particle settings
        particleDistanceBlocks = config.getInt("particles.distance-blocks", 32);
        particleIntervalTicks = config.getInt("particles.interval-ticks", 10);

        // Goggle settings
        gogglesEnabled = config.getBoolean("goggles.enabled", true);
        gogglesSoulbound = config.getBoolean("goggles.soulbound", true);
        recipesEnabled = config.getBoolean("goggles.recipes.enabled", true);
        unlockOnFirstCollect = config.getBoolean("goggles.recipes.unlock_on_first_collect", true);

        // Radar settings
        radarEnabled = config.getBoolean("radar.enabled", true);
        radarRangeBlocks = config.getInt("radar.range-blocks", 32);
        radarUpdateIntervalTicks = config.getInt("radar.update-interval-ticks", 10);

        // Database settings
        databaseType = config.getString("database.type", "sqlite");
        databasePath = config.getString("database.sqlite.path", "plugins/Collections/collections.db");

        // Spawn finder settings
        spawnGridSpacing = config.getInt("spawn.grid-spacing", 8);
        spawnInitialRadius = config.getInt("spawn.initial-radius", 32);
        spawnMaxRadius = config.getInt("spawn.max-radius", 128);
        spawnMaxAttemptsPerPass = config.getInt("spawn.max-attempts-per-pass", 200);
        spawnAllowConditionRelaxation = config.getBoolean("spawn.allow-condition-relaxation", true);
        spawnDebug = config.getBoolean("spawn.debug", false);
        despawnAfterMinutes = config.getInt("spawn.despawn-after-minutes", 10);

        // Notification settings
        progressNotificationStyle = config.getString("notifications.progress.style", "actionbar");
        progressNotificationFormat = config.getString("notifications.progress.format",
            "<gold><current>/<total></gold> <gray>in</gray> <green><collection></green>");
        completionNotificationStyle = config.getString("notifications.completion.style", "title");
        completionTitle = config.getString("notifications.completion.title",
            "<gold><bold>Collection Complete!</bold></gold>");
        completionSubtitle = config.getString("notifications.completion.subtitle",
            "<green><collection></green>");
        completionFadeIn = config.getDouble("notifications.completion.fade-in", 0.5);
        completionStay = config.getDouble("notifications.completion.stay", 3.0);
        completionFadeOut = config.getDouble("notifications.completion.fade-out", 0.5);

        // Milestone notifications
        milestonesEnabled = config.getBoolean("notifications.milestones.enabled", true);

        milestone25Style = config.getString("notifications.milestones.quarter.style", "actionbar");
        milestone25Format = config.getString("notifications.milestones.quarter.format", "<green>25% Complete!</green> <gray>-</gray> <gold><collection></gold>");
        milestone25Sound = config.getString("notifications.milestones.quarter.sound", "entity.experience_orb.pickup");
        milestone25Particles = config.getBoolean("notifications.milestones.quarter.particles", false);

        milestone50Style = config.getString("notifications.milestones.half.style", "subtitle");
        milestone50Title = config.getString("notifications.milestones.half.title", "<gold>Halfway There!</gold>");
        milestone50Format = config.getString("notifications.milestones.half.format", "<green>50% Complete!</green> <gray>-</gray> <gold><collection></gold>");
        milestone50Sound = config.getString("notifications.milestones.half.sound", "entity.player.levelup");
        milestone50Particles = config.getBoolean("notifications.milestones.half.particles", true);

        milestone75Style = config.getString("notifications.milestones.threequarter.style", "title");
        milestone75Title = config.getString("notifications.milestones.threequarter.title", "<gold><bold>75% Complete!</bold></gold>");
        milestone75Format = config.getString("notifications.milestones.threequarter.format", "<green><collection></green> <gray>- Almost done!</gray>");
        milestone75Sound = config.getString("notifications.milestones.threequarter.sound", "ui.toast.challenge_complete");
        milestone75Particles = config.getBoolean("notifications.milestones.threequarter.particles", true);

        // Load messages
        messages.clear();
        if (config.isConfigurationSection("messages")) {
            for (String key : config.getConfigurationSection("messages").getKeys(true)) {
                Object value = config.get("messages." + key);
                if (value instanceof String stringValue) {
                    messages.put(key, stringValue);
                }
            }
        }

        // Load sounds (store as strings for version compatibility)
        sounds.clear();
        if (config.isConfigurationSection("sounds")) {
            for (String key : config.getConfigurationSection("sounds").getKeys(false)) {
                String soundName = config.getString("sounds." + key);
                if (soundName != null && !soundName.isBlank()) {
                    // Store as string - will be used with player.playSound(loc, soundName, vol, pitch)
                    sounds.put(key, soundName);
                }
            }
        }
    }

    // ========== Message Methods ==========

    /**
     * Get a formatted message component.
     *
     * @param key The message key
     * @return The formatted component
     */
    public Component getMessage(String key) {
        String raw = messages.getOrDefault(key, "<red>Missing message: " + key + "</red>");
        return miniMessage.deserialize(raw);
    }

    /**
     * Get a formatted message component with placeholders.
     *
     * @param key          The message key
     * @param placeholders Placeholder name-value pairs (name1, value1, name2, value2, ...)
     * @return The formatted component
     */
    public Component getMessage(String key, Object... placeholders) {
        String raw = messages.getOrDefault(key, "<red>Missing message: " + key + "</red>");

        if (placeholders.length == 0) {
            return miniMessage.deserialize(raw);
        }

        TagResolver.Builder resolver = TagResolver.builder();
        for (int i = 0; i < placeholders.length - 1; i += 2) {
            String name = String.valueOf(placeholders[i]);
            String value = String.valueOf(placeholders[i + 1]);
            resolver.resolver(Placeholder.unparsed(name, value));
        }

        return miniMessage.deserialize(raw, resolver.build());
    }

    /**
     * Get a raw message string (not parsed).
     *
     * @param key The message key
     * @return The raw message string
     */
    public String getRawMessage(String key) {
        return messages.getOrDefault(key, "Missing message: " + key);
    }

    /**
     * Parse a MiniMessage string into a component.
     *
     * @param text The MiniMessage text
     * @return The parsed component
     */
    public Component parse(String text) {
        return miniMessage.deserialize(text);
    }

    /**
     * Parse a MiniMessage string with placeholders.
     *
     * @param text         The MiniMessage text
     * @param placeholders Placeholder name-value pairs
     * @return The parsed component
     */
    public Component parse(String text, Object... placeholders) {
        if (placeholders.length == 0) {
            return miniMessage.deserialize(text);
        }

        TagResolver.Builder resolver = TagResolver.builder();
        for (int i = 0; i < placeholders.length - 1; i += 2) {
            String name = String.valueOf(placeholders[i]);
            String value = String.valueOf(placeholders[i + 1]);
            resolver.resolver(Placeholder.unparsed(name, value));
        }

        return miniMessage.deserialize(text, resolver.build());
    }

    // ========== General Settings ==========

    public int getCollectionCooldownMs() {
        return collectionCooldownMs;
    }

    public int getMaxCollectiblesPerZone() {
        return maxCollectiblesPerZone;
    }

    public int getDefaultRespawnDelaySeconds() {
        return defaultRespawnDelaySeconds;
    }

    public int getValidityCheckIntervalMinutes() {
        return validityCheckIntervalMinutes;
    }

    public int getSpawnCheckIntervalSeconds() {
        return spawnCheckIntervalSeconds;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }

    // ========== Particle Settings ==========

    public int getParticleDistanceBlocks() {
        return particleDistanceBlocks;
    }

    public int getParticleIntervalTicks() {
        return particleIntervalTicks;
    }

    // ========== Goggle Settings ==========

    public boolean isGogglesEnabled() {
        return gogglesEnabled;
    }

    public boolean isGogglesSoulbound() {
        return gogglesSoulbound;
    }

    public boolean areRecipesEnabled() {
        return recipesEnabled;
    }

    public boolean shouldUnlockOnFirstCollect() {
        return unlockOnFirstCollect;
    }

    // ========== Radar Settings ==========

    public boolean isRadarEnabled() {
        return radarEnabled;
    }

    public int getRadarRangeBlocks() {
        return radarRangeBlocks;
    }

    public int getRadarUpdateIntervalTicks() {
        return radarUpdateIntervalTicks;
    }

    // ========== Database Settings ==========

    public String getDatabaseType() {
        return databaseType;
    }

    public String getDatabasePath() {
        return databasePath;
    }

    // ========== Spawn Finder Settings ==========

    public int getSpawnGridSpacing() {
        return spawnGridSpacing;
    }

    public int getSpawnInitialRadius() {
        return spawnInitialRadius;
    }

    public int getSpawnMaxRadius() {
        return spawnMaxRadius;
    }

    public int getSpawnMaxAttemptsPerPass() {
        return spawnMaxAttemptsPerPass;
    }

    public boolean isSpawnAllowConditionRelaxation() {
        return spawnAllowConditionRelaxation;
    }

    public boolean isSpawnDebug() {
        return spawnDebug;
    }

    public int getDespawnAfterMinutes() {
        return despawnAfterMinutes;
    }

    // ========== Utility Methods ==========

    /**
     * Get a string from config.
     *
     * @param path         The config path
     * @param defaultValue The default value
     * @return The config value or default
     */
    public String getString(String path, String defaultValue) {
        return plugin.getConfig().getString(path, defaultValue);
    }

    /**
     * Get an int from config.
     *
     * @param path         The config path
     * @param defaultValue The default value
     * @return The config value or default
     */
    public int getInt(String path, int defaultValue) {
        return plugin.getConfig().getInt(path, defaultValue);
    }

    /**
     * Get a boolean from config.
     *
     * @param path         The config path
     * @param defaultValue The default value
     * @return The config value or default
     */
    public boolean getBoolean(String path, boolean defaultValue) {
        return plugin.getConfig().getBoolean(path, defaultValue);
    }

    /**
     * Get a string list from config.
     *
     * @param path The config path
     * @return The config value or empty list
     */
    public List<String> getStringList(String path) {
        return plugin.getConfig().getStringList(path);
    }

    /**
     * Get the MiniMessage instance.
     *
     * @return The MiniMessage instance
     */
    public MiniMessage getMiniMessage() {
        return miniMessage;
    }

    // ========== Sound Methods ==========

    /**
     * Get a sound name by key from the config.
     * Use with player.playSound(location, soundName, volume, pitch).
     *
     * @param key The sound key (e.g., "collect-item", "add-to-journal")
     * @return The sound name string, or null if not configured
     */
    public String getSound(String key) {
        return sounds.get(key);
    }

    /**
     * Check if a sound is configured.
     *
     * @param key The sound key
     * @return true if the sound exists in config
     */
    public boolean hasSound(String key) {
        return sounds.containsKey(key);
    }

    // ========== Notification Settings ==========

    public String getProgressNotificationStyle() {
        return progressNotificationStyle;
    }

    public String getProgressNotificationFormat() {
        return progressNotificationFormat;
    }

    public String getCompletionNotificationStyle() {
        return completionNotificationStyle;
    }

    public String getCompletionTitle() {
        return completionTitle;
    }

    public String getCompletionSubtitle() {
        return completionSubtitle;
    }

    public double getCompletionFadeIn() {
        return completionFadeIn;
    }

    public double getCompletionStay() {
        return completionStay;
    }

    public double getCompletionFadeOut() {
        return completionFadeOut;
    }

    // ========== Milestone Notification Settings ==========

    public boolean isMilestonesEnabled() {
        return milestonesEnabled;
    }

    public String getMilestoneStyle(int percent) {
        return switch (percent) {
            case 25 -> milestone25Style;
            case 50 -> milestone50Style;
            case 75 -> milestone75Style;
            default -> "none";
        };
    }

    public String getMilestoneTitle(int percent) {
        return switch (percent) {
            case 50 -> milestone50Title;
            case 75 -> milestone75Title;
            default -> "";
        };
    }

    public String getMilestoneFormat(int percent) {
        return switch (percent) {
            case 25 -> milestone25Format;
            case 50 -> milestone50Format;
            case 75 -> milestone75Format;
            default -> "";
        };
    }

    public String getMilestoneSound(int percent) {
        return switch (percent) {
            case 25 -> milestone25Sound;
            case 50 -> milestone50Sound;
            case 75 -> milestone75Sound;
            default -> null;
        };
    }

    public boolean getMilestoneParticles(int percent) {
        return switch (percent) {
            case 25 -> milestone25Particles;
            case 50 -> milestone50Particles;
            case 75 -> milestone75Particles;
            default -> false;
        };
    }
}
