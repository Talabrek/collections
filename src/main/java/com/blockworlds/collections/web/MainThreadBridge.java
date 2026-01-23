package com.blockworlds.collections.web;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Bridge utility for safely accessing Bukkit API from web request handlers.
 *
 * Web routes execute on Jetty's thread pool, but most Bukkit API calls
 * must be made from the main server thread. This class provides methods
 * to safely dispatch work to the main thread and wait for results.
 */
public class MainThreadBridge {

    private final Plugin plugin;

    public MainThreadBridge(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Execute a task on the main thread and wait for the result.
     *
     * If already on the main thread, executes immediately.
     * Otherwise, schedules via Bukkit scheduler and waits with timeout.
     *
     * @param task The task to execute (returns a value)
     * @param timeoutMs Maximum time to wait in milliseconds
     * @param <T> The return type
     * @return The result from the task
     * @throws MainThreadException if the task fails or times out
     */
    public <T> T callSync(Supplier<T> task, long timeoutMs) throws MainThreadException {
        // If already on main thread, execute directly
        if (Bukkit.isPrimaryThread()) {
            try {
                return task.get();
            } catch (Exception e) {
                throw new MainThreadException("Task execution failed", e);
            }
        }

        // Schedule on main thread and wait for result
        CompletableFuture<T> future = new CompletableFuture<>();

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                T result = task.get();
                future.complete(result);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new MainThreadException("Task timed out after " + timeoutMs + "ms", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MainThreadException("Task interrupted", e);
        } catch (ExecutionException e) {
            throw new MainThreadException("Task execution failed", e.getCause());
        }
    }

    /**
     * Execute a task on the main thread without waiting for completion.
     *
     * Use this for fire-and-forget operations where you don't need a result.
     * If already on main thread, executes immediately.
     *
     * @param task The task to execute
     */
    public void runSync(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * Execute a task on the main thread and wait for completion.
     *
     * Similar to runSync but blocks until the task completes.
     *
     * @param task The task to execute
     * @param timeoutMs Maximum time to wait in milliseconds
     * @throws MainThreadException if the task fails or times out
     */
    public void runSyncAndWait(Runnable task, long timeoutMs) throws MainThreadException {
        callSync(() -> {
            task.run();
            return null;
        }, timeoutMs);
    }

    /**
     * Exception thrown when a main thread operation fails.
     */
    public static class MainThreadException extends Exception {
        public MainThreadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
