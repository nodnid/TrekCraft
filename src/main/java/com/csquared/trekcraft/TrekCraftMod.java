package com.csquared.trekcraft;

import com.csquared.trekcraft.registry.ModBlocks;
import com.csquared.trekcraft.registry.ModBlockEntities;
import com.csquared.trekcraft.registry.ModDataComponents;
import com.csquared.trekcraft.registry.ModItems;
import com.csquared.trekcraft.registry.ModMenuTypes;
import com.csquared.trekcraft.registry.ModCreativeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(TrekCraftMod.MODID)
public class TrekCraftMod {
    public static final String MODID = "trekcraft";
    public static final Logger LOGGER = LoggerFactory.getLogger(TrekCraftMod.class);

    public TrekCraftMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("TrekCraft initializing - Engage!");

        // Register config
        modContainer.registerConfig(ModConfig.Type.SERVER, TrekCraftConfig.SPEC);

        // Register all mod content
        ModDataComponents.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModMenuTypes.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
    }
}
