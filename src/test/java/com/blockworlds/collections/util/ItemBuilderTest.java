package com.blockworlds.collections.util;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ItemBuilder utility class.
 */
class ItemBuilderTest {

    private static ServerMock server;

    @BeforeAll
    static void setUp() {
        server = MockBukkit.mock();
    }

    @AfterAll
    static void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("Create simple item")
    void testCreateSimpleItem() {
        ItemStack item = ItemBuilder.of(Material.DIAMOND).build();

        assertNotNull(item);
        assertEquals(Material.DIAMOND, item.getType());
        assertEquals(1, item.getAmount());
    }

    @Test
    @DisplayName("Set item amount")
    void testSetAmount() {
        ItemStack item = ItemBuilder.of(Material.GOLD_INGOT)
                .amount(16)
                .build();

        assertEquals(16, item.getAmount());
    }

    @Test
    @DisplayName("Set item name with MiniMessage")
    void testSetName() {
        ItemStack item = ItemBuilder.of(Material.PAPER)
                .name("<gold>Test Item")
                .build();

        assertNotNull(item.getItemMeta());
        assertNotNull(item.getItemMeta().displayName());
    }

    @Test
    @DisplayName("Set item lore with MiniMessage")
    void testSetLore() {
        ItemStack item = ItemBuilder.of(Material.PAPER)
                .lore(List.of("<gray>Line 1", "<gray>Line 2"))
                .build();

        assertNotNull(item.getItemMeta());
        assertNotNull(item.getItemMeta().lore());
        assertEquals(2, item.getItemMeta().lore().size());
    }

    @Test
    @DisplayName("Add single lore line")
    void testAddLore() {
        ItemStack item = ItemBuilder.of(Material.PAPER)
                .addLore("<gray>First line")
                .addLore("<gray>Second line")
                .build();

        assertNotNull(item.getItemMeta().lore());
        assertEquals(2, item.getItemMeta().lore().size());
    }

    @Test
    @DisplayName("Make item glowing")
    void testGlowing() {
        ItemStack item = ItemBuilder.of(Material.PAPER)
                .glowing()
                .build();

        assertTrue(item.getItemMeta().hasEnchant(Enchantment.UNBREAKING));
        assertTrue(item.getItemMeta().hasItemFlag(ItemFlag.HIDE_ENCHANTS));
    }

    @Test
    @DisplayName("Set custom model data")
    void testCustomModelData() {
        ItemStack item = ItemBuilder.of(Material.PAPER)
                .customModelData(12345)
                .build();

        assertTrue(item.getItemMeta().hasCustomModelData());
        assertEquals(12345, item.getItemMeta().getCustomModelData());
    }

    @Test
    @DisplayName("Set unbreakable")
    void testUnbreakable() {
        ItemStack item = ItemBuilder.of(Material.DIAMOND_SWORD)
                .unbreakable(true)
                .build();

        assertTrue(item.getItemMeta().isUnbreakable());
    }

    @Test
    @DisplayName("Add item flags")
    void testItemFlags() {
        ItemStack item = ItemBuilder.of(Material.DIAMOND_SWORD)
                .flags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE)
                .build();

        assertTrue(item.getItemMeta().hasItemFlag(ItemFlag.HIDE_ATTRIBUTES));
        assertTrue(item.getItemMeta().hasItemFlag(ItemFlag.HIDE_UNBREAKABLE));
    }

    @Test
    @DisplayName("Hide all flags")
    void testHideFlags() {
        ItemStack item = ItemBuilder.of(Material.DIAMOND_SWORD)
                .hideFlags()
                .build();

        // Should have multiple flags hidden
        assertFalse(item.getItemMeta().getItemFlags().isEmpty());
    }

    @Test
    @DisplayName("Store persistent data with NamespacedKey")
    void testPersistentDataNamespacedKey() {
        NamespacedKey key = new NamespacedKey("test", "value");
        ItemStack item = ItemBuilder.of(Material.PAPER)
                .data(key, "test_value")
                .build();

        assertTrue(ItemBuilder.hasData(item, key));
        assertEquals("test_value", ItemBuilder.getData(item, key));
    }

    @Test
    @DisplayName("Static hasData returns false for null item")
    void testHasDataNull() {
        NamespacedKey key = new NamespacedKey("test", "value");
        assertFalse(ItemBuilder.hasData(null, key));
    }

    @Test
    @DisplayName("Static getData returns null for missing key")
    void testGetDataMissing() {
        NamespacedKey key = new NamespacedKey("test", "missing");
        ItemStack item = ItemBuilder.of(Material.PAPER).build();

        assertNull(ItemBuilder.getData(item, key));
    }

    @Test
    @DisplayName("Clone existing item")
    void testCloneItem() {
        ItemStack original = new ItemStack(Material.DIAMOND, 5);
        ItemStack cloned = ItemBuilder.of(original)
                .name("<gold>Cloned")
                .build();

        assertEquals(Material.DIAMOND, cloned.getType());
        assertEquals(5, cloned.getAmount());
        assertNotNull(cloned.getItemMeta().displayName());
    }

    @Test
    @DisplayName("Add enchantment")
    void testEnchant() {
        ItemStack item = ItemBuilder.of(Material.DIAMOND_SWORD)
                .enchant(Enchantment.SHARPNESS, 5)
                .build();

        assertTrue(item.getItemMeta().hasEnchant(Enchantment.SHARPNESS));
        assertEquals(5, item.getItemMeta().getEnchantLevel(Enchantment.SHARPNESS));
    }

    @Test
    @DisplayName("Fluent builder chain")
    void testFluentChain() {
        ItemStack item = ItemBuilder.of(Material.EMERALD)
                .name("<green>Valuable Emerald")
                .lore(List.of("<gray>A precious gem", "<gray>Worth a lot"))
                .amount(3)
                .glowing()
                .build();

        assertEquals(Material.EMERALD, item.getType());
        assertEquals(3, item.getAmount());
        assertNotNull(item.getItemMeta().displayName());
        assertEquals(2, item.getItemMeta().lore().size());
    }
}
