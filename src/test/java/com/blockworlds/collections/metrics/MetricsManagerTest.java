package com.blockworlds.collections.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MetricsManager counter operations.
 * Tests the counter logic independently of bStats integration.
 */
class MetricsManagerTest {

    private MetricsManager metricsManager;

    @BeforeEach
    void setUp() {
        // Create MetricsManager with counters only (no bStats, no storage)
        metricsManager = MetricsManager.createForTesting();
    }

    @Nested
    @DisplayName("Item Collection Counter Tests")
    class ItemCollectionTests {

        @Test
        @DisplayName("recordItemCollected increments items counter")
        void recordItemCollected_incrementsCounter() {
            assertEquals(0, metricsManager.getItemsCollected());

            metricsManager.recordItemCollected();
            assertEquals(1, metricsManager.getItemsCollected());

            metricsManager.recordItemCollected();
            assertEquals(2, metricsManager.getItemsCollected());
        }

        @Test
        @DisplayName("Multiple recordItemCollected calls accumulate correctly")
        void recordItemCollected_multipleCallsAccumulate() {
            for (int i = 0; i < 100; i++) {
                metricsManager.recordItemCollected();
            }
            assertEquals(100, metricsManager.getItemsCollected());
        }
    }

    @Nested
    @DisplayName("Collection Completion Counter Tests")
    class CollectionCompletionTests {

        @Test
        @DisplayName("recordCollectionCompleted increments completions counter")
        void recordCollectionCompleted_incrementsCounter() {
            assertEquals(0, metricsManager.getCollectionsCompleted());

            metricsManager.recordCollectionCompleted();
            assertEquals(1, metricsManager.getCollectionsCompleted());
        }

        @Test
        @DisplayName("Multiple recordCollectionCompleted calls accumulate correctly")
        void recordCollectionCompleted_multipleCallsAccumulate() {
            for (int i = 0; i < 50; i++) {
                metricsManager.recordCollectionCompleted();
            }
            assertEquals(50, metricsManager.getCollectionsCompleted());
        }
    }

    @Nested
    @DisplayName("Spawn Counter Tests")
    class SpawnCounterTests {

        @Test
        @DisplayName("recordSpawnAttempt with success increments attempts and successes")
        void recordSpawnAttempt_success_incrementsBothCounters() {
            metricsManager.recordSpawnAttempt(true);

            assertEquals(1, metricsManager.getSpawnAttempts());
            assertEquals(1, metricsManager.getSpawnSuccesses());
            assertEquals(0, metricsManager.getSpawnFailures());
        }

        @Test
        @DisplayName("recordSpawnAttempt with failure increments attempts and failures")
        void recordSpawnAttempt_failure_incrementsAttemptsAndFailures() {
            metricsManager.recordSpawnAttempt(false);

            assertEquals(1, metricsManager.getSpawnAttempts());
            assertEquals(0, metricsManager.getSpawnSuccesses());
            assertEquals(1, metricsManager.getSpawnFailures());
        }

        @Test
        @DisplayName("Mixed spawn results track correctly")
        void recordSpawnAttempt_mixedResults_tracksCorrectly() {
            metricsManager.recordSpawnAttempt(true);
            metricsManager.recordSpawnAttempt(true);
            metricsManager.recordSpawnAttempt(false);
            metricsManager.recordSpawnAttempt(true);
            metricsManager.recordSpawnAttempt(false);

            assertEquals(5, metricsManager.getSpawnAttempts());
            assertEquals(3, metricsManager.getSpawnSuccesses());
            assertEquals(2, metricsManager.getSpawnFailures());
        }
    }

    @Nested
    @DisplayName("Spawn Success Rate Tests")
    class SpawnSuccessRateTests {

        @Test
        @DisplayName("getSpawnSuccessRate returns 100 when no attempts (no failures)")
        void getSpawnSuccessRate_noAttempts_returns100() {
            // No attempts = no failures = 100% success rate (design choice)
            assertEquals(100.0, metricsManager.getSpawnSuccessRate(), 0.01);
        }

        @Test
        @DisplayName("getSpawnSuccessRate returns 100 when all successful")
        void getSpawnSuccessRate_allSuccess_returns100() {
            metricsManager.recordSpawnAttempt(true);
            metricsManager.recordSpawnAttempt(true);
            metricsManager.recordSpawnAttempt(true);

            assertEquals(100.0, metricsManager.getSpawnSuccessRate(), 0.01);
        }

