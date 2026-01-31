package com.csquared.trekcraft.network;

import com.csquared.trekcraft.TrekCraftMod;
import com.csquared.trekcraft.content.blockentity.HolodeckControllerBlockEntity;
import com.csquared.trekcraft.content.blockentity.TransporterPadBlockEntity;
import com.csquared.trekcraft.holodeck.HoloprogramManager;
import com.csquared.trekcraft.data.TransporterNetworkSavedData;
import com.csquared.trekcraft.data.TricorderData;
import com.csquared.trekcraft.registry.ModDataComponents;
import com.csquared.trekcraft.registry.ModItems;
import com.csquared.trekcraft.service.WormholeService;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
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

        // Naming screen payloads
        registrar.playToClient(
                OpenNamingScreenPayload.TYPE,
                OpenNamingScreenPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        handleOpenNamingScreenOnClient(payload);
                    }
                }
        );

        registrar.playToServer(
                SetTricorderNamePayload.TYPE,
                SetTricorderNamePayload.STREAM_CODEC,
                (payload, context) -> {
                    ServerPlayer player = (ServerPlayer) context.player();
                    handleSetTricorderName(player, payload);
                }
        );

        registrar.playToServer(
                SetPadNamePayload.TYPE,
                SetPadNamePayload.STREAM_CODEC,
                (payload, context) -> {
                    ServerPlayer player = (ServerPlayer) context.player();
                    handleSetPadName(player, payload);
                }
        );

        // Wormhole payloads
        registrar.playToServer(
                SetWormholeNamePayload.TYPE,
                SetWormholeNamePayload.STREAM_CODEC,
                (payload, context) -> {
                    ServerPlayer player = (ServerPlayer) context.player();
                    handleSetWormholeName(player, payload);
                }
        );

        registrar.playToClient(
                OpenWormholeLinkScreenPayload.TYPE,
                OpenWormholeLinkScreenPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        handleOpenWormholeLinkScreenOnClient(payload);
                    }
                }
        );

        registrar.playToServer(
                LinkWormholesPayload.TYPE,
                LinkWormholesPayload.STREAM_CODEC,
                (payload, context) -> {
                    ServerPlayer player = (ServerPlayer) context.player();
                    handleLinkWormholes(player, payload);
                }
        );

        registrar.playToClient(
                OpenContributionScreenPayload.TYPE,
                OpenContributionScreenPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        handleOpenContributionScreenOnClient(payload);
                    }
                }
        );

        // Holodeck payloads
        registrar.playToClient(
                OpenHolodeckScreenPayload.TYPE,
                OpenHolodeckScreenPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        handleOpenHolodeckScreenOnClient(payload);
                    }
                }
        );

        registrar.playToServer(
                SaveHoloprogramPayload.TYPE,
                SaveHoloprogramPayload.STREAM_CODEC,
                (payload, context) -> {
                    ServerPlayer player = (ServerPlayer) context.player();
                    handleSaveHoloprogram(player, payload);
                }
        );

        registrar.playToServer(
                LoadHoloprogramPayload.TYPE,
                LoadHoloprogramPayload.STREAM_CODEC,
                (payload, context) -> {
                    ServerPlayer player = (ServerPlayer) context.player();
                    handleLoadHoloprogram(player, payload);
                }
        );

        registrar.playToServer(
                DeleteHoloprogramPayload.TYPE,
                DeleteHoloprogramPayload.STREAM_CODEC,
                (payload, context) -> {
                    ServerPlayer player = (ServerPlayer) context.player();
                    handleDeleteHoloprogram(player, payload);
                }
        );

        registrar.playToServer(
                ClearHolodeckPayload.TYPE,
                ClearHolodeckPayload.STREAM_CODEC,
                (payload, context) -> {
                    ServerPlayer player = (ServerPlayer) context.player();
                    handleClearHolodeck(player, payload);
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

    private static void handleOpenNamingScreenOnClient(OpenNamingScreenPayload payload) {
        try {
            Class<?> handlerClass = Class.forName("com.csquared.trekcraft.client.ClientPayloadHandler");
            handlerClass.getMethod("openNamingScreen", OpenNamingScreenPayload.class).invoke(null, payload);
        } catch (Exception e) {
            TrekCraftMod.LOGGER.error("Failed to open naming screen", e);
        }
    }

    private static void handleSetTricorderName(ServerPlayer player, SetTricorderNamePayload payload) {
        // Search player inventory for tricorder with matching UUID
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(ModItems.TRICORDER.get())) {
                TricorderData data = stack.get(ModDataComponents.TRICORDER_DATA.get());
                if (data != null && data.tricorderId().equals(payload.tricorderId())) {
                    // Update TricorderData with new label
                    stack.set(ModDataComponents.TRICORDER_DATA.get(), data.withLabel(payload.name()));

                    // Update CUSTOM_NAME component for display
                    stack.set(DataComponents.CUSTOM_NAME, Component.literal(payload.name()));

                    // Update signal registry if this tricorder is tracked
                    ServerLevel serverLevel = player.serverLevel();
                    TransporterNetworkSavedData savedData = TransporterNetworkSavedData.get(serverLevel);
                    String dimensionKey = serverLevel.dimension().location().toString();
                    savedData.getSignal(payload.tricorderId()).ifPresent(signal -> {
                        // Re-register with updated name (use current dimension from signal)
                        if (signal.type() == TransporterNetworkSavedData.SignalType.HELD) {
                            savedData.registerHeldSignal(
                                    payload.tricorderId(),
                                    payload.name(),
                                    signal.lastKnownPos(),
                                    serverLevel.getGameTime(),
                                    signal.holderId(),
                                    signal.dimensionKey()
                            );
                        } else {
                            savedData.registerDroppedSignal(
                                    payload.tricorderId(),
                                    payload.name(),
                                    signal.lastKnownPos(),
                                    serverLevel.getGameTime(),
                                    signal.dimensionKey()
                            );
                        }
                    });

                    player.displayClientMessage(
                            Component.literal("Tricorder named: " + payload.name()), true);
                    return;
                }
            }
        }
    }

    private static void handleSetPadName(ServerPlayer player, SetPadNamePayload payload) {
        ServerLevel level = player.serverLevel();

        // Validate player is within reasonable distance (anti-cheat)
        double distSq = player.blockPosition().distSqr(payload.padPos());
        if (distSq > 256) { // 16 blocks max
            TrekCraftMod.LOGGER.warn("Player {} tried to rename pad too far away", player.getName().getString());
            return;
        }

        BlockEntity be = level.getBlockEntity(payload.padPos());
        if (be instanceof TransporterPadBlockEntity padBE) {
            padBE.setPadName(payload.name());
            String dimensionKey = level.dimension().location().toString();
            TransporterNetworkSavedData.get(level).registerPad(payload.padPos(), payload.name(), dimensionKey);

            player.displayClientMessage(
                    Component.literal("Transporter pad named: " + payload.name()), true);
        }
    }

    private static void handleOpenWormholeLinkScreenOnClient(OpenWormholeLinkScreenPayload payload) {
        try {
            Class<?> handlerClass = Class.forName("com.csquared.trekcraft.client.ClientPayloadHandler");
            handlerClass.getMethod("openWormholeLinkScreen", OpenWormholeLinkScreenPayload.class).invoke(null, payload);
        } catch (Exception e) {
            TrekCraftMod.LOGGER.error("Failed to open wormhole link screen", e);
        }
    }

    private static void handleSetWormholeName(ServerPlayer player, SetWormholeNamePayload payload) {
        ServerLevel level = player.serverLevel();
        WormholeService.renameWormhole(level, payload.portalId(), payload.name());
        player.displayClientMessage(
                Component.literal("Wormhole named: " + payload.name()), true);
    }

    private static void handleLinkWormholes(ServerPlayer player, LinkWormholesPayload payload) {
        ServerLevel level = player.serverLevel();
        boolean success = WormholeService.linkPortals(level, payload.getPortal1Id(), payload.getPortal2Id());

        if (success) {
            TransporterNetworkSavedData data = TransporterNetworkSavedData.get(level);
            String name1 = data.getWormhole(payload.getPortal1Id())
                    .map(w -> w.name()).orElse("Unknown");
            String name2 = data.getWormhole(payload.getPortal2Id())
                    .map(w -> w.name()).orElse("Unknown");
            player.displayClientMessage(
                    Component.literal("Linked wormholes: " + name1 + " <-> " + name2), true);
        } else {
            player.displayClientMessage(
                    Component.literal("Failed to link wormholes"), true);
        }
    }

    private static void handleOpenContributionScreenOnClient(OpenContributionScreenPayload payload) {
        try {
            Class<?> handlerClass = Class.forName("com.csquared.trekcraft.client.ClientPayloadHandler");
            handlerClass.getMethod("openContributionScreen", OpenContributionScreenPayload.class).invoke(null, payload);
        } catch (Exception e) {
            TrekCraftMod.LOGGER.error("Failed to open contribution screen", e);
        }
    }

    private static void handleOpenHolodeckScreenOnClient(OpenHolodeckScreenPayload payload) {
        try {
            Class<?> handlerClass = Class.forName("com.csquared.trekcraft.client.ClientPayloadHandler");
            handlerClass.getMethod("openHolodeckScreen", OpenHolodeckScreenPayload.class).invoke(null, payload);
        } catch (Exception e) {
            TrekCraftMod.LOGGER.error("Failed to open holodeck screen", e);
        }
    }

    private static void handleSaveHoloprogram(ServerPlayer player, SaveHoloprogramPayload payload) {
        ServerLevel level = player.serverLevel();

        // Validate player is within reasonable distance
        double distSq = player.blockPosition().distSqr(payload.controllerPos());
        if (distSq > 256) {
            TrekCraftMod.LOGGER.warn("Player {} tried to save holoprogram too far away", player.getName().getString());
            return;
        }

        BlockEntity be = level.getBlockEntity(payload.controllerPos());
        if (be instanceof HolodeckControllerBlockEntity controller) {
            boolean success = controller.saveHoloprogram(payload.programName());
            if (success) {
                player.displayClientMessage(
                        Component.literal("Holoprogram saved: " + payload.programName()), true);
            } else {
                player.displayClientMessage(
                        Component.literal("Failed to save holoprogram"), true);
            }
        }
    }

    private static void handleLoadHoloprogram(ServerPlayer player, LoadHoloprogramPayload payload) {
        ServerLevel level = player.serverLevel();

        double distSq = player.blockPosition().distSqr(payload.controllerPos());
        if (distSq > 256) {
            TrekCraftMod.LOGGER.warn("Player {} tried to load holoprogram too far away", player.getName().getString());
            return;
        }

        BlockEntity be = level.getBlockEntity(payload.controllerPos());
        if (be instanceof HolodeckControllerBlockEntity controller) {
            HoloprogramManager.LoadResultDetails result = controller.loadHoloprogram(payload.programName());
            if (result == null) {
                player.displayClientMessage(
                        Component.literal("Holodeck not active"), true);
                return;
            }

            String message = switch (result.result()) {
                case SUCCESS -> "Holoprogram loaded: " + payload.programName();
                case NOT_FOUND -> "Holoprogram not found: " + payload.programName();
                case TOO_LARGE -> String.format("Schematic too large (%dx%dx%d) for holodeck (%dx%dx%d)",
                        result.schematicSize().getX(), result.schematicSize().getY(), result.schematicSize().getZ(),
                        result.interiorSize().getX(), result.interiorSize().getY(), result.interiorSize().getZ());
                case READ_ERROR -> "Failed to load holoprogram";
            };
            player.displayClientMessage(Component.literal(message), true);
        }
    }

    private static void handleDeleteHoloprogram(ServerPlayer player, DeleteHoloprogramPayload payload) {
        ServerLevel level = player.serverLevel();

        double distSq = player.blockPosition().distSqr(payload.controllerPos());
        if (distSq > 256) {
            TrekCraftMod.LOGGER.warn("Player {} tried to delete holoprogram too far away", player.getName().getString());
            return;
        }

        BlockEntity be = level.getBlockEntity(payload.controllerPos());
        if (be instanceof HolodeckControllerBlockEntity controller) {
            boolean success = controller.deleteHoloprogram(payload.programName());
            if (success) {
                player.displayClientMessage(
                        Component.literal("Holoprogram deleted: " + payload.programName()), true);
            } else {
                player.displayClientMessage(
                        Component.literal("Failed to delete holoprogram"), true);
            }
        }
    }

    private static void handleClearHolodeck(ServerPlayer player, ClearHolodeckPayload payload) {
        ServerLevel level = player.serverLevel();

        double distSq = player.blockPosition().distSqr(payload.controllerPos());
        if (distSq > 256) {
            TrekCraftMod.LOGGER.warn("Player {} tried to clear holodeck too far away", player.getName().getString());
            return;
        }

        BlockEntity be = level.getBlockEntity(payload.controllerPos());
        if (be instanceof HolodeckControllerBlockEntity controller) {
            controller.manualClear();
            player.displayClientMessage(
                    Component.literal("Holodeck cleared"), true);
        }
    }
}
