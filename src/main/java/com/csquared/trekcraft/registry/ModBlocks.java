package com.csquared.trekcraft.registry;

import com.csquared.trekcraft.TrekCraftMod;
import com.csquared.trekcraft.content.block.HolodeckControllerBlock;
import com.csquared.trekcraft.content.block.HolodeckEmitterBlock;
import com.csquared.trekcraft.content.block.TransporterPadBlock;
import com.csquared.trekcraft.content.block.TransporterRoomBlock;
import com.csquared.trekcraft.content.block.WormholePortalBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(TrekCraftMod.MODID);

    // Latinum storage block (like gold block)
    public static final DeferredBlock<Block> LATINUM_BAR = BLOCKS.registerSimpleBlock(
            "latinum_bar",
            BlockBehaviour.Properties.of()
                    .strength(3.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
    );

    // Transporter Pad - destination marker
    public static final DeferredBlock<TransporterPadBlock> TRANSPORTER_PAD = BLOCKS.register(
            "transporter_pad",
            () -> new TransporterPadBlock(BlockBehaviour.Properties.of()
                    .strength(2.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .noOcclusion()
            )
    );

    // Transporter Room - global controller
    public static final DeferredBlock<TransporterRoomBlock> TRANSPORTER_ROOM = BLOCKS.register(
            "transporter_room",
            () -> new TransporterRoomBlock(BlockBehaviour.Properties.of()
                    .strength(4.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .noOcclusion()
            )
    );

    // Wormhole Portal - hidden teleportation portal
    public static final DeferredBlock<WormholePortalBlock> WORMHOLE_PORTAL = BLOCKS.register(
            "wormhole_portal",
            () -> new WormholePortalBlock(BlockBehaviour.Properties.of()
                    .strength(-1.0f, 3600000.0f) // Unbreakable like bedrock
                    .noCollission()
                    .lightLevel(state -> 11)
                    .noLootTable()
                    .noOcclusion()
            )
    );

    // Holodeck Emitter - forms the frame structure of a holodeck
    public static final DeferredBlock<HolodeckEmitterBlock> HOLODECK_EMITTER = BLOCKS.register(
            "holodeck_emitter",
            () -> new HolodeckEmitterBlock(BlockBehaviour.Properties.of()
                    .strength(3.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(HolodeckEmitterBlock.ACTIVE) ? 7 : 0)
            )
    );

    // Holodeck Controller - central control point for a holodeck
    public static final DeferredBlock<HolodeckControllerBlock> HOLODECK_CONTROLLER = BLOCKS.register(
            "holodeck_controller",
            () -> new HolodeckControllerBlock(BlockBehaviour.Properties.of()
                    .strength(4.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .noOcclusion()
            )
    );

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
