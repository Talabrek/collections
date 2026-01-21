package com.blockworlds.collections.model;

import org.bukkit.World;
import org.bukkit.block.Biome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SpawnConditions validation methods.
 * Pure unit tests that don't require MockBukkit.
 */
class SpawnConditionsTest {

    // ========================================
    // NONE constant tests
    // ========================================

    @Nested
    @DisplayName("NONE constant tests")
    class NoneConstantTests {

        @Test
        @DisplayName("NONE has no biome restrictions")
        void testNoneHasNoBiomeRestrictions() {
            assertNull(SpawnConditions.NONE.biomes());
        }

        @Test
        @DisplayName("NONE has no dimension restrictions")
        void testNoneHasNoDimensionRestrictions() {
            assertNull(SpawnConditions.NONE.dimensions());
        }

        @Test
        @DisplayName("NONE accepts any Y level")
        void testNoneAcceptsAnyY() {
            assertTrue(SpawnConditions.NONE.isYValid(Integer.MIN_VALUE));
            assertTrue(SpawnConditions.NONE.isYValid(-64));
            assertTrue(SpawnConditions.NONE.isYValid(0));
            assertTrue(SpawnConditions.NONE.isYValid(64));
            assertTrue(SpawnConditions.NONE.isYValid(320));
            assertTrue(SpawnConditions.NONE.isYValid(Integer.MAX_VALUE));
        }

        @Test
        @DisplayName("NONE accepts any light level 0-15")
        void testNoneAcceptsAnyLight() {
            for (int light = 0; light <= 15; light++) {
                assertTrue(SpawnConditions.NONE.isLightValid(light),
                    "Light level " + light + " should be valid");
            }
        }

        @Test
        @DisplayName("NONE has no sky requirement")
        void testNoneHasNoSkyRequirement() {
            assertFalse(SpawnConditions.NONE.requireSky());
        }

        @Test
        @DisplayName("NONE has no underground requirement")
        void testNoneHasNoUndergroundRequirement() {
            assertFalse(SpawnConditions.NONE.underground());
        }

        @Test
        @DisplayName("NONE has ALWAYS time condition")
        void testNoneHasAlwaysTimeCondition() {
            assertEquals(SpawnConditions.TimeCondition.ALWAYS, SpawnConditions.NONE.time());
        }
    }

    // ========================================
    // isYValid() tests
    // ========================================

    @Nested
    @DisplayName("isYValid() tests")
    class IsYValidTests {

        @Test
        @DisplayName("Y within range returns true")
        void testIsYValidWithinRange() {
            SpawnConditions conditions = SpawnConditions.builder()
                    .minY(0)
                    .maxY(100)
                    .build();

            assertTrue(conditions.isYValid(50));
            assertTrue(conditions.isYValid(25));
            assertTrue(conditions.isYValid(75));
        }

        @Test
        @DisplayName("Y exactly at boundaries returns true")
        void testIsYValidAtBoundaries() {
            SpawnConditions conditions = SpawnConditions.builder()
                    .minY(10)
                    .maxY(90)
                    .build();

            assertTrue(conditions.isYValid(10), "Y at minY should be valid");
            assertTrue(conditions.isYValid(90), "Y at maxY should be valid");
        }

        @Test
        @DisplayName("Y below min returns false")
        void testIsYValidBelowMin() {
            SpawnConditions conditions = SpawnConditions.builder()
                    .minY(10)
                    .maxY(90)
                    .build();

            assertFalse(conditions.isYValid(9));
            assertFalse(conditions.isYValid(0));
            assertFalse(conditions.isYValid(-64));
        }

        @Test
        @DisplayName("Y above max returns false")
        void testIsYValidAboveMax() {
            SpawnConditions conditions = SpawnConditions.builder()
                    .minY(10)
                    .maxY(90)
                    .build();

            assertFalse(conditions.isYValid(91));
            assertFalse(conditions.isYValid(100));
            assertFalse(conditions.isYValid(320));
        }

