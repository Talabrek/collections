package com.blockworlds.collections.model;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;

import java.util.Set;

/**
 * Spawn conditions that can be applied at Zone, Collection, or Item level.
 * Conditions at more specific levels override broader levels.
 *
 * @param biomes      Allowed biomes (null = all)
 * @param dimensions  Allowed dimensions (null = all)
 * @param minY        Minimum Y level
 * @param maxY        Maximum Y level
 * @param minLight    Minimum light level (0-15)
 * @param maxLight    Maximum light level (0-15)
 * @param requireSky  Must have sky access (no solid blocks above)
 * @param underground Must be underground (solid blocks above)
 * @param time        Time of day requirement
 */
public record SpawnConditions(
        Set<Biome> biomes,
        Set<World.Environment> dimensions,
        int minY,
        int maxY,
        int minLight,
        int maxLight,
        boolean requireSky,
        boolean underground,
        TimeCondition time
) {
    /**
     * Default conditions with no restrictions.
     */
    public static final SpawnConditions NONE = new SpawnConditions(
            null, null, Integer.MIN_VALUE, Integer.MAX_VALUE, 0, 15, false, false, TimeCondition.ALWAYS
    );

    /**
     * Time of day condition for spawning.
     */
    public enum TimeCondition {
        ALWAYS,  // No time restriction
        DAY,     // 0-12000 ticks (sunrise to sunset)
        NIGHT    // 12000-24000 ticks (sunset to sunrise)
    }

    /**
     * Check if the given Y level is within the allowed range.
     */
    public boolean isYValid(int y) {
        return y >= minY && y <= maxY;
    }

    /**
     * Check if the given light level is within the allowed range.
     */
    public boolean isLightValid(int light) {
        return light >= minLight && light <= maxLight;
    }

    /**
     * Check if a location satisfies all these conditions.
     *
     * @param location The location to check
     * @return true if all conditions pass
     */
    public boolean check(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }

        // Check dimension
        if (dimensions != null && !dimensions.contains(world.getEnvironment())) {
            return false;
        }

        // Check Y level
        int y = location.getBlockY();
        if (!isYValid(y)) {
            return false;
        }

        // Check biome
        if (biomes != null) {
            Biome biome = world.getBiome(location);
            if (!biomes.contains(biome)) {
                return false;
            }
        }

        // Check light level
        int lightLevel = location.getBlock().getLightLevel();
        if (!isLightValid(lightLevel)) {
            return false;
        }

        // Check sky access
        if (requireSky) {
            int highestY = world.getHighestBlockYAt(location);
            if (location.getBlockY() <= highestY) {
                return false;
            }
        }

        // Check underground (has solid blocks above)
        if (underground) {
            boolean hasBlockAbove = false;
            for (int checkY = location.getBlockY() + 1; checkY < world.getMaxHeight(); checkY++) {
                if (world.getBlockAt(location.getBlockX(), checkY, location.getBlockZ()).getType().isSolid()) {
                    hasBlockAbove = true;
                    break;
                }
            }
            if (!hasBlockAbove) {
                return false;
            }
        }

        // Check time of day
        if (time != TimeCondition.ALWAYS) {
            long worldTime = world.getTime() % 24000;
            boolean isDay = worldTime >= 0 && worldTime < 12000;
            if (time == TimeCondition.DAY && !isDay) {
                return false;
            }
            if (time == TimeCondition.NIGHT && isDay) {
                return false;
            }
        }

        return true;
    }

    /**
     * Merge this condition with a more specific one.
     * The 'other' conditions override this one where specified.
     *
     * @param other The more specific conditions to merge
     * @return A new SpawnConditions with merged values
     */
    public SpawnConditions mergeWith(SpawnConditions other) {
        if (other == null) {
            return this;
        }

        return new SpawnConditions(
                // Biomes: use other if specified, else this
                other.biomes != null ? other.biomes : this.biomes,
                // Dimensions: use other if specified, else this
                other.dimensions != null ? other.dimensions : this.dimensions,
                // Y range: use other if not default, else this
                other.minY != Integer.MIN_VALUE ? other.minY : this.minY,
                other.maxY != Integer.MAX_VALUE ? other.maxY : this.maxY,
                // Light range: use other if not default (0-15), else this
                (other.minLight != 0 || other.maxLight != 15)
                        ? other.minLight : this.minLight,
                (other.minLight != 0 || other.maxLight != 15)
                        ? other.maxLight : this.maxLight,
                // Sky/underground: other overrides if set to true
                other.requireSky || this.requireSky,
                other.underground || this.underground,
                // Time: use other if not ALWAYS, else this
                other.time != TimeCondition.ALWAYS ? other.time : this.time
        );
    }

    /**
     * Create a builder for constructing SpawnConditions.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for SpawnConditions.
     */
    public static class Builder {
        private Set<Biome> biomes = null;
        private Set<World.Environment> dimensions = null;
        private int minY = Integer.MIN_VALUE;
        private int maxY = Integer.MAX_VALUE;
        private int minLight = 0;
        private int maxLight = 15;
        private boolean requireSky = false;
        private boolean underground = false;
        private TimeCondition time = TimeCondition.ALWAYS;

        public Builder biomes(Set<Biome> biomes) {
            this.biomes = biomes;
            return this;
        }

        public Builder dimensions(Set<World.Environment> dimensions) {
            this.dimensions = dimensions;
            return this;
        }

        public Builder minY(int minY) {
            this.minY = minY;
            return this;
        }

        public Builder maxY(int maxY) {
            this.maxY = maxY;
            return this;
        }

        public Builder minLight(int minLight) {
            this.minLight = minLight;
            return this;
        }

        public Builder maxLight(int maxLight) {
            this.maxLight = maxLight;
            return this;
        }

        public Builder requireSky(boolean requireSky) {
            this.requireSky = requireSky;
            return this;
        }

        public Builder underground(boolean underground) {
            this.underground = underground;
            return this;
        }

        public Builder time(TimeCondition time) {
            this.time = time;
            return this;
        }

        public SpawnConditions build() {
            return new SpawnConditions(biomes, dimensions, minY, maxY, minLight, maxLight, requireSky, underground, time);
        }
    }
}