        @Test
        @DisplayName("getSpawnSuccessRate returns 0 when all failed")
        void getSpawnSuccessRate_allFailures_returnsZero() {
            metricsManager.recordSpawnAttempt(false);
            metricsManager.recordSpawnAttempt(false);

            assertEquals(0.0, metricsManager.getSpawnSuccessRate(), 0.01);
        }

        @Test
        @DisplayName("getSpawnSuccessRate calculates 75% correctly")
        void getSpawnSuccessRate_75percent_calculatesCorrectly() {
            // 3 successes, 1 failure = 75%
            metricsManager.recordSpawnAttempt(true);
            metricsManager.recordSpawnAttempt(true);
            metricsManager.recordSpawnAttempt(true);
            metricsManager.recordSpawnAttempt(false);

            assertEquals(75.0, metricsManager.getSpawnSuccessRate(), 0.01);
        }

        @Test
        @DisplayName("getSpawnSuccessRate calculates 50% correctly")
        void getSpawnSuccessRate_50percent_calculatesCorrectly() {
            // 2 successes, 2 failures = 50%
            metricsManager.recordSpawnAttempt(true);
            metricsManager.recordSpawnAttempt(false);
            metricsManager.recordSpawnAttempt(true);
            metricsManager.recordSpawnAttempt(false);

            assertEquals(50.0, metricsManager.getSpawnSuccessRate(), 0.01);
        }

        @Test
        @DisplayName("getSpawnSuccessRate calculates 33.33% correctly")
        void getSpawnSuccessRate_oneThird_calculatesCorrectly() {
            // 1 success, 2 failures = 33.33%
            metricsManager.recordSpawnAttempt(true);
            metricsManager.recordSpawnAttempt(false);
            metricsManager.recordSpawnAttempt(false);

            assertEquals(33.33, metricsManager.getSpawnSuccessRate(), 0.01);
        }
    }

    @Nested
    @DisplayName("Thread Safety Tests")
    class ThreadSafetyTests {

        @Test
        @DisplayName("Concurrent itemCollected increments produce correct totals")
        void concurrentItemCollected_producesCorrectTotals() throws InterruptedException {
            int threadCount = 10;
            int incrementsPerThread = 1000;

            Thread[] threads = new Thread[threadCount];
            for (int i = 0; i < threadCount; i++) {
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < incrementsPerThread; j++) {
                        metricsManager.recordItemCollected();
                    }
                });
            }

            for (Thread thread : threads) {
                thread.start();
            }
            for (Thread thread : threads) {
                thread.join();
            }

            assertEquals(threadCount * incrementsPerThread, metricsManager.getItemsCollected());
        }

        @Test
        @DisplayName("Concurrent collectionsCompleted increments produce correct totals")
        void concurrentCollectionsCompleted_producesCorrectTotals() throws InterruptedException {
            int threadCount = 10;
            int incrementsPerThread = 1000;

            Thread[] threads = new Thread[threadCount];
            for (int i = 0; i < threadCount; i++) {
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < incrementsPerThread; j++) {
                        metricsManager.recordCollectionCompleted();
                    }
                });
            }

            for (Thread thread : threads) {
                thread.start();
            }
            for (Thread thread : threads) {
                thread.join();
            }

            assertEquals(threadCount * incrementsPerThread, metricsManager.getCollectionsCompleted());
        }

        @Test
        @DisplayName("Concurrent spawn attempts produce correct totals")
        void concurrentSpawnAttempts_producesCorrectTotals() throws InterruptedException {
            int threadCount = 10;
            int incrementsPerThread = 1000;

            Thread[] threads = new Thread[threadCount];
            for (int i = 0; i < threadCount; i++) {
                final int threadIndex = i;
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < incrementsPerThread; j++) {
                        // Even threads record success, odd threads record failure
                        metricsManager.recordSpawnAttempt(threadIndex % 2 == 0);
                    }
                });
            }

            for (Thread thread : threads) {
                thread.start();
            }
            for (Thread thread : threads) {
                thread.join();
            }

            // All threads combined
            assertEquals(threadCount * incrementsPerThread, metricsManager.getSpawnAttempts());
            // 5 even threads = 5000 successes
            assertEquals(5 * incrementsPerThread, metricsManager.getSpawnSuccesses());
            // 5 odd threads = 5000 failures
            assertEquals(5 * incrementsPerThread, metricsManager.getSpawnFailures());
        }
    }

    @Nested
    @DisplayName("bStats Status Tests")
    class BStatsStatusTests {

        @Test
        @DisplayName("isEnabled returns false for test instance")
        void isEnabled_returnsFalse_forTestInstance() {
            // Test instances don't have bStats enabled
            assertFalse(metricsManager.isEnabled());
        }
    }
}
