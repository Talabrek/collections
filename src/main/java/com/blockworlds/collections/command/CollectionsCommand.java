package com.blockworlds.collections.command;

import com.blockworlds.collections.Collections;
import com.blockworlds.collections.config.ConfigManager;
import com.blockworlds.collections.gui.CollectionMenuGUI;
import com.blockworlds.collections.manager.CollectionManager;
import com.blockworlds.collections.manager.DataMigrationManager;
import com.blockworlds.collections.manager.EventManager;
import com.blockworlds.collections.manager.GoggleManager;
import com.blockworlds.collections.manager.PlayerDataManager;
import com.blockworlds.collections.manager.RewardManager;
import com.blockworlds.collections.manager.SpawnManager;
import com.blockworlds.collections.manager.ZoneManager;
import com.blockworlds.collections.model.Collectible;
import com.blockworlds.collections.model.Collection;
import com.blockworlds.collections.model.CollectibleTier;
import com.blockworlds.collections.model.CollectionItem;
import com.blockworlds.collections.model.PlayerProgress;
import com.blockworlds.collections.model.SpawnZone;
import com.blockworlds.collections.spawn.SpawnResult;
import com.blockworlds.collections.util.ItemBuilder;
import com.blockworlds.collections.util.PDCKeys;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.PlayerProfileListResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import com.destroystokyo.paper.profile.PlayerProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Main /collections command with Brigadier.
 */
public class CollectionsCommand {

    private final Collections plugin;
    private final ConfigManager configManager;
    private final CollectionManager collectionManager;
    private final PlayerDataManager playerDataManager;
    private final DataMigrationManager dataMigrationManager;

    public CollectionsCommand(Collections plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.collectionManager = plugin.getCollectionManager();
        this.playerDataManager = plugin.getPlayerDataManager();
        this.dataMigrationManager = plugin.getDataMigrationManager();
    }

    /**
     * Register the command with the registrar.
     *
     * @param commands The command registrar
     */
    public void register(Commands commands) {
        LiteralCommandNode<CommandSourceStack> node = Commands.literal("collections")
                // Base command - opens journal or shows help
                .executes(this::openJournal)

                // /collections list - list all collections
                .then(Commands.literal("list")
                        .requires(src -> src.getSender().hasPermission("collections.use"))
                        .executes(this::listCollections))

                // /collections reload - admin reload
                .then(Commands.literal("reload")
                        .requires(src -> src.getSender().hasPermission("collections.admin"))
                        .executes(this::reload))

                // /collections stats - show player stats
                .then(Commands.literal("stats")
                        .requires(src -> src.getSender().hasPermission("collections.use"))
                        .executes(this::showStats))

                // /collections help - show help
                .then(Commands.literal("help")
                        .executes(this::showHelp))

                // /collections give - admin give commands
                .then(Commands.literal("give")
                        .requires(src -> src.getSender().hasPermission("collections.admin"))
                        // /collections give goggles <player> <tier>
                        .then(Commands.literal("goggles")
                                .then(Commands.argument("player", ArgumentTypes.player())
                                        .then(Commands.argument("tier", StringArgumentType.word())
                                                .suggests(this::suggestGoggleTiers)
                                                .executes(this::giveGoggles))))
                        // /collections give item <player> <collection> <item>
                        .then(Commands.literal("item")
                                .then(Commands.argument("player", ArgumentTypes.player())
                                        .then(Commands.argument("collection", StringArgumentType.word())
                                                .suggests(this::suggestCollections)
                                                .then(Commands.argument("item", StringArgumentType.word())
                                                        .suggests(this::suggestCollectionItems)
                                                        .executes(this::giveItem)))))
                        // /collections give progress <player> <collection> <item>
                        .then(Commands.literal("progress")
                                .then(Commands.argument("player", ArgumentTypes.player())
                                        .then(Commands.argument("collection", StringArgumentType.word())
                                                .suggests(this::suggestCollections)
                                                .then(Commands.argument("item", StringArgumentType.word())
                                                        .suggests(this::suggestCollectionItems)
                                                        .executes(this::giveProgress))))))

                // /collections spawn <zone> - force spawn collectible
                .then(Commands.literal("spawn")
                        .requires(src -> src.getSender().hasPermission("collections.admin"))
                        .then(Commands.argument("zone", StringArgumentType.word())
                                .suggests(this::suggestZones)
                                .executes(this::forceSpawn)))

                // /collections clear [zone] - clear collectibles
                .then(Commands.literal("clear")
                        .requires(src -> src.getSender().hasPermission("collections.admin"))
                        .executes(this::clearAll)
                        .then(Commands.argument("zone", StringArgumentType.word())
                                .suggests(this::suggestZones)
                                .executes(this::clearZone)))

                // /collections complete <player> <collection> - mark collection complete
                .then(Commands.literal("complete")
                        .requires(src -> src.getSender().hasPermission("collections.admin"))
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .then(Commands.argument("collection", StringArgumentType.word())
                                        .suggests(this::suggestCollections)
                                        .executes(this::completeCollection))))

