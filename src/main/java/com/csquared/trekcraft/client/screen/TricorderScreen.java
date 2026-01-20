package com.csquared.trekcraft.client.screen;

import com.csquared.trekcraft.client.ClientPayloadHandler;
import com.csquared.trekcraft.data.TransporterNetworkSavedData.SignalType;
import com.csquared.trekcraft.network.OpenTricorderScreenPayload;
import com.csquared.trekcraft.network.ScanResultPayload;
import com.csquared.trekcraft.registry.ModItems;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import com.mojang.math.Axis;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TricorderScreen extends Screen {

    private static final int PANEL_WIDTH = 220;
    private static final int PANEL_HEIGHT = 200;
    private static final int BUTTON_WIDTH = 170;
    private static final int BUTTON_HEIGHT = 22;
    private static final int BUTTON_SPACING = 6;

    // Layer navigation for scan results
    private static final int SHOW_ALL = -1;
    private int currentLayer = SHOW_ALL;  // -1 = "Show All", 0-9 = specific layer

    // 3D view rotation (in degrees) - can be adjusted by mouse drag
    private float viewRotationX = 30.0f;   // Vertical tilt (up/down)
    private float viewRotationY = 45.0f;   // Horizontal rotation (right-shoulder behind view)
    private boolean isDragging = false;
    private double lastMouseX, lastMouseY;

    private final int fuel;
    private final boolean hasRoom;
    private final List<OpenTricorderScreenPayload.PadEntry> pads;
    private final List<OpenTricorderScreenPayload.SignalEntry> signals;

    // Scan results data
    private String scanFacing;
    private List<ScanResultPayload.ScannedBlock> scanBlocks;
    private List<ScanResultPayload.ScannedEntity> scanEntities;
    private boolean cameFromMenu = false;  // Track if scan results accessed from menu

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
    public static TricorderScreen createForScanResults(String facing, List<ScanResultPayload.ScannedBlock> blocks,
                                                        List<ScanResultPayload.ScannedEntity> entities) {
        TricorderScreen screen = new TricorderScreen(0, false, List.of(), List.of());
        screen.scanFacing = facing;
        screen.scanBlocks = blocks;
        screen.scanEntities = entities != null ? entities : List.of();
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

        // View Last Scan button - only show if there's a cached scan
        if (ClientPayloadHandler.hasCachedScan()) {
            buttonY += BUTTON_HEIGHT + BUTTON_SPACING;
            addRenderableWidget(LCARSButton.lcarsBuilder(
                    Component.literal("VIEW LAST SCAN"),
                    button -> {
                        scanFacing = ClientPayloadHandler.getCachedFacing();
                        scanBlocks = ClientPayloadHandler.getCachedBlocks();
                        scanEntities = ClientPayloadHandler.getCachedEntities();
                        currentLayer = SHOW_ALL;  // Reset layer view
                        cameFromMenu = true;  // Track that we came from menu
                        currentState = MenuState.SCAN_RESULTS;
                        rebuildButtons();
                    }
            ).bounds(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .colors(LCARSRenderer.PURPLE, LCARSRenderer.LAVENDER)
                    .build());
        }
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

        // Layer navigation controls - positioned above the back button
        int navButtonWidth = 24;
        int navButtonHeight = 16;
        int navY = contentY + contentH - BUTTON_HEIGHT - navButtonHeight - 12;

        // Left button [<] - decrease layer
        int leftBtnX = contentX + 8;
        addRenderableWidget(LCARSButton.lcarsBuilder(
                Component.literal("<"),
                button -> {
                    if (currentLayer == SHOW_ALL) {
                        currentLayer = 9;
                    } else if (currentLayer == 0) {
                        currentLayer = SHOW_ALL;
                    } else {
                        currentLayer--;
                    }
                }
        ).bounds(leftBtnX, navY, navButtonWidth, navButtonHeight)
                .colors(LCARSRenderer.ORANGE, LCARSRenderer.LAVENDER)
                .build());

        // Right button [>] - increase layer
        int rightBtnX = leftBtnX + navButtonWidth + 60;
        addRenderableWidget(LCARSButton.lcarsBuilder(
                Component.literal(">"),
                button -> {
                    if (currentLayer == SHOW_ALL) {
                        currentLayer = 0;
                    } else if (currentLayer == 9) {
                        currentLayer = SHOW_ALL;
                    } else {
                        currentLayer++;
                    }
                }
        ).bounds(rightBtnX, navY, navButtonWidth, navButtonHeight)
                .colors(LCARSRenderer.ORANGE, LCARSRenderer.LAVENDER)
                .build());

        // Back button - go to menu if came from there, otherwise close
        int backY = contentY + contentH - BUTTON_HEIGHT - 4;
        addRenderableWidget(LCARSButton.lcarsBuilder(
                Component.literal("< BACK"),
                button -> {
                    if (cameFromMenu) {
                        cameFromMenu = false;  // Reset flag
                        currentState = MenuState.MAIN_MENU;
                        rebuildButtons();
                    } else {
                        this.onClose();
                    }
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
        // Reserve space for layer controls, direction indicator, and back button
        int renderHeight = contentH - BUTTON_HEIGHT - 50;
        int centerX = contentX + contentW / 2;
        int centerY = contentY + 12 + renderHeight / 2;

        // Block rendering size (how big each block appears)
        int blockSize = 10;
        // Grid spacing (distance between block positions) - larger = more spread out
        int gridSpacing = 8;

        // Draw count and layer indicator at top
        int blockCount = scanBlocks != null ? scanBlocks.size() : 0;
        String countText = blockCount + " ANOMAL" + (blockCount == 1 ? "Y" : "IES");
        String layerText = currentLayer == SHOW_ALL ? "Y=ALL" : "Y=" + currentLayer;
        g.drawString(this.font, countText, contentX + 4, contentY + 2, LCARSRenderer.LAVENDER);
        int layerWidth = this.font.width(layerText);
        g.drawString(this.font, layerText, contentX + contentW - layerWidth - 4, contentY + 2, LCARSRenderer.ORANGE);

        if (scanBlocks == null || scanBlocks.isEmpty()) {
            // Show "no interesting blocks" message
            String msg = "NO ANOMALIES DETECTED";
            int msgWidth = this.font.width(msg);
            g.drawString(this.font, msg, contentX + (contentW - msgWidth) / 2, centerY - 5, LCARSRenderer.LAVENDER);
        } else {
            // Render all blocks in a proper 3D scene (includes player indicator)
            render3DBlockScene(g, centerX, centerY, scanBlocks);
        }

        // Draw layer indicator text between navigation buttons
        int navY = contentY + contentH - BUTTON_HEIGHT - 16 - 12;
        String layerIndicator = currentLayer == SHOW_ALL ? "ALL" : "LAYER " + (currentLayer + 1) + "/10";
        int indicatorX = contentX + 8 + 24 + 4;  // after left button
        g.drawString(this.font, layerIndicator, indicatorX, navY + 4, LCARSRenderer.LAVENDER);

        // Draw direction indicator (scan direction label)
        String dirText = "SCAN: " + scanFacing;
        int dirWidth = this.font.width(dirText);
        g.drawString(this.font, dirText, contentX + contentW - dirWidth - 4, navY + 4, LCARSRenderer.ORANGE);

        // Draw compass rose in top-right corner of content area
        int compassRadius = 18;
        int compassX = contentX + contentW - compassRadius - 6;
        int compassY = contentY + compassRadius + 14;
        renderCompass(g, compassX, compassY, compassRadius);
    }

    /**
     * Transforms scan coordinates to normalized display coordinates.
     * The scan stores coordinates as offsets from minPos (world coords), but the
     * relationship between relX/relZ and "left/right/near/far" varies by facing.
     * This normalizes so that: displayX 0=left, 9=right; displayZ 0=near, 9=far.
     *
     * @return int[3] containing {displayX, displayY, displayZ}
     */
    private int[] transformScanCoords(int relX, int relY, int relZ) {
        if (scanFacing == null) return new int[]{relX, relY, relZ};

        return switch (scanFacing) {
            case "SOUTH" -> new int[]{relX, relY, relZ};                    // Baseline - no change
            case "NORTH" -> new int[]{9 - relX, relY, 9 - relZ};           // Flip both X and Z
            case "EAST" -> new int[]{9 - relZ, relY, relX};                // Swap and flip X
            case "WEST" -> new int[]{relZ, relY, 9 - relX};                // Swap and flip Z
            default -> new int[]{relX, relY, relZ};
        };
    }

    /**
     * Transforms entity scan coordinates (float version).
     */
    private float[] transformScanCoords(float relX, float relY, float relZ) {
        if (scanFacing == null) return new float[]{relX, relY, relZ};

        return switch (scanFacing) {
            case "SOUTH" -> new float[]{relX, relY, relZ};
            case "NORTH" -> new float[]{9 - relX, relY, 9 - relZ};
            case "EAST" -> new float[]{9 - relZ, relY, relX};
            case "WEST" -> new float[]{relZ, relY, 9 - relX};
            default -> new float[]{relX, relY, relZ};
        };
    }

    /**
     * Renders a 2D compass rose that rotates with the view.
     * Shows N/E/S/W labels at correct positions based on current view rotation.
     */
    private void renderCompass(GuiGraphics g, int centerX, int centerY, int radius) {
        // Compass rotates with view only (coordinates are pre-transformed)
        double radians = Math.toRadians(-viewRotationY);

        // Draw background circle (semi-transparent)
        int bgColor = 0x40000000;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx * dx + dy * dy <= radius * radius) {
                    g.fill(centerX + dx, centerY + dy, centerX + dx + 1, centerY + dy + 1, bgColor);
                }
            }
        }

        // Draw center dot
        g.fill(centerX - 1, centerY - 1, centerX + 2, centerY + 2, LCARSRenderer.LAVENDER);

        // Cardinal direction positions (before rotation, N is up/negative Y on screen)
        // N at angle 0 (top), E at 90 (right), S at 180 (bottom), W at 270 (left)
        String[] labels = {"N", "E", "S", "W"};
        int[] colors = {LCARSRenderer.RED, LCARSRenderer.LAVENDER, LCARSRenderer.LAVENDER, LCARSRenderer.LAVENDER};
        double[] baseAngles = {-Math.PI / 2, 0, Math.PI / 2, Math.PI};  // N, E, S, W in screen coords

        int labelRadius = radius - 6;  // Slightly inside the circle edge

        for (int i = 0; i < 4; i++) {
            double angle = baseAngles[i] + radians;
            int lx = centerX + (int) (Math.cos(angle) * labelRadius);
            int ly = centerY + (int) (Math.sin(angle) * labelRadius);

            // Center the label on the position
            int labelWidth = this.font.width(labels[i]);
            g.drawString(this.font, labels[i], lx - labelWidth / 2, ly - 4, colors[i]);
        }

        // Draw tick marks at cardinal directions
        int innerTick = radius - 12;
        int outerTick = radius - 2;
        for (int i = 0; i < 4; i++) {
            double angle = baseAngles[i] + radians;
            int x1 = centerX + (int) (Math.cos(angle) * innerTick);
            int y1 = centerY + (int) (Math.sin(angle) * innerTick);
            int x2 = centerX + (int) (Math.cos(angle) * outerTick);
            int y2 = centerY + (int) (Math.sin(angle) * outerTick);
            drawLine(g, x1, y1, x2, y2, i == 0 ? LCARSRenderer.RED : LCARSRenderer.LAVENDER);
        }
    }

    /**
     * Draws a full 3D wireframe cube showing the extent of the 10x10x10 scan area.
     */
    private void drawWireframeCube(GuiGraphics g, int centerX, int centerY, int gridSpacing) {
        // Colors: front edges brighter, back edges dimmer for depth perception
        int frontColor = 0x80FFFFFF;  // Brighter white
        int backColor = 0x30FFFFFF;   // Dimmer white

        // 8 corners of the cube (relative coords, centered around 0)
        // Y goes from -4.5 (bottom) to 4.5 (top)
        float[][] corners = {
            {-4.5f, -4.5f, -4.5f},  // 0: back-left-bottom
            { 4.5f, -4.5f, -4.5f},  // 1: back-right-bottom
            { 4.5f, -4.5f,  4.5f},  // 2: front-right-bottom
            {-4.5f, -4.5f,  4.5f},  // 3: front-left-bottom
            {-4.5f,  4.5f, -4.5f},  // 4: back-left-top
            { 4.5f,  4.5f, -4.5f},  // 5: back-right-top
            { 4.5f,  4.5f,  4.5f},  // 6: front-right-top
            {-4.5f,  4.5f,  4.5f},  // 7: front-left-top
        };

        // Project each corner to screen coordinates
        int[] screenX = new int[8];
        int[] screenY = new int[8];
        for (int i = 0; i < 8; i++) {
            float rx = corners[i][0];
            float ry = corners[i][1];
            float rz = corners[i][2];
            screenX[i] = centerX + (int) ((rx - rz) * gridSpacing * 0.866f);
            screenY[i] = centerY + (int) ((rx + rz) * gridSpacing * 0.5f - ry * gridSpacing * 0.8f);
        }

        // Draw all 12 edges of the cube
        // Bottom face (4 edges) - back edges dimmer
        drawLine(g, screenX[0], screenY[0], screenX[1], screenY[1], backColor);  // back edge
        drawLine(g, screenX[0], screenY[0], screenX[3], screenY[3], backColor);  // left edge
        drawLine(g, screenX[1], screenY[1], screenX[2], screenY[2], frontColor); // right edge
        drawLine(g, screenX[2], screenY[2], screenX[3], screenY[3], frontColor); // front edge

        // Top face (4 edges)
        drawLine(g, screenX[4], screenY[4], screenX[5], screenY[5], backColor);  // back edge
        drawLine(g, screenX[4], screenY[4], screenX[7], screenY[7], backColor);  // left edge
        drawLine(g, screenX[5], screenY[5], screenX[6], screenY[6], frontColor); // right edge
        drawLine(g, screenX[6], screenY[6], screenX[7], screenY[7], frontColor); // front edge

        // Vertical edges (4 edges)
        drawLine(g, screenX[0], screenY[0], screenX[4], screenY[4], backColor);  // back-left
        drawLine(g, screenX[1], screenY[1], screenX[5], screenY[5], backColor);  // back-right
        drawLine(g, screenX[2], screenY[2], screenX[6], screenY[6], frontColor); // front-right
        drawLine(g, screenX[3], screenY[3], screenX[7], screenY[7], frontColor); // front-left
    }

    /**
     * Draws a highlighted plane at a specific Y-level within the cube.
     */
    private void drawLayerPlane(GuiGraphics g, int centerX, int centerY, int gridSpacing, int layer) {
        // Convert layer (0-9) to centered Y coordinate
        float ry = layer - 4.5f;

        // 4 corners of the plane at this Y level
        float[][] corners = {
            {-4.5f, ry, -4.5f},  // back-left
            { 4.5f, ry, -4.5f},  // back-right
            { 4.5f, ry,  4.5f},  // front-right
            {-4.5f, ry,  4.5f},  // front-left
        };

        int[] screenX = new int[4];
        int[] screenY = new int[4];
        for (int i = 0; i < 4; i++) {
            float rx = corners[i][0];
            float cy = corners[i][1];
            float rz = corners[i][2];
            screenX[i] = centerX + (int) ((rx - rz) * gridSpacing * 0.866f);
            screenY[i] = centerY + (int) ((rx + rz) * gridSpacing * 0.5f - cy * gridSpacing * 0.8f);
        }

        // Draw the layer plane outline in a highlight color
        int highlightColor = 0x80FFCC00;  // Semi-transparent gold/yellow
        drawLine(g, screenX[0], screenY[0], screenX[1], screenY[1], highlightColor);
        drawLine(g, screenX[1], screenY[1], screenX[2], screenY[2], highlightColor);
        drawLine(g, screenX[2], screenY[2], screenX[3], screenY[3], highlightColor);
        drawLine(g, screenX[3], screenY[3], screenX[0], screenY[0], highlightColor);
    }

    /**
     * Renders all scanned blocks in a proper 3D scene using BlockRenderDispatcher.
     * All blocks share the same 3D transformation, so they align correctly.
     */
    private void render3DBlockScene(GuiGraphics g, int centerX, int centerY,
                                     List<ScanResultPayload.ScannedBlock> blocks) {
        Minecraft mc = Minecraft.getInstance();
        BlockRenderDispatcher blockRenderer = mc.getBlockRenderer();
        EntityRenderDispatcher entityRenderer = mc.getEntityRenderDispatcher();

        // Sort blocks for correct depth ordering (back-to-front in isometric view)
        // Lower x + z values render first (back), higher y values render later (on top)
        List<ScanResultPayload.ScannedBlock> sorted = new ArrayList<>(blocks);
        sorted.sort(Comparator.comparingInt(
                (ScanResultPayload.ScannedBlock b) -> b.x() + b.z() - b.y()
        ));

        // Scale for each block in the scene
        float blockScale = 7.0f;

        PoseStack poseStack = g.pose();
        poseStack.pushPose();

        // Move to center of render area
        poseStack.translate(centerX, centerY, 100);

        // Apply rotation - can be adjusted by mouse drag
        // Coordinates are pre-transformed by transformScanCoords() so no facing offset needed
        poseStack.mulPose(new Quaternionf().rotationX((float) Math.toRadians(viewRotationX)));
        poseStack.mulPose(new Quaternionf().rotationY((float) Math.toRadians(viewRotationY)));

        // Scale the entire scene
        poseStack.scale(blockScale, -blockScale, blockScale);  // Flip Y for screen coords

        // Get buffer source for rendering
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        // Draw faint XYZ gridlines for the 10x10x10 scan area
        draw3DGridlines(g, poseStack);

        // Render scanned blocks
        for (ScanResultPayload.ScannedBlock block : sorted) {
            // Skip blocks not on current layer when filtering
            boolean isActiveLayer = (currentLayer == SHOW_ALL || block.y() == currentLayer);
            if (!isActiveLayer && currentLayer != SHOW_ALL) {
                // Skip ghost blocks for now
                continue;
            }

            try {
                ResourceLocation blockLoc = ResourceLocation.parse(block.blockId());
                net.minecraft.world.level.block.Block mcBlock = BuiltInRegistries.BLOCK.get(blockLoc);
                BlockState blockState = mcBlock.defaultBlockState();

                // Transform coordinates based on scan facing direction
                int[] transformed = transformScanCoords(block.x(), block.y(), block.z());

                // Position at the block's location (centered around origin)
                // Blocks are at positions 0-9, center at 4.5
                float bx = transformed[0] - 4.5f;
                float by = transformed[1] - 4.5f;
                float bz = transformed[2] - 4.5f;

                renderBlockAt(poseStack, blockRenderer, bufferSource, blockState, bx, by, bz);
            } catch (Exception e) {
                // Skip blocks that fail to render
            }
        }

        // Flush block rendering
        bufferSource.endBatch();

        // Render player indicator at the back-center of the scan area
        // Player's feet are at relY=5 (middle of scan), which is Y=0.5 in centered coords
        // Player is 1 block behind the scan area's near edge (Z = -5.5 in centered coords)
        if (mc.player != null) {
            float playerX = 0f;
            float playerFeetY = 0.5f;
            float playerZ = -5.5f;

            // Draw line of sight indicator (3 blocks extending from eye level into scan area)
            float eyeHeight = 1.6f;
            int losColor = 0xFFFFAA00;  // Orange color for line of sight
            draw3DLine(g, poseStack, playerX, playerFeetY + eyeHeight, playerZ,
                                     playerX, playerFeetY + eyeHeight, playerZ + 3.0f, losColor);

            poseStack.pushPose();
            poseStack.translate(playerX, playerFeetY, playerZ);
            // Undo scene's -Y scale and apply standard GUI entity transforms
            // scale(1, -1, -1) undoes Y flip and flips Z for GUI rendering
            // 180° Z rotation completes the GUI entity flip
            poseStack.scale(1.0f, -1.0f, -1.0f);
            poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
            // Make player face into scan area (+Z direction)
            // Compensate for coordinate normalization based on scan direction
            float playerYRotation = switch (scanFacing) {
                case "SOUTH" -> 180.0F;
                case "NORTH" -> 0.0F;
                case "EAST" -> 90.0F;
                case "WEST" -> -90.0F;
                default -> 180.0F;
            };
            poseStack.mulPose(Axis.YP.rotationDegrees(playerYRotation));

            entityRenderer.render(mc.player, 0, 0, 0, 0, 1.0f, poseStack, bufferSource, LightTexture.FULL_BRIGHT);
            bufferSource.endBatch();
            poseStack.popPose();
        }

        // Render scanned entities (mobs)
        if (scanEntities != null && !scanEntities.isEmpty()) {
            for (ScanResultPayload.ScannedEntity scannedEntity : scanEntities) {
                // Check layer filtering - convert entity Y to layer (0-9)
                int entityLayer = (int) scannedEntity.y();
                if (entityLayer < 0) entityLayer = 0;
                if (entityLayer > 9) entityLayer = 9;

                boolean isActiveLayer = (currentLayer == SHOW_ALL || entityLayer == currentLayer);
                if (!isActiveLayer) {
                    continue;  // Skip entities not on current layer
                }

                try {
                    // Create entity from type
                    ResourceLocation entityLoc = ResourceLocation.parse(scannedEntity.entityType());
                    EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(entityLoc);
                    Entity entity = entityType.create(mc.level);

                    if (entity != null) {
                        // Transform coordinates based on scan facing direction
                        float[] transformed = transformScanCoords(scannedEntity.x(), scannedEntity.y(), scannedEntity.z());

                        // Position in scan area (centered coords)
                        float ex = transformed[0] - 4.5f;
                        float ey = transformed[1] - 4.5f;
                        float ez = transformed[2] - 4.5f;

                        poseStack.pushPose();
                        // Position entity in scene
                        poseStack.translate(ex, ey, ez);

                        // Undo scene's -Y scale and apply standard GUI entity transforms
                        poseStack.scale(1.0f, -1.0f, -1.0f);
                        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
                        // Apply entity's yaw rotation
                        poseStack.mulPose(Axis.YP.rotationDegrees(scannedEntity.yaw()));

                        entityRenderer.render(entity, 0, 0, 0, 0, 1.0f, poseStack, bufferSource, LightTexture.FULL_BRIGHT);
                        bufferSource.endBatch();
                        poseStack.popPose();

                        // Discard the temporary entity
                        entity.discard();
                    }
                } catch (Exception e) {
                    // Skip entities that fail to render
                }
            }
        }

        poseStack.popPose();
    }

    /**
     * Helper to render a single block at a specific position in the 3D scene.
     */
    private void renderBlockAt(PoseStack poseStack, BlockRenderDispatcher blockRenderer,
                                MultiBufferSource bufferSource, BlockState blockState,
                                float x, float y, float z) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        blockRenderer.renderSingleBlock(
                blockState,
                poseStack,
                bufferSource,
                LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY
        );
        poseStack.popPose();
    }

    /**
     * Draws faint XYZ gridlines for the 10x10x10 scan area.
     */
    private void draw3DGridlines(GuiGraphics g, PoseStack poseStack) {
        // Grid bounds in centered coordinates (-4.5 to 4.5, but we draw at -5 to 5 for full cube)
        float min = -5.0f;
        float max = 5.0f;

        // Faint grid color
        int gridColor = 0x30FFFFFF;  // Semi-transparent white

        // Draw the 12 edges of the outer bounding box
        // Bottom face edges
        draw3DLine(g, poseStack, min, min, min, max, min, min, gridColor);
        draw3DLine(g, poseStack, min, min, min, min, min, max, gridColor);
        draw3DLine(g, poseStack, max, min, max, max, min, min, gridColor);
        draw3DLine(g, poseStack, max, min, max, min, min, max, gridColor);

        // Top face edges
        draw3DLine(g, poseStack, min, max, min, max, max, min, gridColor);
        draw3DLine(g, poseStack, min, max, min, min, max, max, gridColor);
        draw3DLine(g, poseStack, max, max, max, max, max, min, gridColor);
        draw3DLine(g, poseStack, max, max, max, min, max, max, gridColor);

        // Vertical edges
        draw3DLine(g, poseStack, min, min, min, min, max, min, gridColor);
        draw3DLine(g, poseStack, max, min, min, max, max, min, gridColor);
        draw3DLine(g, poseStack, min, min, max, min, max, max, gridColor);
        draw3DLine(g, poseStack, max, min, max, max, max, max, gridColor);

        // Draw axis lines through center for reference (colored)
        int xAxisColor = 0x60FF0000;  // Red for X
        int yAxisColor = 0x6000FF00;  // Green for Y
        int zAxisColor = 0x600000FF;  // Blue for Z

        // X axis (horizontal, left-right)
        draw3DLine(g, poseStack, min, 0, 0, max, 0, 0, xAxisColor);
        // Y axis (vertical)
        draw3DLine(g, poseStack, 0, min, 0, 0, max, 0, yAxisColor);
        // Z axis (depth, front-back)
        draw3DLine(g, poseStack, 0, 0, min, 0, 0, max, zAxisColor);

        // Draw horizontal grid lines at each Y level if viewing a specific layer
        if (currentLayer != SHOW_ALL) {
            float layerY = currentLayer - 4.5f;
            int layerColor = 0x60FFCC00;  // Gold highlight

            // Draw a grid at the current layer
            draw3DLine(g, poseStack, min, layerY, min, max, layerY, min, layerColor);
            draw3DLine(g, poseStack, min, layerY, max, max, layerY, max, layerColor);
            draw3DLine(g, poseStack, min, layerY, min, min, layerY, max, layerColor);
            draw3DLine(g, poseStack, max, layerY, min, max, layerY, max, layerColor);
        }
    }

    /**
     * Draws a 3D line between two points in the transformed coordinate space.
     */
    private void draw3DLine(GuiGraphics g, PoseStack poseStack,
                            float x1, float y1, float z1, float x2, float y2, float z2, int color) {
        // Use the pose stack to transform the line coordinates
        var matrix = poseStack.last().pose();

        float a = ((color >> 24) & 0xFF) / 255.0f;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float gr = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        var bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        var buffer = bufferSource.getBuffer(net.minecraft.client.renderer.RenderType.lines());

        // Calculate direction for line normals
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 0) {
            dx /= len;
            dy /= len;
            dz /= len;
        }

        buffer.addVertex(matrix, x1, y1, z1).setColor(r, gr, b, a).setNormal(poseStack.last(), dx, dy, dz);
        buffer.addVertex(matrix, x2, y2, z2).setColor(r, gr, b, a).setNormal(poseStack.last(), dx, dy, dz);

        bufferSource.endBatch(net.minecraft.client.renderer.RenderType.lines());
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
     * Renders a block as a semi-transparent "ghost" to show spatial context for other layers.
     */
    private void renderGhostBlock(GuiGraphics g, ScanResultPayload.ScannedBlock block,
                                   int centerX, int centerY, int gridSpacing, int blockSize) {
        float rx = block.x() - 4.5f;
        float ry = block.y() - 4.5f;
        float rz = block.z() - 4.5f;

        int screenX = centerX + (int) ((rx - rz) * gridSpacing * 0.866f);
        int screenY = centerY + (int) ((rx + rz) * gridSpacing * 0.5f - ry * gridSpacing * 0.8f);

        // Render at reduced scale (0.6x) with fallback coloring for ghosting effect
        int ghostSize = (int) (blockSize * 0.6f);
        int ghostColor = getFallbackColor(block.blockId());
        // Make the color semi-transparent
        ghostColor = (ghostColor & 0x00FFFFFF) | 0x40000000;  // 25% alpha
        drawFallbackBlock(g, screenX, screenY, ghostSize, ghostColor);
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Start drag rotation when clicking in scan results view
        if (currentState == MenuState.SCAN_RESULTS && button == 0) {
            // Check if click is in the render area (not on buttons)
            int[] contentBounds = LCARSRenderer.getContentBounds(panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT);
            int contentY = contentBounds[1];
            int contentH = contentBounds[3];
            int renderAreaBottom = contentY + contentH - BUTTON_HEIGHT - 50;

            if (mouseY < renderAreaBottom) {
                isDragging = true;
                lastMouseX = mouseX;
                lastMouseY = mouseY;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            isDragging = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDragging && currentState == MenuState.SCAN_RESULTS) {
            // Update rotation based on mouse movement
            float sensitivity = 0.5f;
            viewRotationY += (float) dragX * sensitivity;
            viewRotationX += (float) dragY * sensitivity;

            // Clamp vertical rotation to avoid flipping
            viewRotationX = Math.max(-90, Math.min(90, viewRotationX));

            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }
}
