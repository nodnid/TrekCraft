package com.csquared.trekcraft;

import com.csquared.trekcraft.command.TrekCommands;
import com.csquared.trekcraft.content.item.TricorderItem;
import com.csquared.trekcraft.data.TransporterNetworkSavedData;
import com.csquared.trekcraft.data.TricorderData;
import com.csquared.trekcraft.registry.ModDataComponents;
import com.csquared.trekcraft.registry.ModItems;
import com.csquared.trekcraft.service.RequestService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = TrekCraftMod.MODID)
public class TrekCraftEvents {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        TrekCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        // Tick request timeouts
        RequestService.tickRequests(event.getServer());
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        // Track dropped tricorders as signals
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ItemEntity itemEntity)) return;

        ItemStack stack = itemEntity.getItem();
        if (!stack.is(ModItems.TRICORDER.get())) return;

        // Ensure it has data
        TricorderItem.ensureTricorderData(stack);
        TricorderData data = stack.get(ModDataComponents.TRICORDER_DATA.get());
        if (data == null) return;

        // Only register in Overworld
        if (!event.getLevel().dimension().equals(Level.OVERWORLD)) return;

        ServerLevel serverLevel = (ServerLevel) event.getLevel();
        TransporterNetworkSavedData networkData = TransporterNetworkSavedData.get(serverLevel);

        networkData.registerSignal(
                data.tricorderId(),
                data.getDisplayName(),
                itemEntity.blockPosition(),
                serverLevel.getGameTime()
        );

        TrekCraftMod.LOGGER.debug("Registered tricorder signal: {} at {}",
                data.getDisplayName(), itemEntity.blockPosition());
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        // Remove dropped tricorder signals when picked up or despawned
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ItemEntity itemEntity)) return;

        ItemStack stack = itemEntity.getItem();
        if (!stack.is(ModItems.TRICORDER.get())) return;

        TricorderData data = stack.get(ModDataComponents.TRICORDER_DATA.get());
        if (data == null) return;

        if (!event.getLevel().dimension().equals(Level.OVERWORLD)) return;

        ServerLevel serverLevel = (ServerLevel) event.getLevel();
        TransporterNetworkSavedData networkData = TransporterNetworkSavedData.get(serverLevel);

        networkData.unregisterSignal(data.tricorderId());

        TrekCraftMod.LOGGER.debug("Unregistered tricorder signal: {}", data.getDisplayName());
    }
}
