package com.csquared.trekcraft.registry;

import com.csquared.trekcraft.TrekCraftMod;
import com.csquared.trekcraft.content.item.TricorderItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TrekCraftMod.MODID);

    // Latinum currency items
    public static final DeferredItem<Item> LATINUM_SLIP = ITEMS.registerSimpleItem(
            "latinum_slip",
            new Item.Properties().stacksTo(64)
    );

    public static final DeferredItem<Item> LATINUM_STRIP = ITEMS.registerSimpleItem(
            "latinum_strip",
            new Item.Properties().stacksTo(64)
    );

    // Tricorder - the main tool
    public static final DeferredItem<TricorderItem> TRICORDER = ITEMS.register(
            "tricorder",
            () -> new TricorderItem(new Item.Properties().stacksTo(1))
    );

    // Block items
    public static final DeferredItem<BlockItem> LATINUM_BAR = ITEMS.registerSimpleBlockItem(
            "latinum_bar",
            ModBlocks.LATINUM_BAR
    );

    public static final DeferredItem<BlockItem> TRANSPORTER_PAD = ITEMS.registerSimpleBlockItem(
            "transporter_pad",
            ModBlocks.TRANSPORTER_PAD
    );

    public static final DeferredItem<BlockItem> TRANSPORTER_ROOM = ITEMS.registerSimpleBlockItem(
            "transporter_room",
            ModBlocks.TRANSPORTER_ROOM
    );

    public static final DeferredItem<BlockItem> HOLODECK_EMITTER = ITEMS.registerSimpleBlockItem(
            "holodeck_emitter",
            ModBlocks.HOLODECK_EMITTER
    );

    public static final DeferredItem<BlockItem> HOLODECK_CONTROLLER = ITEMS.registerSimpleBlockItem(
            "holodeck_controller",
            ModBlocks.HOLODECK_CONTROLLER
    );

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