        @Test
        @DisplayName("Default Y range accepts everything")
        void testIsYValidWithDefaultRange() {
            SpawnConditions conditions = SpawnConditions.builder().build();

            assertEquals(Integer.MIN_VALUE, conditions.minY());
            assertEquals(Integer.MAX_VALUE, conditions.maxY());

            assertTrue(conditions.isYValid(Integer.MIN_VALUE));
            assertTrue(conditions.isYValid(Integer.MAX_VALUE));
            assertTrue(conditions.isYValid(-64));
            assertTrue(conditions.isYValid(0));
            assertTrue(conditions.isYValid(320));
        }

        @Test
        @DisplayName("Negative Y range works correctly")
        void testIsYValidNegativeRange() {
            SpawnConditions conditions = SpawnConditions.builder()
                    .minY(-64)
                    .maxY(-10)
                    .build();

            assertTrue(conditions.isYValid(-64));
            assertTrue(conditions.isYValid(-30));
            assertTrue(conditions.isYValid(-10));
            assertFalse(conditions.isYValid(-65));
            assertFalse(conditions.isYValid(-9));
            assertFalse(conditions.isYValid(0));
        }
    }

    // ========================================
    // isLightValid() tests
    // ========================================

    @Nested
    @DisplayName("isLightValid() tests")
    class IsLightValidTests {

        @Test
        @DisplayName("Light within range returns true")
        void testIsLightValidWithinRange() {
            SpawnConditions conditions = SpawnConditions.builder()
                    .minLight(5)
                    .maxLight(10)
                    .build();

            assertTrue(conditions.isLightValid(7));
            assertTrue(conditions.isLightValid(6));
            assertTrue(conditions.isLightValid(9));
        }

        @Test
        @DisplayName("Light exactly at boundaries returns true")
        void testIsLightValidAtBoundaries() {
            SpawnConditions conditions = SpawnConditions.builder()
                    .minLight(3)
                    .maxLight(12)
                    .build();

            assertTrue(conditions.isLightValid(3), "Light at minLight should be valid");
            assertTrue(conditions.isLightValid(12), "Light at maxLight should be valid");
        }

        @Test
        @DisplayName("Light below min returns false")
        void testIsLightValidBelowMin() {
            SpawnConditions conditions = SpawnConditions.builder()
                    .minLight(5)
                    .maxLight(15)
                    .build();

            assertFalse(conditions.isLightValid(4));
            assertFalse(conditions.isLightValid(0));
        }

        @Test
        @DisplayName("Light above max returns false")
        void testIsLightValidAboveMax() {
            SpawnConditions conditions = SpawnConditions.builder()
                    .minLight(0)
                    .maxLight(10)
                    .build();

            assertFalse(conditions.isLightValid(11));
            assertFalse(conditions.isLightValid(15));
        }

        @Test
        @DisplayName("Default light range accepts 0-15")
        void testIsLightValidDefaultRange() {
            SpawnConditions conditions = SpawnConditions.builder().build();

            assertEquals(0, conditions.minLight());
            assertEquals(15, conditions.maxLight());

            for (int light = 0; light <= 15; light++) {
                assertTrue(conditions.isLightValid(light));
            }
        }

        @Test
        @DisplayName("Single light value (min equals max)")
        void testIsLightValidSingleValue() {
            SpawnConditions conditions = SpawnConditions.builder()
                    .minLight(8)
                    .maxLight(8)
                    .build();

            assertTrue(conditions.isLightValid(8));
            assertFalse(conditions.isLightValid(7));
            assertFalse(conditions.isLightValid(9));
        }
    }

    // ========================================
    // Builder tests
    // ========================================

    @Nested
    @DisplayName("Builder tests")
    class BuilderTests {

