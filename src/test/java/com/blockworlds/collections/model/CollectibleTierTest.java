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
}
