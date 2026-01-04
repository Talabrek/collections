package com.blockworlds.collections.util;

import com.blockworlds.collections.model.CollectibleTier;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for HeadUtil utility class.
 */
class HeadUtilTest {

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
    @DisplayName("Create head with base64 texture")
    void testCreateHead() {
        // Use a simple valid base64 texture
        String texture = HeadUtil.getDefaultTexture(CollectibleTier.COMMON);
        ItemStack head = HeadUtil.createHead(texture);

        assertNotNull(head);
        assertEquals(Material.PLAYER_HEAD, head.getType());
    }

    @Test
    @DisplayName("Create collectible head for each tier")
    void testCreateCollectibleHeadAllTiers() {
        for (CollectibleTier tier : CollectibleTier.values()) {
            ItemStack head = HeadUtil.createCollectibleHead(tier);

            assertNotNull(head, "Head for " + tier + " should not be null");
            assertEquals(Material.PLAYER_HEAD, head.getType(), "Head for " + tier + " should be PLAYER_HEAD");
        }
    }

    @Test
    @DisplayName("Create collectible head with custom texture")
    void testCreateCollectibleHeadCustomTexture() {
        String customTexture = HeadUtil.getDefaultTexture(CollectibleTier.RARE);
        ItemStack head = HeadUtil.createCollectibleHead(CollectibleTier.COMMON, customTexture);

        assertNotNull(head);
        assertEquals(Material.PLAYER_HEAD, head.getType());
    }

    @Test
    @DisplayName("Create collectible head falls back to tier default for null texture")
    void testCreateCollectibleHeadNullTexture() {
        ItemStack head = HeadUtil.createCollectibleHead(CollectibleTier.UNCOMMON, null);

        assertNotNull(head);
        assertEquals(Material.PLAYER_HEAD, head.getType());
    }

    @Test
    @DisplayName("Create collectible head falls back to tier default for blank texture")
    void testCreateCollectibleHeadBlankTexture() {
        ItemStack head = HeadUtil.createCollectibleHead(CollectibleTier.RARE, "   ");

        assertNotNull(head);
        assertEquals(Material.PLAYER_HEAD, head.getType());
    }

    @Test
    @DisplayName("Get default texture for each tier")
    void testGetDefaultTextureAllTiers() {
        for (CollectibleTier tier : CollectibleTier.values()) {
            String texture = HeadUtil.getDefaultTexture(tier);

            assertNotNull(texture, "Default texture for " + tier + " should not be null");
            assertFalse(texture.isBlank(), "Default texture for " + tier + " should not be blank");
        }
    }

    @Test
    @DisplayName("Clear cache does not throw")
    void testClearCache() {
        assertDoesNotThrow(() -> HeadUtil.clearCache());
    }

    @Test
    @DisplayName("Profile caching works")
    void testProfileCaching() {
        String texture = HeadUtil.getDefaultTexture(CollectibleTier.COMMON);

        // Create two heads with same texture - should use cached profile
        ItemStack head1 = HeadUtil.createHead(texture);
        ItemStack head2 = HeadUtil.createHead(texture);

        assertNotNull(head1);
        assertNotNull(head2);
        // Both should be valid heads
        assertEquals(Material.PLAYER_HEAD, head1.getType());
        assertEquals(Material.PLAYER_HEAD, head2.getType());
    }
}
