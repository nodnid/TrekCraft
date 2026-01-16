package com.csquared.trekcraft.command;

import com.csquared.trekcraft.content.item.TricorderItem;
import com.csquared.trekcraft.data.TransporterNetworkSavedData;
import com.csquared.trekcraft.data.TricorderData;
import com.csquared.trekcraft.registry.ModDataComponents;
import com.csquared.trekcraft.registry.ModItems;
import com.csquared.trekcraft.service.RequestService;
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
                                .then(Commands.literal("listPlayers")
                                        .executes(TrekCommands::listPlayers))
                                .then(Commands.literal("toPlayer")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(TrekCommands::transportToPlayer)))
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

                        // Request subcommands
                        .then(Commands.literal("request")
                                .then(Commands.literal("send")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(TrekCommands::requestSend)))
                                .then(Commands.literal("accept")
                                        .executes(TrekCommands::requestAccept))
                                .then(Commands.literal("decline")
                                        .executes(TrekCommands::requestDecline))
                                .then(Commands.literal("hold")
                                        .executes(TrekCommands::requestHold))
                                .then(Commands.literal("list")
                                        .executes(TrekCommands::requestList)))

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

    private static int listPlayers(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        var playerList = player.getServer().getPlayerList().getPlayers();

        player.sendSystemMessage(Component.literal("=== PLAYERS WITH TRICORDERS ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        boolean foundAny = false;
        for (ServerPlayer otherPlayer : playerList) {
            if (otherPlayer == player) continue;
            if (!TransportService.playerHasTricorder(otherPlayer)) continue;

            foundAny = true;
            String playerName = otherPlayer.getName().getString();
            player.sendSystemMessage(
                    Component.literal("[" + playerName + "]")
                            .withStyle(style -> style
                                    .withColor(ChatFormatting.AQUA)
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                            "/trek transport toPlayer " + playerName))
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                            Component.literal("Transport to " + playerName))))
            );
        }

        if (!foundAny) {
            player.sendSystemMessage(Component.literal("No other players with tricorders detected.")
                    .withStyle(ChatFormatting.YELLOW));
        }
        return 1;
    }

    private static int transportToPlayer(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");

        TransportService.TransportResult result = TransportService.transportToPlayer(player, target);
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
            player.sendSystemMessage(Component.literal("No dropped tricorder signals detected.")
                    .withStyle(ChatFormatting.YELLOW));
            return 1;
        }

        player.sendSystemMessage(Component.literal("=== TRICORDER SIGNALS ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        for (var entry : signals.entrySet()) {
            var signal = entry.getValue();
            BlockPos pos = signal.lastKnownPos();

            player.sendSystemMessage(
                    Component.literal("[" + signal.displayName() + "]")
                            .withStyle(style -> style
                                    .withColor(ChatFormatting.AQUA)
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                            "/trek transport toSignal " + signal.tricorderId()))
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                            Component.literal("Transport to signal at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()))))
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

        player.sendSystemMessage(Component.literal("=== TRANSPORTER ROOM STATUS ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        if (data.hasTransporterRoom()) {
            BlockPos pos = data.getTransporterRoomPos();
            player.sendSystemMessage(Component.literal("Status: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("ONLINE").withStyle(ChatFormatting.GREEN)));
            player.sendSystemMessage(Component.literal("Location: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ())
                            .withStyle(ChatFormatting.WHITE)));
            player.sendSystemMessage(Component.literal("Fuel: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(data.getCachedFuel() + " Latinum Strips")
                            .withStyle(data.getCachedFuel() > 0 ? ChatFormatting.GREEN : ChatFormatting.RED)));
        } else {
            player.sendSystemMessage(Component.literal("Status: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("OFFLINE").withStyle(ChatFormatting.RED)));
            player.sendSystemMessage(Component.literal("No Transporter Room placed.")
                    .withStyle(ChatFormatting.YELLOW));
        }
        return 1;
    }

    private static int roomLocate(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        ServerLevel level = (ServerLevel) player.level();
        TransporterNetworkSavedData data = TransporterNetworkSavedData.get(level);

        if (data.hasTransporterRoom()) {
            BlockPos pos = data.getTransporterRoomPos();
            player.sendSystemMessage(Component.literal("Transporter Room located at: " +
                    pos.getX() + ", " + pos.getY() + ", " + pos.getZ())
                    .withStyle(ChatFormatting.GREEN));
        } else {
            player.sendSystemMessage(Component.literal("No Transporter Room found.")
                    .withStyle(ChatFormatting.RED));
        }
        return 1;
    }

    private static int roomReset(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        ServerLevel level = (ServerLevel) player.level();
        TransporterNetworkSavedData data = TransporterNetworkSavedData.get(level);

        data.clearTransporterRoom();

        player.sendSystemMessage(Component.literal("Transporter Room data reset. System offline.")
                .withStyle(ChatFormatting.YELLOW));
        return 1;
    }

    // Request commands
    private static int requestSend(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");

        RequestService.RequestResult result = RequestService.sendRequest(player, target);
        player.sendSystemMessage(Component.literal(RequestService.getResultMessage(result))
                .withStyle(result == RequestService.RequestResult.SUCCESS ? ChatFormatting.GREEN : ChatFormatting.RED));

        return result == RequestService.RequestResult.SUCCESS ? 1 : 0;
    }

    private static int requestAccept(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        RequestService.RequestResult result = RequestService.acceptRequest(player);
        if (result != RequestService.RequestResult.SUCCESS) {
            player.sendSystemMessage(Component.literal(RequestService.getResultMessage(result))
                    .withStyle(ChatFormatting.RED));
        }
        return result == RequestService.RequestResult.SUCCESS ? 1 : 0;
    }

    private static int requestDecline(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        RequestService.RequestResult result = RequestService.declineRequest(player);
        player.sendSystemMessage(Component.literal(
                result == RequestService.RequestResult.SUCCESS ? "Request declined." : RequestService.getResultMessage(result))
                .withStyle(result == RequestService.RequestResult.SUCCESS ? ChatFormatting.YELLOW : ChatFormatting.RED));

        return result == RequestService.RequestResult.SUCCESS ? 1 : 0;
    }

    private static int requestHold(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        RequestService.RequestResult result = RequestService.holdRequest(player);
        player.sendSystemMessage(Component.literal(
                result == RequestService.RequestResult.SUCCESS ? "Request held. You have 5 minutes to accept." : RequestService.getResultMessage(result))
                .withStyle(result == RequestService.RequestResult.SUCCESS ? ChatFormatting.YELLOW : ChatFormatting.RED));

        return result == RequestService.RequestResult.SUCCESS ? 1 : 0;
    }

    private static int requestList(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        ServerLevel level = (ServerLevel) player.level();
        TransporterNetworkSavedData data = TransporterNetworkSavedData.get(level);

        var requestOpt = data.getRequestForRecipient(player.getUUID());
        if (requestOpt.isEmpty()) {
            player.sendSystemMessage(Component.literal("No pending requests.")
                    .withStyle(ChatFormatting.YELLOW));
            return 1;
        }

        var request = requestOpt.get();
        ServerPlayer requester = level.getServer().getPlayerList().getPlayer(request.requester());
        String requesterName = requester != null ? requester.getName().getString() : "Unknown";

        player.sendSystemMessage(Component.literal("Pending request from: " + requesterName)
                .withStyle(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.literal("Status: " + request.status().name())
                .withStyle(ChatFormatting.GRAY));

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
