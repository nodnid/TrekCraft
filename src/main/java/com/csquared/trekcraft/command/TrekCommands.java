package com.csquared.trekcraft.command;

import com.csquared.trekcraft.TrekCraftConfig;
import com.csquared.trekcraft.content.item.TricorderItem;
import com.csquared.trekcraft.data.ContributorRank;
import com.csquared.trekcraft.data.StarfleetSavedData;
import com.csquared.trekcraft.data.TransporterNetworkSavedData;
import com.csquared.trekcraft.data.TransporterNetworkSavedData.ContributorRecord;
import com.csquared.trekcraft.data.TransporterNetworkSavedData.RoomRecord;
import com.csquared.trekcraft.data.TransporterNetworkSavedData.SignalType;
import com.csquared.trekcraft.data.TricorderData;
import com.csquared.trekcraft.mission.Mission;
import com.csquared.trekcraft.mission.objectives.DefendObjective;
import com.csquared.trekcraft.network.OpenContributionScreenPayload;
import com.csquared.trekcraft.network.mission.OpenMissionBoardPayload;
import com.csquared.trekcraft.network.mission.OpenMissionInfoPayload;
import com.csquared.trekcraft.network.mission.OpenMissionLogPayload;
import com.csquared.trekcraft.network.mission.OpenServiceRecordPayload;
import com.csquared.trekcraft.registry.ModDataComponents;
import com.csquared.trekcraft.registry.ModItems;
import com.csquared.trekcraft.service.MissionService;
import com.csquared.trekcraft.service.ScanService;
import com.csquared.trekcraft.service.StarfleetService;
import com.csquared.trekcraft.service.TransportService;
import com.csquared.trekcraft.starfleet.StarfleetRank;
import com.csquared.trekcraft.util.ChatUi;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TrekCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("trek")
                        // Tricorder subcommands
                        .then(Commands.literal("tricorder")
                                .then(Commands.literal("menu")
                                        .executes(TrekCommands::tricorderMenu))
                                .then(Commands.literal("name")
                                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                                .executes(TrekCommands::tricorderName))))

                        // Transport subcommands
                        .then(Commands.literal("transport")
                                .then(Commands.literal("listPads")
                                        .executes(TrekCommands::listPads))
                                .then(Commands.literal("toPad")
                                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                                .executes(TrekCommands::transportToPad)))))
                                .then(Commands.literal("listSignals")
                                        .executes(TrekCommands::listSignals))
                                .then(Commands.literal("toSignal")
                                        .then(Commands.argument("uuid", UuidArgument.uuid())
                                                .executes(TrekCommands::transportToSignal))))

                        // Room subcommands
                        .then(Commands.literal("room")
                                .then(Commands.literal("status")
                                        .executes(TrekCommands::roomStatus))
                                .then(Commands.literal("locate")
                                        .executes(TrekCommands::roomLocate))
                                .then(Commands.literal("reset")
                                        .requires(source -> source.hasPermission(2))
                                        .executes(TrekCommands::roomReset)))

                        // Scan
                        .then(Commands.literal("scan")
                                .executes(TrekCommands::scan))

                        // Contribution commands
                        .then(Commands.literal("contribution")
                                .executes(TrekCommands::openContributionScreen)
                                .then(Commands.literal("status")
                                        .executes(TrekCommands::openContributionScreen))
                                .then(Commands.literal("leaderboard")
                                        .executes(TrekCommands::openContributionScreen)))

                        // Starfleet commands
                        .then(Commands.literal("starfleet")
                                .then(Commands.literal("rank")
                                        .executes(TrekCommands::starfleetRank)
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(TrekCommands::starfleetRankOther)))
                                .then(Commands.literal("setxp")
                                        .requires(source -> source.hasPermission(2))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                                        .executes(TrekCommands::starfleetSetXp))))
                                .then(Commands.literal("addxp")
                                        .requires(source -> source.hasPermission(2))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                        .executes(TrekCommands::starfleetAddXp)))))

                        // Mission commands
                        .then(Commands.literal("mission")
                                .then(Commands.literal("list")
                                        .executes(TrekCommands::missionList))
                                .then(Commands.literal("info")
                                        .then(Commands.argument("uuid", UuidArgument.uuid())
                                                .executes(TrekCommands::missionInfo)))
                                .then(Commands.literal("accept")
                                        .then(Commands.argument("uuid", UuidArgument.uuid())
                                                .executes(TrekCommands::missionAccept)))
                                .then(Commands.literal("abandon")
                                        .then(Commands.argument("uuid", UuidArgument.uuid())
                                                .executes(TrekCommands::missionAbandon)))
                                .then(Commands.literal("delete")
                                        .requires(source -> source.hasPermission(2))
                                        .then(Commands.argument("uuid", UuidArgument.uuid())
                                                .executes(TrekCommands::missionDelete)))
                                .then(Commands.literal("log")
                                        .executes(TrekCommands::missionLog)))

                        // Admin commands
                        .then(Commands.literal("admin")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.literal("admiral")
                                        .then(Commands.literal("grant")
                                                .then(Commands.argument("player", EntityArgument.player())
                                                        .executes(TrekCommands::admiralGrant)))
                                        .then(Commands.literal("revoke")
                                                .then(Commands.argument("player", EntityArgument.player())
                                                        .executes(TrekCommands::admiralRevoke)))
                                        .then(Commands.literal("list")
                                                .executes(TrekCommands::admiralList))))
        );
    }

    // Tricorder commands
    private static int tricorderMenu(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player != null) {
            ChatUi.sendTricorderMenu(player);
        }
        return 1;
    }

    private static int tricorderName(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        String name = StringArgumentType.getString(ctx, "text");
        ItemStack heldItem = player.getMainHandItem();

        if (!heldItem.is(ModItems.TRICORDER.get())) {
            player.sendSystemMessage(Component.literal("You must be holding a tricorder to rename it.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        // Set display name
        heldItem.set(DataComponents.CUSTOM_NAME, Component.literal(name));

        // Update tricorder data label
        TricorderData data = TricorderItem.getTricorderData(heldItem);
        heldItem.set(ModDataComponents.TRICORDER_DATA.get(), data.withLabel(name));

        player.sendSystemMessage(Component.literal("Tricorder renamed to: " + name)
                .withStyle(ChatFormatting.GREEN));
        return 1;
    }

    // Transport commands
    private static int listPads(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        ServerLevel level = (ServerLevel) player.level();
        TransporterNetworkSavedData data = TransporterNetworkSavedData.get(level);
        String playerDimension = level.dimension().location().toString();

        // Only show pads in the player's current dimension
        var pads = data.getPadsInDimension(playerDimension);
        if (pads.isEmpty()) {
            player.sendSystemMessage(Component.literal("No transporter pads registered in this dimension.")
                    .withStyle(ChatFormatting.YELLOW));
            return 1;
        }

        player.sendSystemMessage(Component.literal("=== TRANSPORTER PADS ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        for (var entry : pads.entrySet()) {
            BlockPos pos = entry.getKey();
            String padName = entry.getValue().name();

            player.sendSystemMessage(
                    Component.literal("[" + padName + "]")
                            .withStyle(style -> style
                                    .withColor(ChatFormatting.AQUA)
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                            "/trek transport toPad " + pos.getX() + " " + pos.getY() + " " + pos.getZ()))
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                            Component.literal("Transport to " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()))))
                            .append(Component.literal(" at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ())
                                    .withStyle(ChatFormatting.GRAY))
            );
        }
        return 1;
    }

    private static int transportToPad(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        int x = IntegerArgumentType.getInteger(ctx, "x");
        int y = IntegerArgumentType.getInteger(ctx, "y");
        int z = IntegerArgumentType.getInteger(ctx, "z");
        BlockPos padPos = new BlockPos(x, y, z);

        TransportService.TransportResult result = TransportService.transportToPad(player, padPos);
        ChatUi.sendTrekMessage(player, TransportService.getResultMessage(result),
                result == TransportService.TransportResult.SUCCESS);

        return result == TransportService.TransportResult.SUCCESS ? 1 : 0;
    }

    private static int listSignals(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        ServerLevel level = (ServerLevel) player.level();
        TransporterNetworkSavedData data = TransporterNetworkSavedData.get(level);
        String playerDimension = level.dimension().location().toString();

        // Only show signals in the player's current dimension
        var signals = data.getSignalsInDimension(playerDimension);
        if (signals.isEmpty()) {
            player.sendSystemMessage(Component.literal("No tricorder signals detected in this dimension.")
                    .withStyle(ChatFormatting.YELLOW));
            return 1;
        }

        player.sendSystemMessage(Component.literal("=== TRICORDER SIGNALS ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        for (var entry : signals.entrySet()) {
            var signal = entry.getValue();
            BlockPos pos = signal.lastKnownPos();

            // Show signal type indicator
            String typeIndicator = signal.type() == SignalType.HELD ? "[HELD]" : "[DROPPED]";
            ChatFormatting typeColor = signal.type() == SignalType.HELD ? ChatFormatting.GREEN : ChatFormatting.YELLOW;

            player.sendSystemMessage(
                    Component.literal(typeIndicator)
                            .withStyle(typeColor)
                            .append(Component.literal(" "))
                            .append(Component.literal("[" + signal.displayName() + "]")
                                    .withStyle(style -> style
                                            .withColor(ChatFormatting.AQUA)
                                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                                    "/trek transport toSignal " + signal.tricorderId()))
                                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                                    Component.literal("Transport to signal at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ())))))
                            .append(Component.literal(" near " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ())
                                    .withStyle(ChatFormatting.GRAY))
            );
        }
        return 1;
    }

    private static int transportToSignal(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        UUID tricorderId = UuidArgument.getUuid(ctx, "uuid");

        TransportService.TransportResult result = TransportService.transportToSignal(player, tricorderId);
        ChatUi.sendTrekMessage(player, TransportService.getResultMessage(result),
                result == TransportService.TransportResult.SUCCESS);

        return result == TransportService.TransportResult.SUCCESS ? 1 : 0;
    }

    // Room commands
    private static int roomStatus(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        ServerLevel level = (ServerLevel) player.level();
        TransporterNetworkSavedData data = TransporterNetworkSavedData.get(level);

        player.sendSystemMessage(Component.literal("=== TRANSPORTER NETWORK STATUS ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        var rooms = data.getRooms();
        if (rooms.isEmpty()) {
            player.sendSystemMessage(Component.literal("Status: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("OFFLINE").withStyle(ChatFormatting.RED)));
            player.sendSystemMessage(Component.literal("No Transporter Rooms placed.")
                    .withStyle(ChatFormatting.YELLOW));
        } else {
            player.sendSystemMessage(Component.literal("Status: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("ONLINE").withStyle(ChatFormatting.GREEN)));
            player.sendSystemMessage(Component.literal("Rooms in network: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(String.valueOf(rooms.size()))
                            .withStyle(ChatFormatting.WHITE)));

            int totalFuel = data.getTotalNetworkFuel();
            player.sendSystemMessage(Component.literal("Total network fuel: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(totalFuel + " Latinum Strips")
                            .withStyle(totalFuel > 0 ? ChatFormatting.GREEN : ChatFormatting.RED)));

            // Find nearest room and show range info
            double baseRange = TrekCraftConfig.transportBaseRange;
            double padRange = TrekCraftConfig.transportPadRange;
            var nearestBase = data.getNearestRoom(player.blockPosition(), baseRange);
            var nearestPad = data.getNearestRoom(player.blockPosition(), padRange);

            if (nearestPad.isPresent()) {
                RoomRecord nearest = nearestPad.get();
                double distance = Math.sqrt(player.blockPosition().distSqr(nearest.pos()));
                boolean inBaseRange = nearestBase.isPresent();

                player.sendSystemMessage(Component.literal("Nearest room: ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(String.format("%.0f blocks away", distance))
                                .withStyle(ChatFormatting.WHITE)));

                if (inBaseRange) {
                    player.sendSystemMessage(Component.literal("Range status: ")
                            .withStyle(ChatFormatting.GRAY)
                            .append(Component.literal("In range for all transport")
                                    .withStyle(ChatFormatting.GREEN)));
                } else {
                    player.sendSystemMessage(Component.literal("Range status: ")
                            .withStyle(ChatFormatting.GRAY)
                            .append(Component.literal("Pad transport only (signal transport out of range)")
                                    .withStyle(ChatFormatting.YELLOW)));
                }
            } else {
                player.sendSystemMessage(Component.literal("Range status: ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("OUT OF RANGE")
                                .withStyle(ChatFormatting.RED)));
            }
        }
        return 1;
    }

    private static int roomLocate(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        ServerLevel level = (ServerLevel) player.level();
        TransporterNetworkSavedData data = TransporterNetworkSavedData.get(level);

        var rooms = data.getRooms();
        if (rooms.isEmpty()) {
            player.sendSystemMessage(Component.literal("No Transporter Rooms found.")
                    .withStyle(ChatFormatting.RED));
            return 1;
        }

        double baseRange = TrekCraftConfig.transportBaseRange;
        double padRange = TrekCraftConfig.transportPadRange;

        player.sendSystemMessage(Component.literal("=== TRANSPORTER ROOMS ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        for (RoomRecord room : rooms.values()) {
            BlockPos pos = room.pos();
            double distance = Math.sqrt(player.blockPosition().distSqr(pos));
            boolean inBaseRange = distance <= baseRange;
            boolean inPadRange = distance <= padRange;

            String rangeStatus;
            ChatFormatting rangeColor;
            if (inBaseRange) {
                rangeStatus = "FULL RANGE";
                rangeColor = ChatFormatting.GREEN;
            } else if (inPadRange) {
                rangeStatus = "PAD ONLY";
                rangeColor = ChatFormatting.YELLOW;
            } else {
                rangeStatus = "OUT OF RANGE";
                rangeColor = ChatFormatting.RED;
            }

            player.sendSystemMessage(
                    Component.literal("• ")
                            .withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ())
                                    .withStyle(ChatFormatting.WHITE))
                            .append(Component.literal(" - ")
                                    .withStyle(ChatFormatting.GRAY))
                            .append(Component.literal(String.format("%.0f blocks", distance))
                                    .withStyle(ChatFormatting.AQUA))
                            .append(Component.literal(" - Fuel: ")
                                    .withStyle(ChatFormatting.GRAY))
                            .append(Component.literal(String.valueOf(room.cachedFuel()))
                                    .withStyle(room.cachedFuel() > 0 ? ChatFormatting.GREEN : ChatFormatting.RED))
                            .append(Component.literal(" [")
                                    .withStyle(ChatFormatting.GRAY))
                            .append(Component.literal(rangeStatus)
                                    .withStyle(rangeColor))
                            .append(Component.literal("]")
                                    .withStyle(ChatFormatting.GRAY))
            );
        }
        return 1;
    }

    private static int roomReset(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        ServerLevel level = (ServerLevel) player.level();
        TransporterNetworkSavedData data = TransporterNetworkSavedData.get(level);

        // Clear all rooms
        var roomPositions = data.getRooms().keySet().toArray(new BlockPos[0]);
        for (BlockPos pos : roomPositions) {
            data.unregisterRoom(pos);
        }

        player.sendSystemMessage(Component.literal("Transporter network data reset. All rooms unregistered.")
                .withStyle(ChatFormatting.YELLOW));
        return 1;
    }

    // Scan command
    private static int scan(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        ScanService.ScanResult result = ScanService.performScan(player);
        if (result != ScanService.ScanResult.SUCCESS) {
            player.sendSystemMessage(Component.literal(ScanService.getResultMessage(result))
                    .withStyle(ChatFormatting.RED));
        }
        return result == ScanService.ScanResult.SUCCESS ? 1 : 0;
    }

    // Contribution command - opens GUI screen
    private static int openContributionScreen(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        ServerLevel level = (ServerLevel) player.level();
        TransporterNetworkSavedData data = TransporterNetworkSavedData.get(level);

        // Get player's contribution data
        var contributorOpt = data.getContributor(player.getUUID());

        long totalDeposited = 0;
        long totalWithdrawn = 0;
        int freeTransportsUsed = 0;
        String highestRankName = ContributorRank.CREWMAN.name();

        if (contributorOpt.isPresent()) {
            ContributorRecord record = contributorOpt.get();
            totalDeposited = record.totalDeposited();
            totalWithdrawn = record.totalWithdrawn();
            freeTransportsUsed = record.freeTransportsUsed();
            highestRankName = record.highestRankAchieved().name();
        }

        // Get leaderboard data
        var topContributors = data.getTopContributors(10);
        List<OpenContributionScreenPayload.LeaderboardEntry> leaderboard = new ArrayList<>();
        for (ContributorRecord record : topContributors) {
            if (record.getNetContribution() > 0) {
                leaderboard.add(new OpenContributionScreenPayload.LeaderboardEntry(
                        record.lastKnownName(),
                        record.getNetContribution(),
                        record.highestRankAchieved().name()
                ));
            }
        }

        // Send payload to client
        OpenContributionScreenPayload payload = new OpenContributionScreenPayload(
                totalDeposited,
                totalWithdrawn,
                freeTransportsUsed,
                highestRankName,
                leaderboard
        );

        PacketDistributor.sendToPlayer(player, payload);
        return 1;
    }

    // ===== Starfleet Commands =====

    private static int starfleetRank(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        ServerLevel level = player.serverLevel();
        StarfleetSavedData data = StarfleetSavedData.get(level);

        StarfleetRank rank = StarfleetService.getPlayerRank(player);
        long totalXp = StarfleetService.getPlayerXp(player);
        long xpToNextRank = StarfleetRank.getXpToNextRank(totalXp);

        // Get mission counts
        int activeMissionCount = data.getPlayerActiveMissions(player.getUUID()).size();
        int completedMissionCount = data.getPlayer(player.getUUID())
                .map(rec -> rec.completedMissions().size())
                .orElse(0);

        // Stats tracking - placeholders for now (can be expanded later)
        int totalKills = 0;
        int totalScans = 0;
        int biomesExplored = 0;

        OpenServiceRecordPayload payload = new OpenServiceRecordPayload(
                rank.getTitle(),
                totalXp,
                xpToNextRank,
                activeMissionCount,
                completedMissionCount,
                totalKills,
                totalScans,
                biomesExplored
        );

        PacketDistributor.sendToPlayer(player, payload);
        return 1;
    }

    private static int starfleetRankOther(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer requestor = ctx.getSource().getPlayer();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");

        if (requestor == null) return 0;

        StarfleetRank rank = StarfleetService.getPlayerRank(target);
        long xp = StarfleetService.getPlayerXp(target);

        requestor.sendSystemMessage(Component.literal("=== STARFLEET RECORD: ")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal(target.getName().getString())
                        .withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" ===")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));

        requestor.sendSystemMessage(Component.literal("Rank: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(rank.getTitle())
                        .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                .append(Component.literal(" (" + xp + " XP)")
                        .withStyle(ChatFormatting.GRAY)));

        return 1;
    }

    private static int starfleetSetXp(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer admin = ctx.getSource().getPlayer();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        int amount = IntegerArgumentType.getInteger(ctx, "amount");

        ServerLevel level = target.serverLevel();
        StarfleetService.setXp(level, target.getUUID(), target.getName().getString(), amount);

        if (admin != null) {
            admin.sendSystemMessage(Component.literal("Set ")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(target.getName().getString())
                            .withStyle(ChatFormatting.AQUA))
                    .append(Component.literal("'s XP to " + amount)
                            .withStyle(ChatFormatting.YELLOW)));
        }

        target.sendSystemMessage(Component.literal("Your Starfleet XP has been set to " + amount)
                .withStyle(ChatFormatting.YELLOW));

        return 1;
    }

    private static int starfleetAddXp(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer admin = ctx.getSource().getPlayer();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        int amount = IntegerArgumentType.getInteger(ctx, "amount");

        StarfleetService.awardXp(target, amount);

        if (admin != null) {
            admin.sendSystemMessage(Component.literal("Added ")
                    .withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(amount + " XP")
                            .withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" to ")
                            .withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(target.getName().getString())
                            .withStyle(ChatFormatting.AQUA)));
        }

        return 1;
    }

    // ===== Mission Commands =====

    private static int missionList(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        ServerLevel level = player.serverLevel();
        List<Mission> missions = MissionService.getMissionBoard(level);
        StarfleetRank playerRank = StarfleetService.getPlayerRank(player);

        // Build payload for GUI
        List<OpenMissionBoardPayload.MissionSummary> summaries = new ArrayList<>();
        for (Mission mission : missions) {
            boolean canAccept = mission.canAccept(playerRank);
            boolean isParticipant = mission.hasParticipant(player.getUUID());

            summaries.add(new OpenMissionBoardPayload.MissionSummary(
                    mission.missionId(),
                    mission.title(),
                    mission.objective().getDescription(),
                    mission.xpReward(),
                    mission.minRank().getTitle(),
                    mission.getStatusString(),
                    mission.progress().getProgressPercent(),
                    mission.participants().size(),
                    isParticipant,
                    canAccept
            ));
        }

        // Send payload to update GUI
        PacketDistributor.sendToPlayer(player, new OpenMissionBoardPayload(summaries, playerRank.getTitle()));

        return 1;
    }

    private static int missionInfo(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        UUID missionId = UuidArgument.getUuid(ctx, "uuid");
        ServerLevel level = player.serverLevel();

        var missionOpt = MissionService.getMission(level, missionId);
        if (missionOpt.isEmpty()) {
            player.sendSystemMessage(Component.literal("Mission not found.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        Mission mission = missionOpt.get();
        StarfleetRank playerRank = StarfleetService.getPlayerRank(player);
        boolean isParticipant = mission.hasParticipant(player.getUUID());
        boolean canAccept = mission.canAccept(playerRank);

        // Generate progress text
        String progressText = generateProgressText(mission);

        // Build and send payload for GUI
        OpenMissionInfoPayload payload = new OpenMissionInfoPayload(
                mission.missionId(),
                mission.title(),
                mission.description(),
                mission.objective().getDescription(),
                mission.xpReward(),
                mission.minRank().getTitle(),
                mission.getStatusString(),
                mission.progress().getProgressPercent(),
                mission.progress().totalProgress(),
                mission.progress().targetProgress(),
                progressText,
                mission.participants().size(),
                isParticipant,
                canAccept
        );

        PacketDistributor.sendToPlayer(player, payload);

        return 1;
    }

    private static int missionAccept(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        UUID missionId = UuidArgument.getUuid(ctx, "uuid");
        MissionService.MissionResult result = MissionService.acceptMission(player, missionId);

        if (result != MissionService.MissionResult.SUCCESS) {
            player.sendSystemMessage(Component.literal(MissionService.getResultMessage(result))
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        return 1;
    }

    private static int missionAbandon(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        UUID missionId = UuidArgument.getUuid(ctx, "uuid");
        MissionService.MissionResult result = MissionService.abandonMission(player, missionId);

        if (result != MissionService.MissionResult.SUCCESS) {
            player.sendSystemMessage(Component.literal(MissionService.getResultMessage(result))
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        return 1;
    }

    private static int missionDelete(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        UUID missionId = UuidArgument.getUuid(ctx, "uuid");
        MissionService.MissionResult result = MissionService.deleteMission(player, missionId);

        if (result != MissionService.MissionResult.SUCCESS) {
            player.sendSystemMessage(Component.literal(MissionService.getResultMessage(result))
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        return 1;
    }

    private static int missionLog(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        List<Mission> missions = MissionService.getPlayerMissionLog(player);

        // Build payload for GUI
        List<OpenMissionLogPayload.ActiveMissionEntry> entries = new ArrayList<>();
        for (Mission mission : missions) {
            int contribution = mission.progress().getPlayerContribution(player.getUUID());

            // Generate progress text based on objective type
            String progressText = generateProgressText(mission);

            entries.add(new OpenMissionLogPayload.ActiveMissionEntry(
                    mission.missionId(),
                    mission.title(),
                    mission.objective().getDescription(),
                    mission.xpReward(),
                    mission.progress().getProgressPercent(),
                    mission.progress().totalProgress(),
                    mission.progress().targetProgress(),
                    contribution,
                    progressText
            ));
        }

        // Send payload to update GUI
        PacketDistributor.sendToPlayer(player, new OpenMissionLogPayload(entries));

        return 1;
    }

    /**
     * Generate human-readable progress text based on mission objective type.
     */
    private static String generateProgressText(Mission mission) {
        var progress = mission.progress();
        var objective = mission.objective();

        return switch (objective.getType()) {
            case KILL -> progress.totalProgress() + "/" + progress.targetProgress();
            case SCAN -> progress.totalProgress() + "/" + progress.targetProgress();
            case GATHER -> progress.totalProgress() + "/" + progress.targetProgress();
            case EXPLORE -> progress.totalProgress() + "/" + progress.targetProgress();
            case CONTRIBUTION -> progress.totalProgress() + "/" + progress.targetProgress();
            case DEFEND -> {
                // For defend missions, show elapsed/total seconds
                if (objective instanceof DefendObjective defendObj) {
                    long totalTicks = defendObj.durationTicks();
                    long remaining = progress.defendTicksRemaining();
                    int totalSeconds = (int)(totalTicks / 20);
                    int secondsElapsed = totalSeconds - (int)(remaining / 20);
                    yield secondsElapsed + "/" + totalSeconds + "s";
                }
                yield progress.totalProgress() + "/" + progress.targetProgress();
            }
            case BUILD -> progress.totalProgress() + "/" + progress.targetProgress();
            case COMPOSITE -> progress.totalProgress() + "/" + progress.targetProgress();
        };
    }

    // ===== Admin Commands =====

    private static int admiralGrant(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer admin = ctx.getSource().getPlayer();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");

        ServerLevel level = target.serverLevel();
        StarfleetService.grantAdmiral(level, target.getUUID());

        if (admin != null) {
            admin.sendSystemMessage(Component.literal("Granted Admiral status to ")
                    .withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(target.getName().getString())
                            .withStyle(ChatFormatting.AQUA)));
        }

        return 1;
    }

    private static int admiralRevoke(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer admin = ctx.getSource().getPlayer();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");

        ServerLevel level = target.serverLevel();
        StarfleetService.revokeAdmiral(level, target.getUUID());

        if (admin != null) {
            admin.sendSystemMessage(Component.literal("Revoked Admiral status from ")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(target.getName().getString())
                            .withStyle(ChatFormatting.AQUA)));
        }

        return 1;
    }

    private static int admiralList(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        ServerLevel level = player.serverLevel();
        var admirals = StarfleetService.getManualAdmirals(level);

        player.sendSystemMessage(Component.literal("=== ADMIRALS ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        if (admirals.isEmpty()) {
            player.sendSystemMessage(Component.literal("No manually assigned admirals.")
                    .withStyle(ChatFormatting.GRAY));
            player.sendSystemMessage(Component.literal("Note: Server operators automatically have Admiral status.")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return 1;
        }

        for (UUID admiralId : admirals) {
            // Try to get player name
            ServerPlayer admiralPlayer = level.getServer().getPlayerList().getPlayer(admiralId);
            String name = admiralPlayer != null ? admiralPlayer.getName().getString() : admiralId.toString().substring(0, 8) + "...";

            player.sendSystemMessage(Component.literal("• ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(name)
                            .withStyle(ChatFormatting.AQUA)));
        }

        return 1;
    }
}
