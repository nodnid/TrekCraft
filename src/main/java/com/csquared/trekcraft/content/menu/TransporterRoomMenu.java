package com.csquared.trekcraft.content.menu;

import com.csquared.trekcraft.content.blockentity.TransporterRoomBlockEntity;
import com.csquared.trekcraft.data.TransporterNetworkSavedData;
import com.csquared.trekcraft.registry.ModItems;
import com.csquared.trekcraft.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.UUID;

/**
 * Custom menu for Transporter Room that tracks latinum deposits/withdrawals
 * and awards ranks and free transports based on contributions.
 */
public class TransporterRoomMenu extends AbstractContainerMenu {
    private final Container container;
    private final UUID playerId;
    private final String playerName;
    private final int initialLatinumCount;

    /**
     * Client-side constructor - reads block position from buffer and gets the block entity.
     */
    public TransporterRoomMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory, getContainerFromBuf(playerInventory, buf));
    }

    private static Container getContainerFromBuf(Inventory playerInventory, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        BlockEntity be = playerInventory.player.level().getBlockEntity(pos);
        if (be instanceof TransporterRoomBlockEntity roomBE) {
            return roomBE;
        }
        // Fallback to empty container if block entity not found
        return new SimpleContainer(27);
    }

    /**
     * Server-side constructor - uses the actual block entity container.
     */
    public TransporterRoomMenu(int containerId, Inventory playerInventory, Container container) {
        super(ModMenuTypes.TRANSPORTER_ROOM.get(), containerId);
        this.container = container;
        this.playerId = playerInventory.player.getUUID();
        this.playerName = playerInventory.player.getName().getString();

        // Count initial latinum in container
        this.initialLatinumCount = countLatinum(container);

        container.startOpen(playerInventory.player);

        // Container slots (3 rows x 9 = 27 slots)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new LatinumOnlySlot(container, col + row * 9, 8 + col * 18, 18 + row * 18));
            }
        }

        // Player inventory slots (3 rows x 9 = 27 slots)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        // Player hotbar slots (9 slots)
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);

        // Only process on server side
        if (player.level().isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        ServerLevel level = (ServerLevel) player.level();
        TransporterNetworkSavedData data = TransporterNetworkSavedData.get(level);

        // Calculate delta
        int finalLatinumCount = countLatinum(container);
        int delta = finalLatinumCount - initialLatinumCount;

        if (delta > 0) {
            // Deposit - player added latinum
            data.recordDeposit(playerId, playerName, delta);
            data.checkAndAwardRankUp(playerId, serverPlayer);
        } else if (delta < 0) {
            // Withdrawal - player removed latinum
            data.recordWithdrawal(playerId, playerName, Math.abs(delta));
        }
    }

    private static int countLatinum(Container container) {
        int count = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.is(ModItems.LATINUM_STRIP.get())) {
                count += stack.getCount();
            }
        }
        return count;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            result = slotStack.copy();

            // If clicking on container slot (0-26)
            if (index < 27) {
                // Move to player inventory (27-62)
                if (!this.moveItemStackTo(slotStack, 27, 63, true)) {
                    return ItemStack.EMPTY;
                }
            }
            // If clicking on player inventory slot (27-62)
            else {
                // Only move latinum strips to container
                if (slotStack.is(ModItems.LATINUM_STRIP.get())) {
                    if (!this.moveItemStackTo(slotStack, 0, 27, false)) {
                        return ItemStack.EMPTY;
                    }
                } else {
                    return ItemStack.EMPTY;
                }
            }

            if (slotStack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    /**
     * Custom slot that only accepts latinum strips.
     */
    private static class LatinumOnlySlot extends Slot {
        public LatinumOnlySlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(ModItems.LATINUM_STRIP.get());
        }
    }
}
