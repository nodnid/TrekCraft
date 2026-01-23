package com.csquared.trekcraft.client.screen;

import com.csquared.trekcraft.network.OpenNamingScreenPayload.NamingType;
import com.csquared.trekcraft.network.SetPadNamePayload;
import com.csquared.trekcraft.network.SetTricorderNamePayload;
import com.csquared.trekcraft.network.SetWormholeNamePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

/**
 * LCARS-styled naming screen with EditBox text input.
 * Used for naming tricorders and transporter pads.
 */
public class NamingScreen extends Screen {

    private static final int PANEL_WIDTH = 250;
    private static final int PANEL_HEIGHT = 220;  // Same as TricorderScreen
    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_HEIGHT = 22;

    private final NamingType type;
    private final String defaultName;
    private final UUID tricorderId;  // For TRICORDER type
    private final BlockPos padPos;   // For PAD type
    private final UUID wormholeId;   // For WORMHOLE type

    private EditBox nameInput;
    private int panelLeft;
    private int panelTop;

    private NamingScreen(NamingType type, String defaultName, UUID tricorderId, BlockPos padPos, UUID wormholeId) {
        super(Component.translatable("screen.trekcraft.naming"));
        this.type = type;
        this.defaultName = defaultName;
        this.tricorderId = tricorderId;
        this.padPos = padPos;
        this.wormholeId = wormholeId;
    }

    /**
     * Factory method for tricorder naming.
     */
    public static NamingScreen forTricorder(UUID tricorderId, String defaultName) {
        return new NamingScreen(NamingType.TRICORDER, defaultName, tricorderId, null, null);
    }

    /**
     * Factory method for pad naming.
     */
    public static NamingScreen forPad(BlockPos padPos, String defaultName) {
        return new NamingScreen(NamingType.PAD, defaultName, null, padPos, null);
    }

    /**
     * Factory method for wormhole naming.
     */
    public static NamingScreen forWormhole(UUID wormholeId, String defaultName) {
        return new NamingScreen(NamingType.WORMHOLE, defaultName, null, null, wormholeId);
    }

    @Override
    protected void init() {
        super.init();
        panelLeft = (this.width - PANEL_WIDTH) / 2;
        panelTop = (this.height - PANEL_HEIGHT) / 2;

        int[] contentBounds = LCARSRenderer.getContentBounds(panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT);
        int contentX = contentBounds[0];
        int contentY = contentBounds[1];
        int contentW = contentBounds[2];
        int contentH = contentBounds[3];

        // Create the text input box - leave room for instruction label above
        int inputWidth = contentW - 20;
        int inputX = contentX + (contentW - inputWidth) / 2;
        int inputY = contentY + 25;  // Room for "Enter a name:" label above

        nameInput = new EditBox(this.font, inputX, inputY, inputWidth, 20, Component.literal("Name"));
        nameInput.setMaxLength(32);
        nameInput.setValue(defaultName);
        nameInput.setHighlightPos(0);  // Select all text for easy replacement
        nameInput.setFocused(true);
        addRenderableWidget(nameInput);

        // Buttons at bottom
        int buttonY = contentY + contentH - BUTTON_HEIGHT - 8;
        int buttonSpacing = 10;
        int totalButtonWidth = BUTTON_WIDTH * 2 + buttonSpacing;
        int buttonStartX = contentX + (contentW - totalButtonWidth) / 2;

        // CONFIRM button
        addRenderableWidget(LCARSButton.lcarsBuilder(
                Component.literal("CONFIRM"),
                button -> confirmName()
        ).bounds(buttonStartX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .colors(LCARSRenderer.LAVENDER, LCARSRenderer.PURPLE)
                .build());

        // CANCEL button
        addRenderableWidget(LCARSButton.lcarsBuilder(
                Component.literal("CANCEL"),
                button -> cancelNaming()
        ).bounds(buttonStartX + BUTTON_WIDTH + buttonSpacing, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .colors(LCARSRenderer.BLUE, LCARSRenderer.LAVENDER)
                .build());

        // Set initial focus to the text input
        setInitialFocus(nameInput);
    }

    private void confirmName() {
        String name = nameInput.getValue().trim();
        if (name.isEmpty()) {
            name = defaultName;
        }

        switch (type) {
            case TRICORDER -> {
                if (tricorderId != null) {
                    PacketDistributor.sendToServer(new SetTricorderNamePayload(tricorderId, name));
                }
            }
            case PAD -> {
                if (padPos != null) {
                    PacketDistributor.sendToServer(new SetPadNamePayload(padPos, name));
                }
            }
            case WORMHOLE -> {
                if (wormholeId != null) {
                    PacketDistributor.sendToServer(new SetWormholeNamePayload(wormholeId, name));
                }
            }
        }

        this.onClose();
    }

    private void cancelNaming() {
        // Use default name
        switch (type) {
            case TRICORDER -> {
                if (tricorderId != null) {
                    PacketDistributor.sendToServer(new SetTricorderNamePayload(tricorderId, defaultName));
                }
            }
            case PAD -> {
                if (padPos != null) {
                    PacketDistributor.sendToServer(new SetPadNamePayload(padPos, defaultName));
                }
            }
            case WORMHOLE -> {
                if (wormholeId != null) {
                    PacketDistributor.sendToServer(new SetWormholeNamePayload(wormholeId, defaultName));
                }
            }
        }

        this.onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Enter key confirms
        if (keyCode == 257 || keyCode == 335) { // ENTER or NUMPAD_ENTER
            confirmName();
            return true;
        }
        // Escape key cancels
        if (keyCode == 256) { // ESCAPE
            cancelNaming();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        // Fill panel background with black
        guiGraphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, 0xFF000000);

        // Draw LCARS frame
        String title = switch (type) {
            case TRICORDER -> "NAME TRICORDER";
            case PAD -> "NAME PAD";
            case WORMHOLE -> "NAME WORMHOLE";
        };
        LCARSRenderer.drawLCARSFrame(guiGraphics, panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT, title, this.font);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Draw instruction text above input (inside content area)
        int[] contentBounds = LCARSRenderer.getContentBounds(panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT);
        int contentX = contentBounds[0];
        int contentY = contentBounds[1];
        int contentW = contentBounds[2];

        String instruction = "Enter a name:";
        int instructionWidth = this.font.width(instruction);
        int instructionX = contentX + (contentW - instructionWidth) / 2;
        int instructionY = contentY + 8;  // Inside content area, above the input box
        guiGraphics.drawString(this.font, instruction, instructionX, instructionY, LCARSRenderer.ORANGE);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