        @Test
        @DisplayName("Builder creates correct values for all fields")
        void testBuilderCreatesCorrectValues() {
            Set<Biome> biomes = Set.of(Biome.PLAINS, Biome.FOREST);
            Set<World.Environment> dimensions = Set.of(World.Environment.NORMAL);

            SpawnConditions conditions = SpawnConditions.builder()
                    .biomes(biomes)
                    .dimensions(dimensions)
                    .minY(10)
                    .maxY(200)
                    .minLight(4)
                    .maxLight(12)
                    .requireSky(true)
                    .underground(false)
                    .time(SpawnConditions.TimeCondition.DAY)
                    .build();

            assertEquals(biomes, conditions.biomes());
            assertEquals(dimensions, conditions.dimensions());
            assertEquals(10, conditions.minY());
            assertEquals(200, conditions.maxY());
            assertEquals(4, conditions.minLight());
            assertEquals(12, conditions.maxLight());
            assertTrue(conditions.requireSky());
            assertFalse(conditions.underground());
            assertEquals(SpawnConditions.TimeCondition.DAY, conditions.time());
        }

        @Test
        @DisplayName("Builder has correct defaults")
        void testBuilderDefaults() {
            SpawnConditions conditions = SpawnConditions.builder().build();

            assertNull(conditions.biomes());
            assertNull(conditions.dimensions());
            assertEquals(Integer.MIN_VALUE, conditions.minY());
            assertEquals(Integer.MAX_VALUE, conditions.maxY());
            assertEquals(0, conditions.minLight());
            assertEquals(15, conditions.maxLight());
            assertFalse(conditions.requireSky());
            assertFalse(conditions.underground());
            assertEquals(SpawnConditions.TimeCondition.ALWAYS, conditions.time());
        }

        @Test
        @DisplayName("Builder can set underground true")
        void testBuilderUnderground() {
            SpawnConditions conditions = SpawnConditions.builder()
                    .underground(true)
                    .build();

            assertTrue(conditions.underground());
        }

        @Test
        @DisplayName("Builder can set NIGHT time")
        void testBuilderNightTime() {
            SpawnConditions conditions = SpawnConditions.builder()
                    .time(SpawnConditions.TimeCondition.NIGHT)
                    .build();

            assertEquals(SpawnConditions.TimeCondition.NIGHT, conditions.time());
        }
    }

    // ========================================
    // mergeWith() tests
    // ========================================

    @Nested
    @DisplayName("mergeWith() tests")
    class MergeWithTests {

        @Test
        @DisplayName("mergeWith null returns this unchanged")
        void testMergeWithNull() {
            SpawnConditions base = SpawnConditions.builder()
                    .minY(10)
                    .maxY(100)
                    .build();

            SpawnConditions result = base.mergeWith(null);

            assertSame(base, result);
        }

        @Test
        @DisplayName("other biomes replace base biomes when set")
        void testMergeWithOverridesBiomes() {
            Set<Biome> baseBiomes = Set.of(Biome.PLAINS);
            Set<Biome> otherBiomes = Set.of(Biome.DESERT, Biome.BADLANDS);

            SpawnConditions base = SpawnConditions.builder()
                    .biomes(baseBiomes)
                    .build();
            SpawnConditions other = SpawnConditions.builder()
                    .biomes(otherBiomes)
                    .build();

            SpawnConditions result = base.mergeWith(other);

            assertEquals(otherBiomes, result.biomes());
        }

        @Test
        @DisplayName("other dimensions replace base dimensions when set")
        void testMergeWithOverridesDimensions() {
            Set<World.Environment> baseDims = Set.of(World.Environment.NORMAL);
            Set<World.Environment> otherDims = Set.of(World.Environment.NETHER);

            SpawnConditions base = SpawnConditions.builder()
                    .dimensions(baseDims)
                    .build();
            SpawnConditions other = SpawnConditions.builder()
                    .dimensions(otherDims)
                    .build();

            SpawnConditions result = base.mergeWith(other);

            assertEquals(otherDims, result.dimensions());
        }

