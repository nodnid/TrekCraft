package com.csquared.trekcraft.client.screen;

import com.csquared.trekcraft.network.LinkWormholesPayload;
import com.csquared.trekcraft.network.OpenWormholeLinkScreenPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.UUID;

/**
 * LCARS-styled screen for selecting a wormhole portal to link to.
 */
public class WormholeLinkScreen extends Screen {

    private static final int PANEL_WIDTH = 280;
    private static final int PANEL_HEIGHT = 260;
    private static final int BUTTON_HEIGHT = 22;
    private static final int BUTTON_SPACING = 4;
    private static final int MAX_VISIBLE_PORTALS = 6;

    private final UUID sourcePortalId;
    private final String sourcePortalName;
    private final List<OpenWormholeLinkScreenPayload.PortalEntry> availablePortals;

    private int panelLeft;
    private int panelTop;
    private int scrollOffset = 0;

    public WormholeLinkScreen(OpenWormholeLinkScreenPayload payload) {
        super(Component.translatable("screen.trekcraft.wormhole_link"));
        this.sourcePortalId = payload.getSourcePortalId();
        this.sourcePortalName = payload.sourcePortalName();
        this.availablePortals = payload.availablePortals();
    }

    @Override
    protected void init() {
        super.init();
        panelLeft = (this.width - PANEL_WIDTH) / 2;
        panelTop = (this.height - PANEL_HEIGHT) / 2;

        rebuildPortalButtons();

        // Add close button
        int[] contentBounds = LCARSRenderer.getContentBounds(panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT);
        int contentX = contentBounds[0];
        int contentY = contentBounds[1];
        int contentW = contentBounds[2];
        int contentH = contentBounds[3];

        int closeButtonY = contentY + contentH - BUTTON_HEIGHT - 5;
        addRenderableWidget(LCARSButton.lcarsBuilder(
                Component.literal("CANCEL"),
                button -> this.onClose()
        ).bounds(contentX + (contentW - 80) / 2, closeButtonY, 80, BUTTON_HEIGHT)
                .colors(LCARSRenderer.BLUE, LCARSRenderer.LAVENDER)
                .build());
    }

    private void rebuildPortalButtons() {
        // Remove existing portal buttons but keep the close button
        this.clearWidgets();

        int[] contentBounds = LCARSRenderer.getContentBounds(panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT);
        int contentX = contentBounds[0];
        int contentY = contentBounds[1];
        int contentW = contentBounds[2];
        int contentH = contentBounds[3];

        // Starting Y position for portal buttons (after header text)
        int buttonY = contentY + 35;
        int buttonWidth = contentW - 10;

        // Calculate visible range
        int maxVisible = Math.min(MAX_VISIBLE_PORTALS, availablePortals.size() - scrollOffset);

        for (int i = 0; i < maxVisible; i++) {
            int portalIndex = i + scrollOffset;
            if (portalIndex >= availablePortals.size()) break;

            OpenWormholeLinkScreenPayload.PortalEntry portal = availablePortals.get(portalIndex);
            String buttonText = portal.name() + " (" + portal.getPositionString() + ")";

            final UUID targetId = portal.getPortalId();
            addRenderableWidget(LCARSButton.lcarsBuilder(
                    Component.literal(buttonText),
                    button -> linkToPortal(targetId)
            ).bounds(contentX + 5, buttonY + i * (BUTTON_HEIGHT + BUTTON_SPACING), buttonWidth, BUTTON_HEIGHT)
                    .colors(LCARSRenderer.LAVENDER, LCARSRenderer.PURPLE)
                    .build());
        }

        // Add scroll buttons if needed
        if (availablePortals.size() > MAX_VISIBLE_PORTALS) {
            int scrollButtonY = contentY + contentH - BUTTON_HEIGHT * 2 - 15;

            // Scroll up button
            if (scrollOffset > 0) {
                addRenderableWidget(LCARSButton.lcarsBuilder(
                        Component.literal("^"),
                        button -> {
                            scrollOffset = Math.max(0, scrollOffset - 1);
                            rebuildPortalButtons();
                        }
                ).bounds(contentX + 5, scrollButtonY, 30, BUTTON_HEIGHT)
                        .colors(LCARSRenderer.PEACH, LCARSRenderer.ORANGE)
                        .centerAligned()
                        .build());
            }

            // Scroll down button
            if (scrollOffset < availablePortals.size() - MAX_VISIBLE_PORTALS) {
                addRenderableWidget(LCARSButton.lcarsBuilder(
                        Component.literal("v"),
                        button -> {
                            scrollOffset = Math.min(availablePortals.size() - MAX_VISIBLE_PORTALS, scrollOffset + 1);
                            rebuildPortalButtons();
                        }
                ).bounds(contentX + 40, scrollButtonY, 30, BUTTON_HEIGHT)
                        .colors(LCARSRenderer.PEACH, LCARSRenderer.ORANGE)
                        .centerAligned()
                        .build());
            }
        }

        // Re-add close button
        int closeButtonY = contentY + contentH - BUTTON_HEIGHT - 5;
        addRenderableWidget(LCARSButton.lcarsBuilder(
                Component.literal("CANCEL"),
                button -> this.onClose()
        ).bounds(contentX + (contentW - 80) / 2, closeButtonY, 80, BUTTON_HEIGHT)
                .colors(LCARSRenderer.BLUE, LCARSRenderer.LAVENDER)
                .build());
    }

    private void linkToPortal(UUID targetPortalId) {
        // Send link request to server
        PacketDistributor.sendToServer(new LinkWormholesPayload(
                sourcePortalId.toString(),
                targetPortalId.toString()
        ));
        this.onClose();
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        // Fill panel background with black
        guiGraphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, 0xFF000000);

        // Draw LCARS frame
        LCARSRenderer.drawLCARSFrame(guiGraphics, panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT, "LINK WORMHOLE", this.font);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int[] contentBounds = LCARSRenderer.getContentBounds(panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT);
        int contentX = contentBounds[0];
        int contentY = contentBounds[1];
        int contentW = contentBounds[2];

        // Draw source portal info
        String sourceText = "Link from: " + sourcePortalName;
        int textWidth = this.font.width(sourceText);
        guiGraphics.drawString(this.font, sourceText, contentX + (contentW - textWidth) / 2, contentY + 8, LCARSRenderer.ORANGE);

        // Draw instruction
        String instruction = availablePortals.isEmpty() ?
                "No unlinked wormholes available" :
                "Select a destination:";
        textWidth = this.font.width(instruction);
        guiGraphics.drawString(this.font, instruction, contentX + (contentW - textWidth) / 2, contentY + 22, 0xFFCCCCCC);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (availablePortals.size() > MAX_VISIBLE_PORTALS) {
            if (scrollY > 0) {
                scrollOffset = Math.max(0, scrollOffset - 1);
                rebuildPortalButtons();
            } else if (scrollY < 0) {
                scrollOffset = Math.min(availablePortals.size() - MAX_VISIBLE_PORTALS, scrollOffset + 1);
                rebuildPortalButtons();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
