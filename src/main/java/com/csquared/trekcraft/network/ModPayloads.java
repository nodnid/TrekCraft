package com.csquared.trekcraft.network;

import com.csquared.trekcraft.TrekCraftMod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = TrekCraftMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public class ModPayloads {

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(TrekCraftMod.MODID);

        registrar.playToClient(
                OpenTricorderScreenPayload.TYPE,
                OpenTricorderScreenPayload.STREAM_CODEC,
                (payload, context) -> {
                    // Only execute on client - the class reference is deferred via string
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        handleOnClient(payload);
                    }
                }
        );

        registrar.playToClient(
                ScanResultPayload.TYPE,
                ScanResultPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        handleScanResultOnClient(payload);
                    }
                }
        );
    }

    // This method uses a fully qualified class name string to defer class loading
    private static void handleOnClient(OpenTricorderScreenPayload payload) {
        try {
            Class<?> handlerClass = Class.forName("com.csquared.trekcraft.client.ClientPayloadHandler");
            handlerClass.getMethod("openScreen", OpenTricorderScreenPayload.class).invoke(null, payload);
        } catch (Exception e) {
            TrekCraftMod.LOGGER.error("Failed to open tricorder screen", e);
        }
    }

    private static void handleScanResultOnClient(ScanResultPayload payload) {
        try {
            Class<?> handlerClass = Class.forName("com.csquared.trekcraft.client.ClientPayloadHandler");
            handlerClass.getMethod("handleScanResult", ScanResultPayload.class).invoke(null, payload);
        } catch (Exception e) {
            TrekCraftMod.LOGGER.error("Failed to handle scan result", e);
        }
    }
}
