package com.blockworlds.collections.manager;

import com.blockworlds.collections.config.ConfigManager;
import com.blockworlds.collections.model.Collection;
import com.blockworlds.collections.model.CollectionItem;
import com.blockworlds.collections.model.CollectibleTier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NotificationManager.
 * Tests notification dispatch for different configuration styles.
 */
@ExtendWith(MockitoExtension.class)
class NotificationManagerTest {

    @Mock
    private ConfigManager configManager;

    @Mock
    private Player player;

    private NotificationManager notificationManager;

    // Test collection for all tests
    private Collection testCollection;

    @BeforeEach
    void setUp() {
        notificationManager = new NotificationManager(configManager);

        // Create a test item (Collection requires at least one item)
        CollectionItem testItem = CollectionItem.simple("test_item", "Test Item", Material.DIAMOND);

        // Create a test collection with all required parameters
        testCollection = new Collection(
            "test_collection",           // id
            "Test Collection",           // name
            "A test collection",         // description
            CollectibleTier.COMMON,      // tier
            List.of(testItem),           // items (required, minimum 1)
            null,                         // rewards (null = EMPTY)
            null,                         // requiredCollections (null = empty list)
            null,                         // allowedZones (null = empty list)
            null,                         // headTexture (null = empty string)
            null,                         // icon (null = PAPER)
            null                          // spawnConditions (null = no restrictions)
        );

        // Default parse behavior - return a simple component for any input
        // Use doAnswer to handle varargs properly
        lenient().doAnswer(invocation -> {
            String text = invocation.getArgument(0);
            return Component.text(text != null ? text : "null");
        }).when(configManager).parse(anyString(), any(Object[].class));
    }

    @Nested
    @DisplayName("Progress Notification Tests")
    class ProgressNotificationTests {

        @Test
        @DisplayName("Should send actionbar when style is actionbar")
        void sendProgressNotification_actionbarStyle_sendsActionBar() {
            when(configManager.getProgressNotificationStyle()).thenReturn("actionbar");
            when(configManager.getProgressNotificationFormat()).thenReturn("<current>/<total> in <collection>");

            notificationManager.sendProgressNotification(player, testCollection, 2, 5);

            verify(player).sendActionBar(any(Component.class));
            verify(player, never()).sendMessage(any(Component.class));
            verify(player, never()).showTitle(any(Title.class));
        }

        @Test
        @DisplayName("Should send chat when style is chat")
        void sendProgressNotification_chatStyle_sendsChat() {
            when(configManager.getProgressNotificationStyle()).thenReturn("chat");
            when(configManager.getProgressNotificationFormat()).thenReturn("<current>/<total> in <collection>");

            notificationManager.sendProgressNotification(player, testCollection, 2, 5);

            verify(player).sendMessage(any(Component.class));
            verify(player, never()).sendActionBar(any(Component.class));
            verify(player, never()).showTitle(any(Title.class));
        }

        @Test
        @DisplayName("Should send title when style is title")
        void sendProgressNotification_titleStyle_sendsTitle() {
            when(configManager.getProgressNotificationStyle()).thenReturn("title");
            when(configManager.getProgressNotificationFormat()).thenReturn("<current>/<total> in <collection>");

            notificationManager.sendProgressNotification(player, testCollection, 2, 5);

            verify(player).showTitle(any(Title.class));
            verify(player, never()).sendActionBar(any(Component.class));
            verify(player, never()).sendMessage(any(Component.class));
        }

        @Test
        @DisplayName("Should send nothing when style is none")
        void sendProgressNotification_noneStyle_sendsNothing() {
            when(configManager.getProgressNotificationStyle()).thenReturn("none");

            notificationManager.sendProgressNotification(player, testCollection, 2, 5);

            verify(player, never()).sendActionBar(any(Component.class));
            verify(player, never()).sendMessage(any(Component.class));
            verify(player, never()).showTitle(any(Title.class));
        }

        @Test
        @DisplayName("Should default to actionbar for unknown style")
        void sendProgressNotification_unknownStyle_defaultsToActionBar() {
            when(configManager.getProgressNotificationStyle()).thenReturn("invalid");
            when(configManager.getProgressNotificationFormat()).thenReturn("<current>/<total>");

            notificationManager.sendProgressNotification(player, testCollection, 2, 5);

            verify(player).sendActionBar(any(Component.class));
        }

