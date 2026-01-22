package com.blockworlds.collections.model;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Particle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CollectibleTier enum.
 */
class CollectibleTierTest {

    @Test
    @DisplayName("fromString returns correct tier for valid input")
    void testFromStringValid() {
        assertEquals(CollectibleTier.COMMON, CollectibleTier.fromString("COMMON"));
        assertEquals(CollectibleTier.UNCOMMON, CollectibleTier.fromString("uncommon"));
        assertEquals(CollectibleTier.RARE, CollectibleTier.fromString("Rare"));
        assertEquals(CollectibleTier.EPIC, CollectibleTier.fromString("EPIC"));
        assertEquals(CollectibleTier.EPIC, CollectibleTier.fromString("epic"));
        assertEquals(CollectibleTier.LEGENDARY, CollectibleTier.fromString("LEGENDARY"));
        assertEquals(CollectibleTier.LEGENDARY, CollectibleTier.fromString("legendary"));
        assertEquals(CollectibleTier.EVENT, CollectibleTier.fromString("EVENT"));
    }

    @Test
    @DisplayName("fromString returns COMMON for invalid input")
    void testFromStringInvalid() {
        assertEquals(CollectibleTier.COMMON, CollectibleTier.fromString("INVALID"));
        assertEquals(CollectibleTier.COMMON, CollectibleTier.fromString(null));
        assertEquals(CollectibleTier.COMMON, CollectibleTier.fromString(""));
        assertEquals(CollectibleTier.COMMON, CollectibleTier.fromString("  "));
    }

    @Test
    @DisplayName("Tier properties are correctly set")
    void testTierProperties() {
        // COMMON
        assertEquals("Common", CollectibleTier.COMMON.getDisplayName());
        assertFalse(CollectibleTier.COMMON.requiresGoggles());
        assertEquals(NamedTextColor.WHITE, CollectibleTier.COMMON.getColor());
        assertEquals(Particle.HAPPY_VILLAGER, CollectibleTier.COMMON.getParticle());

        // UNCOMMON
        assertEquals("Uncommon", CollectibleTier.UNCOMMON.getDisplayName());
        assertTrue(CollectibleTier.UNCOMMON.requiresGoggles());
        assertEquals(NamedTextColor.GREEN, CollectibleTier.UNCOMMON.getColor());

        // RARE
        assertEquals("Rare", CollectibleTier.RARE.getDisplayName());
        assertTrue(CollectibleTier.RARE.requiresGoggles());
        assertEquals(NamedTextColor.BLUE, CollectibleTier.RARE.getColor());

        // EVENT
        assertEquals("Event", CollectibleTier.EVENT.getDisplayName());
        assertTrue(CollectibleTier.EVENT.requiresGoggles());
        assertEquals(NamedTextColor.LIGHT_PURPLE, CollectibleTier.EVENT.getColor());
    }

    @Test
    @DisplayName("EPIC tier has correct properties")
    void testEpicTierProperties() {
        assertEquals("Epic", CollectibleTier.EPIC.getDisplayName());
        assertTrue(CollectibleTier.EPIC.requiresGoggles());
        assertEquals(NamedTextColor.DARK_PURPLE, CollectibleTier.EPIC.getColor());
        assertEquals(Particle.SOUL_FIRE_FLAME, CollectibleTier.EPIC.getParticle());
    }

    @Test
    @DisplayName("LEGENDARY tier has correct properties")
    void testLegendaryTierProperties() {
        assertEquals("Legendary", CollectibleTier.LEGENDARY.getDisplayName());
        assertTrue(CollectibleTier.LEGENDARY.requiresGoggles());
        assertEquals(NamedTextColor.GOLD, CollectibleTier.LEGENDARY.getColor());
        assertEquals(Particle.DRAGON_BREATH, CollectibleTier.LEGENDARY.getParticle());
    }
}