                // /collections reset <player> [collection] - reset progress
                .then(Commands.literal("reset")
                        .requires(src -> src.getSender().hasPermission("collections.admin"))
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .executes(this::resetAll)
                                .then(Commands.argument("collection", StringArgumentType.word())
                                        .suggests(this::suggestCollections)
                                        .executes(this::resetCollection))))

                // /collections debug - toggle debug mode
                .then(Commands.literal("debug")
                        .requires(src -> src.getSender().hasPermission("collections.admin"))
                        .executes(this::toggleDebug))

                // /collections event - event management
                .then(Commands.literal("event")
                        .requires(src -> src.getSender().hasPermission("collections.admin"))
                        .then(Commands.literal("start")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(this::startEvent)))
                        .then(Commands.literal("end")
                                .executes(this::endAllEvents)
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(this::endEvent)))
                        .then(Commands.literal("list")
                                .executes(this::listEvents)))

                // /collections admin - admin operations on any player (online or offline)
                .then(Commands.literal("admin")
                        .requires(src -> src.getSender().hasPermission("collections.admin"))
                        // /collections admin inspect <target>
                        .then(Commands.literal("inspect")
                                .then(Commands.argument("target", ArgumentTypes.playerProfiles())
                                        .executes(this::adminInspect)))
                        // /collections admin complete <target> <collection> [--rewards]
                        .then(Commands.literal("complete")
                                .then(Commands.argument("target", ArgumentTypes.playerProfiles())
                                        .then(Commands.argument("collection", StringArgumentType.word())
                                                .suggests(this::suggestCollections)
                                                .executes(ctx -> adminComplete(ctx, false))
                                                .then(Commands.literal("--rewards")
                                                        .executes(ctx -> adminComplete(ctx, true)))))))

                // /collections export - data export commands
                .then(Commands.literal("export")
                        .requires(src -> src.getSender().hasPermission("collections.admin"))
                        // /collections export <player> - export single player
                        .then(Commands.argument("target", ArgumentTypes.playerProfiles())
                                .executes(this::exportPlayer))
                        // /collections export all - export all players
                        .then(Commands.literal("all")
                                .executes(this::exportAll)))

                // /collections import - data import commands
                .then(Commands.literal("import")
                        .requires(src -> src.getSender().hasPermission("collections.admin"))
                        .then(Commands.argument("file", StringArgumentType.word())
                                .suggests(this::suggestExportFiles)
                                .executes(ctx -> importData(ctx, false))
                                .then(Commands.literal("--dry-run")
                                        .executes(ctx -> importData(ctx, true)))))

                .build();

        commands.register(node, "Collection journal and management commands", List.of("col", "collect"));
    }

    /**
     * Open the collection journal GUI.
     */
    private int openJournal(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) {
            ctx.getSource().getSender().sendMessage(
                    Component.text("This command can only be used by players.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        if (!player.hasPermission("collections.use")) {
            player.sendMessage(configManager.getMessage("no-permission"));
            return Command.SINGLE_SUCCESS;
        }

        // Open the collection journal GUI
        CollectionMenuGUI gui = new CollectionMenuGUI(plugin, player);
        gui.open();

        return Command.SINGLE_SUCCESS;
    }

    /**
     * List all available collections.
     */
    private int listCollections(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        Map<String, Collection> allCollections = collectionManager.getAllCollections();

        if (allCollections.isEmpty()) {
            sender.sendMessage(Component.text("No collections loaded.", NamedTextColor.YELLOW));
            return Command.SINGLE_SUCCESS;
        }

        sender.sendMessage(Component.text()
                .append(Component.text("=== ", NamedTextColor.GOLD))
                .append(Component.text("Collections (" + allCollections.size() + ")", NamedTextColor.YELLOW))
                .append(Component.text(" ===", NamedTextColor.GOLD))
                .build());

        for (Collection collection : allCollections.values()) {
            Component tierColor = Component.text("[" + collection.tier().name() + "] ",
                    collection.tier().getColor());

            int itemCount = collection.items().size();
            String progress = "";

            // Show progress if sender is a player
            if (sender instanceof Player player) {
                PlayerProgress playerProgress = playerDataManager.getProgressBlocking(player.getUniqueId());
                if (playerProgress != null) {
                    int collected = playerProgress.getCollectedCount(collection.id());
                    progress = " (" + collected + "/" + itemCount + ")";
                }
            }

            sender.sendMessage(Component.text()
                    .append(tierColor)
                    .append(Component.text(collection.name(), NamedTextColor.WHITE))
                    .append(Component.text(" - " + itemCount + " items" + progress, NamedTextColor.GRAY))
                    .build());
        }

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Reload the plugin configuration.
     */
    private int reload(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();

        sender.sendMessage(Component.text("Reloading Collections...", NamedTextColor.YELLOW));

        try {
            // Full plugin reload
            plugin.reload();

            ZoneManager zoneManager = plugin.getZoneManager();
            int zoneCount = zoneManager != null ? zoneManager.getZoneCount() : 0;

            sender.sendMessage(Component.text()
                    .append(Component.text("Collections reloaded! ", NamedTextColor.GREEN))
                    .append(Component.text("(" + collectionManager.getCollectionCount() + " collections, " +
                            zoneCount + " zones)", NamedTextColor.GRAY))
                    .build());

        } catch (Exception e) {
            sender.sendMessage(Component.text("Reload failed: " + e.getMessage(), NamedTextColor.RED));
            plugin.getLogger().warning("Reload failed: " + e.getMessage());
        }

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Show player statistics.
     */
    private int showStats(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) {
            ctx.getSource().getSender().sendMessage(
                    Component.text("This command can only be used by players.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        PlayerProgress progress = playerDataManager.getProgressBlocking(player.getUniqueId());

        player.sendMessage(Component.text()
                .append(Component.text("=== ", NamedTextColor.GOLD))
                .append(Component.text("Your Collection Stats", NamedTextColor.YELLOW))
                .append(Component.text(" ===", NamedTextColor.GOLD))
                .build());

        if (progress == null) {
            player.sendMessage(Component.text("No collection data found.", NamedTextColor.GRAY));
            return Command.SINGLE_SUCCESS;
        }

        player.sendMessage(Component.text()
                .append(Component.text("Total Items Collected: ", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(progress.getTotalCollectiblesCollected()), NamedTextColor.GREEN))
                .build());

        player.sendMessage(Component.text()
                .append(Component.text("Collections Completed: ", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(progress.getTotalCollectionsCompleted()), NamedTextColor.GREEN))
                .build());

        // Show progress per collection
        Map<String, Collection> allCollections = collectionManager.getAllCollections();
        for (Collection collection : allCollections.values()) {
            int collected = progress.getCollectedCount(collection.id());
            int total = collection.items().size();
            boolean complete = progress.hasCompleted(collection.id());

            NamedTextColor statusColor = complete ? NamedTextColor.GREEN :
                    (collected > 0 ? NamedTextColor.YELLOW : NamedTextColor.GRAY);

            player.sendMessage(Component.text()
                    .append(Component.text("  " + collection.name() + ": ", NamedTextColor.WHITE))
                    .append(Component.text(collected + "/" + total, statusColor))
                    .append(complete ? Component.text(" [COMPLETE]", NamedTextColor.GREEN) : Component.empty())
                    .build());
        }

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Suggest goggle tiers for tab completion.
     */
    private CompletableFuture<Suggestions> suggestGoggleTiers(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        builder.suggest("basic");
        builder.suggest("master");
        return builder.buildFuture();
    }

    /**
     * Give goggles to a player.
     */
    private int giveGoggles(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();

        try {
            PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
            List<Player> players = resolver.resolve(ctx.getSource());

            if (players.isEmpty()) {
                sender.sendMessage(Component.text("No player found.", NamedTextColor.RED));
                return Command.SINGLE_SUCCESS;
            }

            Player target = players.get(0);
            String tierArg = ctx.getArgument("tier", String.class).toLowerCase();

            GoggleManager goggleManager = plugin.getGoggleManager();
            if (goggleManager == null) {
                sender.sendMessage(Component.text("GoggleManager not initialized.", NamedTextColor.RED));
                return Command.SINGLE_SUCCESS;
            }

            // Determine goggle tier
            CollectibleTier tier = switch (tierArg) {
                case "basic", "uncommon" -> CollectibleTier.UNCOMMON;
                case "master", "rare" -> CollectibleTier.RARE;
                default -> null;
            };

            if (tier == null) {
                sender.sendMessage(Component.text("Invalid tier. Use 'basic' or 'master'.", NamedTextColor.RED));
                return Command.SINGLE_SUCCESS;
            }

            // Create and give goggles
            ItemStack goggles = goggleManager.createGoggles(tier);

            if (target.getInventory().firstEmpty() == -1) {
                target.getWorld().dropItemNaturally(target.getLocation(), goggles);
                sender.sendMessage(Component.text("Goggles dropped at " + target.getName() + "'s feet (inventory full).",
                        NamedTextColor.YELLOW));
            } else {
                target.getInventory().addItem(goggles);
                sender.sendMessage(Component.text()
                        .append(Component.text("Gave ", NamedTextColor.GREEN))
                        .append(Component.text(tier == CollectibleTier.RARE ? "Master " : "", NamedTextColor.GOLD))
                        .append(Component.text("Collector's Goggles to ", NamedTextColor.GREEN))
                        .append(Component.text(target.getName(), NamedTextColor.WHITE))
                        .build());
            }

            // Notify target
            if (!target.equals(sender)) {
                target.sendMessage(Component.text()
                        .append(Component.text("You received ", NamedTextColor.GREEN))
                        .append(Component.text(tier == CollectibleTier.RARE ? "Master " : "", NamedTextColor.GOLD))
                        .append(Component.text("Collector's Goggles!", NamedTextColor.GREEN))
                        .build());
            }

        } catch (Exception e) {
            sender.sendMessage(Component.text("Error giving goggles: " + e.getMessage(), NamedTextColor.RED));
        }

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Show help information.
     */
    private int showHelp(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();

        sender.sendMessage(Component.text()
                .append(Component.text("=== ", NamedTextColor.GOLD))
                .append(Component.text("Collections Help", NamedTextColor.YELLOW))
                .append(Component.text(" ===", NamedTextColor.GOLD))
                .build());

        sender.sendMessage(Component.text()
                .append(Component.text("/collections", NamedTextColor.AQUA))
                .append(Component.text(" - Open your collection journal", NamedTextColor.GRAY))
                .build());

        sender.sendMessage(Component.text()
                .append(Component.text("/collections list", NamedTextColor.AQUA))
                .append(Component.text(" - List all collections", NamedTextColor.GRAY))
                .build());

        sender.sendMessage(Component.text()
                .append(Component.text("/collections stats", NamedTextColor.AQUA))
                .append(Component.text(" - View your statistics", NamedTextColor.GRAY))
                .build());

        if (sender.hasPermission("collections.admin")) {
            sender.sendMessage(Component.text()
                    .append(Component.text("/collections reload", NamedTextColor.AQUA))
                    .append(Component.text(" - Reload configuration", NamedTextColor.GRAY))
                    .build());

            sender.sendMessage(Component.text()
                    .append(Component.text("/collections give goggles <player> <tier>", NamedTextColor.AQUA))
                    .append(Component.text(" - Give goggles", NamedTextColor.GRAY))
                    .build());

            sender.sendMessage(Component.text()
                    .append(Component.text("/collections give item <player> <col> <item>", NamedTextColor.AQUA))
                    .append(Component.text(" - Give item", NamedTextColor.GRAY))
                    .build());

            sender.sendMessage(Component.text()
                    .append(Component.text("/collections give progress <player> <col> <item>", NamedTextColor.AQUA))
                    .append(Component.text(" - Add to journal", NamedTextColor.GRAY))
                    .build());

            sender.sendMessage(Component.text()
                    .append(Component.text("/collections spawn <zone>", NamedTextColor.AQUA))
                    .append(Component.text(" - Force spawn", NamedTextColor.GRAY))
                    .build());

            sender.sendMessage(Component.text()
                    .append(Component.text("/collections clear [zone]", NamedTextColor.AQUA))
                    .append(Component.text(" - Clear collectibles", NamedTextColor.GRAY))
                    .build());

            sender.sendMessage(Component.text()
                    .append(Component.text("/collections complete <player> <collection>", NamedTextColor.AQUA))
                    .append(Component.text(" - Complete collection", NamedTextColor.GRAY))
                    .build());

            sender.sendMessage(Component.text()
                    .append(Component.text("/collections reset <player> [collection]", NamedTextColor.AQUA))
                    .append(Component.text(" - Reset progress", NamedTextColor.GRAY))
                    .build());

            sender.sendMessage(Component.text()
                    .append(Component.text("/collections debug", NamedTextColor.AQUA))
                    .append(Component.text(" - Toggle debug mode", NamedTextColor.GRAY))
                    .build());

            sender.sendMessage(Component.text()
                    .append(Component.text("/collections event start|end|list", NamedTextColor.AQUA))
                    .append(Component.text(" - Manage events", NamedTextColor.GRAY))
                    .build());

            sender.sendMessage(Component.text()
                    .append(Component.text("/collections admin inspect <player>", NamedTextColor.AQUA))
                    .append(Component.text(" - View player progress", NamedTextColor.GRAY))
                    .build());

            sender.sendMessage(Component.text()
                    .append(Component.text("/collections admin complete <player> <col> [--rewards]", NamedTextColor.AQUA))
                    .append(Component.text(" - Force complete", NamedTextColor.GRAY))
                    .build());

            sender.sendMessage(Component.text()
                    .append(Component.text("/collections export <player>", NamedTextColor.AQUA))
                    .append(Component.text(" - Export player data", NamedTextColor.GRAY))
                    .build());

            sender.sendMessage(Component.text()
                    .append(Component.text("/collections export all", NamedTextColor.AQUA))
                    .append(Component.text(" - Export all player data", NamedTextColor.GRAY))
                    .build());

            sender.sendMessage(Component.text()
                    .append(Component.text("/collections import <file>", NamedTextColor.AQUA))
                    .append(Component.text(" - Import player data", NamedTextColor.GRAY))
                    .build());

            sender.sendMessage(Component.text()
                    .append(Component.text("/collections import <file> --dry-run", NamedTextColor.AQUA))
                    .append(Component.text(" - Preview import", NamedTextColor.GRAY))
                    .build());
        }

        return Command.SINGLE_SUCCESS;
    }

    // ========== Tab Completion Helpers ==========

    /**
     * Suggest collections for tab completion.
     */
    private CompletableFuture<Suggestions> suggestCollections(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        for (String id : collectionManager.getAllCollections().keySet()) {
            builder.suggest(id);
        }
        return builder.buildFuture();
    }

    /**
     * Suggest collection items for tab completion.
     */
    private CompletableFuture<Suggestions> suggestCollectionItems(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        try {
            String collectionId = ctx.getArgument("collection", String.class);
            Collection collection = collectionManager.getCollection(collectionId);
            if (collection != null) {
                for (CollectionItem item : collection.items()) {
                    builder.suggest(item.id());
                }
            }
        } catch (Exception ignored) {
            // Argument not yet provided
        }
        return builder.buildFuture();
    }

    /**
     * Suggest zones for tab completion.
     */
    private CompletableFuture<Suggestions> suggestZones(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        ZoneManager zoneManager = plugin.getZoneManager();
        if (zoneManager != null) {
            for (SpawnZone zone : zoneManager.getAllZones().values()) {
                builder.suggest(zone.id());
            }
        }
        return builder.buildFuture();
    }

    // ========== Give Commands ==========

    /**
     * Give a physical collection item to a player.
     */
    private int giveItem(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();

        try {
            PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
            List<Player> players = resolver.resolve(ctx.getSource());

            if (players.isEmpty()) {
                sender.sendMessage(Component.text("No player found.", NamedTextColor.RED));
                return Command.SINGLE_SUCCESS;
            }

            Player target = players.get(0);
            String collectionId = ctx.getArgument("collection", String.class);
            String itemId = ctx.getArgument("item", String.class);

            Collection collection = collectionManager.getCollection(collectionId);
            if (collection == null) {
                sender.sendMessage(Component.text("Collection not found: " + collectionId, NamedTextColor.RED));
                return Command.SINGLE_SUCCESS;
            }

            CollectionItem item = collection.getItem(itemId);
            if (item == null) {
                sender.sendMessage(Component.text("Item not found: " + itemId, NamedTextColor.RED));
                return Command.SINGLE_SUCCESS;
            }

            // Create the physical item
            ItemStack physicalItem = createCollectionItem(collection, item);

            if (target.getInventory().firstEmpty() == -1) {
                target.getWorld().dropItemNaturally(target.getLocation(), physicalItem);
                sender.sendMessage(Component.text("Item dropped at " + target.getName() + "'s feet (inventory full).",
                        NamedTextColor.YELLOW));
            } else {
                target.getInventory().addItem(physicalItem);
                sender.sendMessage(Component.text()
                        .append(Component.text("Gave ", NamedTextColor.GREEN))
                        .append(Component.text(item.name(), NamedTextColor.GOLD))
                        .append(Component.text(" to ", NamedTextColor.GREEN))
                        .append(Component.text(target.getName(), NamedTextColor.WHITE))
                        .build());
            }

        } catch (Exception e) {
            sender.sendMessage(Component.text("Error: " + e.getMessage(), NamedTextColor.RED));
        }

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Add an item directly to a player's journal.
     */
    private int giveProgress(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();

        try {
            PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
            List<Player> players = resolver.resolve(ctx.getSource());

            if (players.isEmpty()) {
                sender.sendMessage(Component.text("No player found.", NamedTextColor.RED));
                return Command.SINGLE_SUCCESS;
            }

            Player target = players.get(0);
            String collectionId = ctx.getArgument("collection", String.class);
            String itemId = ctx.getArgument("item", String.class);

            Collection collection = collectionManager.getCollection(collectionId);
            if (collection == null) {
                sender.sendMessage(Component.text("Collection not found: " + collectionId, NamedTextColor.RED));
                return Command.SINGLE_SUCCESS;
            }

            CollectionItem item = collection.getItem(itemId);
            if (item == null) {
                sender.sendMessage(Component.text("Item not found: " + itemId, NamedTextColor.RED));
                return Command.SINGLE_SUCCESS;
            }

            boolean added = playerDataManager.addItem(target.getUniqueId(), collectionId, itemId);
            if (added) {
                sender.sendMessage(Component.text()
                        .append(Component.text("Added ", NamedTextColor.GREEN))
                        .append(Component.text(item.name(), NamedTextColor.GOLD))
                        .append(Component.text(" to ", NamedTextColor.GREEN))
                        .append(Component.text(target.getName(), NamedTextColor.WHITE))
                        .append(Component.text("'s journal", NamedTextColor.GREEN))
                        .build());
            } else {
                sender.sendMessage(Component.text("Player already has this item in their journal.", NamedTextColor.YELLOW));
            }

        } catch (Exception e) {
            sender.sendMessage(Component.text("Error: " + e.getMessage(), NamedTextColor.RED));
        }

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Create a physical collection item.
     */
    private ItemStack createCollectionItem(Collection collection, CollectionItem item) {
        List<String> fullLore = new ArrayList<>(item.lore());
        fullLore.add("");
        fullLore.add("<gray>Part of: <gold>" + collection.name() + "</gold></gray>");
        fullLore.add("<gray>Right-click to add to journal</gray>");

        if (item.soulbound()) {
            fullLore.add("<red>Soulbound</red>");
        }

        return ItemBuilder.of(item.material())
                .name("<gold>" + item.name() + "</gold>")
                .lore(fullLore)
                .data(PDCKeys.COLLECTION_ID(), collection.id())
                .data(PDCKeys.ITEM_ID(), item.id())
                .data(PDCKeys.SOULBOUND(), item.soulbound() ? (byte) 1 : (byte) 0)
                .build();
    }

    // ========== Spawn Commands ==========

    /**
     * Force spawn a collectible in a zone.
     */
    private int forceSpawn(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        String zoneId = ctx.getArgument("zone", String.class);

        ZoneManager zoneManager = plugin.getZoneManager();
        if (zoneManager == null) {
            sender.sendMessage(Component.text("ZoneManager not initialized.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        SpawnZone zone = zoneManager.getZone(zoneId);
        if (zone == null) {
            sender.sendMessage(Component.text("Zone not found: " + zoneId, NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        SpawnManager spawnManager = plugin.getSpawnManager();
        if (spawnManager == null) {
            sender.sendMessage(Component.text("SpawnManager not initialized.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        // Use the new forceSpawnWithResult for detailed feedback
        SpawnResult result = spawnManager.forceSpawnWithResult(zone);

        if (result.success()) {
            sender.sendMessage(Component.text()
                    .append(Component.text("Spawned collectible in zone ", NamedTextColor.GREEN))
                    .append(Component.text(zone.name(), NamedTextColor.GOLD))
                    .append(Component.text(" at ", NamedTextColor.GREEN))
                    .append(Component.text(formatLocation(result.location()), NamedTextColor.WHITE))
                    .build());

            if (result.relaxedConditions()) {
                sender.sendMessage(Component.text("(Note: Used relaxed spawn conditions)", NamedTextColor.YELLOW));
            }
        } else {
            // Detailed error message
            var stats = result.stats();
            sender.sendMessage(Component.text()
                    .append(Component.text("Failed to spawn collectible after ", NamedTextColor.RED))
                    .append(Component.text(String.valueOf(stats.getTotalAttempts()), NamedTextColor.WHITE))
                    .append(Component.text(" attempts.", NamedTextColor.RED))
                    .build());

            sender.sendMessage(Component.text()
                    .append(Component.text("Top failure reason: ", NamedTextColor.GRAY))
                    .append(Component.text(stats.getTopReason(), NamedTextColor.YELLOW))
                    .build());

            // Show detailed breakdown for admins
            if (sender.hasPermission("collections.admin")) {
                sender.sendMessage(Component.text()
                        .append(Component.text("Details: ", NamedTextColor.GRAY))
                        .append(Component.text(stats.getSummary(), NamedTextColor.DARK_GRAY))
                        .build());

                sender.sendMessage(Component.text("Check zone conditions in zones.yml or expand zone bounds.", NamedTextColor.GRAY));
            }
        }

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Clear all collectibles.
     */
    private int clearAll(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        SpawnManager spawnManager = plugin.getSpawnManager();
        if (spawnManager == null) {
            sender.sendMessage(Component.text("SpawnManager not initialized.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        int count = spawnManager.clearAll();
        sender.sendMessage(Component.text()
                .append(Component.text("Cleared ", NamedTextColor.GREEN))
                .append(Component.text(String.valueOf(count), NamedTextColor.WHITE))
                .append(Component.text(" collectibles.", NamedTextColor.GREEN))
                .build());

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Clear collectibles in a specific zone.
     */
    private int clearZone(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        String zoneId = ctx.getArgument("zone", String.class);

        ZoneManager zoneManager = plugin.getZoneManager();
        if (zoneManager == null) {
            sender.sendMessage(Component.text("ZoneManager not initialized.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        SpawnZone zone = zoneManager.getZone(zoneId);
        if (zone == null) {
            sender.sendMessage(Component.text("Zone not found: " + zoneId, NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        SpawnManager spawnManager = plugin.getSpawnManager();
        if (spawnManager == null) {
            sender.sendMessage(Component.text("SpawnManager not initialized.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        int count = spawnManager.clearZone(zoneId);

        sender.sendMessage(Component.text()
                .append(Component.text("Cleared ", NamedTextColor.GREEN))
                .append(Component.text(String.valueOf(count), NamedTextColor.WHITE))
                .append(Component.text(" collectibles from ", NamedTextColor.GREEN))
                .append(Component.text(zone.name(), NamedTextColor.GOLD))
                .build());

        return Command.SINGLE_SUCCESS;
    }

    // ========== Complete/Reset Commands ==========

    /**
     * Mark a collection as complete for a player.
     */
    private int completeCollection(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();

        try {
            PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
            List<Player> players = resolver.resolve(ctx.getSource());

            if (players.isEmpty()) {
                sender.sendMessage(Component.text("No player found.", NamedTextColor.RED));
                return Command.SINGLE_SUCCESS;
            }

            Player target = players.get(0);
            String collectionId = ctx.getArgument("collection", String.class);

            Collection collection = collectionManager.getCollection(collectionId);
            if (collection == null) {
                sender.sendMessage(Component.text("Collection not found: " + collectionId, NamedTextColor.RED));
                return Command.SINGLE_SUCCESS;
            }

            // Add all items to the player's progress
            for (CollectionItem item : collection.items()) {
                playerDataManager.addItem(target.getUniqueId(), collectionId, item.id());
            }

            sender.sendMessage(Component.text()
                    .append(Component.text("Completed ", NamedTextColor.GREEN))
                    .append(Component.text(collection.name(), NamedTextColor.GOLD))
                    .append(Component.text(" for ", NamedTextColor.GREEN))
                    .append(Component.text(target.getName(), NamedTextColor.WHITE))
                    .build());

        } catch (Exception e) {
            sender.sendMessage(Component.text("Error: " + e.getMessage(), NamedTextColor.RED));
        }

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Reset all progress for a player.
     */
    private int resetAll(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();

        try {
            PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
            List<Player> players = resolver.resolve(ctx.getSource());

            if (players.isEmpty()) {
                sender.sendMessage(Component.text("No player found.", NamedTextColor.RED));
                return Command.SINGLE_SUCCESS;
            }

            Player target = players.get(0);

            playerDataManager.resetPlayer(target.getUniqueId());
            sender.sendMessage(Component.text()
                    .append(Component.text("Reset all progress for ", NamedTextColor.GREEN))
                    .append(Component.text(target.getName(), NamedTextColor.WHITE))
                    .build());

        } catch (Exception e) {
            sender.sendMessage(Component.text("Error: " + e.getMessage(), NamedTextColor.RED));
        }

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Reset progress for a specific collection.
     */
    private int resetCollection(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();

        try {
            PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
            List<Player> players = resolver.resolve(ctx.getSource());

            if (players.isEmpty()) {
                sender.sendMessage(Component.text("No player found.", NamedTextColor.RED));
                return Command.SINGLE_SUCCESS;
            }

            Player target = players.get(0);
            String collectionId = ctx.getArgument("collection", String.class);

            Collection collection = collectionManager.getCollection(collectionId);
            if (collection == null) {
                sender.sendMessage(Component.text("Collection not found: " + collectionId, NamedTextColor.RED));
                return Command.SINGLE_SUCCESS;
            }

            playerDataManager.resetCollection(target.getUniqueId(), collectionId);
            sender.sendMessage(Component.text()
                    .append(Component.text("Reset ", NamedTextColor.GREEN))
                    .append(Component.text(collection.name(), NamedTextColor.GOLD))
                    .append(Component.text(" for ", NamedTextColor.GREEN))
                    .append(Component.text(target.getName(), NamedTextColor.WHITE))
                    .build());

        } catch (Exception e) {
            sender.sendMessage(Component.text("Error: " + e.getMessage(), NamedTextColor.RED));
        }

        return Command.SINGLE_SUCCESS;
    }

    // ========== Debug Command ==========

    /**
     * Toggle debug mode.
     */
    private int toggleDebug(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();

        boolean newState = !configManager.isDebugMode();
        configManager.setDebugMode(newState);

        sender.sendMessage(Component.text()
                .append(Component.text("Debug mode ", NamedTextColor.YELLOW))
                .append(Component.text(newState ? "ENABLED" : "DISABLED",
                        newState ? NamedTextColor.GREEN : NamedTextColor.RED))
                .build());

        return Command.SINGLE_SUCCESS;
    }

    // ========== Event Commands ==========

    /**
     * Start an event.
     */
    private int startEvent(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        String eventName = ctx.getArgument("name", String.class);

        EventManager eventManager = plugin.getEventManager();
        String startedBy = sender instanceof Player p ? p.getName() : "Console";

        if (eventManager.startEvent(eventName, startedBy)) {
            sender.sendMessage(Component.text()
                    .append(Component.text("Event ", NamedTextColor.GREEN))
                    .append(Component.text(eventName, NamedTextColor.GOLD))
                    .append(Component.text(" started! EVENT tier collectibles are now visible to all.", NamedTextColor.GREEN))
                    .build());
        } else {
            sender.sendMessage(Component.text("Event '" + eventName + "' is already active.", NamedTextColor.YELLOW));
        }

        return Command.SINGLE_SUCCESS;
    }

    /**
     * End a specific event.
     */
    private int endEvent(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        String eventName = ctx.getArgument("name", String.class);

        EventManager eventManager = plugin.getEventManager();

        if (eventManager.endEvent(eventName)) {
            sender.sendMessage(Component.text()
                    .append(Component.text("Event ", NamedTextColor.GREEN))
                    .append(Component.text(eventName, NamedTextColor.GOLD))
                    .append(Component.text(" ended.", NamedTextColor.GREEN))
                    .build());
        } else {
            sender.sendMessage(Component.text("Event '" + eventName + "' is not active.", NamedTextColor.YELLOW));
        }

        return Command.SINGLE_SUCCESS;
    }

    /**
     * End all events.
     */
    private int endAllEvents(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();

        EventManager eventManager = plugin.getEventManager();
        int count = eventManager.endAllEvents();

        if (count > 0) {
            sender.sendMessage(Component.text()
                    .append(Component.text("Ended ", NamedTextColor.GREEN))
                    .append(Component.text(String.valueOf(count), NamedTextColor.WHITE))
                    .append(Component.text(" event(s).", NamedTextColor.GREEN))
                    .build());
        } else {
            sender.sendMessage(Component.text("No active events to end.", NamedTextColor.YELLOW));
        }

        return Command.SINGLE_SUCCESS;
    }

    /**
     * List active events.
     */
    private int listEvents(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();

        EventManager eventManager = plugin.getEventManager();
        Set<String> events = eventManager.getActiveEventNames();

        if (events.isEmpty()) {
            sender.sendMessage(Component.text("No active events.", NamedTextColor.YELLOW));
        } else {
            sender.sendMessage(Component.text()
                    .append(Component.text("Active events (", NamedTextColor.GOLD))
                    .append(Component.text(String.valueOf(events.size()), NamedTextColor.WHITE))
                    .append(Component.text("):", NamedTextColor.GOLD))
                    .build());

            for (String eventName : events) {
                EventManager.ActiveEvent event = eventManager.getEvent(eventName);
                if (event != null) {
                    long durationSec = event.getDuration() / 1000;
                    sender.sendMessage(Component.text()
                            .append(Component.text("  - ", NamedTextColor.GRAY))
                            .append(Component.text(event.name(), NamedTextColor.GOLD))
                            .append(Component.text(" (started by " + event.startedBy() + ", " + durationSec + "s ago)",
                                    NamedTextColor.GRAY))
                            .build());
                }
            }
        }

        return Command.SINGLE_SUCCESS;
    }

    // ========== Admin Commands ==========

    /**
     * Inspect a player's collection progress (works for offline players).
     */
    private int adminInspect(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        String executor = sender instanceof Player p ? p.getName() : "CONSOLE";

        try {
            PlayerProfileListResolver resolver = ctx.getArgument("target", PlayerProfileListResolver.class);
            java.util.Collection<PlayerProfile> profiles = resolver.resolve(ctx.getSource());

            if (profiles.isEmpty()) {
                sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                return Command.SINGLE_SUCCESS;
            }

            PlayerProfile profile = profiles.iterator().next();
            UUID targetUuid = profile.getId();
            String targetName = profile.getName();

            // Log the admin action
            playerDataManager.logAdminAction("INSPECT", executor, targetUuid, targetName, "viewing progress");

            // Load progress asynchronously (works for offline players)
            playerDataManager.getProgressOffline(targetUuid).thenAccept(progress -> {
                // Send response back on main thread
                Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                    sendInspectResult(sender, targetName != null ? targetName : targetUuid.toString(), progress);
                });
            }).exceptionally(throwable -> {
                Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                    sender.sendMessage(Component.text("Failed to load player data: " + throwable.getMessage(), NamedTextColor.RED));
                });
                return null;
            });

        } catch (Exception e) {
            sender.sendMessage(Component.text("Error: " + e.getMessage(), NamedTextColor.RED));
        }

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Send inspection results to the command sender.
     */
    private void sendInspectResult(org.bukkit.command.CommandSender sender, String targetName, PlayerProgress progress) {
        sender.sendMessage(Component.text()
                .append(Component.text("=== ", NamedTextColor.GOLD))
                .append(Component.text("Progress for " + targetName, NamedTextColor.YELLOW))
                .append(Component.text(" ===", NamedTextColor.GOLD))
                .build());

        if (progress == null) {
            sender.sendMessage(Component.text("No collection data found.", NamedTextColor.GRAY));
            return;
        }

        sender.sendMessage(Component.text()
                .append(Component.text("Total Items: ", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(progress.getTotalCollectiblesCollected()), NamedTextColor.GREEN))
                .build());

        sender.sendMessage(Component.text()
                .append(Component.text("Collections Completed: ", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(progress.getTotalCollectionsCompleted()), NamedTextColor.GREEN))
                .build());

        // Show per-collection progress
        Map<String, Collection> allCollections = collectionManager.getAllCollections();
        for (Collection collection : allCollections.values()) {
            int collected = progress.getCollectedCount(collection.id());
            int total = collection.items().size();
            boolean complete = progress.hasCompleted(collection.id());
            boolean claimed = progress.hasClaimedReward(collection.id());

            int percent = total > 0 ? (collected * 100 / total) : 0;

            NamedTextColor statusColor = complete ? NamedTextColor.GREEN :
                    (collected > 0 ? NamedTextColor.YELLOW : NamedTextColor.GRAY);

            Component status = Component.empty();
            if (complete && claimed) {
                status = Component.text(" [COMPLETE+CLAIMED]", NamedTextColor.AQUA);
            } else if (complete) {
                status = Component.text(" [COMPLETE]", NamedTextColor.GREEN);
            }

            sender.sendMessage(Component.text()
                    .append(Component.text("  " + collection.name() + ": ", NamedTextColor.WHITE))
                    .append(Component.text(collected + "/" + total + " (" + percent + "%)", statusColor))
                    .append(status)
                    .build());
        }
    }

    /**
     * Force complete a collection for a player (works for offline players).
     *
     * @param ctx The command context
     * @param grantRewards Whether to grant rewards (only works for online players)
     */
    private int adminComplete(CommandContext<CommandSourceStack> ctx, boolean grantRewards) {
        var sender = ctx.getSource().getSender();
        String executor = sender instanceof Player p ? p.getName() : "CONSOLE";

        try {
            PlayerProfileListResolver resolver = ctx.getArgument("target", PlayerProfileListResolver.class);
            java.util.Collection<PlayerProfile> profiles = resolver.resolve(ctx.getSource());

            if (profiles.isEmpty()) {
                sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                return Command.SINGLE_SUCCESS;
            }

            PlayerProfile profile = profiles.iterator().next();
            UUID targetUuid = profile.getId();
            String targetName = profile.getName();
            String collectionId = ctx.getArgument("collection", String.class);

            Collection collection = collectionManager.getCollection(collectionId);
            if (collection == null) {
                sender.sendMessage(Component.text("Collection not found: " + collectionId, NamedTextColor.RED));
                return Command.SINGLE_SUCCESS;
            }

            // Get item IDs for the collection
            List<String> itemIds = collection.items().stream()
                    .map(item -> item.id())
                    .toList();

            // Log the admin action
            String details = "collection=" + collectionId + ", rewards=" + grantRewards;
            playerDataManager.logAdminAction("FORCE_COMPLETE", executor, targetUuid, targetName, details);

            // Check if player is online for rewards
            Player onlineTarget = Bukkit.getPlayer(targetUuid);
            boolean canGrantRewards = grantRewards && onlineTarget != null;

            if (grantRewards && onlineTarget == null) {
                sender.sendMessage(Component.text("Note: Player is offline, rewards will not be granted.", NamedTextColor.YELLOW));
            }

            // Complete collection asynchronously
            playerDataManager.completeCollectionOffline(targetUuid, collectionId, itemIds).thenRun(() -> {
                // Grant rewards if applicable (must be on main thread)
                if (canGrantRewards) {
                    Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                        RewardManager rewardManager = plugin.getRewardManager();
                        if (rewardManager != null && !playerDataManager.hasClaimedReward(targetUuid, collectionId)) {
                            rewardManager.giveRewards(onlineTarget, collection);
                            playerDataManager.claimReward(targetUuid, collectionId);
                        }
                    });
                }

                // Send confirmation on main thread
                Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                    String displayName = targetName != null ? targetName : targetUuid.toString();
                    Component msg = Component.text()
                            .append(Component.text("Completed ", NamedTextColor.GREEN))
                            .append(Component.text(collection.name(), NamedTextColor.GOLD))
                            .append(Component.text(" for ", NamedTextColor.GREEN))
                            .append(Component.text(displayName, NamedTextColor.WHITE))
                            .build();
                    sender.sendMessage(msg);

                    if (canGrantRewards) {
                        sender.sendMessage(Component.text("Rewards granted.", NamedTextColor.GREEN));
                    }
                });
            }).exceptionally(throwable -> {
                Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                    sender.sendMessage(Component.text("Failed to complete collection: " + throwable.getMessage(), NamedTextColor.RED));
                });
                return null;
            });

        } catch (Exception e) {
            sender.sendMessage(Component.text("Error: " + e.getMessage(), NamedTextColor.RED));
        }

        return Command.SINGLE_SUCCESS;
    }

    // ========== Export Commands ==========

    /**
     * Export a single player's data to a JSON file.
     */
    private int exportPlayer(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();

        try {
            PlayerProfileListResolver resolver = ctx.getArgument("target", PlayerProfileListResolver.class);
            java.util.Collection<PlayerProfile> profiles = resolver.resolve(ctx.getSource());

            if (profiles.isEmpty()) {
                sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                return Command.SINGLE_SUCCESS;
            }

            PlayerProfile profile = profiles.iterator().next();
            UUID targetUuid = profile.getId();
            String targetName = profile.getName();
            String displayName = targetName != null ? targetName : targetUuid.toString();

            sender.sendMessage(Component.text()
                    .append(Component.text("Exporting data for ", NamedTextColor.YELLOW))
                    .append(Component.text(displayName, NamedTextColor.WHITE))
                    .append(Component.text("...", NamedTextColor.YELLOW))
                    .build());

            dataMigrationManager.exportPlayer(targetUuid, sender).thenAccept(result -> {
                Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                    if (result.success()) {
                        sender.sendMessage(Component.text()
                                .append(Component.text("Export complete! ", NamedTextColor.GREEN))
                                .append(Component.text("File: ", NamedTextColor.GRAY))
                                .append(Component.text(result.filePath().getFileName().toString(), NamedTextColor.WHITE))
                                .build());
                    } else {
                        sender.sendMessage(Component.text()
                                .append(Component.text("Export failed: ", NamedTextColor.RED))
                                .append(Component.text(result.errorMessage(), NamedTextColor.GRAY))
                                .build());
                    }
                });
            });

        } catch (Exception e) {
            sender.sendMessage(Component.text("Error: " + e.getMessage(), NamedTextColor.RED));
        }

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Export all player data to a JSON file.
     */
    private int exportAll(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();

        sender.sendMessage(Component.text("Starting export of all player data...", NamedTextColor.YELLOW));

        dataMigrationManager.exportAllPlayers(sender).thenAccept(result -> {
            Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                if (result.success()) {
                    sender.sendMessage(Component.text()
                            .append(Component.text("Export complete! ", NamedTextColor.GREEN))
                            .append(Component.text(String.valueOf(result.playerCount()), NamedTextColor.WHITE))
                            .append(Component.text(" players exported to ", NamedTextColor.GREEN))
                            .append(Component.text(result.filePath().getFileName().toString(), NamedTextColor.WHITE))
                            .build());
                } else {
                    sender.sendMessage(Component.text()
                            .append(Component.text("Export failed: ", NamedTextColor.RED))
                            .append(Component.text(result.errorMessage(), NamedTextColor.GRAY))
                            .build());
                }
            });
        });

        return Command.SINGLE_SUCCESS;
    }

    // ========== Import Commands ==========

    /**
     * Suggest export files for import tab completion.
     */
    private CompletableFuture<Suggestions> suggestExportFiles(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        for (String filename : dataMigrationManager.suggestExportFiles()) {
            builder.suggest(filename);
        }
        return builder.buildFuture();
    }

    /**
     * Import player data from a JSON file.
     *
     * @param ctx The command context
     * @param dryRun If true, only preview the import without applying changes
     */
    private int importData(CommandContext<CommandSourceStack> ctx, boolean dryRun) {
        var sender = ctx.getSource().getSender();
        String filename = ctx.getArgument("file", String.class);

        // Resolve file path
        Path filePath = dataMigrationManager.getExportsDirectory().resolve(filename);

        if (!Files.exists(filePath)) {
            sender.sendMessage(Component.text()
                    .append(Component.text("File not found: ", NamedTextColor.RED))
                    .append(Component.text(filename, NamedTextColor.WHITE))
                    .build());
            return Command.SINGLE_SUCCESS;
        }

        if (dryRun) {
            sender.sendMessage(Component.text()
                    .append(Component.text("Validating ", NamedTextColor.YELLOW))
                    .append(Component.text(filename, NamedTextColor.WHITE))
                    .append(Component.text("...", NamedTextColor.YELLOW))
                    .build());
        } else {
            sender.sendMessage(Component.text()
                    .append(Component.text("Starting import from ", NamedTextColor.YELLOW))
                    .append(Component.text(filename, NamedTextColor.WHITE))
                    .append(Component.text("...", NamedTextColor.YELLOW))
                    .build());
        }

        dataMigrationManager.importPlayers(filePath, dryRun, sender).thenAccept(result -> {
            Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                if (result.success()) {
                    if (dryRun) {
                        // Dry-run preview
                        int onlineCount = result.affectedOnlinePlayers().size();
                        sender.sendMessage(Component.text()
                                .append(Component.text("Dry-run complete! ", NamedTextColor.GREEN))
                                .append(Component.text("Would import ", NamedTextColor.GRAY))
                                .append(Component.text(String.valueOf(result.playersImported()), NamedTextColor.WHITE))
                                .append(Component.text(" players", NamedTextColor.GRAY))
                                .build());

                        if (onlineCount > 0) {
                            sender.sendMessage(Component.text()
                                    .append(Component.text("  ", NamedTextColor.GRAY))
                                    .append(Component.text(String.valueOf(onlineCount), NamedTextColor.YELLOW))
                                    .append(Component.text(" player(s) are currently online", NamedTextColor.GRAY))
                                    .build());
                        }

                        sender.sendMessage(Component.text()
                                .append(Component.text("Run without --dry-run to apply changes.", NamedTextColor.GRAY))
                                .build());
                    } else {
                        // Actual import complete
                        int onlineRefreshed = result.affectedOnlinePlayers().size();
                        sender.sendMessage(Component.text()
                                .append(Component.text("Import complete! ", NamedTextColor.GREEN))
                                .append(Component.text(String.valueOf(result.playersImported()), NamedTextColor.WHITE))
                                .append(Component.text(" players imported", NamedTextColor.GREEN))
                                .build());

                        if (result.playersSkipped() > 0) {
                            sender.sendMessage(Component.text()
                                    .append(Component.text("  Skipped: ", NamedTextColor.GRAY))
                                    .append(Component.text(String.valueOf(result.playersSkipped()), NamedTextColor.YELLOW))
                                    .append(Component.text(" players (invalid data)", NamedTextColor.GRAY))
                                    .build());
                        }

                        if (onlineRefreshed > 0) {
                            sender.sendMessage(Component.text()
                                    .append(Component.text("  Refreshed: ", NamedTextColor.GRAY))
                                    .append(Component.text(String.valueOf(onlineRefreshed), NamedTextColor.GREEN))
                                    .append(Component.text(" online player cache(s)", NamedTextColor.GRAY))
                                    .build());
                        }
                    }
                } else {
                    // Import failed
                    sender.sendMessage(Component.text()
                            .append(Component.text("Import failed: ", NamedTextColor.RED))
                            .append(Component.text(result.errorMessage(), NamedTextColor.GRAY))
                            .build());
                }
            });
        });

        return Command.SINGLE_SUCCESS;
    }

    // ========== Utility Methods ==========

    /**
     * Format a location for display.
     */
    private String formatLocation(org.bukkit.Location loc) {
        return String.format("%.0f, %.0f, %.0f", loc.getX(), loc.getY(), loc.getZ());
    }
}
