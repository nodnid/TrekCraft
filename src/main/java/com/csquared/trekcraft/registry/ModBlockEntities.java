package com.csquared.trekcraft.registry;

import com.csquared.trekcraft.TrekCraftMod;
import com.csquared.trekcraft.content.blockentity.TransporterPadBlockEntity;
import com.csquared.trekcraft.content.blockentity.TransporterRoomBlockEntity;
import com.csquared.trekcraft.content.blockentity.WormholePortalBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, TrekCraftMod.MODID);

    public static final Supplier<BlockEntityType<TransporterPadBlockEntity>> TRANSPORTER_PAD =
            BLOCK_ENTITIES.register("transporter_pad",
                    () -> BlockEntityType.Builder.of(
                            TransporterPadBlockEntity::new,
                            ModBlocks.TRANSPORTER_PAD.get()
                    ).build(null)
            );

    public static final Supplier<BlockEntityType<TransporterRoomBlockEntity>> TRANSPORTER_ROOM =
            BLOCK_ENTITIES.register("transporter_room",
                    () -> BlockEntityType.Builder.of(
                            TransporterRoomBlockEntity::new,
                            ModBlocks.TRANSPORTER_ROOM.get()
                    ).build(null)
            );

    public static final Supplier<BlockEntityType<WormholePortalBlockEntity>> WORMHOLE_PORTAL =
            BLOCK_ENTITIES.register("wormhole_portal",
                    () -> BlockEntityType.Builder.of(
                            WormholePortalBlockEntity::new,
                            ModBlocks.WORMHOLE_PORTAL.get()
                    ).build(null)
            );

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
