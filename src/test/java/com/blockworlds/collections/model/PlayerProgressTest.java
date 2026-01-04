package com.blockworlds.collections.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PlayerProgress class.
 */
class PlayerProgressTest {

    private UUID playerId;
    private PlayerProgress progress;

    @BeforeEach
    void setup() {
        playerId = UUID.randomUUID();
        progress = new PlayerProgress(playerId);
    }

    @Test
    @DisplayName("New progress has correct initial values")
    void testNewProgress() {
        assertEquals(playerId, progress.getPlayerId());
        assertEquals(0, progress.getTotalCollectiblesCollected());
        assertEquals(0, progress.getTotalCollectionsCompleted());
        assertEquals(0, progress.getFirstCollectionDate());
        assertEquals(0, progress.getLastActivityDate());
    }

    @Test
    @DisplayName("Adding item increases count and sets dates")
    void testAddItem() {
        boolean added = progress.addItem("collection1", "item1");

        assertTrue(added);
        assertEquals(1, progress.getTotalCollectiblesCollected());
        assertTrue(progress.getFirstCollectionDate() > 0);
        assertTrue(progress.getLastActivityDate() > 0);
    }

    @Test
    @DisplayName("Adding duplicate item returns false")
    void testAddDuplicateItem() {
        progress.addItem("collection1", "item1");
        boolean addedAgain = progress.addItem("collection1", "item1");

        assertFalse(addedAgain);
        assertEquals(1, progress.getTotalCollectiblesCollected());
    }

    @Test
    @DisplayName("hasItem returns correct value")
    void testHasItem() {
        assertFalse(progress.hasItem("collection1", "item1"));

        progress.addItem("collection1", "item1");

        assertTrue(progress.hasItem("collection1", "item1"));
        assertFalse(progress.hasItem("collection1", "item2"));
        assertFalse(progress.hasItem("collection2", "item1"));
    }

    @Test
    @DisplayName("Mark collection complete increases count")
    void testMarkComplete() {
        progress.addItem("collection1", "item1");
        progress.markComplete("collection1");

        assertTrue(progress.hasCompleted("collection1"));
        assertEquals(1, progress.getTotalCollectionsCompleted());
    }

    @Test
    @DisplayName("Mark complete multiple times doesn't double count")
    void testMarkCompleteMultipleTimes() {
        progress.addItem("collection1", "item1");
        progress.markComplete("collection1");
        progress.markComplete("collection1");

        assertEquals(1, progress.getTotalCollectionsCompleted());
    }

    @Test
    @DisplayName("Claim reward sets flag")
    void testClaimReward() {
        assertFalse(progress.hasClaimedReward("collection1"));

        progress.claimReward("collection1");

        assertTrue(progress.hasClaimedReward("collection1"));
    }

    @Test
    @DisplayName("getCollectedCount returns correct count")
    void testGetCollectedCount() {
        assertEquals(0, progress.getCollectedCount("collection1"));

        progress.addItem("collection1", "item1");
        assertEquals(1, progress.getCollectedCount("collection1"));

        progress.addItem("collection1", "item2");
        assertEquals(2, progress.getCollectedCount("collection1"));

        progress.addItem("collection2", "item1");
        assertEquals(2, progress.getCollectedCount("collection1"));
        assertEquals(1, progress.getCollectedCount("collection2"));
    }

    @Test
    @DisplayName("getProgress creates new progress if needed")
    void testGetProgressCreatesNew() {
        PlayerProgress.CollectionProgress colProgress = progress.getProgress("new_collection");

        assertNotNull(colProgress);
        assertEquals("new_collection", colProgress.getCollectionId());
        assertEquals(0, colProgress.getCollectedItems().size());
    }

    @Test
    @DisplayName("getAllProgress returns copy")
    void testGetAllProgressReturnsCopy() {
        progress.addItem("collection1", "item1");
        progress.addItem("collection2", "item1");

        var allProgress = progress.getAllProgress();

        assertEquals(2, allProgress.size());
        assertTrue(allProgress.containsKey("collection1"));
        assertTrue(allProgress.containsKey("collection2"));
    }

    @Test
    @DisplayName("CollectionProgress tracks items correctly")
    void testCollectionProgress() {
        PlayerProgress.CollectionProgress colProgress = new PlayerProgress.CollectionProgress("test");

        assertEquals("test", colProgress.getCollectionId());
        assertEquals(0, colProgress.getCollectedItems().size());
        assertFalse(colProgress.isComplete());
        assertFalse(colProgress.isRewardClaimed());
        assertEquals(0, colProgress.getCompletedDate());

        colProgress.addItem("item1");
        assertTrue(colProgress.hasItem("item1"));
        assertEquals(1, colProgress.getCollectedItems().size());

        colProgress.setComplete(true);
        colProgress.setCompletedDate(12345L);
        assertTrue(colProgress.isComplete());
        assertEquals(12345L, colProgress.getCompletedDate());

        colProgress.setRewardClaimed(true);
        assertTrue(colProgress.isRewardClaimed());
    }
}