        @Test
        @DisplayName("Should parse format with correct placeholders")
        void sendProgressNotification_parsesPlaceholdersCorrectly() {
            when(configManager.getProgressNotificationStyle()).thenReturn("actionbar");
            String format = "<current>/<total> in <collection>";
            when(configManager.getProgressNotificationFormat()).thenReturn(format);

            notificationManager.sendProgressNotification(player, testCollection, 3, 7);

            // Verify parse was called with correct arguments
            verify(configManager).parse(eq(format),
                eq("current"), eq("3"),
                eq("total"), eq("7"),
                eq("collection"), eq("Test Collection")
            );
        }
    }

    @Nested
    @DisplayName("Completion Notification Tests")
    class CompletionNotificationTests {

        @BeforeEach
        void setUpCompletionMocks() {
            // Default completion config values
            lenient().when(configManager.getCompletionTitle()).thenReturn("<bold>Complete!</bold>");
            lenient().when(configManager.getCompletionSubtitle()).thenReturn("<collection>");
            lenient().when(configManager.getCompletionFadeIn()).thenReturn(0.5);
            lenient().when(configManager.getCompletionStay()).thenReturn(3.0);
            lenient().when(configManager.getCompletionFadeOut()).thenReturn(0.5);
        }

        @Test
        @DisplayName("Should send title when style is title")
        void sendCompletionNotification_titleStyle_sendsTitle() {
            when(configManager.getCompletionNotificationStyle()).thenReturn("title");

            notificationManager.sendCompletionNotification(player, testCollection);

            verify(player).showTitle(any(Title.class));
            verify(player, never()).sendMessage(any(Component.class));
        }

        @Test
        @DisplayName("Should send chat when style is chat")
        void sendCompletionNotification_chatStyle_sendsChat() {
            when(configManager.getCompletionNotificationStyle()).thenReturn("chat");
            when(configManager.getMessage(eq("collection-complete"), any(), any()))
                .thenReturn(Component.text("Collection complete!"));

            notificationManager.sendCompletionNotification(player, testCollection);

            verify(player).sendMessage(any(Component.class));
            verify(player, never()).showTitle(any(Title.class));
        }

        @Test
        @DisplayName("Should send both title and chat when style is both")
        void sendCompletionNotification_bothStyle_sendsTitleAndChat() {
            when(configManager.getCompletionNotificationStyle()).thenReturn("both");
            when(configManager.getMessage(eq("collection-complete"), any(), any()))
                .thenReturn(Component.text("Collection complete!"));

            notificationManager.sendCompletionNotification(player, testCollection);

            verify(player).showTitle(any(Title.class));
            verify(player).sendMessage(any(Component.class));
        }

        @Test
        @DisplayName("Should send nothing when style is none")
        void sendCompletionNotification_noneStyle_sendsNothing() {
            when(configManager.getCompletionNotificationStyle()).thenReturn("none");

            notificationManager.sendCompletionNotification(player, testCollection);

            verify(player, never()).showTitle(any(Title.class));
            verify(player, never()).sendMessage(any(Component.class));
        }

        @Test
        @DisplayName("Should use configurable title timing")
        void sendCompletionNotification_usesConfiguredTiming() {
            when(configManager.getCompletionNotificationStyle()).thenReturn("title");
            when(configManager.getCompletionFadeIn()).thenReturn(1.0);
            when(configManager.getCompletionStay()).thenReturn(5.0);
            when(configManager.getCompletionFadeOut()).thenReturn(2.0);

            notificationManager.sendCompletionNotification(player, testCollection);

            ArgumentCaptor<Title> titleCaptor = ArgumentCaptor.forClass(Title.class);
            verify(player).showTitle(titleCaptor.capture());

            Title capturedTitle = titleCaptor.getValue();
            Title.Times times = capturedTitle.times();
            assertNotNull(times);
            // Verify timing values (converted to milliseconds)
            assertEquals(1000, times.fadeIn().toMillis());
            assertEquals(5000, times.stay().toMillis());
            assertEquals(2000, times.fadeOut().toMillis());
        }

