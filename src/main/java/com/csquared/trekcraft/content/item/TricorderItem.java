package com.csquared.trekcraft.content.item;

import com.csquared.trekcraft.data.TransporterNetworkSavedData;
import com.csquared.trekcraft.data.TransporterNetworkSavedData.SignalType;
import com.csquared.trekcraft.data.TricorderData;
import com.csquared.trekcraft.network.OpenNamingScreenPayload;
import com.csquared.trekcraft.network.OpenTricorderScreenPayload;
import com.csquared.trekcraft.registry.ModDataComponents;
import com.csquared.trekcraft.registry.ModItems;
import com.csquared.trekcraft.service.WormholeService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class TricorderItem extends Item {

    public TricorderItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();

        if (player == null) {
            return InteractionResult.PASS;
        }

        // Ensure tricorder has data
        ensureTricorderData(stack);
        TricorderData tricorderData = stack.get(ModDataComponents.TRICORDER_DATA.get());

        // Check if tricorder is named "Cleo" (case-insensitive) AND clicking cobblestone
        if (tricorderData != null && tricorderData.label().filter(l -> "Cleo".equalsIgnoreCase(l)).isPresent()) {
            // Only process on server side, and only for cobblestone
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                WormholeService.ActivationAttempt attempt = WormholeService.tryActivate(player, context.getClickedPos());

                switch (attempt.result()) {
                    case SUCCESS -> {
                        String defaultName = "Wormhole-" + attempt.portalId().toString().substring(0, 4).toUpperCase();
                        PacketDistributor.sendToPlayer(serverPlayer,
                                OpenNamingScreenPayload.forWormhole(attempt.portalId(), defaultName));
                        return InteractionResult.SUCCESS;
                    }
                    case INVALID_FRAME -> {
                        player.displayClientMessage(
                                Component.literal("Invalid wormhole frame. Build a rectangular cobblestone frame with air inside."), true);
                        return InteractionResult.FAIL;
                    }
                    case PORTAL_EXISTS_HERE -> {
                        player.displayClientMessage(
                                Component.literal("A wormhole portal already exists here."), true);
                        return InteractionResult.FAIL;
                    }
                    case NOT_COBBLESTONE -> {
                        // Fall through to normal use - return PASS
                    }
                }
            }
        }

        // Not a Cleo tricorder, or not clicking cobblestone - fall through to normal use
        return InteractionResult.PASS;
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
                // Open naming screen
                String defaultName = "Tricorder-" + tricorderData.tricorderId().toString().substring(0, 4).toUpperCase();
                PacketDistributor.sendToPlayer(serverPlayer,
                        OpenNamingScreenPayload.forTricorder(tricorderData.tricorderId(), defaultName));
                return InteractionResultHolder.success(stack);
            }

            ServerLevel serverLevel = serverPlayer.serverLevel();
            TransporterNetworkSavedData data = TransporterNetworkSavedData.get(serverLevel);
            int fuel = data.getTotalNetworkFuel();
            boolean hasRoom = data.hasAnyRoom();

            // Count slips in player inventory
            int slips = 0;
            for (int i = 0; i < serverPlayer.getInventory().getContainerSize(); i++) {
                ItemStack invStack = serverPlayer.getInventory().getItem(i);
                if (invStack.is(ModItems.LATINUM_SLIP.get())) {
                    slips += invStack.getCount();
                }
            }

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
            PacketDistributor.sendToPlayer(serverPlayer, new OpenTricorderScreenPayload(fuel, slips, hasRoom, pads, signals));
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
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