        @Test
        @DisplayName("other Y range overrides base when non-default")
        void testMergeWithOverridesYRange() {
            SpawnConditions base = SpawnConditions.builder()
                    .minY(0)
                    .maxY(256)
                    .build();
            SpawnConditions other = SpawnConditions.builder()
                    .minY(50)
                    .maxY(100)
                    .build();

            SpawnConditions result = base.mergeWith(other);

            assertEquals(50, result.minY());
            assertEquals(100, result.maxY());
        }

        @Test
        @DisplayName("base values kept when other has defaults")
        void testMergeWithKeepsBaseWhenOtherDefault() {
            Set<Biome> baseBiomes = Set.of(Biome.OCEAN);
            Set<World.Environment> baseDims = Set.of(World.Environment.THE_END);

            SpawnConditions base = SpawnConditions.builder()
                    .biomes(baseBiomes)
                    .dimensions(baseDims)
                    .minY(20)
                    .maxY(80)
                    .time(SpawnConditions.TimeCondition.NIGHT)
                    .build();
            SpawnConditions other = SpawnConditions.builder().build(); // all defaults

            SpawnConditions result = base.mergeWith(other);

            assertEquals(baseBiomes, result.biomes());
            assertEquals(baseDims, result.dimensions());
            assertEquals(20, result.minY());
            assertEquals(80, result.maxY());
            assertEquals(SpawnConditions.TimeCondition.NIGHT, result.time());
        }

        @Test
        @DisplayName("requireSky flags are OR'd together")
        void testMergeWithCombinesRequireSky() {
            SpawnConditions baseWithSky = SpawnConditions.builder()
                    .requireSky(true)
                    .build();
            SpawnConditions otherNoSky = SpawnConditions.builder()
                    .requireSky(false)
                    .build();

            // true OR false = true
            SpawnConditions result1 = baseWithSky.mergeWith(otherNoSky);
            assertTrue(result1.requireSky());

            // false OR true = true
            SpawnConditions result2 = otherNoSky.mergeWith(baseWithSky);
            assertTrue(result2.requireSky());

            // false OR false = false
            SpawnConditions bothFalse = SpawnConditions.builder().requireSky(false).build();
            SpawnConditions result3 = bothFalse.mergeWith(otherNoSky);
            assertFalse(result3.requireSky());
        }

        @Test
        @DisplayName("underground flags are OR'd together")
        void testMergeWithCombinesUnderground() {
            SpawnConditions baseUnderground = SpawnConditions.builder()
                    .underground(true)
                    .build();
            SpawnConditions otherNotUnderground = SpawnConditions.builder()
                    .underground(false)
                    .build();

            // true OR false = true
            SpawnConditions result1 = baseUnderground.mergeWith(otherNotUnderground);
            assertTrue(result1.underground());

            // false OR true = true
            SpawnConditions result2 = otherNotUnderground.mergeWith(baseUnderground);
            assertTrue(result2.underground());
        }

        @Test
        @DisplayName("non-ALWAYS time from other overrides base")
        void testMergeWithOverridesTime() {
            SpawnConditions base = SpawnConditions.builder()
                    .time(SpawnConditions.TimeCondition.ALWAYS)
                    .build();
            SpawnConditions otherDay = SpawnConditions.builder()
                    .time(SpawnConditions.TimeCondition.DAY)
                    .build();

            SpawnConditions result = base.mergeWith(otherDay);

            assertEquals(SpawnConditions.TimeCondition.DAY, result.time());
        }

        @Test
        @DisplayName("base time kept when other is ALWAYS")
        void testMergeWithKeepsBaseTimeWhenOtherAlways() {
            SpawnConditions base = SpawnConditions.builder()
                    .time(SpawnConditions.TimeCondition.NIGHT)
                    .build();
            SpawnConditions other = SpawnConditions.builder()
                    .time(SpawnConditions.TimeCondition.ALWAYS)
                    .build();

            SpawnConditions result = base.mergeWith(other);

            assertEquals(SpawnConditions.TimeCondition.NIGHT, result.time());
        }

