package com.csquared.trekcraft.content.blockentity;

import com.csquared.trekcraft.content.menu.TransporterRoomMenu;
import com.csquared.trekcraft.data.TransporterNetworkSavedData;
import com.csquared.trekcraft.registry.ModBlockEntities;
import com.csquared.trekcraft.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class TransporterRoomBlockEntity extends BaseContainerBlockEntity implements MenuProvider {
    private NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
    private boolean needsReconciliation = true;

    public TransporterRoomBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRANSPORTER_ROOM.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TransporterRoomBlockEntity be) {
        if (be.needsReconciliation && level instanceof ServerLevel serverLevel) {
            be.reconcileFuel(serverLevel);
            be.needsReconciliation = false;
        }
    }

    @Override
    public void setChanged() {
        super.setChanged();
        // Update cached fuel in SavedData whenever inventory changes
        if (level instanceof ServerLevel serverLevel) {
            updateCachedFuel(serverLevel);
        }
    }

    private void reconcileFuel(ServerLevel serverLevel) {
        TransporterNetworkSavedData data = TransporterNetworkSavedData.get(serverLevel);
        int cachedFuel = data.getRoomFuel(this.worldPosition);
        int actualFuel = countStrips();

        // If cached fuel is less than actual (teleports happened while unloaded),
        // remove strips from inventory to match
        if (cachedFuel < actualFuel) {
            int toRemove = actualFuel - cachedFuel;
            removeStrips(toRemove);
        }
        // If cached fuel is more than actual (shouldn't happen normally),
        // trust the inventory and update cache
        else if (cachedFuel > actualFuel) {
            data.setRoomFuel(this.worldPosition, actualFuel);
        }
    }

    private void updateCachedFuel(ServerLevel serverLevel) {
        TransporterNetworkSavedData data = TransporterNetworkSavedData.get(serverLevel);
        data.setRoomFuel(this.worldPosition, countStrips());
    }

    public int countStrips() {
        int count = 0;
        for (ItemStack stack : items) {
            if (stack.is(ModItems.LATINUM_STRIP.get())) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public boolean removeStrips(int amount) {
        int remaining = amount;
        for (int i = 0; i < items.size() && remaining > 0; i++) {
            ItemStack stack = items.get(i);
            if (stack.is(ModItems.LATINUM_STRIP.get())) {
                int toRemove = Math.min(remaining, stack.getCount());
                stack.shrink(toRemove);
                remaining -= toRemove;
                if (stack.isEmpty()) {
                    items.set(i, ItemStack.EMPTY);
                }
            }
        }
        setChanged();
        return remaining == 0;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(27, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        needsReconciliation = true;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.trekcraft.transporter_room");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory playerInventory) {
        return new TransporterRoomMenu(containerId, playerInventory, this);
    }

    @Override
    public int getContainerSize() {
        return 27;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        // Only accept latinum strips as fuel
        return stack.is(ModItems.LATINUM_STRIP.get());
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
