package com.blockworlds.collections.web.api;

import com.blockworlds.collections.Collections;
import com.blockworlds.collections.model.Collection;
import com.blockworlds.collections.model.CollectionItem;
import com.blockworlds.collections.web.MainThreadBridge;
import com.blockworlds.collections.web.api.dto.CollectionDetail;
import com.blockworlds.collections.web.api.dto.CollectionSummary;
import com.blockworlds.collections.web.api.dto.ItemSummary;
import com.blockworlds.collections.web.api.dto.RewardSummary;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.InternalServerErrorResponse;
import io.javalin.http.NotFoundResponse;

import java.util.Comparator;
import java.util.List;

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
