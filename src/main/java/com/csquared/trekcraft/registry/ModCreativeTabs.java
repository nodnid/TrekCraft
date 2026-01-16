package com.csquared.trekcraft.registry;

import com.csquared.trekcraft.TrekCraftMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TrekCraftMod.MODID);

    public static final Supplier<CreativeModeTab> TREKCRAFT_TAB = CREATIVE_TABS.register("trekcraft_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.trekcraft"))
                    .icon(() -> new ItemStack(ModItems.TRICORDER.get()))
                    .displayItems((params, output) -> {
                        // Tools
                        output.accept(ModItems.TRICORDER.get());

                        // Latinum currency
                        output.accept(ModItems.LATINUM_SLIP.get());
                        output.accept(ModItems.LATINUM_STRIP.get());
                        output.accept(ModItems.LATINUM_BAR.get());

                        // Transport blocks
                        output.accept(ModItems.TRANSPORTER_PAD.get());
                        output.accept(ModItems.TRANSPORTER_ROOM.get());
                    })
                    .build()
    );

    public static void register(IEventBus eventBus) {
        CREATIVE_TABS.register(eventBus);
    }
}
