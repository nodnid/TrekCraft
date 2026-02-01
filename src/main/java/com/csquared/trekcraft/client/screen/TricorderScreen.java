package com.csquared.trekcraft.client.screen;

import com.csquared.trekcraft.client.ClientPayloadHandler;
import com.csquared.trekcraft.data.TransporterNetworkSavedData.SignalType;
import com.csquared.trekcraft.data.TricorderData;
import com.csquared.trekcraft.network.OpenTricorderScreenPayload;
import com.csquared.trekcraft.network.ScanResultPayload;
import com.csquared.trekcraft.network.mission.OpenMissionBoardPayload;
import com.csquared.trekcraft.network.mission.OpenMissionInfoPayload;
import com.csquared.trekcraft.network.mission.OpenMissionLogPayload;
import com.csquared.trekcraft.network.mission.OpenServiceRecordPayload;
import com.csquared.trekcraft.registry.ModDataComponents;
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

    private static final int PANEL_WIDTH = 250;
    private static final int PANEL_HEIGHT = 220;
    private static final int BUTTON_WIDTH = 170;
    private static final int BUTTON_HEIGHT = 22;
    private static final int BUTTON_SPACING = 6;
    private static final int BUTTON_Y_OFFSET = -16;  // Offset from contentY to position buttons correctly
    private static final int MAX_VISIBLE_BUTTONS = 6;  // Max buttons before scrolling kicks in

    // Layer navigation for scan results
    private static final int SHOW_ALL = -1;
    private static final int FEET_LEVEL_LAYER = 5;  // Internal Y layer at player feet level
    private int currentLayer = SHOW_ALL;  // -1 = "Show All", 0-9 = specific Y layer
    private int currentZSlice = SHOW_ALL;  // -1 = "Show All", 0-9 = specific Z slice (depth)

    // 3D view rotation (in degrees) - can be adjusted by mouse drag
    // Default view: over the player's shoulder looking into the scan area
    private float viewRotationX = -25.0f;  // Looking down from above
    private float viewRotationY = 200.0f;  // Looking from behind, rotated slightly to one side
    private boolean isDragging = false;
    private double lastMouseX, lastMouseY;

    private final int fuel;
    private final int slips;
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

    // Scroll offsets for lists
    private int mainMenuScrollOffset = 0;
    private int padScrollOffset = 0;
    private int signalScrollOffset = 0;
    private int missionBoardScrollOffset = 0;
    private int missionLogScrollOffset = 0;

    // Mission data (populated by payloads)
    private String playerRankTitle = "Crewman";
    private long playerXp = 0;
    private long xpToNextRank = 100;
    private int activeMissionCount = 0;
    private int completedMissionCount = 0;
    private int totalKills = 0;
    private int totalScans = 0;
    private int biomesExplored = 0;
    private List<OpenMissionBoardPayload.MissionSummary> missionBoardData = new ArrayList<>();
    private List<OpenMissionLogPayload.ActiveMissionEntry> missionLogData = new ArrayList<>();
    private OpenMissionInfoPayload currentMissionInfo = null;
    private MenuState previousMissionState = MenuState.MISSION_LOG;  // Track where we came from
    private int missionInfoScrollOffset = 0;  // Scroll offset for mission info screen
    private int missionInfoContentHeight = 0;  // Total height of mission info content

    private enum MenuState {
        MAIN_MENU,
        PAD_LIST,
        SIGNAL_LIST,
        SCAN_RESULTS,
        STARFLEET_COMMAND,
        MISSION_BOARD,
        MISSION_LOG,
        MISSION_INFO,
        SERVICE_RECORD
    }

    public TricorderScreen(int fuel, int slips, boolean hasRoom,
                           List<OpenTricorderScreenPayload.PadEntry> pads,
                           List<OpenTricorderScreenPayload.SignalEntry> signals) {
        super(Component.translatable("screen.trekcraft.tricorder"));
        this.fuel = fuel;
        this.slips = slips;
        this.hasRoom = hasRoom;
        this.pads = pads;
        this.signals = signals;
    }

    /**
     * Factory method to create screen in scan results mode.
     */
    public static TricorderScreen createForScanResults(String facing, List<ScanResultPayload.ScannedBlock> blocks,
                                                        List<ScanResultPayload.ScannedEntity> entities) {
        TricorderScreen screen = new TricorderScreen(0, 0, false, List.of(), List.of());
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
            case STARFLEET_COMMAND -> buildStarfleetCommand();
            case MISSION_BOARD -> buildMissionBoard();
            case MISSION_LOG -> buildMissionLog();
            case MISSION_INFO -> buildMissionInfo();
            case SERVICE_RECORD -> buildServiceRecord();
        }
    }

    private void buildMainMenu() {
        int[] contentBounds = LCARSRenderer.getContentBounds(panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT);
        int contentX = contentBounds[0];
        int contentY = contentBounds[1];
        int contentW = contentBounds[2];

        int buttonX = contentX + (contentW - BUTTON_WIDTH) / 2;
        int buttonY = contentY + BUTTON_Y_OFFSET;

        // Build list of all menu items
        List<MenuButton> menuButtons = new ArrayList<>();

        menuButtons.add(new MenuButton(
                Component.literal("TRANSPORT TO PAD"),
                button -> { currentState = MenuState.PAD_LIST; rebuildButtons(); },
                LCARSRenderer.PEACH, LCARSRenderer.ORANGE
        ));

        menuButtons.add(new MenuButton(
                Component.literal("TRANSPORT TO SIGNAL"),
                button -> { currentState = MenuState.SIGNAL_LIST; rebuildButtons(); },
                LCARSRenderer.PEACH, LCARSRenderer.ORANGE
        ));

        menuButtons.add(new MenuButton(
                Component.literal("SCAN AREA"),
                button -> executeCommand("trek scan"),
                LCARSRenderer.LAVENDER, LCARSRenderer.PURPLE
        ));

        if (ClientPayloadHandler.hasCachedScan()) {
            menuButtons.add(new MenuButton(
                    Component.literal("VIEW LAST SCAN"),
                    button -> {
                        scanFacing = ClientPayloadHandler.getCachedFacing();
                        scanBlocks = ClientPayloadHandler.getCachedBlocks();
                        scanEntities = ClientPayloadHandler.getCachedEntities();
                        currentLayer = SHOW_ALL;
                        currentZSlice = SHOW_ALL;
                        cameFromMenu = true;
                        currentState = MenuState.SCAN_RESULTS;
                        rebuildButtons();
                    },
                    LCARSRenderer.PURPLE, LCARSRenderer.LAVENDER
            ));
        }

        menuButtons.add(new MenuButton(
                Component.literal("RENAME TRICORDER"),
                button -> openRenameTricorderScreen(),
                LCARSRenderer.LAVENDER, LCARSRenderer.PURPLE
        ));

        menuButtons.add(new MenuButton(
                Component.literal("STARFLEET RANK"),
                button -> executeCommand("trek contribution"),
                LCARSRenderer.PEACH, LCARSRenderer.ORANGE
        ));

        // Starfleet Command submenu
        menuButtons.add(new MenuButton(
                Component.literal("STARFLEET COMMAND"),
                button -> {
                    // Request mission data from server before opening menu
                    executeCommandNoClose("trek starfleet rank");
                    currentState = MenuState.STARFLEET_COMMAND;
                    rebuildButtons();
                },
                LCARSRenderer.BLUE, LCARSRenderer.LAVENDER
        ));

        // Render buttons with scrolling support
        renderScrollableButtons(menuButtons, buttonX, buttonY, mainMenuScrollOffset,
                offset -> { mainMenuScrollOffset = offset; rebuildButtons(); }, true);

        // Close button in bottom bar
        addCloseButton();
    }

    /**
     * Renders a scrollable list of buttons with consistent positioning across all menus.
     * @param buttons List of buttons to render
     * @param buttonX X position for buttons
     * @param buttonY Starting Y position for buttons
     * @param scrollOffset Current scroll offset
     * @param onScrollChange Callback when scroll offset changes
     * @param showScrollInSidebar If true, shows scroll buttons in sidebar; if false, uses default position
     */
    private void renderScrollableButtons(List<MenuButton> buttons, int buttonX, int buttonY,
                                          int scrollOffset, java.util.function.IntConsumer onScrollChange,
                                          boolean showScrollInSidebar) {
        int totalButtons = buttons.size();
        boolean needsScroll = totalButtons > MAX_VISIBLE_BUTTONS;

        // Clamp scroll offset
        int maxOffset = Math.max(0, totalButtons - MAX_VISIBLE_BUTTONS);
        int clampedOffset = Math.max(0, Math.min(scrollOffset, maxOffset));

        // Add scroll buttons if needed
        if (needsScroll) {
            int navButtonSize = 16;
            int navX = panelLeft + 4;
            int navY = panelTop + 116;  // Below currency display

            addRenderableWidget(LCARSButton.lcarsBuilder(
                    Component.literal("^"),
                    button -> { if (clampedOffset > 0) onScrollChange.accept(clampedOffset - 1); }
            ).bounds(navX, navY, navButtonSize, navButtonSize)
                    .colors(LCARSRenderer.ORANGE, LCARSRenderer.LAVENDER)
                    .build());

            addRenderableWidget(LCARSButton.lcarsBuilder(
                    Component.literal("v"),
                    button -> { if (clampedOffset < maxOffset) onScrollChange.accept(clampedOffset + 1); }
            ).bounds(navX + navButtonSize + 2, navY, navButtonSize, navButtonSize)
                    .colors(LCARSRenderer.ORANGE, LCARSRenderer.LAVENDER)
                    .build());
        }

        // Display visible buttons
        int endIndex = Math.min(clampedOffset + MAX_VISIBLE_BUTTONS, totalButtons);
        for (int i = clampedOffset; i < endIndex; i++) {
            MenuButton mb = buttons.get(i);
            addRenderableWidget(LCARSButton.lcarsBuilder(mb.text, mb.action)
                    .bounds(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .colors(mb.normalColor, mb.hoverColor)
                    .build());
            buttonY += BUTTON_HEIGHT + BUTTON_SPACING;
        }
    }

    /**
     * Helper record for building menu buttons dynamically.
     */
    private record MenuButton(Component text, net.minecraft.client.gui.components.Button.OnPress action,
                               int normalColor, int hoverColor) {
    }

    /**
     * Returns the current number of buttons in the main menu.
     */
    private int getMainMenuButtonCount() {
        int count = 6;  // Base buttons: Transport to Pad, Transport to Signal, Scan, Rename, Starfleet Rank, Starfleet Command
        if (ClientPayloadHandler.hasCachedScan()) {
            count++;  // View Last Scan
        }
        return count;
    }

    /**
     * Opens the naming screen for the currently held tricorder.
     */
    private void openRenameTricorderScreen() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        // Find the tricorder in hand
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(ModItems.TRICORDER.get())) {
            stack = player.getOffhandItem();
        }

        if (stack.is(ModItems.TRICORDER.get())) {
            TricorderData data = stack.get(ModDataComponents.TRICORDER_DATA.get());
            if (data != null) {
                String currentName = data.getDisplayName();
                mc.setScreen(NamingScreen.forTricorder(data.tricorderId(), currentName));
            }
        }
    }

    private void buildPadList() {
        int[] contentBounds = LCARSRenderer.getContentBounds(panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT);
        int contentX = contentBounds[0];
        int contentY = contentBounds[1];
        int contentW = contentBounds[2];

        int buttonX = contentX + (contentW - BUTTON_WIDTH) / 2;
        int buttonY = contentY + BUTTON_Y_OFFSET;

        // Build pad buttons
        List<MenuButton> padButtons = new ArrayList<>();
        for (OpenTricorderScreenPayload.PadEntry pad : pads) {
            BlockPos pos = pad.pos();
            padButtons.add(new MenuButton(
                    Component.literal(pad.name().toUpperCase()),
                    button -> executeCommand("trek transport toPad " + pos.getX() + " " + pos.getY() + " " + pos.getZ()),
                    LCARSRenderer.PEACH, LCARSRenderer.ORANGE
            ));
        }

        // Clamp scroll offset
        int maxOffset = Math.max(0, padButtons.size() - MAX_VISIBLE_BUTTONS);
        padScrollOffset = Math.max(0, Math.min(padScrollOffset, maxOffset));

        // Render with scrolling
        renderScrollableButtons(padButtons, buttonX, buttonY, padScrollOffset,
                offset -> { padScrollOffset = offset; rebuildButtons(); }, true);

        // Back button in bottom bar
        addBackButton(() -> { padScrollOffset = 0; });
    }

    private void buildSignalList() {
        int[] contentBounds = LCARSRenderer.getContentBounds(panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT);
        int contentX = contentBounds[0];
        int contentY = contentBounds[1];
        int contentW = contentBounds[2];

        int buttonX = contentX + (contentW - BUTTON_WIDTH) / 2;
        int buttonY = contentY + BUTTON_Y_OFFSET;

        // Build signal buttons
        List<MenuButton> signalButtons = new ArrayList<>();
        for (OpenTricorderScreenPayload.SignalEntry signal : signals) {
            String typePrefix = signal.type() == SignalType.HELD ? "[H] " : "[D] ";
            signalButtons.add(new MenuButton(
                    Component.literal(typePrefix + signal.name().toUpperCase()),
                    button -> executeCommand("trek transport toSignal " + signal.tricorderId()),
                    LCARSRenderer.PEACH, LCARSRenderer.ORANGE
            ));
        }

        // Clamp scroll offset
        int maxOffset = Math.max(0, signalButtons.size() - MAX_VISIBLE_BUTTONS);
        signalScrollOffset = Math.max(0, Math.min(signalScrollOffset, maxOffset));

        // Render with scrolling
        renderScrollableButtons(signalButtons, buttonX, buttonY, signalScrollOffset,
                offset -> { signalScrollOffset = offset; rebuildButtons(); }, true);

        // Back button in bottom bar
        addBackButton(() -> { signalScrollOffset = 0; });
    }

    /**
     * Adds a back button in the bottom bar that returns to the main menu.
     */
    private void addBackButton(Runnable onBack) {
        addBottomBarButton("< BACK", () -> {
            if (onBack != null) onBack.run();
            currentState = MenuState.MAIN_MENU;
            rebuildButtons();
        });
    }

    /**
     * Adds a close button in the bottom bar that closes the screen.
     */
    private void addCloseButton() {
        addBottomBarButton("CLOSE X", this::onClose);
    }

    /**
     * Adds a button in the bottom bar, right-aligned with main content buttons.
     */
    private void addBottomBarButton(String label, Runnable onClick) {
        int[] contentBounds = LCARSRenderer.getContentBounds(panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT);
        int contentX = contentBounds[0];
        int contentW = contentBounds[2];
        int[] bottomBarBounds = LCARSRenderer.getBottomBarBounds(panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT);
        int bottomBarY = bottomBarBounds[1];
        int bottomBarH = bottomBarBounds[3];
        int buttonWidth = 65;
        int buttonHeight = 15;
        int mainButtonRightEdge = contentX + (contentW + BUTTON_WIDTH) / 2;
        int buttonX = mainButtonRightEdge - buttonWidth;
        int buttonY = bottomBarY + (bottomBarH - buttonHeight) / 2 + 12;
        addRenderableWidget(LCARSButton.lcarsBuilder(
                Component.literal(label),
                button -> onClick.run()
        ).bounds(buttonX, buttonY, buttonWidth, buttonHeight)
                .colors(LCARSRenderer.BLUE, LCARSRenderer.LAVENDER)
                .centerAligned()
                .build());
    }

    private void buildScanResults() {
        int[] contentBounds = LCARSRenderer.getContentBounds(panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT);
        int contentX = contentBounds[0];
        int contentY = contentBounds[1];
        int contentW = contentBounds[2];
        int contentH = contentBounds[3];

        // Layer navigation controls - two vertical columns in the left sidebar
        // Layout:  YZ-LVL
        //          [^] [<]
        //          [v] [>]
        //           0  ALL
        int navButtonSize = 16;  // Small square buttons
        int navX = panelLeft + 4;  // Left sidebar
        int navStartY = panelTop + 80;  // Button row Y position (lowered for spacing from top bar)
        int colGap = 6;  // Gap between Y and Z columns

        // Y up button [^] - increase layer (left column, top)
        addRenderableWidget(LCARSButton.lcarsBuilder(
                Component.literal("^"),
                button -> {
                    if (currentLayer == SHOW_ALL) {
                        currentLayer = 0;
                    } else if (currentLayer == 9) {
                        currentLayer = SHOW_ALL;
                    } else {
                        currentLayer++;
                    }
                }
        ).bounds(navX, navStartY, navButtonSize, navButtonSize)
                .colors(LCARSRenderer.ORANGE, LCARSRenderer.LAVENDER)
                .build());

        // Y down button [v] - decrease layer (left column, bottom)
        addRenderableWidget(LCARSButton.lcarsBuilder(
                Component.literal("v"),
                button -> {
                    if (currentLayer == SHOW_ALL) {
                        currentLayer = 9;
                    } else if (currentLayer == 0) {
                        currentLayer = SHOW_ALL;
                    } else {
                        currentLayer--;
                    }
                }
        ).bounds(navX, navStartY + navButtonSize + 2, navButtonSize, navButtonSize)
                .colors(LCARSRenderer.ORANGE, LCARSRenderer.LAVENDER)
                .build());

        // Z back button [<] - decrease Z slice (right column, top)
        addRenderableWidget(LCARSButton.lcarsBuilder(
                Component.literal("<"),
                button -> {
                    if (currentZSlice == SHOW_ALL) {
                        currentZSlice = 9;
                    } else if (currentZSlice == 0) {
                        currentZSlice = SHOW_ALL;
                    } else {
                        currentZSlice--;
                    }
                }
        ).bounds(navX + navButtonSize + colGap, navStartY, navButtonSize, navButtonSize)
                .colors(LCARSRenderer.BLUE, LCARSRenderer.LAVENDER)
                .build());

        // Z forward button [>] - increase Z slice (right column, bottom)
        addRenderableWidget(LCARSButton.lcarsBuilder(
                Component.literal(">"),
                button -> {
                    if (currentZSlice == SHOW_ALL) {
                        currentZSlice = 0;
                    } else if (currentZSlice == 9) {
                        currentZSlice = SHOW_ALL;
                    } else {
                        currentZSlice++;
                    }
                }
        ).bounds(navX + navButtonSize + colGap, navStartY + navButtonSize + 2, navButtonSize, navButtonSize)
                .colors(LCARSRenderer.BLUE, LCARSRenderer.LAVENDER)
                .build());

        // Back button in bottom bar - always goes to main menu
        addBackButton(() -> { cameFromMenu = false; });
    }

    private void buildStarfleetCommand() {
        int[] contentBounds = LCARSRenderer.getContentBounds(panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT);
        int contentX = contentBounds[0];
        int contentY = contentBounds[1];
        int contentW = contentBounds[2];

        int buttonX = contentX + (contentW - BUTTON_WIDTH) / 2;
        int buttonY = contentY + BUTTON_Y_OFFSET;

        List<MenuButton> menuButtons = new ArrayList<>();

        // Mission Board - view available missions
        menuButtons.add(new MenuButton(
                Component.literal("MISSION BOARD"),
                button -> {
                    // Request mission board data then show
                    requestMissionBoard();
                },
                LCARSRenderer.PEACH, LCARSRenderer.ORANGE
        ));

        // Mission Log - view active missions
        menuButtons.add(new MenuButton(
                Component.literal("MISSION LOG"),
                button -> {
                    requestMissionLog();
                },
                LCARSRenderer.PEACH, LCARSRenderer.ORANGE
        ));

        // Service Record - view rank and stats
        menuButtons.add(new MenuButton(
                Component.literal("SERVICE RECORD"),
                button -> {
                    // Request service record data, the handler will switch to SERVICE_RECORD state
                    executeCommandNoClose("trek starfleet rank");
                },
                LCARSRenderer.LAVENDER, LCARSRenderer.PURPLE
        ));

        // Contribution - for backward compatibility
        menuButtons.add(new MenuButton(
                Component.literal("CONTRIBUTE LATINUM"),
                button -> executeCommand("trek contribution"),
                LCARSRenderer.LAVENDER, LCARSRenderer.PURPLE
        ));

        renderScrollableButtons(menuButtons, buttonX, buttonY, 0, offset -> {}, true);

        // Back button
        addBackButton(null);
    }

    private void buildMissionBoard() {
        int[] contentBounds = LCARSRenderer.getContentBounds(panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT);
        int contentX = contentBounds[0];
        int contentY = contentBounds[1];
        int contentW = contentBounds[2];

        int buttonX = contentX + (contentW - BUTTON_WIDTH) / 2;
        int buttonY = contentY + BUTTON_Y_OFFSET;

        List<MenuButton> missionButtons = new ArrayList<>();

        for (OpenMissionBoardPayload.MissionSummary mission : missionBoardData) {
            String title = mission.title().toUpperCase();
            if (title.length() > 20) title = title.substring(0, 17) + "...";

            // Color based on status
            int normalColor = mission.isParticipating() ? LCARSRenderer.GREEN :
                    (mission.canAccept() ? LCARSRenderer.PEACH : LCARSRenderer.GRAY);
            int hoverColor = mission.isParticipating() ? LCARSRenderer.LAVENDER :
                    (mission.canAccept() ? LCARSRenderer.ORANGE : LCARSRenderer.GRAY);

            String missionId = mission.missionId().toString();
            missionButtons.add(new MenuButton(
                    Component.literal(title),
                    button -> {
                        if (mission.isParticipating()) {
                            // Already on mission - show info
                            executeCommandNoClose("trek mission info " + missionId);
                        } else if (mission.canAccept()) {
                            // Show mission info (can accept from there)
                            executeCommandNoClose("trek mission info " + missionId);
                        }
                    },
                    normalColor, hoverColor
            ));
        }

        // Clamp scroll offset
        int maxOffset = Math.max(0, missionButtons.size() - MAX_VISIBLE_BUTTONS);
        missionBoardScrollOffset = Math.max(0, Math.min(missionBoardScrollOffset, maxOffset));

        renderScrollableButtons(missionButtons, buttonX, buttonY, missionBoardScrollOffset,
                offset -> { missionBoardScrollOffset = offset; rebuildButtons(); }, true);

        // Back button - return to Starfleet Command
        addBottomBarButton("< BACK", () -> {
            missionBoardScrollOffset = 0;
            currentState = MenuState.STARFLEET_COMMAND;
            rebuildButtons();
        });
    }

    private void buildMissionLog() {
        int[] contentBounds = LCARSRenderer.getContentBounds(panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT);
        int contentX = contentBounds[0];
        int contentY = contentBounds[1];
        int contentW = contentBounds[2];

        int buttonX = contentX + (contentW - BUTTON_WIDTH) / 2;
        int buttonY = contentY + BUTTON_Y_OFFSET;

        List<MenuButton> missionButtons = new ArrayList<>();

        for (OpenMissionLogPayload.ActiveMissionEntry mission : missionLogData) {
            String title = mission.title().toUpperCase();
            if (title.length() > 14) title = title.substring(0, 11) + "...";

            // Show detailed progress text (e.g., "12/20" or "45/60s")
            String displayTitle = title + " " + mission.progressText();

            String missionId = mission.missionId().toString();
            missionButtons.add(new MenuButton(
                    Component.literal(displayTitle),
                    button -> executeCommandNoClose("trek mission info " + missionId),
                    LCARSRenderer.GREEN, LCARSRenderer.LAVENDER
            ));
        }

        // Clamp scroll offset
        int maxOffset = Math.max(0, missionButtons.size() - MAX_VISIBLE_BUTTONS);
        missionLogScrollOffset = Math.max(0, Math.min(missionLogScrollOffset, maxOffset));

        renderScrollableButtons(missionButtons, buttonX, buttonY, missionLogScrollOffset,
                offset -> { missionLogScrollOffset = offset; rebuildButtons(); }, true);

        // Back button - return to Starfleet Command
        addBottomBarButton("< BACK", () -> {
            missionLogScrollOffset = 0;
            currentState = MenuState.STARFLEET_COMMAND;
            rebuildButtons();
        });
    }

    private void buildServiceRecord() {
        int[] contentBounds = LCARSRenderer.getContentBounds(panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT);
        int contentX = contentBounds[0];
        int contentY = contentBounds[1];
        int contentW = contentBounds[2];

        // Service Record is a display-only screen with stats
        // No interactive buttons except back

        // Back button - return to Starfleet Command
        addBottomBarButton("< BACK", () -> {
            currentState = MenuState.STARFLEET_COMMAND;
            rebuildButtons();
        });
    }

    private void buildMissionInfo() {
        int[] contentBounds = LCARSRenderer.getContentBounds(panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT);
        int contentX = contentBounds[0];
        int contentY = contentBounds[1];
        int contentW = contentBounds[2];

        // Action button in slot 5 (6th position), text scrolls in slots 0-4 area
        if (currentMissionInfo != null) {
            int buttonX = contentX + (contentW - BUTTON_WIDTH) / 2;
            int buttonY = contentY + BUTTON_Y_OFFSET + (5 * (BUTTON_HEIGHT + BUTTON_SPACING));

            if (currentMissionInfo.isParticipating()) {
                // Abandon button
                String missionId = currentMissionInfo.missionId().toString();
                addRenderableWidget(LCARSButton.lcarsBuilder(
                        Component.literal("ABANDON MISSION"),
                        button -> executeCommand("trek mission abandon " + missionId)
                ).bounds(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                        .colors(LCARSRenderer.RED, LCARSRenderer.ORANGE)
                        .build());
            } else if (currentMissionInfo.canAccept()) {
                // Accept button
                String missionId = currentMissionInfo.missionId().toString();
                addRenderableWidget(LCARSButton.lcarsBuilder(
                        Component.literal("ACCEPT MISSION"),
                        button -> executeCommand("trek mission accept " + missionId)
                ).bounds(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                        .colors(LCARSRenderer.GREEN, LCARSRenderer.LAVENDER)
                        .build());
            }
        }

        // Scroll buttons in sidebar if content is scrollable
        if (missionInfoContentHeight > 0) {
            int scrollAreaHeight = getScrollableTextAreaHeight();
            if (missionInfoContentHeight > scrollAreaHeight) {
                int navButtonSize = 16;
                int navX = panelLeft + 4;
                int navY = panelTop + 116;

                int maxScroll = missionInfoContentHeight - scrollAreaHeight;

                addRenderableWidget(LCARSButton.lcarsBuilder(
                        Component.literal("^"),
                        button -> { if (missionInfoScrollOffset > 0) { missionInfoScrollOffset = Math.max(0, missionInfoScrollOffset - 20); } }
                ).bounds(navX, navY, navButtonSize, navButtonSize)
                        .colors(LCARSRenderer.ORANGE, LCARSRenderer.LAVENDER)
                        .build());

                addRenderableWidget(LCARSButton.lcarsBuilder(
                        Component.literal("v"),
                        button -> { if (missionInfoScrollOffset < maxScroll) { missionInfoScrollOffset = Math.min(maxScroll, missionInfoScrollOffset + 20); } }
                ).bounds(navX + navButtonSize + 2, navY, navButtonSize, navButtonSize)
                        .colors(LCARSRenderer.ORANGE, LCARSRenderer.LAVENDER)
                        .build());
            }
        }

        // Back button - return to previous mission screen
        addBottomBarButton("< BACK", () -> {
            missionInfoScrollOffset = 0;
            currentState = previousMissionState;
            rebuildButtons();
        });
    }

    /**
     * Get the height of the scrollable text area (5 button slots).
     * BUTTON_SPACING provides the built-in 6px margin between slots.
     */
    private int getScrollableTextAreaHeight() {
        return 5 * (BUTTON_HEIGHT + BUTTON_SPACING);
    }

    /**
     * Request mission board data from server.
     */
    private void requestMissionBoard() {
        executeCommandNoClose("trek mission list");
        // For now, switch to mission board immediately
        // In a full implementation, the server would send a payload
        currentState = MenuState.MISSION_BOARD;
        rebuildButtons();
    }

    /**
     * Request mission log data from server.
     */
    private void requestMissionLog() {
        executeCommandNoClose("trek mission log");
        currentState = MenuState.MISSION_LOG;
        rebuildButtons();
    }

    /**
     * Update mission board data from server payload.
     */
    public void updateMissionBoard(List<OpenMissionBoardPayload.MissionSummary> missions) {
        this.missionBoardData = new ArrayList<>(missions);
        if (currentState == MenuState.MISSION_BOARD) {
            rebuildButtons();
        }
    }

    /**
     * Update mission log data from server payload.
     */
    public void updateMissionLog(List<OpenMissionLogPayload.ActiveMissionEntry> missions) {
        this.missionLogData = new ArrayList<>(missions);
        if (currentState == MenuState.MISSION_LOG) {
            rebuildButtons();
        }
    }

    /**
     * Update player's Starfleet record from server payload.
     */
    public void updateStarfleetRecord(String rankTitle, long xp, int activeMissions, int completedMissions) {
        this.playerRankTitle = rankTitle;
        this.playerXp = xp;
        this.activeMissionCount = activeMissions;
        this.completedMissionCount = completedMissions;
    }

    /**
     * Update service record data from server payload and switch to service record screen.
     */
    public void updateServiceRecord(OpenServiceRecordPayload payload) {
        this.playerRankTitle = payload.rankName();
        this.playerXp = payload.totalXp();
        this.xpToNextRank = payload.xpToNextRank();
        this.activeMissionCount = payload.activeMissionCount();
        this.completedMissionCount = payload.completedMissionCount();
        this.totalKills = payload.totalKills();
        this.totalScans = payload.totalScans();
        this.biomesExplored = payload.biomesExplored();
        // Switch to service record view
        currentState = MenuState.SERVICE_RECORD;
        rebuildButtons();
    }

    /**
     * Update mission info data from server payload and switch to mission info screen.
     */
    public void updateMissionInfo(OpenMissionInfoPayload payload) {
        this.currentMissionInfo = payload;
        this.missionInfoScrollOffset = 0;  // Reset scroll when viewing new mission
        this.missionInfoContentHeight = 0;  // Will be calculated on first render
        // Track where we came from (MISSION_LOG or MISSION_BOARD)
        if (currentState == MenuState.MISSION_LOG || currentState == MenuState.MISSION_BOARD) {
            this.previousMissionState = currentState;
        }
        currentState = MenuState.MISSION_INFO;
        rebuildButtons();
    }

    private void executeCommand(String command) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.connection != null) {
            mc.player.connection.sendCommand(command);
        }
        this.onClose();
    }

    private void executeCommandNoClose(String command) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.connection != null) {
            mc.player.connection.sendCommand(command);
        }
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // First render the standard blurred background
        super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        // Fill the entire panel area with black before drawing the LCARS frame
        guiGraphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, 0xFF000000);

        // Then draw LCARS frame on top of the black background
        // For SCAN_RESULTS, the title (right side) shows the anomaly count
        String titleText = switch (currentState) {
            case MAIN_MENU -> "TRICORDER";
            case PAD_LIST -> "SELECT PAD";
            case SIGNAL_LIST -> "SELECT SIGNAL";
            case SCAN_RESULTS -> {
                int count = scanBlocks != null ? scanBlocks.size() : 0;
                yield count + " ANOMAL" + (count == 1 ? "Y" : "IES");
            }
            case STARFLEET_COMMAND -> "STARFLEET";
            case MISSION_BOARD -> "MISSIONS";
            case MISSION_LOG -> "ACTIVE";
            case MISSION_INFO -> "DETAILS";
            case SERVICE_RECORD -> "RECORD";
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
        } else if (currentState == MenuState.MISSION_BOARD && missionBoardData.isEmpty()) {
            String msg = "NO MISSIONS";
            int msgWidth = this.font.width(msg);
            guiGraphics.drawString(this.font, msg, contentX + (contentW - msgWidth) / 2, contentY + 40, LCARSRenderer.ORANGE);
            String msg2 = "AVAILABLE";
            int msg2Width = this.font.width(msg2);
            guiGraphics.drawString(this.font, msg2, contentX + (contentW - msg2Width) / 2, contentY + 52, LCARSRenderer.ORANGE);
        } else if (currentState == MenuState.MISSION_LOG && missionLogData.isEmpty()) {
            String msg = "NO ACTIVE MISSIONS";
            int msgWidth = this.font.width(msg);
            guiGraphics.drawString(this.font, msg, contentX + (contentW - msgWidth) / 2, contentY + 40, LCARSRenderer.ORANGE);
            String msg2 = "USE MISSION BOARD";
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

        // Draw currency info in the sidebar (only on main menu)
        if (currentState == MenuState.MAIN_MENU) {
            int sidebarX = panelLeft + 6;
            int sidebarStartY = panelTop + 70;

            if (!hasRoom) {
                // Show warning in sidebar when no transporter room
                guiGraphics.drawString(this.font, "NO", sidebarX, sidebarStartY, LCARSRenderer.RED, false);
                guiGraphics.drawString(this.font, "ROOM", sidebarX, sidebarStartY + 10, LCARSRenderer.RED, false);
            } else {
                // Transport funds (strips in transporter room)
                ItemStack stripStack = new ItemStack(ModItems.LATINUM_STRIP.get());
                guiGraphics.renderFakeItem(stripStack, sidebarX, sidebarStartY);
                String fuelStr = String.valueOf(fuel);
                guiGraphics.drawString(this.font, fuelStr, sidebarX + 18, sidebarStartY + 4, LCARSRenderer.TEXT_DARK, false);
            }

            // Scan funds (slips in player inventory) - always show
            int slipsY = sidebarStartY + 22;
            ItemStack slipStack = new ItemStack(ModItems.LATINUM_SLIP.get());
            guiGraphics.renderFakeItem(slipStack, sidebarX, slipsY);
            String slipsStr = String.valueOf(slips);
            guiGraphics.drawString(this.font, slipsStr, sidebarX + 18, slipsY + 4, LCARSRenderer.TEXT_DARK, false);
            // Scroll buttons (if needed) are placed below this by buildMainMenu()
        }

        // Draw rank info in sidebar for Starfleet menus
        if (currentState == MenuState.STARFLEET_COMMAND ||
            currentState == MenuState.MISSION_BOARD ||
            currentState == MenuState.MISSION_LOG) {
            int sidebarX = panelLeft + 4;
            int sidebarStartY = panelTop + 70;

            // Rank display (abbreviated)
            String rankAbbr = playerRankTitle.length() > 4 ?
                    playerRankTitle.substring(0, 4).toUpperCase() :
                    playerRankTitle.toUpperCase();
            guiGraphics.drawString(this.font, rankAbbr, sidebarX, sidebarStartY, LCARSRenderer.BLUE, false);

            // XP display
            String xpStr = playerXp >= 1000 ? (playerXp / 1000) + "K" : String.valueOf(playerXp);
            guiGraphics.drawString(this.font, xpStr + " XP", sidebarX, sidebarStartY + 12, LCARSRenderer.TEXT_DARK, false);
        }

        // Draw scan results with isometric 3D visualization
        if (currentState == MenuState.SCAN_RESULTS) {
            renderScanResults(guiGraphics, contentX, contentY, contentW, contentH);
        }

        // Draw service record content
        if (currentState == MenuState.SERVICE_RECORD) {
            renderServiceRecord(guiGraphics, contentX, contentY, contentW, contentH);
        }

        // Draw mission info content
        if (currentState == MenuState.MISSION_INFO) {
            renderMissionInfo(guiGraphics, contentX, contentY, contentW, contentH);
        }
    }

    /**
     * Renders the service record showing player's Starfleet career information.
     */
    private void renderServiceRecord(GuiGraphics g, int contentX, int contentY, int contentW, int contentH) {
        int y = contentY + 10;
        int labelX = contentX + 10;
        int valueX = contentX + contentW - 10;

        // Rank title (centered, large)
        String rankDisplay = playerRankTitle.toUpperCase();
        int rankWidth = this.font.width(rankDisplay);
        g.drawString(this.font, rankDisplay, contentX + (contentW - rankWidth) / 2, y, LCARSRenderer.BLUE);
        y += 18;

        // XP Progress bar
        int barX = labelX;
        int barWidth = contentW - 20;
        int barHeight = 8;

        // Calculate progress to next rank
        double progress = 0.0;
        if (xpToNextRank > 0) {
            progress = Math.min(1.0, (double) playerXp / (playerXp + xpToNextRank));
        } else {
            progress = 1.0; // Max rank
        }

        // Draw progress bar background
        g.fill(barX, y, barX + barWidth, y + barHeight, 0xFF333333);
        // Draw progress fill
        int fillWidth = (int) (barWidth * progress);
        if (fillWidth > 0) {
            g.fill(barX, y, barX + fillWidth, y + barHeight, LCARSRenderer.BLUE);
        }
        // Draw border
        g.renderOutline(barX, y, barWidth, barHeight, LCARSRenderer.LAVENDER);
        y += barHeight + 4;

        // XP text
        String xpText = "XP: " + playerXp;
        if (xpToNextRank > 0) {
            xpText += " / " + (playerXp + xpToNextRank);
        } else {
            xpText += " (MAX RANK)";
        }
        int xpWidth = this.font.width(xpText);
        g.drawString(this.font, xpText, contentX + (contentW - xpWidth) / 2, y, LCARSRenderer.LAVENDER);
        y += 20;

        // Stats section
        int lineHeight = 14;

        // Missions row
        String missionsLabel = "MISSIONS";
        String missionsValue = activeMissionCount + " ACTIVE / " + completedMissionCount + " COMPLETED";
        g.drawString(this.font, missionsLabel, labelX, y, LCARSRenderer.PEACH);
        int mvWidth = this.font.width(missionsValue);
        g.drawString(this.font, missionsValue, valueX - mvWidth, y, LCARSRenderer.TEXT_DARK);
        y += lineHeight;

        // Kills row
        String killsLabel = "HOSTILES NEUTRALIZED";
        String killsValue = String.valueOf(totalKills);
        g.drawString(this.font, killsLabel, labelX, y, LCARSRenderer.PEACH);
        int kvWidth = this.font.width(killsValue);
        g.drawString(this.font, killsValue, valueX - kvWidth, y, LCARSRenderer.TEXT_DARK);
        y += lineHeight;

        // Scans row
        String scansLabel = "SCANS PERFORMED";
        String scansValue = String.valueOf(totalScans);
        g.drawString(this.font, scansLabel, labelX, y, LCARSRenderer.PEACH);
        int svWidth = this.font.width(scansValue);
        g.drawString(this.font, scansValue, valueX - svWidth, y, LCARSRenderer.TEXT_DARK);
        y += lineHeight;

        // Biomes row
        String biomesLabel = "BIOMES EXPLORED";
        String biomesValue = String.valueOf(biomesExplored);
        g.drawString(this.font, biomesLabel, labelX, y, LCARSRenderer.PEACH);
        int bvWidth = this.font.width(biomesValue);
        g.drawString(this.font, biomesValue, valueX - bvWidth, y, LCARSRenderer.TEXT_DARK);
    }

    /**
     * Renders the mission info screen showing detailed mission information.
     * Content is scrollable in slots 0-4 area; button is fixed in slot 5.
     */
    private void renderMissionInfo(GuiGraphics g, int contentX, int contentY, int contentW, int contentH) {
        if (currentMissionInfo == null) {
            String msg = "NO MISSION DATA";
            int msgWidth = this.font.width(msg);
            g.drawString(this.font, msg, contentX + (contentW - msgWidth) / 2, contentY + 40, LCARSRenderer.ORANGE);
            return;
        }

        int labelX = contentX + 5;
        int valueX = contentX + contentW - 5;
        int lineHeight = 11;

        // Calculate scrollable area (slots 0-4, above the action button in slot 5)
        int scrollAreaHeight = getScrollableTextAreaHeight();
        int scrollAreaTop = contentY + BUTTON_Y_OFFSET;
        int scrollAreaBottom = scrollAreaTop + scrollAreaHeight;

        // First pass: calculate total content height (without rendering)
        int totalHeight = 0;
        totalHeight += 14; // title
        totalHeight += 14; // status

        String desc = currentMissionInfo.description();
        java.util.List<String> descLines = wrapText(desc, contentW - 10);
        totalHeight += descLines.size() * lineHeight;
        totalHeight += 4; // gap

        totalHeight += lineHeight; // "OBJECTIVE" label
        String objDesc = currentMissionInfo.objectiveDescription();
        java.util.List<String> objLines = wrapText(objDesc, contentW - 10);
        totalHeight += objLines.size() * lineHeight;
        totalHeight += 4; // gap

        totalHeight += 8 + 3; // progress bar + gap
        totalHeight += 14; // progress text
        totalHeight += lineHeight + 2; // XP/Rank line
        totalHeight += lineHeight; // participants

        missionInfoContentHeight = totalHeight;

        // Clamp scroll offset
        int maxScroll = Math.max(0, totalHeight - scrollAreaHeight);
        missionInfoScrollOffset = Math.max(0, Math.min(missionInfoScrollOffset, maxScroll));

        // Enable scissor to clip content to scrollable area
        g.enableScissor(contentX, scrollAreaTop, contentX + contentW, scrollAreaBottom);

        // Start rendering with scroll offset applied
        int y = scrollAreaTop - missionInfoScrollOffset;

        // Mission title (centered) - no truncation
        String titleDisplay = currentMissionInfo.title().toUpperCase();
        int titleWidth = this.font.width(titleDisplay);
        g.drawString(this.font, titleDisplay, contentX + (contentW - titleWidth) / 2, y, LCARSRenderer.ORANGE);
        y += 14;

        // Status indicator
        String statusStr = currentMissionInfo.isParticipating() ? "[ACTIVE]" :
                (currentMissionInfo.canAccept() ? "[AVAILABLE]" : "[LOCKED]");
        int statusColor = currentMissionInfo.isParticipating() ? LCARSRenderer.GREEN :
                (currentMissionInfo.canAccept() ? LCARSRenderer.PEACH : LCARSRenderer.GRAY);
        int statusWidth = this.font.width(statusStr);
        g.drawString(this.font, statusStr, contentX + (contentW - statusWidth) / 2, y, statusColor);
        y += 14;

        // Description (word-wrapped) - all lines
        for (String line : descLines) {
            g.drawString(this.font, line, labelX, y, LCARSRenderer.LAVENDER);
            y += lineHeight;
        }
        y += 4;

        // Objective label
        g.drawString(this.font, "OBJECTIVE", labelX, y, LCARSRenderer.PEACH);
        y += lineHeight;

        // Objective description - all lines
        for (String line : objLines) {
            g.drawString(this.font, line, labelX, y, LCARSRenderer.LAVENDER);
            y += lineHeight;
        }
        y += 4;

        // Progress bar
        int barX = labelX;
        int barWidth = contentW - 10;
        int barHeight = 8;
        double progress = currentMissionInfo.progressPercent();

        g.fill(barX, y, barX + barWidth, y + barHeight, 0xFF333333);
        int fillWidth = (int) (barWidth * progress);
        if (fillWidth > 0) {
            g.fill(barX, y, barX + fillWidth, y + barHeight, LCARSRenderer.GREEN);
        }
        g.renderOutline(barX, y, barWidth, barHeight, LCARSRenderer.LAVENDER);
        y += barHeight + 3;

        // Progress text
        String progressStr = currentMissionInfo.progressText() + " (" +
                String.format("%.0f%%", progress * 100) + ")";
        int progressWidth = this.font.width(progressStr);
        g.drawString(this.font, progressStr, contentX + (contentW - progressWidth) / 2, y, LCARSRenderer.LAVENDER);
        y += 14;

        // XP Reward and Min Rank on same line
        String xpLabel = "XP: " + currentMissionInfo.xpReward();
        String rankLabel = "RANK: " + currentMissionInfo.minRankTitle().toUpperCase();
        g.drawString(this.font, xpLabel, labelX, y, LCARSRenderer.GREEN);
        int rankWidth = this.font.width(rankLabel);
        g.drawString(this.font, rankLabel, valueX - rankWidth, y, LCARSRenderer.BLUE);
        y += lineHeight + 2;

        // Participants
        String partLabel = "PARTICIPANTS: " + currentMissionInfo.participantCount();
        g.drawString(this.font, partLabel, labelX, y, LCARSRenderer.LAVENDER);

        // Disable scissor
        g.disableScissor();
    }

    /**
     * Helper to wrap text into lines that fit within a given width.
     */
    private java.util.List<String> wrapText(String text, int maxWidth) {
        java.util.List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;

        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
            if (this.font.width(testLine) <= maxWidth) {
                if (currentLine.length() > 0) currentLine.append(" ");
                currentLine.append(word);
            } else {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder(word);
                } else {
                    // Word is too long, just add it
                    lines.add(word);
                }
            }
        }
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        return lines;
    }

    /**
     * Renders the scan results as an isometric 3D view of the scanned area.
     */
    private void renderScanResults(GuiGraphics g, int contentX, int contentY, int contentW, int contentH) {
        // Get top bar bounds for positioning the label text
        int[] topBarBounds = LCARSRenderer.getTopBarBounds(panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT);
        int topBarX = topBarBounds[0];
        int topBarY = topBarBounds[1];
        int topBarW = topBarBounds[2];
        int topBarH = topBarBounds[3];

        // Draw "Scan results" label on the left side of the top bar (scaled smaller)
        // (The anomaly count is drawn as the title on the right by LCARSRenderer)
        String labelText = "Scan results";
        float textScale = 0.85f;
        int labelX = topBarX + 28;  // Left side of top bar, moved more inward
        int labelY = topBarY + (topBarH - (int)(this.font.lineHeight * textScale)) / 2 - 8;
        g.pose().pushPose();
        g.pose().translate(labelX, labelY, 0);
        g.pose().scale(textScale, textScale, 1.0f);
        g.drawString(this.font, labelText, 0, 0, LCARSRenderer.TEXT_DARK, false);
        g.pose().popPose();

        // Reserve minimal space for layer controls at bottom (just above blue bar)
        int renderHeight = contentH - 20;  // More space for the 3D cube
        int centerX = contentX + contentW / 2;
        int centerY = contentY + (renderHeight - 10) / 2 + 25;  // Center the cube, moved down to avoid top bar

        if (scanBlocks == null || scanBlocks.isEmpty()) {
            // Show "no interesting blocks" message
            String msg = "NO ANOMALIES DETECTED";
            int msgWidth = this.font.width(msg);
            g.drawString(this.font, msg, contentX + (contentW - msgWidth) / 2, centerY - 5, LCARSRenderer.LAVENDER);
        } else {
            // Render all blocks in a proper 3D scene (includes player indicator)
            render3DBlockScene(g, centerX, centerY, scanBlocks);
        }

        // Draw combined YZ-LVL label above both button columns
        int navButtonSize = 16;
        int navX = panelLeft + 8;
        int navStartY = panelTop + 80;
        int colGap = 6;

        // Combined YZ-LVL label spanning both columns
        String yzLabel = "YZ-LVL";
        int yzLabelX = navX - 4;
        int yzLabelY = navStartY - 14;
        g.drawString(this.font, yzLabel, yzLabelX, yzLabelY, LCARSRenderer.TEXT_DARK, false);
        // Line under label
        g.fill(yzLabelX, navStartY - 4, yzLabelX + this.font.width(yzLabel), navStartY - 3, LCARSRenderer.TEXT_DARK);

        // Y value below left column buttons (black for readability)
        // Display player-relative Y: 0 = feet level, positive above, negative below
        String yVal;
        if (currentLayer == SHOW_ALL) {
            yVal = "ALL";
        } else {
            int relativeY = currentLayer - FEET_LEVEL_LAYER;
            yVal = (relativeY > 0 ? "+" : "") + relativeY;  // Show + sign for positive only
        }
        int yValY = navStartY + (navButtonSize + 2) * 2 + 2;
        g.drawString(this.font, yVal, navX, yValY, LCARSRenderer.TEXT_DARK, false);

        // Z value below right column buttons (black for readability)
        // Display 1-based Z: 1 = closest to player, 10 = farthest
        int zColX = navX + navButtonSize + colGap;
        String zVal = currentZSlice == SHOW_ALL ? "ALL" : String.valueOf(currentZSlice + 1);
        g.drawString(this.font, zVal, zColX, yValY, LCARSRenderer.TEXT_DARK, false);

        // Draw direction indicator in the bottom area
        int[] bottomBarBounds = LCARSRenderer.getBottomBarBounds(panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT);
        int bottomBarY = bottomBarBounds[1];
        String dirText = "SCAN:" + scanFacing.charAt(0);  // e.g., "SCAN:N"
        int dirWidth = this.font.width(dirText);
        g.drawString(this.font, dirText, contentX + contentW - dirWidth - 4, bottomBarY + 10, LCARSRenderer.ORANGE);
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

        // Scale for each block in the scene - larger value = bigger visualization
        float blockScale = 11.0f;

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
        // Dimmed light for non-selected blocks (low ambient light)
        int dimmedLight = LightTexture.pack(2, 2);

        for (ScanResultPayload.ScannedBlock block : sorted) {
            // Transform coordinates to check against filters
            int[] transformed = transformScanCoords(block.x(), block.y(), block.z());

            // Check if block is on the selected Y layer and Z slice
            boolean isActiveLayer = (currentLayer == SHOW_ALL || transformed[1] == currentLayer);
            boolean isActiveZSlice = (currentZSlice == SHOW_ALL || transformed[2] == currentZSlice);
            boolean isHighlighted = isActiveLayer && isActiveZSlice;

            // Use full brightness for highlighted blocks, dimmed for others
            int lightLevel = isHighlighted ? LightTexture.FULL_BRIGHT : dimmedLight;

            try {
                ResourceLocation blockLoc = ResourceLocation.parse(block.blockId());
                net.minecraft.world.level.block.Block mcBlock = BuiltInRegistries.BLOCK.get(blockLoc);
                BlockState blockState = mcBlock.defaultBlockState();

                // Position at the block's location (centered around origin)
                // Blocks are at positions 0-9, center at 4.5
                // Use already-transformed coordinates from above
                float bx = transformed[0] - 4.5f;
                float by = transformed[1] - 4.5f;
                float bz = transformed[2] - 4.5f;

                renderBlockAt(poseStack, blockRenderer, bufferSource, blockState, bx, by, bz, lightLevel);
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
                // Transform coordinates based on scan facing direction
                float[] transformedEntity = transformScanCoords(scannedEntity.x(), scannedEntity.y(), scannedEntity.z());

                // Check Y layer filtering - convert entity Y to layer (0-9)
                int entityLayer = (int) transformedEntity[1];
                if (entityLayer < 0) entityLayer = 0;
                if (entityLayer > 9) entityLayer = 9;

                // Check Z slice filtering - convert entity Z to slice (0-9)
                int entityZSlice = (int) transformedEntity[2];
                if (entityZSlice < 0) entityZSlice = 0;
                if (entityZSlice > 9) entityZSlice = 9;

                boolean isActiveLayer = (currentLayer == SHOW_ALL || entityLayer == currentLayer);
                boolean isActiveZSlice = (currentZSlice == SHOW_ALL || entityZSlice == currentZSlice);
                boolean isHighlighted = isActiveLayer && isActiveZSlice;

                // Use full brightness for highlighted entities, dimmed for others
                int entityLight = isHighlighted ? LightTexture.FULL_BRIGHT : dimmedLight;

                try {
                    // Create entity from type
                    ResourceLocation entityLoc = ResourceLocation.parse(scannedEntity.entityType());
                    EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(entityLoc);
                    Entity entity = entityType.create(mc.level);

                    if (entity != null) {
                        // Position in scan area (centered coords)
                        // Use already-transformed coordinates from above
                        float ex = transformedEntity[0] - 4.5f;
                        float ey = transformedEntity[1] - 4.5f;
                        float ez = transformedEntity[2] - 4.5f;

                        poseStack.pushPose();
                        // Position entity in scene
                        poseStack.translate(ex, ey, ez);

                        // Undo scene's -Y scale and apply standard GUI entity transforms
                        poseStack.scale(1.0f, -1.0f, -1.0f);
                        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
                        // Apply entity's yaw rotation
                        poseStack.mulPose(Axis.YP.rotationDegrees(scannedEntity.yaw()));

                        entityRenderer.render(entity, 0, 0, 0, 0, 1.0f, poseStack, bufferSource, entityLight);
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
                                float x, float y, float z, int packedLight) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        blockRenderer.renderSingleBlock(
                blockState,
                poseStack,
                bufferSource,
                packedLight,
                OverlayTexture.NO_OVERLAY
        );
        poseStack.popPose();
    }

    /**
     * Draws faint XYZ gridlines for the 10x10x10 scan area.
     */
    private void draw3DGridlines(GuiGraphics g, PoseStack poseStack) {
        // Grid bounds match actual block extents (blocks at -4.5 to +4.5 occupy -4.5 to +5.5)
        float min = -4.5f;
        float max = 5.5f;

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

        // Draw vertical grid lines at the current Z slice if viewing a specific slice
        if (currentZSlice != SHOW_ALL) {
            float sliceZ = currentZSlice - 4.5f;
            int sliceColor = 0x6000CCFF;  // Cyan/teal highlight

            // Draw a vertical plane (X-Y plane) at the current Z slice
            draw3DLine(g, poseStack, min, min, sliceZ, max, min, sliceZ, sliceColor);
            draw3DLine(g, poseStack, min, max, sliceZ, max, max, sliceZ, sliceColor);
            draw3DLine(g, poseStack, min, min, sliceZ, min, max, sliceZ, sliceColor);
            draw3DLine(g, poseStack, max, min, sliceZ, max, max, sliceZ, sliceColor);
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
            // Check if click is in the render area (not on buttons or sidebar)
            int[] contentBounds = LCARSRenderer.getContentBounds(panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT);
            int contentX = contentBounds[0];
            int contentY = contentBounds[1];
            int contentH = contentBounds[3];
            int renderAreaBottom = contentY + contentH - BUTTON_HEIGHT - 50;

            // Only start drag if click is in the content area (not sidebar) and above buttons
            if (mouseX > contentX && mouseY < renderAreaBottom) {
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Handle scrolling in main menu, pad and signal lists
        if (currentState == MenuState.MAIN_MENU) {
            int totalButtons = getMainMenuButtonCount();
            if (totalButtons > MAX_VISIBLE_BUTTONS) {
                int maxOffset = totalButtons - MAX_VISIBLE_BUTTONS;
                if (scrollY > 0 && mainMenuScrollOffset > 0) {
                    mainMenuScrollOffset--;
                    rebuildButtons();
                    return true;
                } else if (scrollY < 0 && mainMenuScrollOffset < maxOffset) {
                    mainMenuScrollOffset++;
                    rebuildButtons();
                    return true;
                }
            }
        } else if (currentState == MenuState.PAD_LIST && pads.size() > MAX_VISIBLE_BUTTONS) {
            int maxOffset = pads.size() - MAX_VISIBLE_BUTTONS;
            if (scrollY > 0 && padScrollOffset > 0) {
                padScrollOffset--;
                rebuildButtons();
                return true;
            } else if (scrollY < 0 && padScrollOffset < maxOffset) {
                padScrollOffset++;
                rebuildButtons();
                return true;
            }
        } else if (currentState == MenuState.SIGNAL_LIST && signals.size() > MAX_VISIBLE_BUTTONS) {
            int maxOffset = signals.size() - MAX_VISIBLE_BUTTONS;
            if (scrollY > 0 && signalScrollOffset > 0) {
                signalScrollOffset--;
                rebuildButtons();
                return true;
            } else if (scrollY < 0 && signalScrollOffset < maxOffset) {
                signalScrollOffset++;
                rebuildButtons();
                return true;
            }
        } else if (currentState == MenuState.MISSION_INFO && missionInfoContentHeight > 0) {
            int scrollAreaHeight = getScrollableTextAreaHeight();
            int maxScroll = Math.max(0, missionInfoContentHeight - scrollAreaHeight);
            if (maxScroll > 0) {
                int scrollAmount = 15;  // Pixels per scroll
                if (scrollY > 0 && missionInfoScrollOffset > 0) {
                    missionInfoScrollOffset = Math.max(0, missionInfoScrollOffset - scrollAmount);
                    return true;
                } else if (scrollY < 0 && missionInfoScrollOffset < maxScroll) {
                    missionInfoScrollOffset = Math.min(maxScroll, missionInfoScrollOffset + scrollAmount);
                    return true;
                }
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
