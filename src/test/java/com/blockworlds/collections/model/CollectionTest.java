package com.blockworlds.collections.model;

import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Collection record.
 */
class CollectionTest {

    private Collection collection;
    private List<CollectionItem> items;

    @BeforeEach
    void setup() {
        items = List.of(
                new CollectionItem("item1", "Item 1", Material.DIAMOND, List.of(), 10, true, null, null),
                new CollectionItem("item2", "Item 2", Material.GOLD_INGOT, List.of(), 5, true, null, null),
                new CollectionItem("item3", "Item 3", Material.IRON_INGOT, List.of(), 2, true, null, null)
        );

        collection = new Collection(
                "test_collection",
                "Test Collection",
                "A test collection",
                CollectibleTier.COMMON,
                items,
                Collection.CollectionRewards.EMPTY,
                List.of(),
                List.of(),
                "",
                Material.PAPER,
                null
        );
    }

    @Test
    @DisplayName("Collection properties are correctly set")
    void testCollectionProperties() {
        assertEquals("test_collection", collection.id());
        assertEquals("Test Collection", collection.name());
        assertEquals("A test collection", collection.description());
        assertEquals(CollectibleTier.COMMON, collection.tier());
        assertEquals(3, collection.items().size());
        assertEquals(Material.PAPER, collection.icon());
    }

    @Test
    @DisplayName("getRandomItem returns items based on weight")
    void testGetRandomItemWeight() {
        // Run many iterations to verify weighted distribution
        Map<String, Integer> counts = new HashMap<>();
        int iterations = 1000;

        for (int i = 0; i < iterations; i++) {
            CollectionItem item = collection.getRandomItem();
            counts.merge(item.id(), 1, Integer::sum);
        }

        // item1 (weight 10) should appear more than item3 (weight 2)
        assertTrue(counts.get("item1") > counts.get("item3"),
                "item1 with weight 10 should appear more than item3 with weight 2");
    }

    @Test
    @DisplayName("getItem finds item by ID")
    void testGetItem() {
        CollectionItem found = collection.getItem("item2");
        assertNotNull(found);
        assertEquals("Item 2", found.name());
        assertEquals(Material.GOLD_INGOT, found.material());
    }

    @Test
    @DisplayName("getItem returns null for unknown ID")
    void testGetItemNotFound() {
        CollectionItem found = collection.getItem("unknown");
        assertNull(found);
    }

    @Test
    @DisplayName("Collection with empty rewards")
    void testEmptyRewards() {
        assertEquals(0, collection.rewards().experience());
        assertTrue(collection.rewards().items().isEmpty());
        assertTrue(collection.rewards().commands().isEmpty());
        assertEquals("", collection.rewards().message());
    }

    @Test
    @DisplayName("Collection rewards contain data")
    void testCollectionWithRewards() {
        Collection.CollectionRewards rewards = new Collection.CollectionRewards(
                100,
                List.of(new Collection.RewardItem(Material.DIAMOND, 5, "Reward Diamond", List.of())),
                List.of("give %player% diamond 1"),
                "Congratulations!",
                true,  // fireworks
                "ui.toast.challenge_complete"  // sound
        );

        Collection col = new Collection(
                "rewarding",
                "Rewarding Collection",
                "Has rewards",
                CollectibleTier.RARE,
                items,
                rewards,
                List.of(),
                List.of(),
                "",
                Material.CHEST,
                null
        );

        assertEquals(100, col.rewards().experience());
        assertEquals(1, col.rewards().items().size());
        assertEquals(Material.DIAMOND, col.rewards().items().get(0).material());
        assertEquals(5, col.rewards().items().get(0).amount());
        assertEquals(1, col.rewards().commands().size());
        assertEquals("Congratulations!", col.rewards().message());
    }

    @Test
    @DisplayName("Meta-collection requirements")
    void testRequiredCollections() {
        Collection metaCollection = new Collection(
                "meta",
                "Meta Collection",
                "Requires other collections",
                CollectibleTier.RARE,
                items,
                Collection.CollectionRewards.EMPTY,
                List.of("collection1", "collection2"),
                List.of(),
                "",
                Material.NETHER_STAR,
                null
        );

        assertEquals(2, metaCollection.requiredCollections().size());
        assertTrue(metaCollection.requiredCollections().contains("collection1"));
        assertTrue(metaCollection.requiredCollections().contains("collection2"));
    }

    @Test
    @DisplayName("Allowed zones restriction")
    void testAllowedZones() {
        Collection zonedCollection = new Collection(
                "zoned",
                "Zoned Collection",
                "Only in certain zones",
                CollectibleTier.COMMON,
                items,
                Collection.CollectionRewards.EMPTY,
                List.of(),
                List.of("zone1", "zone2"),
                "",
                Material.MAP,
                null
        );

        assertEquals(2, zonedCollection.allowedZones().size());
        assertTrue(zonedCollection.allowedZones().contains("zone1"));
        assertTrue(zonedCollection.allowedZones().contains("zone2"));
    }
}
