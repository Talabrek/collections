package com.blockworlds.collections.web.api;

import com.blockworlds.collections.Collections;
import com.blockworlds.collections.model.Collection;
import com.blockworlds.collections.model.CollectionItem;
import com.blockworlds.collections.web.MainThreadBridge;
import com.blockworlds.collections.web.api.dto.CollectionDetail;
import com.blockworlds.collections.web.api.dto.CollectionRequest;
import com.blockworlds.collections.web.api.dto.CollectionSummary;
import com.blockworlds.collections.web.api.dto.ItemSummary;
import com.blockworlds.collections.web.api.dto.RewardSummary;
import com.blockworlds.collections.web.api.dto.ValidationErrorResponse;
import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ConflictResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.InternalServerErrorResponse;
import io.javalin.http.NotFoundResponse;
import org.bukkit.Material;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * REST API controller for collection data.
 *
 * Provides endpoints for listing and viewing collection details.
 * All CollectionManager access is thread-safe via MainThreadBridge.
 */
public class CollectionsController {

    private static final long TIMEOUT_MS = 2000;

    private final Collections plugin;
    private final MainThreadBridge mainThreadBridge;
    private final CollectionValidator validator = new CollectionValidator();
    private final CollectionYamlWriter yamlWriter = new CollectionYamlWriter();

    /**
     * Create a collections controller instance.
     *
     * @param plugin           The Collections plugin instance
     * @param mainThreadBridge Bridge for thread-safe Bukkit API access
     */
    public CollectionsController(Collections plugin, MainThreadBridge mainThreadBridge) {
        this.plugin = plugin;
        this.mainThreadBridge = mainThreadBridge;
    }

    /**
     * Register the collections API routes.
     *
     * @param app The Javalin application instance
     */
    public void register(Javalin app) {
        app.get("/api/collections", this::listCollections);
        app.get("/api/collections/{id}", this::getCollection);
        app.post("/api/collections", this::createCollection);
        app.put("/api/collections/{id}", this::updateCollection);
        app.delete("/api/collections/{id}", this::deleteCollection);
        app.post("/api/reload", this::reloadCollections);
        app.get("/api/materials", this::listMaterials);
    }

    /**
     * GET /api/collections - List all collections.
     *
     * Returns JSON array of collection summaries sorted by name.
     */
    private void listCollections(Context ctx) {
        try {
            List<CollectionSummary> summaries = mainThreadBridge.callSync(() -> {
                return plugin.getCollectionManager().getAllCollections().values().stream()
                        .map(this::toSummary)
                        .sorted(Comparator.comparing(CollectionSummary::name))
                        .toList();
            }, TIMEOUT_MS);

            ctx.json(summaries);
        } catch (MainThreadBridge.MainThreadException e) {
            throw new InternalServerErrorResponse("Server busy, please retry");
        }
    }

    /**
     * GET /api/collections/{id} - Get collection details.
     *
     * Returns JSON object with full collection information.
     * Returns 404 if collection not found.
     */
    private void getCollection(Context ctx) {
        String id = ctx.pathParam("id");

        try {
            Collection collection = mainThreadBridge.callSync(() -> {
                return plugin.getCollectionManager().getCollection(id);
            }, TIMEOUT_MS);

            if (collection == null) {
                throw new NotFoundResponse("Collection not found: " + id);
            }

            ctx.json(toDetail(collection));
        } catch (MainThreadBridge.MainThreadException e) {
            throw new InternalServerErrorResponse("Server busy, please retry");
        }
    }

    /**
     * POST /api/collections - Create a new collection.
     *
     * Validates the request, checks for duplicate ID, writes YAML file,
     * and reloads collections. Returns 201 Created on success.
     */
    private void createCollection(Context ctx) {
        // Parse body
        CollectionRequest request;
        try {
            request = ctx.bodyAsClass(CollectionRequest.class);
        } catch (Exception e) {
            throw new BadRequestResponse("Invalid JSON body");
        }

        // Validate request
        CollectionValidator.ValidationResult validation = validator.validate(request);
        if (!validation.isValid()) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(ValidationErrorResponse.of(validation.errors()));
            return;
        }

