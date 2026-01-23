package com.blockworlds.collections.web.api;

import com.blockworlds.collections.Collections;
import io.javalin.Javalin;

import java.util.Map;

/**
 * Status API endpoint for health checks and basic server info.
 *
 * Provides authenticated clients with server status information including
 * plugin version, collection counts, and zone counts.
 */
public class StatusController {

    private final Collections plugin;

    /**
     * Create a status controller instance.
     *
     * @param plugin The Collections plugin instance
     */
    public StatusController(Collections plugin) {
        this.plugin = plugin;
    }

    /**
     * Register the status API route.
     *
     * @param app The Javalin application instance
     */
    public void register(Javalin app) {
        app.get("/api/status", ctx -> {
            ctx.json(Map.of(
                "status", "ok",
                "plugin", "Collections",
                "version", plugin.getPluginMeta().getVersion(),
                "collections", plugin.getCollectionManager().getCollectionCount(),
                "zones", plugin.getZoneManager().getZoneCount()
            ));
        });
    }
}
