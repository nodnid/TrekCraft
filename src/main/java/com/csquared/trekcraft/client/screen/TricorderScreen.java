package com.csquared.trekcraft.client.screen;

import com.csquared.trekcraft.data.TransporterNetworkSavedData.SignalType;
import com.csquared.trekcraft.network.OpenTricorderScreenPayload;
import com.csquared.trekcraft.network.ScanResultPayload;
import com.csquared.trekcraft.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
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

    // Scan results data
    private String scanFacing;
    private List<ScanResultPayload.ScannedBlock> scanBlocks;

    private MenuState currentState = MenuState.MAIN_MENU;
    private int panelLeft;
    private int panelTop;

    private enum MenuState {
        MAIN_MENU,
        PAD_LIST,
        SIGNAL_LIST,
        SCAN_RESULTS
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

    /**
     * Factory method to create screen in scan results mode.
     */
    public static TricorderScreen createForScanResults(String facing, List<ScanResultPayload.ScannedBlock> blocks) {
        TricorderScreen screen = new TricorderScreen(0, false, List.of(), List.of());
        screen.scanFacing = facing;
        screen.scanBlocks = blocks;
        screen.currentState = MenuState.SCAN_RESULTS;
        return screen;
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
            case SCAN_RESULTS -> buildScanResults();
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

    private void buildScanResults() {
        int[] contentBounds = LCARSRenderer.getContentBounds(panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT);
        int contentX = contentBounds[0];
        int contentY = contentBounds[1];
        int contentW = contentBounds[2];
        int contentH = contentBounds[3];

        int buttonX = contentX + (contentW - BUTTON_WIDTH) / 2;

        // Back button at bottom - closes the screen since we came from command, not menu
        int backY = contentY + contentH - BUTTON_HEIGHT - 4;
        addRenderableWidget(LCARSButton.lcarsBuilder(
                Component.literal("< BACK"),
                button -> this.onClose()
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
            case SCAN_RESULTS -> "SCAN RESULTS";
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

        // Draw scan results with isometric 3D visualization
        if (currentState == MenuState.SCAN_RESULTS) {
            renderScanResults(guiGraphics, contentX, contentY, contentW, contentH);
        }
    }

    /**
     * Renders the scan results as an isometric 3D view of the scanned area.
     */
    private void renderScanResults(GuiGraphics g, int contentX, int contentY, int contentW, int contentH) {
        // Reserve space for direction indicator and back button
        int renderHeight = contentH - BUTTON_HEIGHT - 30;
        int centerX = contentX + contentW / 2;
        int centerY = contentY + renderHeight / 2;

        // Block rendering size (how big each block appears)
        int blockSize = 10;
        // Grid spacing (distance between block positions) - larger = more spread out
        int gridSpacing = 8;

        // Draw count of blocks found at top
        int blockCount = scanBlocks != null ? scanBlocks.size() : 0;
        String countText = blockCount + " ANOMAL" + (blockCount == 1 ? "Y" : "IES");
        int countWidth = this.font.width(countText);
        g.drawString(this.font, countText, contentX + (contentW - countWidth) / 2, contentY + 2, LCARSRenderer.LAVENDER);

        if (scanBlocks == null || scanBlocks.isEmpty()) {
            // Show "no interesting blocks" message
            String msg = "NO ANOMALIES DETECTED";
            int msgWidth = this.font.width(msg);
            g.drawString(this.font, msg, contentX + (contentW - msgWidth) / 2, centerY - 5, LCARSRenderer.LAVENDER);
        } else {
            // Draw a subtle grid/bounding box to show scan area extent
            drawScanAreaOutline(g, centerX, centerY, gridSpacing);

            // Draw player position indicator at the back-center of the scan area
            // Player is at relative position (4.5, 4.5, -0.5) - behind the scan area
            int playerScreenX = centerX + (int)((4.5f - (-0.5f)) * gridSpacing * 0.866f);
            int playerScreenY = centerY + (int)((4.5f + (-0.5f)) * gridSpacing * 0.5f);
            drawPlayerIndicator(g, playerScreenX, playerScreenY + 20);

            // Sort blocks for correct depth ordering (back-to-front)
            // Lower x + z values render first (back), higher y values render later (on top)
            List<ScanResultPayload.ScannedBlock> sorted = new ArrayList<>(scanBlocks);
            sorted.sort(Comparator.comparingInt(
                    (ScanResultPayload.ScannedBlock b) -> b.x() + b.z() - b.y()
            ));

            // Render each block in isometric projection
            for (ScanResultPayload.ScannedBlock block : sorted) {
                // Convert from relative coords (0-9) to centered coords (-4.5 to 4.5)
                float rx = block.x() - 4.5f;
                float ry = block.y() - 4.5f;
                float rz = block.z() - 4.5f;

                // Isometric projection with separate grid spacing
                // X goes right-down, Z goes left-down, Y goes straight up
                int screenX = centerX + (int) ((rx - rz) * gridSpacing * 0.866f);
                int screenY = centerY + (int) ((rx + rz) * gridSpacing * 0.5f - ry * gridSpacing * 0.8f);

                // Render the block
                renderIsometricBlock(g, block.blockId(), screenX, screenY, blockSize);
            }
        }

        // Draw direction indicator at bottom of render area
        String dirText = "[ " + scanFacing + " ]";
        int dirWidth = this.font.width(dirText);
        int dirY = contentY + renderHeight + 5;
        g.drawString(this.font, dirText, contentX + (contentW - dirWidth) / 2, dirY, LCARSRenderer.ORANGE);
    }

    /**
     * Draws a subtle outline showing the extent of the scan area.
     */
    private void drawScanAreaOutline(GuiGraphics g, int centerX, int centerY, int gridSpacing) {
        int color = 0x40FFFFFF; // Semi-transparent white

        // Calculate corner positions of the 10x10x10 scan cube (at y=0 level)
        // Corners at (0,0,0), (9,0,0), (0,0,9), (9,0,9) relative, converted to centered
        float[][] corners = {
            {-4.5f, 0, -4.5f},  // Back-left
            {4.5f, 0, -4.5f},   // Back-right
            {4.5f, 0, 4.5f},    // Front-right
            {-4.5f, 0, 4.5f}    // Front-left
        };

        int[] screenX = new int[4];
        int[] screenY = new int[4];

        for (int i = 0; i < 4; i++) {
            screenX[i] = centerX + (int) ((corners[i][0] - corners[i][2]) * gridSpacing * 0.866f);
            screenY[i] = centerY + (int) ((corners[i][0] + corners[i][2]) * gridSpacing * 0.5f);
        }

        // Draw the diamond outline
        drawLine(g, screenX[0], screenY[0], screenX[1], screenY[1], color);
        drawLine(g, screenX[1], screenY[1], screenX[2], screenY[2], color);
        drawLine(g, screenX[2], screenY[2], screenX[3], screenY[3], color);
        drawLine(g, screenX[3], screenY[3], screenX[0], screenY[0], color);
    }

    /**
     * Draws a simple line between two points.
     */
    private void drawLine(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int steps = Math.max(dx, dy);
        if (steps == 0) {
            g.fill(x1, y1, x1 + 1, y1 + 1, color);
            return;
        }
        for (int i = 0; i <= steps; i++) {
            int x = x1 + (x2 - x1) * i / steps;
            int y = y1 + (y2 - y1) * i / steps;
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    /**
     * Draws a player position indicator.
     */
    private void drawPlayerIndicator(GuiGraphics g, int x, int y) {
        // Draw a small triangle pointing into the scan area
        int color = LCARSRenderer.ORANGE;
        // Simple arrow/chevron pointing up (into the scan)
        g.fill(x - 1, y + 4, x + 2, y + 5, color);
        g.fill(x - 2, y + 5, x + 3, y + 6, color);
        g.fill(x - 3, y + 6, x + 4, y + 7, color);

        // Label
        String label = "YOU";
        int labelWidth = this.font.width(label);
        g.drawString(this.font, label, x - labelWidth / 2, y + 9, color);
    }

    /**
     * Renders a single block as a 3D isometric block using Minecraft's item renderer.
     */
    private void renderIsometricBlock(GuiGraphics g, String blockId, int x, int y, int size) {
        try {
            // Get the block from registry and create an ItemStack
            ResourceLocation blockLoc = ResourceLocation.parse(blockId);
            net.minecraft.world.level.block.Block block = BuiltInRegistries.BLOCK.get(blockLoc);
            ItemStack blockStack = new ItemStack(block);

            // Scale factor - default item rendering is 16x16
            float scale = size / 16.0f;

            // Use pose stack to position and scale the block
            g.pose().pushPose();

            // Move to the target position (centered)
            g.pose().translate(x - size / 2, y - size / 2, 0);

            // Scale up/down as needed
            g.pose().scale(scale, scale, scale);

            // Render the block as a 3D item (this gives the isometric look)
            g.renderFakeItem(blockStack, 0, 0);

            g.pose().popPose();

        } catch (Exception e) {
            // Fallback: draw colored block for unknown blocks
            int fallbackColor = getFallbackColor(blockId);
            drawFallbackBlock(g, x, y, size, fallbackColor);
        }
    }

    /**
     * Gets a characteristic color for a block type (used as fallback).
     */
    private int getFallbackColor(String blockId) {
        if (blockId.contains("diamond")) return 0xFF5DECF5;
        if (blockId.contains("emerald")) return 0xFF41F384;
        if (blockId.contains("gold")) return 0xFFFAD64A;
        if (blockId.contains("iron")) return 0xFFD8AF93;
        if (blockId.contains("copper")) return 0xFFE77C56;
        if (blockId.contains("coal")) return 0xFF393939;
        if (blockId.contains("redstone")) return 0xFFFF0000;
        if (blockId.contains("lapis")) return 0xFF345EC3;
        if (blockId.contains("quartz")) return 0xFFE3DDD5;
        if (blockId.contains("debris") || blockId.contains("ancient")) return 0xFF6B4837;
        if (blockId.contains("spawner")) return 0xFF2A4A6A;
        if (blockId.contains("chest")) return 0xFFA06A2C;
        if (blockId.contains("barrel")) return 0xFF8B6914;
        return 0xFFAAAAAA; // Default gray
    }

    /**
     * Draws a fallback colored block when texture loading fails.
     */
    private void drawFallbackBlock(GuiGraphics g, int x, int y, int size, int color) {
        int halfSize = size / 2;
        int halfW = (int) (size * 0.866f);

        int topColor = color;
        int leftColor = darkenColor(color, 0.6f);
        int rightColor = darkenColor(color, 0.4f);

        // Draw top diamond
        for (int row = 0; row < halfSize; row++) {
            int width = (int) (halfW * (row + 1) / (float) halfSize);
            g.fill(x - width, y - halfSize + row, x + width, y - halfSize + row + 1, topColor);
        }
        for (int row = 0; row < halfSize; row++) {
            int width = (int) (halfW * (halfSize - row) / (float) halfSize);
            g.fill(x - width, y + row, x + width, y + row + 1, topColor);
        }

        // Draw left face
        for (int row = 0; row < size; row++) {
            int offset = (row < halfSize) ? row : size - row - 1;
            int leftX = x - halfW + (int) (offset * 0.5f);
            g.fill(leftX, y + row, x, y + row + 1, leftColor);
        }

        // Draw right face
        for (int row = 0; row < size; row++) {
            int offset = (row < halfSize) ? row : size - row - 1;
            int rightX = x + halfW - (int) (offset * 0.5f);
            g.fill(x, y + row, rightX, y + row + 1, rightColor);
        }
    }

    /**
     * Darkens a color by a brightness factor.
     */
    private int darkenColor(int color, float brightness) {
        int r = (int) (((color >> 16) & 0xFF) * brightness);
        int g = (int) (((color >> 8) & 0xFF) * brightness);
        int b = (int) ((color & 0xFF) * brightness);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
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
