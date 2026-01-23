package com.blockworlds.collections.web;

import com.blockworlds.collections.Collections;
import io.javalin.Javalin;

/**
 * Manages the embedded Javalin web server lifecycle.
 *
 * Handles starting and stopping the web panel, including the critical
 * classloader fix required for Bukkit plugin compatibility.
 */
public class WebPanelManager {

    private final Collections plugin;
    private Javalin app;

    public WebPanelManager(Collections plugin) {
        this.plugin = plugin;
    }

    /**
     * Start the web server on the specified port.
     *
     * Applies the classloader fix required for Javalin to work correctly
     * within a Bukkit plugin context.
     *
     * @param port The port to bind the web server to
     */
    public void start(int port) {
        // WEB-02: Classloader fix for Bukkit compatibility
        // Javalin/Jetty uses ServiceLoader which needs the plugin classloader
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            // Set plugin classloader so Javalin can find its dependencies
            Thread.currentThread().setContextClassLoader(plugin.getClass().getClassLoader());

            // Create Javalin instance with minimal configuration
            this.app = Javalin.create(config -> {
                config.showJavalinBanner = false;
                config.jetty.modifyServer(server -> {
                    server.setStopTimeout(5000);
                });
            });

            // Start the server
            app.start(port);

            plugin.getLogger().info("Web panel started on port " + port);

        } finally {
            // Restore original classloader
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    /**
     * Stop the web server gracefully.
     *
     * This must be called on plugin disable to release the port
     * and allow clean reload.
     */
    public void stop() {
        if (app != null) {
            app.stop();
            app = null;
            plugin.getLogger().info("Web panel stopped");
        }
    }

    /**
     * Check if the web panel is currently running.
     *
     * @return true if the web server is active
     */
    public boolean isRunning() {
        return app != null;
    }

    /**
     * Get the Javalin instance for route registration.
     *
     * @return The Javalin app instance, or null if not running
     */
    public Javalin getApp() {
        return app;
    }
}
