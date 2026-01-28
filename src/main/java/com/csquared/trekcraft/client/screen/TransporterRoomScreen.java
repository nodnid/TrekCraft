package com.csquared.trekcraft.client.screen;

import com.csquared.trekcraft.TrekCraftMod;
import com.csquared.trekcraft.content.menu.TransporterRoomMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for the Transporter Room container.
 * Uses the standard chest texture for a 3-row container.
 */
public class TransporterRoomScreen extends AbstractContainerScreen<TransporterRoomMenu> {
    private static final ResourceLocation CONTAINER_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

    public TransporterRoomScreen(TransporterRoomMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        // 3 rows = 114 for container + 52 for inventory slots
        this.imageHeight = 114 + 3 * 18;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        // Draw top part (container rows)
        guiGraphics.blit(CONTAINER_TEXTURE, x, y, 0, 0, this.imageWidth, 3 * 18 + 17);
        // Draw bottom part (player inventory)
        guiGraphics.blit(CONTAINER_TEXTURE, x, y + 3 * 18 + 17, 0, 126, this.imageWidth, 96);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
