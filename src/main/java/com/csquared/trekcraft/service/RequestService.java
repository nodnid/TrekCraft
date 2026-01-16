package com.csquared.trekcraft.service;

import com.csquared.trekcraft.data.TransporterNetworkSavedData;
import com.csquared.trekcraft.data.TransporterNetworkSavedData.RequestRecord;
import com.csquared.trekcraft.data.TransporterNetworkSavedData.RequestStatus;
import com.csquared.trekcraft.util.ChatUi;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

public class RequestService {
    private static final long REQUEST_TIMEOUT_TICKS = 200; // 10 seconds at 20 TPS
    private static final long HELD_TIMEOUT_TICKS = 6000; // 5 minutes when held

    public enum RequestResult {
        SUCCESS,
        ALREADY_HAS_REQUEST,
        PLAYER_NOT_FOUND,
        SELF_REQUEST,
        NO_REQUEST,
        REQUEST_EXPIRED
    }

    public static RequestResult sendRequest(ServerPlayer requester, ServerPlayer recipient) {
        if (requester.equals(recipient)) {
            return RequestResult.SELF_REQUEST;
        }

        ServerLevel level = (ServerLevel) requester.level();
        TransporterNetworkSavedData data = TransporterNetworkSavedData.get(level);

        // Check if recipient already has a pending request
        Optional<RequestRecord> existingRequest = data.getRequestForRecipient(recipient.getUUID());
        if (existingRequest.isPresent()) {
            // Check if it's expired
            long currentTime = level.getGameTime();
            if (currentTime < existingRequest.get().expiresGameTime()) {
                return RequestResult.ALREADY_HAS_REQUEST;
            }
            // Expired, remove it
            data.removeRequest(recipient.getUUID());
        }

        // Create new request
        long currentTime = level.getGameTime();
        RequestRecord request = new RequestRecord(
                requester.getUUID(),
                recipient.getUUID(),
                requester.blockPosition(),
                currentTime,
                currentTime + REQUEST_TIMEOUT_TICKS,
                RequestStatus.PENDING
        );

        data.addRequest(request);

        // Notify recipient
        sendRequestNotification(recipient, requester.getName().getString());

        return RequestResult.SUCCESS;
    }

    public static RequestResult acceptRequest(ServerPlayer recipient) {
        ServerLevel level = (ServerLevel) recipient.level();
        TransporterNetworkSavedData data = TransporterNetworkSavedData.get(level);

        Optional<RequestRecord> requestOpt = data.getRequestForRecipient(recipient.getUUID());
        if (requestOpt.isEmpty()) {
            return RequestResult.NO_REQUEST;
        }

        RequestRecord request = requestOpt.get();

        // Check if expired
        if (level.getGameTime() > request.expiresGameTime()) {
            data.removeRequest(recipient.getUUID());
            return RequestResult.REQUEST_EXPIRED;
        }

        // Get requester
        ServerPlayer requester = level.getServer().getPlayerList().getPlayer(request.requester());
        if (requester == null) {
            data.removeRequest(recipient.getUUID());
            return RequestResult.PLAYER_NOT_FOUND;
        }

        // Transport recipient to requester
        TransportService.TransportResult result = TransportService.transportToPlayer(recipient, requester);

        // Remove request regardless of result
        data.removeRequest(recipient.getUUID());

        if (result == TransportService.TransportResult.SUCCESS) {
            ChatUi.sendTrekMessage(recipient, "Transport complete. Welcome aboard.", true);
            ChatUi.sendTrekMessage(requester, recipient.getName().getString() + " has arrived.", true);
            return RequestResult.SUCCESS;
        } else {
            ChatUi.sendTrekMessage(recipient, TransportService.getResultMessage(result), false);
            return RequestResult.SUCCESS; // Request was processed, even if transport failed
        }
    }