        @Test
        @DisplayName("other light range overrides base when non-default")
        void testMergeWithOverridesLightRange() {
            SpawnConditions base = SpawnConditions.builder()
                    .minLight(0)
                    .maxLight(15)
                    .build();
            SpawnConditions other = SpawnConditions.builder()
                    .minLight(4)
                    .maxLight(8)
                    .build();

            SpawnConditions result = base.mergeWith(other);

            assertEquals(4, result.minLight());
            assertEquals(8, result.maxLight());
        }

        @Test
        @DisplayName("base light kept when other has default 0-15")
        void testMergeWithKeepsBaseLightWhenOtherDefault() {
            SpawnConditions base = SpawnConditions.builder()
                    .minLight(5)
                    .maxLight(10)
                    .build();
            SpawnConditions other = SpawnConditions.builder().build(); // default 0-15

            SpawnConditions result = base.mergeWith(other);

            assertEquals(5, result.minLight());
            assertEquals(10, result.maxLight());
        }

        @Test
        @DisplayName("mergeWith handles complex scenario with multiple overrides")
        void testMergeWithComplexScenario() {
            // Base: PLAINS biome, NORMAL dimension, Y 0-256, no sky, DAY only
            SpawnConditions base = SpawnConditions.builder()
                    .biomes(Set.of(Biome.PLAINS))
                    .dimensions(Set.of(World.Environment.NORMAL))
                    .minY(0)
                    .maxY(256)
                    .requireSky(false)
                    .time(SpawnConditions.TimeCondition.DAY)
                    .build();

            // Other: FOREST biome (override), Y 50-100 (override), require sky (OR = true)
            SpawnConditions other = SpawnConditions.builder()
                    .biomes(Set.of(Biome.FOREST))
                    .minY(50)
                    .maxY(100)
                    .requireSky(true)
                    .build();

            SpawnConditions result = base.mergeWith(other);

            // Biomes overridden
            assertEquals(Set.of(Biome.FOREST), result.biomes());
            // Dimensions kept from base (other was null)
            assertEquals(Set.of(World.Environment.NORMAL), result.dimensions());
            // Y range overridden
            assertEquals(50, result.minY());
            assertEquals(100, result.maxY());
            // requireSky OR'd (false OR true = true)
            assertTrue(result.requireSky());
            // Time kept from base (other was ALWAYS)
            assertEquals(SpawnConditions.TimeCondition.DAY, result.time());
        }
    }

    // ========================================
    // TimeCondition enum tests
    // ========================================

    @Nested
    @DisplayName("TimeCondition enum tests")
    class TimeConditionTests {

        @Test
        @DisplayName("TimeCondition has three values")
        void testTimeConditionValues() {
            SpawnConditions.TimeCondition[] values = SpawnConditions.TimeCondition.values();

            assertEquals(3, values.length);
            assertNotNull(SpawnConditions.TimeCondition.ALWAYS);
            assertNotNull(SpawnConditions.TimeCondition.DAY);
            assertNotNull(SpawnConditions.TimeCondition.NIGHT);
        }

        @Test
        @DisplayName("TimeCondition valueOf works correctly")
        void testTimeConditionValueOf() {
            assertEquals(SpawnConditions.TimeCondition.ALWAYS,
                    SpawnConditions.TimeCondition.valueOf("ALWAYS"));
            assertEquals(SpawnConditions.TimeCondition.DAY,
                    SpawnConditions.TimeCondition.valueOf("DAY"));
            assertEquals(SpawnConditions.TimeCondition.NIGHT,
                    SpawnConditions.TimeCondition.valueOf("NIGHT"));
        }
    }
}
