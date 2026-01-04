package com.blockworlds.collections.model;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Particle;

/**
 * Tier levels for collectibles, determining visibility requirements and visual effects.
 */
public enum CollectibleTier {
    COMMON(Particle.HAPPY_VILLAGER, "Common", false, NamedTextColor.WHITE),
    UNCOMMON(Particle.ENCHANT, "Uncommon", true, NamedTextColor.GREEN),
    RARE(Particle.END_ROD, "Rare", true, NamedTextColor.BLUE),
    EVENT(Particle.FIREWORK, "Event", true, NamedTextColor.LIGHT_PURPLE);

    private final Particle particle;
    private final String displayName;
    private final boolean requiresGoggles;
    private final NamedTextColor color;

    CollectibleTier(Particle particle, String displayName, boolean requiresGoggles, NamedTextColor color) {
        this.particle = particle;
        this.displayName = displayName;
        this.requiresGoggles = requiresGoggles;
        this.color = color;
    }

    public Particle getParticle() {
        return particle;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean requiresGoggles() {
        return requiresGoggles;
    }

    public NamedTextColor getColor() {
        return color;
    }

    /**
     * Parse tier from string, case-insensitive.
     */
    public static CollectibleTier fromString(String name) {
        if (name == null || name.isBlank()) {
            return COMMON;
        }
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return COMMON;
        }
    }
}
