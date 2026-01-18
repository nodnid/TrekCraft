package com.csquared.trekcraft.content.item;

import com.csquared.trekcraft.data.TransporterNetworkSavedData;
import com.csquared.trekcraft.data.TransporterNetworkSavedData.SignalType;
import com.csquared.trekcraft.data.TricorderData;
import com.csquared.trekcraft.network.OpenTricorderScreenPayload;
import com.csquared.trekcraft.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class TricorderItem extends Item {

    public TricorderItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // Ensure tricorder has an ID
        ensureTricorderData(stack);

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            TricorderData tricorderData = stack.get(ModDataComponents.TRICORDER_DATA.get());

            // Check if tricorder needs naming
            if (tricorderData != null && tricorderData.label().isEmpty()) {
                // Show naming prompt
                sendNamingPrompt(serverPlayer, tricorderData);
                return InteractionResultHolder.success(stack);
            }

            ServerLevel serverLevel = serverPlayer.serverLevel();
            TransporterNetworkSavedData data = TransporterNetworkSavedData.get(serverLevel);
            int fuel = data.getTotalNetworkFuel();
            boolean hasRoom = data.hasAnyRoom();

            // Gather pad data
            List<OpenTricorderScreenPayload.PadEntry> pads = new ArrayList<>();
            for (var entry : data.getPads().entrySet()) {
                pads.add(new OpenTricorderScreenPayload.PadEntry(entry.getValue().name(), entry.getKey()));
            }

            // Gather signal data (both held and dropped tricorders)
            List<OpenTricorderScreenPayload.SignalEntry> signals = new ArrayList<>();
            for (var entry : data.getSignals().entrySet()) {
                var signal = entry.getValue();
                // Don't show the player's own tricorder in the list
                if (tricorderData != null && signal.tricorderId().equals(tricorderData.tricorderId())) {
                    continue;
                }
                signals.add(new OpenTricorderScreenPayload.SignalEntry(
                        signal.displayName(),
                        entry.getKey(),
                        signal.type()
                ));
            }

            // Send packet to open tricorder screen on client
            PacketDistributor.sendToPlayer(serverPlayer, new OpenTricorderScreenPayload(fuel, hasRoom, pads, signals));
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private void sendNamingPrompt(ServerPlayer player, TricorderData data) {
        String defaultName = "Tricorder-" + data.tricorderId().toString().substring(0, 4).toUpperCase();

        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal("=== TRICORDER INITIALIZATION ===")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal("This tricorder needs a name before use.")
                .withStyle(ChatFormatting.WHITE));
        player.sendSystemMessage(Component.literal("To name it, hold it and use: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal("/trek tricorder name <your name>")
                        .withStyle(ChatFormatting.AQUA)));
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(
                Component.literal("[Use Default: " + defaultName + "]")
                        .withStyle(style -> style
                                .withColor(ChatFormatting.GREEN)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                        "/trek tricorder name " + defaultName))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        Component.literal("Click to use default name"))))
        );
        player.sendSystemMessage(Component.literal("================================")
                .withStyle(ChatFormatting.GOLD));
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        // Ensure tricorder always has data component
        if (!level.isClientSide) {
            ensureTricorderData(stack);
        }
    }

    public static void ensureTricorderData(ItemStack stack) {
        if (!stack.has(ModDataComponents.TRICORDER_DATA.get())) {
            stack.set(ModDataComponents.TRICORDER_DATA.get(), TricorderData.create());
        }
    }

    public static TricorderData getTricorderData(ItemStack stack) {
        ensureTricorderData(stack);
        return stack.get(ModDataComponents.TRICORDER_DATA.get());
    }
}
