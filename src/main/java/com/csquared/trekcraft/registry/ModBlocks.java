package com.csquared.trekcraft.registry;

import com.csquared.trekcraft.TrekCraftMod;
import com.csquared.trekcraft.content.block.TransporterPadBlock;
import com.csquared.trekcraft.content.block.TransporterRoomBlock;
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
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
            )
    );

    // Transporter Room - global controller
    public static final DeferredBlock<TransporterRoomBlock> TRANSPORTER_ROOM = BLOCKS.register(
            "transporter_room",
            () -> new TransporterRoomBlock(BlockBehaviour.Properties.of()
                    .strength(4.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
            )
    );

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
