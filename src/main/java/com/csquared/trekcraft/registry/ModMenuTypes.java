package com.csquared.trekcraft.registry;

import com.csquared.trekcraft.TrekCraftMod;
import com.csquared.trekcraft.content.menu.TransporterRoomMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, TrekCraftMod.MODID);

    public static final Supplier<MenuType<TransporterRoomMenu>> TRANSPORTER_ROOM =
            MENU_TYPES.register("transporter_room",
                    () -> IMenuTypeExtension.create(TransporterRoomMenu::new)
            );

    public static void register(IEventBus eventBus) {
        MENU_TYPES.register(eventBus);
    }
}
