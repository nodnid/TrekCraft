package com.csquared.trekcraft.registry;

import com.csquared.trekcraft.TrekCraftMod;
import com.csquared.trekcraft.data.TricorderData;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, TrekCraftMod.MODID);

    public static final Supplier<DataComponentType<TricorderData>> TRICORDER_DATA = DATA_COMPONENTS.register(
            "tricorder_data",
            () -> DataComponentType.<TricorderData>builder()
                    .persistent(TricorderData.CODEC)
                    .networkSynchronized(TricorderData.STREAM_CODEC)
                    .build()
    );

    public static void register(IEventBus eventBus) {
        DATA_COMPONENTS.register(eventBus);
    }
}