        // Execute on main thread: check duplicate, write file, reload
        try {
            mainThreadBridge.runSyncAndWait(() -> {
                // Check for duplicate ID
                if (plugin.getCollectionManager().hasCollection(request.id())) {
                    throw new RuntimeException("CONFLICT:" + request.id());
                }

                // Write YAML file
                File file = new File(plugin.getDataFolder(), "collections/" + request.id() + ".yml");
                try {
                    yamlWriter.write(request, file);
                } catch (IOException e) {
                    throw new RuntimeException("IO_ERROR", e);
                }

                // Reload collections
                plugin.getCollectionManager().loadCollections();
            }, TIMEOUT_MS);

            ctx.status(HttpStatus.CREATED);
            ctx.json(Map.of("success", true, "id", request.id()));
        } catch (MainThreadBridge.MainThreadException e) {
            handleMainThreadError(e);
        }
    }

    /**
     * PUT /api/collections/{id} - Update an existing collection.
     *
     * Validates the request, verifies collection exists, writes YAML file,
     * and reloads collections. Returns 200 OK on success.
     */
    private void updateCollection(Context ctx) {
        String id = ctx.pathParam("id");

        // Parse body
        CollectionRequest request;
        try {
            request = ctx.bodyAsClass(CollectionRequest.class);
        } catch (Exception e) {
            throw new BadRequestResponse("Invalid JSON body");
        }

        // If body has an id, it must match the path
        if (request.id() != null && !request.id().equals(id)) {
            throw new BadRequestResponse("ID in body does not match path");
        }

        // Create request with path ID if body ID is null
        CollectionRequest effectiveRequest = request.id() != null ? request :
            new CollectionRequest(id, request.name(), request.description(),
                request.tier(), request.icon(), request.items(),
                request.rewards(), request.zones(), request.requires());

        // Validate request
        CollectionValidator.ValidationResult validation = validator.validate(effectiveRequest);
        if (!validation.isValid()) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(ValidationErrorResponse.of(validation.errors()));
            return;
        }

        // Execute on main thread: check exists, write file, reload
        try {
            mainThreadBridge.runSyncAndWait(() -> {
                // Check collection exists
                if (!plugin.getCollectionManager().hasCollection(id)) {
                    throw new RuntimeException("NOT_FOUND:" + id);
                }

                // Write YAML file (overwrites existing)
                File file = new File(plugin.getDataFolder(), "collections/" + id + ".yml");
                try {
                    yamlWriter.write(effectiveRequest, file);
                } catch (IOException e) {
                    throw new RuntimeException("IO_ERROR", e);
                }

                // Reload collections
                plugin.getCollectionManager().loadCollections();
            }, TIMEOUT_MS);

            ctx.json(Map.of("success", true, "id", id));
        } catch (MainThreadBridge.MainThreadException e) {
            handleMainThreadError(e);
        }
    }

    /**
     * DELETE /api/collections/{id} - Delete a collection.
     *
     * Validates ID format, verifies collection exists, deletes YAML file,
     * and reloads collections. Returns 204 No Content on success.
     */
    private void deleteCollection(Context ctx) {
        String id = ctx.pathParam("id");

        // Validate ID format for security (path traversal prevention)
        if (!isValidCollectionId(id)) {
            throw new BadRequestResponse("Invalid collection ID format");
        }

        // Execute on main thread: check exists, delete file, reload
        try {
            mainThreadBridge.runSyncAndWait(() -> {
                // Check collection exists
                if (!plugin.getCollectionManager().hasCollection(id)) {
                    throw new RuntimeException("NOT_FOUND:" + id);
                }

                // Delete file
                File file = new File(plugin.getDataFolder(), "collections/" + id + ".yml");
                if (file.exists() && !file.delete()) {
                    throw new RuntimeException("IO_ERROR");
                }

                // Reload collections
                plugin.getCollectionManager().loadCollections();
            }, TIMEOUT_MS);

            ctx.status(HttpStatus.NO_CONTENT);
        } catch (MainThreadBridge.MainThreadException e) {
            handleMainThreadError(e);
        }
    }

    /**
     * POST /api/reload - Reload all collections from disk.
     *
     * Triggers CollectionManager.loadCollections() and returns the count.
     */
    private void reloadCollections(Context ctx) {
        try {
            int count = mainThreadBridge.callSync(() -> {
                plugin.getCollectionManager().loadCollections();
                return plugin.getCollectionManager().getAllCollections().size();
            }, TIMEOUT_MS);

            ctx.json(Map.of("success", true, "collectionCount", count));
        } catch (MainThreadBridge.MainThreadException e) {
            throw new InternalServerErrorResponse("Server busy, please retry");
        }
    }

    /**
     * GET /api/materials - List all valid Minecraft material names.
     *
     * Returns JSON array of material names sorted alphabetically.
     * Filters out legacy materials and non-item materials.
     */
    private void listMaterials(Context ctx) {
        // No main thread needed - Material enum is static
        List<String> materials = Arrays.stream(Material.values())
            .filter(m -> !m.isLegacy())
            .filter(Material::isItem)
            .map(Material::name)
            .sorted()
            .toList();
        ctx.json(materials);
    }

    /**
     * Handle errors from main thread execution.
     *
     * Translates RuntimeException messages into appropriate HTTP responses.
     */
    private void handleMainThreadError(MainThreadBridge.MainThreadException e) {
        Throwable cause = e.getCause();
        if (cause instanceof RuntimeException) {
            String message = cause.getMessage();
            if (message != null) {
                if (message.startsWith("CONFLICT:")) {
                    throw new ConflictResponse("Collection already exists: " + message.substring(9));
                }
                if (message.startsWith("NOT_FOUND:")) {
                    throw new NotFoundResponse("Collection not found: " + message.substring(10));
                }
                if (message.equals("IO_ERROR")) {
                    throw new InternalServerErrorResponse("Failed to save collection");
                }
            }
        }
        throw new InternalServerErrorResponse("Server busy, please retry");
    }

    /**
     * Validate collection ID format for security.
     * Prevents path traversal attacks by ensuring ID contains only safe characters.
     */
    private boolean isValidCollectionId(String id) {
        return id != null && id.matches("^[a-z0-9_]+$");
    }

    /**
     * Map a Collection to a CollectionSummary DTO.
     */
    private CollectionSummary toSummary(Collection c) {
        return new CollectionSummary(
                c.id(),
                c.name(),
                c.tier().name(),
                c.getItemCount(),
                c.allowedZones()
        );
    }

    /**
     * Map a Collection to a CollectionDetail DTO.
     */
    private CollectionDetail toDetail(Collection c) {
        List<ItemSummary> items = c.items().stream()
                .map(this::toItemSummary)
                .toList();

        RewardSummary rewards = toRewardSummary(c.rewards());

        return new CollectionDetail(
                c.id(),
                c.name(),
                c.description(),
                c.tier().name(),
                c.icon().name(),
                items,
                rewards,
                c.allowedZones(),
                c.requiredCollections()
        );
    }

    /**
     * Map a CollectionItem to an ItemSummary DTO.
     */
    private ItemSummary toItemSummary(CollectionItem item) {
        return new ItemSummary(
                item.id(),
                item.name(),
                item.material().name(),
                item.weight(),
                item.soulbound(),
                item.lore()
        );
    }

    /**
     * Map CollectionRewards to a RewardSummary DTO.
     */
    private RewardSummary toRewardSummary(Collection.CollectionRewards rewards) {
        return new RewardSummary(
                rewards.experience(),
                rewards.commands(),
                rewards.fireworks()
        );
    }
}
