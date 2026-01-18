package com.csquared.trekcraft.client.screen;

import com.csquared.trekcraft.data.TransporterNetworkSavedData.SignalType;
import com.csquared.trekcraft.network.OpenTricorderScreenPayload;
import com.csquared.trekcraft.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class TricorderScreen extends Screen {

    private static final int PANEL_WIDTH = 220;
    private static final int PANEL_HEIGHT = 200;
    private static final int BUTTON_WIDTH = 170;
    private static final int BUTTON_HEIGHT = 22;
    private static final int BUTTON_SPACING = 6;

    private final int fuel;
    private final boolean hasRoom;
    private final List<OpenTricorderScreenPayload.PadEntry> pads;
    private final List<OpenTricorderScreenPayload.SignalEntry> signals;

    private MenuState currentState = MenuState.MAIN_MENU;
    private int panelLeft;
    private int panelTop;

    private enum MenuState {
        MAIN_MENU,
        PAD_LIST,
        SIGNAL_LIST
    }

    public TricorderScreen(int fuel, boolean hasRoom,
                           List<OpenTricorderScreenPayload.PadEntry> pads,
                           List<OpenTricorderScreenPayload.SignalEntry> signals) {
        super(Component.translatable("screen.trekcraft.tricorder"));
        this.fuel = fuel;
        this.hasRoom = hasRoom;
        this.pads = pads;
        this.signals = signals;
    }

    @Override
    protected void init() {
        super.init();
        panelLeft = (this.width - PANEL_WIDTH) / 2;
        panelTop = (this.height - PANEL_HEIGHT) / 2;
        rebuildButtons();
    }

    private void rebuildButtons() {
        clearWidgets();

        switch (currentState) {
            case MAIN_MENU -> buildMainMenu();
            case PAD_LIST -> buildPadList();
            case SIGNAL_LIST -> buildSignalList();
        }
    }

    private void buildMainMenu() {
        int[] contentBounds = LCARSRenderer.getContentBounds(panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT);
        int contentX = contentBounds[0];
        int contentY = contentBounds[1];
        int contentW = contentBounds[2];

        int buttonX = contentX + (contentW - BUTTON_WIDTH) / 2;
        int buttonY = contentY + 10;

        // Transport to Pad button
        addRenderableWidget(LCARSButton.lcarsBuilder(
                Component.literal("TRANSPORT TO PAD"),
                button -> {
                    currentState = MenuState.PAD_LIST;
                    rebuildButtons();
                }
        ).bounds(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build());

        buttonY += BUTTON_HEIGHT + BUTTON_SPACING;

        // Transport to Signal button
        addRenderableWidget(LCARSButton.lcarsBuilder(
                Component.literal("TRANSPORT TO SIGNAL"),
                button -> {
                    currentState = MenuState.SIGNAL_LIST;
                    rebuildButtons();
                }
        ).bounds(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build());

        buttonY += BUTTON_HEIGHT + BUTTON_SPACING;

        // Scan button
        addRenderableWidget(LCARSButton.lcarsBuilder(
                Component.literal("SCAN AREA"),
                button -> executeCommand("trek scan")
        ).bounds(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .colors(LCARSRenderer.LAVENDER, LCARSRenderer.PURPLE)
                .build());
    }

    private void buildPadList() {
        int[] contentBounds = LCARSRenderer.getContentBounds(panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT);
        int contentX = contentBounds[0];
        int contentY = contentBounds[1];
        int contentW = contentBounds[2];
        int contentH = contentBounds[3];

        int buttonX = contentX + (contentW - BUTTON_WIDTH) / 2;
        int buttonY = contentY + 10;

        if (!pads.isEmpty()) {
            int maxVisible = 5;
            int count = 0;
            for (OpenTricorderScreenPayload.PadEntry pad : pads) {
                if (count >= maxVisible) break;
                BlockPos pos = pad.pos();
                addRenderableWidget(LCARSButton.lcarsBuilder(
                        Component.literal(pad.name().toUpperCase()),
                        button -> executeCommand("trek transport toPad " + pos.getX() + " " + pos.getY() + " " + pos.getZ())
                ).bounds(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build());
                buttonY += BUTTON_HEIGHT + BUTTON_SPACING;
                count++;
            }
        }

        // Back button at bottom with different color
        int backY = contentY + contentH - BUTTON_HEIGHT - 4;
        addRenderableWidget(LCARSButton.lcarsBuilder(
                Component.literal("< BACK"),
                button -> {
                    currentState = MenuState.MAIN_MENU;
                    rebuildButtons();
                }
        ).bounds(buttonX, backY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .colors(LCARSRenderer.BLUE, LCARSRenderer.LAVENDER)
                .build());
    }

    private void buildSignalList() {
        int[] contentBounds = LCARSRenderer.getContentBounds(panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT);
        int contentX = contentBounds[0];
        int contentY = contentBounds[1];
        int contentW = contentBounds[2];
        int contentH = contentBounds[3];

        int buttonX = contentX + (contentW - BUTTON_WIDTH) / 2;
        int buttonY = contentY + 10;

        if (!signals.isEmpty()) {
            int maxVisible = 5;
            int count = 0;
            for (OpenTricorderScreenPayload.SignalEntry signal : signals) {
                if (count >= maxVisible) break;
                // Show type indicator in button text
                String typePrefix = signal.type() == SignalType.HELD ? "[H] " : "[D] ";
                addRenderableWidget(LCARSButton.lcarsBuilder(
                        Component.literal(typePrefix + signal.name().toUpperCase()),
                        button -> executeCommand("trek transport toSignal " + signal.tricorderId())
                ).bounds(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build());
                buttonY += BUTTON_HEIGHT + BUTTON_SPACING;
                count++;
            }
        }

        // Back button at bottom
        int backY = contentY + contentH - BUTTON_HEIGHT - 4;
        addRenderableWidget(LCARSButton.lcarsBuilder(
                Component.literal("< BACK"),
                button -> {
                    currentState = MenuState.MAIN_MENU;
                    rebuildButtons();
                }
        ).bounds(buttonX, backY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .colors(LCARSRenderer.BLUE, LCARSRenderer.LAVENDER)
                .build());
    }

    private void executeCommand(String command) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.connection != null) {
            mc.player.connection.sendCommand(command);
        }
        this.onClose();
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // First render the standard blurred background
        super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        // Then draw LCARS frame on top of the blur, before widgets
        String titleText = switch (currentState) {
            case MAIN_MENU -> "TRICORDER";
            case PAD_LIST -> "SELECT PAD";
            case SIGNAL_LIST -> "SELECT SIGNAL";
        };
        LCARSRenderer.drawLCARSFrame(guiGraphics, panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT, titleText, this.font);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Render background (with LCARS frame) and buttons
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Get content bounds for text positioning
        int[] contentBounds = LCARSRenderer.getContentBounds(panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT);
        int contentX = contentBounds[0];
        int contentY = contentBounds[1];
        int contentW = contentBounds[2];
        int contentH = contentBounds[3];

        // Draw empty list messages for sub-menus
        if (currentState == MenuState.PAD_LIST && pads.isEmpty()) {
            String msg = "NO TRANSPORTER PADS";
            int msgWidth = this.font.width(msg);
            guiGraphics.drawString(this.font, msg, contentX + (contentW - msgWidth) / 2, contentY + 40, LCARSRenderer.ORANGE);
            String msg2 = "REGISTERED";
            int msg2Width = this.font.width(msg2);
            guiGraphics.drawString(this.font, msg2, contentX + (contentW - msg2Width) / 2, contentY + 52, LCARSRenderer.ORANGE);
        } else if (currentState == MenuState.SIGNAL_LIST && signals.isEmpty()) {
            String msg = "NO TRICORDER SIGNALS";
            int msgWidth = this.font.width(msg);
            guiGraphics.drawString(this.font, msg, contentX + (contentW - msgWidth) / 2, contentY + 40, LCARSRenderer.ORANGE);
            String msg2 = "DETECTED";
            int msg2Width = this.font.width(msg2);
            guiGraphics.drawString(this.font, msg2, contentX + (contentW - msg2Width) / 2, contentY + 52, LCARSRenderer.ORANGE);
        }

        // Draw signal list legend
        if (currentState == MenuState.SIGNAL_LIST && !signals.isEmpty()) {
            String legend = "[H]=HELD  [D]=DROPPED";
            int legendWidth = this.font.width(legend);
            int legendY = contentY + contentH - BUTTON_HEIGHT - 18;
            guiGraphics.drawString(this.font, legend, contentX + (contentW - legendWidth) / 2, legendY, LCARSRenderer.LAVENDER);
        }

        // Draw fuel status at bottom (only on main menu)
        if (currentState == MenuState.MAIN_MENU) {
            int statusY = contentY + contentH - 20;

            if (!hasRoom) {
                String status = "NO TRANSPORTER ROOM";
                int statusWidth = this.font.width(status);
                guiGraphics.drawString(this.font, status, contentX + (contentW - statusWidth) / 2, statusY, LCARSRenderer.RED);
            } else {
                // Draw fuel label and bar
                String fuelLabel = "FUEL:";
                guiGraphics.drawString(this.font, fuelLabel, contentX + 4, statusY, LCARSRenderer.ORANGE);

                int barX = contentX + this.font.width(fuelLabel) + 8;
                int barWidth = 80;
                int barHeight = 8;

                // Determine fuel color based on level
                int fuelColor;
                if (fuel <= 0) {
                    fuelColor = LCARSRenderer.RED;
                } else if (fuel < 10) {
                    fuelColor = LCARSRenderer.ORANGE;
                } else {
                    fuelColor = LCARSRenderer.BLUE;
                }

                // Draw fuel bar (max assumed 64 for display purposes)
                float maxFuel = 64.0f;
                LCARSRenderer.drawStatusBar(guiGraphics, barX, statusY, barWidth, barHeight, fuel, maxFuel, fuelColor);

                // Draw numeric value
                String fuelNum = String.valueOf(fuel);
                guiGraphics.drawString(this.font, fuelNum, barX + barWidth + 6, statusY, LCARSRenderer.ORANGE);
            }
        }
    }

    @Override
    public void tick() {
        super.tick();

        // Close screen if tricorder is no longer in hand
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            this.onClose();
            return;
        }

        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();

        boolean hasTricorder = mainHand.is(ModItems.TRICORDER.get()) || offHand.is(ModItems.TRICORDER.get());
        if (!hasTricorder) {
            this.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
