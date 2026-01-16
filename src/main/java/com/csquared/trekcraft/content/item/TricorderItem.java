package com.csquared.trekcraft.content.item;

import com.csquared.trekcraft.data.TricorderData;
import com.csquared.trekcraft.registry.ModDataComponents;
import com.csquared.trekcraft.util.ChatUi;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class TricorderItem extends Item {

    public TricorderItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // Ensure tricorder has an ID
        ensureTricorderData(stack);

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            // Open the tricorder menu via chat
            ChatUi.sendTricorderMenu(serverPlayer);
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        // Ensure tricorder always has data component
        if (!level.isClientSide) {
            ensureTricorderData(stack);
        }
    }

    public static void ensureTricorderData(ItemStack stack) {
        if (!stack.has(ModDataComponents.TRICORDER_DATA.get())) {
            stack.set(ModDataComponents.TRICORDER_DATA.get(), TricorderData.create());
        }
    }

    public static TricorderData getTricorderData(ItemStack stack) {
        ensureTricorderData(stack);
        return stack.get(ModDataComponents.TRICORDER_DATA.get());
    }
}
