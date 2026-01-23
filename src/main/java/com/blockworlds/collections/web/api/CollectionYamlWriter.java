package com.blockworlds.collections.web.api;

import com.blockworlds.collections.web.api.dto.CollectionRequest;
import com.blockworlds.collections.web.api.dto.ItemRequest;
import com.blockworlds.collections.web.api.dto.RewardRequest;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

/**
 * Converts CollectionRequest to YAML format and saves to file.
 *
 * Produces YAML files compatible with CollectionManager.loadCollectionFile().
 * Handles null fields gracefully by not setting keys for empty/null values.
 */
public class CollectionYamlWriter {

    /**
     * Write a collection request to a YAML file.
     *
     * @param request The collection request to write
     * @param file    The file to write to
     * @throws IOException If file writing fails
     */
    public void write(CollectionRequest request, File file) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();

        // Basic fields
        yaml.set("id", request.id());
        yaml.set("name", request.name());

        if (request.description() != null && !request.description().isBlank()) {
            yaml.set("description", request.description());
        }

        if (request.tier() != null && !request.tier().isBlank()) {
            yaml.set("tier", request.tier().toUpperCase());
        }

        if (request.icon() != null && !request.icon().isBlank()) {
            yaml.set("icon", request.icon().toUpperCase());
        }

        // Zones list
        if (request.zones() != null && !request.zones().isEmpty()) {
            yaml.set("zones", request.zones());
        }

        // Required collections list
        if (request.requires() != null && !request.requires().isEmpty()) {
            yaml.set("requires", request.requires());
        }

        // Spawn conditions
        if (request.biomes() != null && !request.biomes().isEmpty()) {
            yaml.set("biomes", request.biomes());
        }

        if (request.dimensions() != null && !request.dimensions().isEmpty()) {
            yaml.set("dimensions", request.dimensions());
        }

        if (request.minY() != null && request.minY() != -64) {
            yaml.set("min-y", request.minY());
        }

        if (request.maxY() != null && request.maxY() != 320) {
            yaml.set("max-y", request.maxY());
        }

        // Items section
        if (request.items() != null) {
            for (ItemRequest item : request.items()) {
                writeItem(yaml, item);
            }
        }

        // Rewards section
        if (request.rewards() != null) {
            writeRewards(yaml, request.rewards());
        }

        // Save to file
        yaml.save(file);
    }

    /**
     * Write an item to the YAML configuration.
     *
     * @param yaml The YAML configuration
     * @param item The item to write
     */
    private void writeItem(YamlConfiguration yaml, ItemRequest item) {
        if (item == null || item.id() == null) {
            return;
        }

        String prefix = "items." + item.id();

        yaml.set(prefix + ".name", item.name());

        if (item.material() != null && !item.material().isBlank()) {
            yaml.set(prefix + ".material", item.material().toUpperCase());
        }

        if (item.lore() != null && !item.lore().isEmpty()) {
            yaml.set(prefix + ".lore", item.lore());
        }

        // Weight defaults to 10 in CollectionManager, but we write it if provided
        if (item.weight() != null) {
            yaml.set(prefix + ".weight", item.weight());
        }

        // Only write soulbound if true (default is false)
        if (item.soulbound() != null && item.soulbound()) {
            yaml.set(prefix + ".soulbound", true);
        }
    }

    /**
     * Write rewards to the YAML configuration.
     *
     * @param yaml    The YAML configuration
     * @param rewards The rewards to write
     */
    private void writeRewards(YamlConfiguration yaml, RewardRequest rewards) {
        if (rewards == null) {
            return;
        }

        boolean hasContent = false;

        if (rewards.experience() != null && rewards.experience() > 0) {
            yaml.set("rewards.experience", rewards.experience());
            hasContent = true;
        }

        if (rewards.commands() != null && !rewards.commands().isEmpty()) {
            yaml.set("rewards.commands", rewards.commands());
            hasContent = true;
        }

        if (rewards.message() != null && !rewards.message().isBlank()) {
            yaml.set("rewards.message", rewards.message());
            hasContent = true;
        }

        if (rewards.fireworks() != null && rewards.fireworks()) {
            yaml.set("rewards.fireworks", true);
            hasContent = true;
        }

        // If no reward content, don't create empty rewards section
        // (hasContent variable is just for documentation - YAML only creates sections when keys are set)
    }
}
