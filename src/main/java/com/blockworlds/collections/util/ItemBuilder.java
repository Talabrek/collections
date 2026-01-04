package com.blockworlds.collections.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for creating ItemStacks with custom properties.
 */
public class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material);
        this.meta = item.getItemMeta();
    }

    public ItemBuilder(ItemStack item) {
        this.item = item.clone();
        this.meta = this.item.getItemMeta();
    }

    /**
     * Set the display name using MiniMessage format.
     */
    public ItemBuilder name(String name) {
        if (meta != null && name != null) {
            meta.displayName(miniMessage.deserialize(name));
        }
        return this;
    }

    /**
     * Set the display name using a Component.
     */
    public ItemBuilder name(Component name) {
        if (meta != null && name != null) {
            meta.displayName(name);
        }
        return this;
    }

    /**
     * Set the lore using MiniMessage format strings.
     */
    public ItemBuilder lore(List<String> lore) {
        if (meta != null && lore != null) {
            List<Component> components = new ArrayList<>();
            for (String line : lore) {
                components.add(miniMessage.deserialize(line));
            }
            meta.lore(components);
        }
        return this;
    }

    /**
     * Set the lore using Components.
     */
    public ItemBuilder loreComponents(List<Component> lore) {
        if (meta != null && lore != null) {
            meta.lore(lore);
        }
        return this;
    }

    /**
     * Add a single lore line.
     */
    public ItemBuilder addLore(String line) {
        if (meta != null && line != null) {
            List<Component> existing = meta.lore();
            if (existing == null) {
                existing = new ArrayList<>();
            } else {
                existing = new ArrayList<>(existing);
            }
            existing.add(miniMessage.deserialize(line));
            meta.lore(existing);
        }
        return this;
    }

    /**
     * Set the item amount.
     */
    public ItemBuilder amount(int amount) {
        item.setAmount(amount);
        return this;
    }

    /**
     * Add an enchantment.
     */
    public ItemBuilder enchant(Enchantment enchantment, int level) {
        if (meta != null) {
            meta.addEnchant(enchantment, level, true);
        }
        return this;
    }

    /**
     * Add a fake enchantment glint without actual enchants.
     */
    public ItemBuilder glowing() {
        if (meta != null) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        return this;
    }

    /**
     * Add item flags.
     */
    public ItemBuilder flags(ItemFlag... flags) {
        if (meta != null) {
            meta.addItemFlags(flags);
        }
        return this;
    }

    /**
     * Hide all flags.
     */
    public ItemBuilder hideFlags() {
        if (meta != null) {
            meta.addItemFlags(ItemFlag.values());
        }
        return this;
    }

    /**
     * Set unbreakable.
     */
    public ItemBuilder unbreakable(boolean unbreakable) {
        if (meta != null) {
            meta.setUnbreakable(unbreakable);
        }
        return this;
    }

    /**
     * Set custom model data.
     */
    public ItemBuilder customModelData(int data) {
        if (meta != null) {
            meta.setCustomModelData(data);
        }
        return this;
    }

    /**
     * Add a persistent data string.
     */
    public ItemBuilder data(Plugin plugin, String key, String value) {
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(new NamespacedKey(plugin, key), PersistentDataType.STRING, value);
        }
        return this;
    }

    /**
     * Add a persistent data integer.
     */
    public ItemBuilder data(Plugin plugin, String key, int value) {
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(new NamespacedKey(plugin, key), PersistentDataType.INTEGER, value);
        }
        return this;
    }

    /**
     * Add a persistent data boolean.
     */
    public ItemBuilder data(Plugin plugin, String key, boolean value) {
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(new NamespacedKey(plugin, key), PersistentDataType.BOOLEAN, value);
        }
        return this;
    }

    /**
     * Add persistent data with NamespacedKey (String).
     */
    public ItemBuilder data(NamespacedKey key, String value) {
        if (meta != null) {
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
        }
        return this;
    }

    /**
     * Add persistent data with NamespacedKey (Byte).
     */
    public ItemBuilder data(NamespacedKey key, byte value) {
        if (meta != null) {
            meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, value);
        }
        return this;
    }

    /**
     * Build the final ItemStack.
     */
    public ItemStack build() {
        if (meta != null) {
            item.setItemMeta(meta);
        }
        return item;
    }

    // Static factory methods

    /**
     * Create a new ItemBuilder.
     */
    public static ItemBuilder of(Material material) {
        return new ItemBuilder(material);
    }

    /**
     * Create a new ItemBuilder from an existing item.
     */
    public static ItemBuilder of(ItemStack item) {
        return new ItemBuilder(item);
    }

    /**
     * Check if an item has a specific persistent data key.
     */
    public static boolean hasData(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(key);
    }

    /**
     * Get a string value from persistent data.
     */
    public static String getData(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    /**
     * Get an integer value from persistent data.
     */
    public static Integer getDataInt(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
    }

    /**
     * Get a boolean value from persistent data.
     */
    public static Boolean getDataBoolean(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.BOOLEAN);
    }

    /**
     * Get a byte value from persistent data.
     */
    public static Byte getDataByte(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.BYTE);
    }
}
