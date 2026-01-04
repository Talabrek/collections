package com.blockworlds.collections.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a player's collection progress.
 */
public class PlayerProgress {

    private final UUID playerId;
    private final Map<String, CollectionProgress> collections;
    private int totalCollectiblesCollected;
    private int totalCollectionsCompleted;
    private long firstCollectionDate;
    private long lastActivityDate;

    public PlayerProgress(UUID playerId) {
        this.playerId = playerId;
        this.collections = new HashMap<>();
        this.totalCollectiblesCollected = 0;
        this.totalCollectionsCompleted = 0;
        this.firstCollectionDate = 0;
        this.lastActivityDate = 0;
    }

    // Getters

    public UUID getPlayerId() {
        return playerId;
    }

    public int getTotalCollectiblesCollected() {
        return totalCollectiblesCollected;
    }

    public int getTotalCollectionsCompleted() {
        return totalCollectionsCompleted;
    }

    public long getFirstCollectionDate() {
        return firstCollectionDate;
    }

    public long getLastActivityDate() {
        return lastActivityDate;
    }

    // Setters for loading from database

    public void setTotalCollectiblesCollected(int count) {
        this.totalCollectiblesCollected = count;
    }

    public void setTotalCollectionsCompleted(int count) {
        this.totalCollectionsCompleted = count;
    }

    public void setFirstCollectionDate(long date) {
        this.firstCollectionDate = date;
    }

    public void setLastActivityDate(long date) {
        this.lastActivityDate = date;
    }

    // Collection progress methods

    /**
     * Get progress for a specific collection, creating if it doesn't exist.
     */
    public CollectionProgress getProgress(String collectionId) {
        return collections.computeIfAbsent(collectionId, id -> new CollectionProgress(collectionId));
    }

    /**
     * Get all collection progress entries.
     */
    public Map<String, CollectionProgress> getAllProgress() {
        return Map.copyOf(collections);
    }

    /**
     * Check if the player has collected a specific item.
     */
    public boolean hasItem(String collectionId, String itemId) {
        CollectionProgress progress = collections.get(collectionId);
        return progress != null && progress.hasItem(itemId);
    }

    /**
     * Add an item to the player's collection.
     *
     * @return true if the item was newly added, false if already had it
     */
    public boolean addItem(String collectionId, String itemId) {
        CollectionProgress progress = getProgress(collectionId);
        boolean added = progress.addItem(itemId);

        if (added) {
            totalCollectiblesCollected++;
            lastActivityDate = System.currentTimeMillis();
            if (firstCollectionDate == 0) {
                firstCollectionDate = lastActivityDate;
            }
        }

        return added;
    }

    /**
     * Mark a collection as complete.
     */
    public void markComplete(String collectionId) {
        CollectionProgress progress = getProgress(collectionId);
        if (!progress.isComplete()) {
            progress.setComplete(true);
            progress.setCompletedDate(System.currentTimeMillis());
            totalCollectionsCompleted++;
        }
    }

    /**
     * Check if the player has completed a collection.
     */
    public boolean hasCompleted(String collectionId) {
        CollectionProgress progress = collections.get(collectionId);
        return progress != null && progress.isComplete();
    }

    /**
     * Check if rewards have been claimed for a collection.
     */
    public boolean hasClaimedReward(String collectionId) {
        CollectionProgress progress = collections.get(collectionId);
        return progress != null && progress.isRewardClaimed();
    }

    /**
     * Mark rewards as claimed for a collection.
     */
    public void claimReward(String collectionId) {
        CollectionProgress progress = getProgress(collectionId);
        progress.setRewardClaimed(true);
    }

    /**
     * Get the count of collected items for a collection.
     */
    public int getCollectedCount(String collectionId) {
        CollectionProgress progress = collections.get(collectionId);
        return progress != null ? progress.getCollectedItems().size() : 0;
    }

    /**
     * Reset progress for a specific collection.
     */
    public void resetCollection(String collectionId) {
        CollectionProgress removed = collections.remove(collectionId);
        if (removed != null && removed.isComplete()) {
            totalCollectionsCompleted = Math.max(0, totalCollectionsCompleted - 1);
        }
    }

    /**
     * Check if a meta-collection's requirements are met.
     *
     * @param requiredCollections List of collection IDs that must be completed
     * @return true if all required collections are complete
     */
    public boolean areMetaRequirementsMet(java.util.List<String> requiredCollections) {
        if (requiredCollections == null || requiredCollections.isEmpty()) {
            return true;
        }

        for (String requiredId : requiredCollections) {
            if (!hasCompleted(requiredId)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Get the count of completed required collections for a meta-collection.
     *
     * @param requiredCollections List of collection IDs that must be completed
     * @return Number of required collections that are complete
     */
    public int getMetaProgressCount(java.util.List<String> requiredCollections) {
        if (requiredCollections == null || requiredCollections.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (String requiredId : requiredCollections) {
            if (hasCompleted(requiredId)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Progress for a single collection.
     */
    public static class CollectionProgress {
        private final String collectionId;
        private final Set<String> collectedItems;
        private boolean complete;
        private boolean rewardClaimed;
        private long completedDate;

        public CollectionProgress(String collectionId) {
            this.collectionId = collectionId;
            this.collectedItems = new HashSet<>();
            this.complete = false;
            this.rewardClaimed = false;
            this.completedDate = 0;
        }

        public String getCollectionId() {
            return collectionId;
        }

        public Set<String> getCollectedItems() {
            return Set.copyOf(collectedItems);
        }

        public boolean hasItem(String itemId) {
            return collectedItems.contains(itemId);
        }

        public boolean addItem(String itemId) {
            return collectedItems.add(itemId);
        }

        public void addItemDirect(String itemId) {
            collectedItems.add(itemId);
        }

        public boolean isComplete() {
            return complete;
        }

        public void setComplete(boolean complete) {
            this.complete = complete;
        }

        public boolean isRewardClaimed() {
            return rewardClaimed;
        }

        public void setRewardClaimed(boolean rewardClaimed) {
            this.rewardClaimed = rewardClaimed;
        }

        public long getCompletedDate() {
            return completedDate;
        }

        public void setCompletedDate(long completedDate) {
            this.completedDate = completedDate;
        }
    }
}