    public static RequestResult declineRequest(ServerPlayer recipient) {
        ServerLevel level = (ServerLevel) recipient.level();
        TransporterNetworkSavedData data = TransporterNetworkSavedData.get(level);

        Optional<RequestRecord> requestOpt = data.getRequestForRecipient(recipient.getUUID());
        if (requestOpt.isEmpty()) {
            return RequestResult.NO_REQUEST;
        }

        RequestRecord request = requestOpt.get();
        data.removeRequest(recipient.getUUID());

        // Notify requester
        ServerPlayer requester = level.getServer().getPlayerList().getPlayer(request.requester());
        if (requester != null) {
            ChatUi.sendTrekMessage(requester, recipient.getName().getString() + " declined the transport request.", false);
        }

        return RequestResult.SUCCESS;
    }

    public static RequestResult holdRequest(ServerPlayer recipient) {
        ServerLevel level = (ServerLevel) recipient.level();
        TransporterNetworkSavedData data = TransporterNetworkSavedData.get(level);

        Optional<RequestRecord> requestOpt = data.getRequestForRecipient(recipient.getUUID());
        if (requestOpt.isEmpty()) {
            return RequestResult.NO_REQUEST;
        }

        RequestRecord request = requestOpt.get();

        // Check if already expired
        if (level.getGameTime() > request.expiresGameTime()) {
            data.removeRequest(recipient.getUUID());
            return RequestResult.REQUEST_EXPIRED;
        }

        // Update to HELD status with extended timeout
        RequestRecord heldRequest = request
                .withStatus(RequestStatus.HELD)
                .extendExpiration(level.getGameTime() + HELD_TIMEOUT_TICKS);

        data.addRequest(heldRequest);

        return RequestResult.SUCCESS;
    }

    public static void tickRequests(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;

        TransporterNetworkSavedData data = TransporterNetworkSavedData.get(overworld);
        long currentTime = overworld.getGameTime();

        // Remove expired requests
        var requests = data.getRequests();
        for (var entry : requests.entrySet()) {
            if (currentTime > entry.getValue().expiresGameTime()) {
                data.removeRequest(entry.getKey());

                // Notify recipient that request expired
                ServerPlayer recipient = server.getPlayerList().getPlayer(entry.getKey());
                if (recipient != null) {
                    ChatUi.sendInfo(recipient, "Away team request expired.");
                }
            }
        }
    }

    private static void sendRequestNotification(ServerPlayer recipient, String requesterName) {
        recipient.sendSystemMessage(Component.literal(""));
        recipient.sendSystemMessage(Component.literal("=== AWAY TEAM REQUEST ===")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        recipient.sendSystemMessage(Component.literal(requesterName + " is requesting your transport.")
                .withStyle(ChatFormatting.WHITE));
        recipient.sendSystemMessage(Component.literal(""));

        // Accept button
        recipient.sendSystemMessage(
                Component.literal("[Accept]")
                        .withStyle(style -> style
                                .withColor(ChatFormatting.GREEN)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trek request accept"))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        Component.literal("Transport to " + requesterName))))
                        .append(Component.literal(" ").withStyle(ChatFormatting.RESET))
                        .append(Component.literal("[Decline]")
                                .withStyle(style -> style
                                        .withColor(ChatFormatting.RED)
                                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trek request decline"))
                                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                                Component.literal("Decline the request")))))
                        .append(Component.literal(" ").withStyle(ChatFormatting.RESET))
                        .append(Component.literal("[Hold]")
                                .withStyle(style -> style
                                        .withColor(ChatFormatting.YELLOW)
                                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trek request hold"))
                                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                                Component.literal("Hold the request for later")))))
        );

        recipient.sendSystemMessage(Component.literal("Request expires in 10 seconds.")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        recipient.sendSystemMessage(Component.literal("=========================")
                .withStyle(ChatFormatting.GOLD));
    }

    public static String getResultMessage(RequestResult result) {
        return switch (result) {
            case SUCCESS -> "Request sent.";
            case ALREADY_HAS_REQUEST -> "That player already has a pending request.";
            case PLAYER_NOT_FOUND -> "Player not found.";
            case SELF_REQUEST -> "You cannot request transport to yourself.";
            case NO_REQUEST -> "No pending request found.";
            case REQUEST_EXPIRED -> "The request has expired.";
        };
    }
}
