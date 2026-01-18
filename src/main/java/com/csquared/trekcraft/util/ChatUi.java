package com.csquared.trekcraft.util;

import com.csquared.trekcraft.data.TransporterNetworkSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class ChatUi {

    public static void sendTricorderMenu(ServerPlayer player) {
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal("=== TRICORDER ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        // Transport options
        player.sendSystemMessage(createMenuButton("[Transport -> Pad]", "/trek transport listPads",
                "View available transporter pads", ChatFormatting.AQUA));

        player.sendSystemMessage(createMenuButton("[Transport -> Signal]", "/trek transport listSignals",
                "View tricorder signals (held and dropped)", ChatFormatting.AQUA));

        // Scan
        player.sendSystemMessage(createMenuButton("[Scan]", "/trek scan",
                "Scan for anomalies (costs 1 Latinum Slip)", ChatFormatting.GREEN));

        // Fuel status
        if (player.level() instanceof ServerLevel serverLevel) {
            TransporterNetworkSavedData data = TransporterNetworkSavedData.get(serverLevel);
            if (data.hasAnyRoom()) {
                int totalFuel = data.getTotalNetworkFuel();
                player.sendSystemMessage(Component.literal("Fuel: ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(totalFuel + " strips")
                                .withStyle(totalFuel > 0 ? ChatFormatting.GREEN : ChatFormatting.RED)));
            } else {
                player.sendSystemMessage(Component.literal("Fuel: ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("No Transporter Room")
                                .withStyle(ChatFormatting.RED)));
            }
        }

        player.sendSystemMessage(Component.literal("=================").withStyle(ChatFormatting.GOLD));
    }

    public static MutableComponent createMenuButton(String text, String command, String hoverText, ChatFormatting color) {
        return Component.literal(text)
                .withStyle(style -> style
                        .withColor(color)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal(hoverText).withStyle(ChatFormatting.GRAY)))
                );
    }

    public static void sendTrekMessage(ServerPlayer player, String message, boolean success) {
        ChatFormatting color = success ? ChatFormatting.GREEN : ChatFormatting.RED;
        String prefix = success ? "[TRANSPORT] " : "[TRANSPORT FAILED] ";
        player.sendSystemMessage(Component.literal(prefix + message).withStyle(color));
    }

    public static void sendInfo(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal("[TRICORDER] " + message).withStyle(ChatFormatting.AQUA));
    }

    public static void sendScanResult(ServerPlayer player, String quadrant, String findings) {
        player.sendSystemMessage(Component.literal("  " + quadrant + ": ")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(findings).withStyle(ChatFormatting.WHITE)));
    }
}