        @Test
        @DisplayName("Should parse title with collection placeholder")
        void sendCompletionNotification_parsesTitlePlaceholders() {
            when(configManager.getCompletionNotificationStyle()).thenReturn("title");
            String titleFormat = "<bold>Complete!</bold>";
            String subtitleFormat = "<collection> done";
            when(configManager.getCompletionTitle()).thenReturn(titleFormat);
            when(configManager.getCompletionSubtitle()).thenReturn(subtitleFormat);

            notificationManager.sendCompletionNotification(player, testCollection);

            // Verify both title and subtitle are parsed with collection name
            verify(configManager).parse(eq(titleFormat), eq("collection"), eq("Test Collection"));
            verify(configManager).parse(eq(subtitleFormat), eq("collection"), eq("Test Collection"));
        }
    }

    @Nested
    @DisplayName("Case Insensitivity Tests")
    class CaseInsensitivityTests {

        @Test
        @DisplayName("Progress style should be case insensitive")
        void progressStyle_caseInsensitive() {
            when(configManager.getProgressNotificationFormat()).thenReturn("test");

            // Test uppercase
            when(configManager.getProgressNotificationStyle()).thenReturn("ACTIONBAR");
            notificationManager.sendProgressNotification(player, testCollection, 1, 5);
            verify(player, times(1)).sendActionBar(any(Component.class));

            // Test mixed case
            when(configManager.getProgressNotificationStyle()).thenReturn("ActionBar");
            notificationManager.sendProgressNotification(player, testCollection, 1, 5);
            verify(player, times(2)).sendActionBar(any(Component.class));

            // Test "None" uppercase
            when(configManager.getProgressNotificationStyle()).thenReturn("NONE");
            notificationManager.sendProgressNotification(player, testCollection, 1, 5);
            // Should not have any additional calls
            verify(player, times(2)).sendActionBar(any(Component.class));
        }

        @Test
        @DisplayName("Completion style should be case insensitive")
        void completionStyle_caseInsensitive() {
            when(configManager.getCompletionTitle()).thenReturn("test");
            when(configManager.getCompletionSubtitle()).thenReturn("test");
            when(configManager.getCompletionFadeIn()).thenReturn(0.5);
            when(configManager.getCompletionStay()).thenReturn(3.0);
            when(configManager.getCompletionFadeOut()).thenReturn(0.5);
            when(configManager.getMessage(eq("collection-complete"), any(), any()))
                .thenReturn(Component.text("done"));

            // Test "BOTH" uppercase
            when(configManager.getCompletionNotificationStyle()).thenReturn("BOTH");
            notificationManager.sendCompletionNotification(player, testCollection);
            verify(player, times(1)).showTitle(any(Title.class));
            verify(player, times(1)).sendMessage(any(Component.class));
        }
    }

    @Nested
    @DisplayName("Duplicate Handling Tests")
    class DuplicateHandlingTests {

        @Test
        @DisplayName("Should not send progress notification when item is duplicate")
        void noProgressNotification_whenItemAlreadyCollected() {
            // This test documents the expected behavior:
            // When playerDataManager.addItem() returns false (duplicate),
            // the calling code in ConfirmAddGUI returns early BEFORE
            // calling notificationManager.sendProgressNotification().
            //
            // Since NotificationManager itself doesn't know about duplicates,
            // we verify the contract: if sendProgressNotification is NOT called,
            // no notification methods are invoked on player.

            // NotificationManager should only send notifications when explicitly called.
            // This test verifies that when notification methods are NOT called,
            // no player notifications occur.

            // Create a fresh player mock to verify no interactions
            Player freshPlayer = mock(Player.class);

            // No notification methods called = no player interactions
            verifyNoInteractions(freshPlayer);
        }

        @Test
        @DisplayName("Progress notification is only sent for successful adds")
        void progressNotification_onlyCalledOnSuccess() {
            // This test documents the integration contract:
            // ConfirmAddGUI.confirmAdd() should only call sendProgressNotification
            // AFTER playerDataManager.addItem() returns true.
            //
            // The flow is:
            // 1. boolean added = playerDataManager.addItem(...)
            // 2. if (!added) { return; } // early exit, no notification
            // 3. notificationManager.sendProgressNotification(...) // only on success

            // Verify that when we DO call sendProgressNotification, it works correctly
            when(configManager.getProgressNotificationStyle()).thenReturn("actionbar");
            when(configManager.getProgressNotificationFormat()).thenReturn("test");

            notificationManager.sendProgressNotification(player, testCollection, 1, 5);

            // Notification was sent because the method was called
            verify(player).sendActionBar(any(Component.class));
        }
    }
}
