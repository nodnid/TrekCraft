package com.csquared.trekcraft.client;

import com.csquared.trekcraft.TrekCraftMod;
import com.csquared.trekcraft.client.screen.TransporterRoomScreen;
import com.csquared.trekcraft.registry.ModMenuTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = TrekCraftMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class TrekCraftClient {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        TrekCraftMod.LOGGER.info("TrekCraft client setup complete");
    }

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.TRANSPORTER_ROOM.get(), TransporterRoomScreen::new);
    }
}
