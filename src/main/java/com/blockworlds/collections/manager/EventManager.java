package com.blockworlds.collections.manager;

import com.blockworlds.collections.Collections;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages collection events for EVENT tier collectibles.
 * Events make EVENT tier collectibles visible to all players, regardless of goggles.
 */
public class EventManager {

    private final Collections plugin;

    // Active events: name -> event data
    private final Map<String, ActiveEvent> activeEvents = new ConcurrentHashMap<>();

    public EventManager(Collections plugin) {
        this.plugin = plugin;
    }

    /**
     * Check if any event is currently active.
     *
     * @return true if at least one event is active
     */
    public boolean isAnyEventActive() {
        return !activeEvents.isEmpty();
    }

    /**
     * Check if a specific event is active.
     *
     * @param eventName The event name to check
     * @return true if the specified event is active
     */
    public boolean isEventActive(String eventName) {
        return activeEvents.containsKey(eventName.toLowerCase());
    }

    /**
     * Start an event manually.
     *
     * @param eventName The event name
     * @param startedBy The player/console who started it (for logging)
     * @return true if the event was started, false if already active
     */
    public boolean startEvent(String eventName, String startedBy) {
        String key = eventName.toLowerCase();

        if (activeEvents.containsKey(key)) {
            return false; // Already active
        }

        ActiveEvent event = new ActiveEvent(eventName, System.currentTimeMillis(), startedBy);
        activeEvents.put(key, event);

        plugin.getLogger().info("Event '" + eventName + "' started by " + startedBy);

        // Refresh visibility for all online players
        refreshAllPlayerVisibility();

        return true;
    }

    /**
     * End a specific event.
     *
     * @param eventName The event name to end
     * @return true if the event was ended, false if not active
     */
    public boolean endEvent(String eventName) {
        String key = eventName.toLowerCase();

        ActiveEvent removed = activeEvents.remove(key);
        if (removed == null) {
            return false; // Was not active
        }

        long duration = System.currentTimeMillis() - removed.startTime();
        plugin.getLogger().info("Event '" + eventName + "' ended after " +
                formatDuration(duration));

        // Refresh visibility for all online players
        refreshAllPlayerVisibility();

        return true;
    }

    /**
     * End all active events.
     *
     * @return the number of events ended
     */
    public int endAllEvents() {
        int count = activeEvents.size();

        if (count > 0) {
            activeEvents.clear();
            plugin.getLogger().info("Ended all " + count + " active events");

            // Refresh visibility for all online players
            refreshAllPlayerVisibility();
        }

        return count;
    }

    /**
     * Get all active event names.
     *
     * @return set of active event names
     */
    public Set<String> getActiveEventNames() {
        return Set.copyOf(activeEvents.keySet());
    }

    /**
     * Get the number of active events.
     *
     * @return count of active events
     */
    public int getActiveEventCount() {
        return activeEvents.size();
    }

    /**
     * Get details about an active event.
     *
     * @param eventName The event name
     * @return the event data, or null if not active
     */
    public ActiveEvent getEvent(String eventName) {
        return activeEvents.get(eventName.toLowerCase());
    }

    /**
     * Refresh collectible visibility for all online players.
     * Called when events start/end to update what players can see.
     */
    private void refreshAllPlayerVisibility() {
        GoggleManager goggleManager = plugin.getGoggleManager();
        if (goggleManager == null) return;

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            goggleManager.refreshVisibilityForPlayer(player);
        }
    }

    /**
     * Format a duration in milliseconds to a human-readable string.
     */
    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (hours > 0) {
            return hours + "h " + (minutes % 60) + "m";
        } else if (minutes > 0) {
            return minutes + "m " + (seconds % 60) + "s";
        } else {
            return seconds + "s";
        }
    }

    /**
     * Data class representing an active event.
     */
    public record ActiveEvent(String name, long startTime, String startedBy) {

        /**
         * Get how long this event has been running.
         *
         * @return duration in milliseconds
         */
        public long getDuration() {
            return System.currentTimeMillis() - startTime;
        }
    }
}
