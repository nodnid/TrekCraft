package com.csquared.trekcraft.command;

import com.csquared.trekcraft.TrekCraftConfig;
import com.csquared.trekcraft.content.item.TricorderItem;
import com.csquared.trekcraft.data.TransporterNetworkSavedData;
import com.csquared.trekcraft.data.TransporterNetworkSavedData.RoomRecord;
import com.csquared.trekcraft.data.TransporterNetworkSavedData.SignalType;
import com.csquared.trekcraft.data.TricorderData;
import com.csquared.trekcraft.registry.ModDataComponents;
import com.csquared.trekcraft.registry.ModItems;
import com.csquared.trekcraft.service.ScanService;
import com.csquared.trekcraft.service.TransportService;
import com.csquared.trekcraft.util.ChatUi;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

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

        var pads = data.getPads();
        if (pads.isEmpty()) {
            player.sendSystemMessage(Component.literal("No transporter pads registered.")
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

        var signals = data.getSignals();
        if (signals.isEmpty()) {
            player.sendSystemMessage(Component.literal("No tricorder signals detected.")
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
}
