package com.csquared.trekcraft.client.screen;

import com.csquared.trekcraft.network.ClearHolodeckPayload;
import com.csquared.trekcraft.network.DeleteHoloprogramPayload;
import com.csquared.trekcraft.network.LoadHoloprogramPayload;
import com.csquared.trekcraft.network.OpenHolodeckScreenPayload;
import com.csquared.trekcraft.network.SaveHoloprogramPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * LCARS-styled holodeck controller screen.
 * Allows players to save, load, and delete holoprograms.
 */
public class HolodeckControllerScreen extends Screen {

    private static final int PANEL_WIDTH = 280;
    private static final int PANEL_HEIGHT = 260;
    private static final int BUTTON_WIDTH = 100;
    private static final int BUTTON_HEIGHT = 20;
    private static final int LIST_ITEM_HEIGHT = 18;
    private static final int MAX_VISIBLE_ITEMS = 8;

    private final BlockPos controllerPos;
    private final List<String> holoprograms;
    private int selectedIndex = -1;
    private int scrollOffset = 0;

    private int panelLeft;
    private int panelTop;

    private EditBox nameInput;

    private enum ScreenState {
        MAIN_MENU,
        SAVE_DIALOG
    }

    private ScreenState currentState = ScreenState.MAIN_MENU;

    public HolodeckControllerScreen(OpenHolodeckScreenPayload payload) {
        super(Component.translatable("screen.trekcraft.holodeck"));
        this.controllerPos = payload.controllerPos();
        this.holoprograms = new ArrayList<>(payload.holoprograms());
    }

    @Override
    protected void init() {
        super.init();
        panelLeft = (this.width - PANEL_WIDTH) / 2;
        panelTop = (this.height - PANEL_HEIGHT) / 2;

        setupWidgets();
    }

    private void setupWidgets() {
        clearWidgets();

        switch (currentState) {
            case MAIN_MENU -> buildMainMenu();
            case SAVE_DIALOG -> buildSaveDialog();
        }
    }

    private void buildMainMenu() {
        int[] contentBounds = LCARSRenderer.getContentBounds(panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT);
        int contentX = contentBounds[0];
        int contentY = contentBounds[1];
        int contentW = contentBounds[2];
        int contentH = contentBounds[3];

        // Action buttons at top
        int buttonY = contentY + 5;
        int buttonSpacing = 8;

        // SAVE button
        addRenderableWidget(LCARSButton.lcarsBuilder(
                Component.literal("SAVE"),
                button -> openSaveDialog()
        ).bounds(contentX, buttonY, 60, BUTTON_HEIGHT)
                .colors(LCARSRenderer.LAVENDER, LCARSRenderer.PURPLE)
                .build());

        // LOAD button
        addRenderableWidget(LCARSButton.lcarsBuilder(
                Component.literal("LOAD"),
                button -> loadSelected()
        ).bounds(contentX + 68, buttonY, 60, BUTTON_HEIGHT)
                .colors(LCARSRenderer.PEACH, LCARSRenderer.ORANGE)
                .build());

        // DELETE button
        addRenderableWidget(LCARSButton.lcarsBuilder(
                Component.literal("DELETE"),
                button -> deleteSelected()
        ).bounds(contentX + 136, buttonY, 60, BUTTON_HEIGHT)
                .colors(LCARSRenderer.RED, 0xFFFF6666)
                .textColor(0xFFFFFFFF)
                .build());

        // List area buttons (for holoprogram selection)
        int listY = buttonY + BUTTON_HEIGHT + 10;
        int listHeight = MAX_VISIBLE_ITEMS * LIST_ITEM_HEIGHT;

        // Create selection buttons for visible items
        for (int i = 0; i < MAX_VISIBLE_ITEMS; i++) {
            int index = scrollOffset + i;
            if (index >= holoprograms.size()) break;

            String name = holoprograms.get(index);
            final int finalIndex = index;

            int itemY = listY + i * LIST_ITEM_HEIGHT;
            boolean isSelected = (index == selectedIndex);

            addRenderableWidget(LCARSButton.lcarsBuilder(
                    Component.literal(name),
                    button -> selectItem(finalIndex)
            ).bounds(contentX, itemY, contentW, LIST_ITEM_HEIGHT - 2)
                    .colors(isSelected ? LCARSRenderer.ORANGE : LCARSRenderer.BLUE,
                            isSelected ? LCARSRenderer.PEACH : LCARSRenderer.LAVENDER)
                    .build());
        }

        // Scroll buttons if needed
        if (holoprograms.size() > MAX_VISIBLE_ITEMS) {
            int scrollButtonY = listY + listHeight + 5;

            // UP button
            addRenderableWidget(LCARSButton.lcarsBuilder(
                    Component.literal("UP"),
                    button -> scrollUp()
            ).bounds(contentX, scrollButtonY, 50, BUTTON_HEIGHT)
                    .colors(LCARSRenderer.BLUE, LCARSRenderer.LAVENDER)
                    .centerAligned()
                    .build());

            // DOWN button
            addRenderableWidget(LCARSButton.lcarsBuilder(
                    Component.literal("DOWN"),
                    button -> scrollDown()
            ).bounds(contentX + 55, scrollButtonY, 50, BUTTON_HEIGHT)
                    .colors(LCARSRenderer.BLUE, LCARSRenderer.LAVENDER)
                    .centerAligned()
                    .build());
        }

        // Bottom buttons
        int bottomY = contentY + contentH - BUTTON_HEIGHT - 5;

        // CLEAR button
        addRenderableWidget(LCARSButton.lcarsBuilder(
                Component.literal("CLEAR HOLODECK"),
                button -> clearHolodeck()
        ).bounds(contentX, bottomY, 110, BUTTON_HEIGHT)
                .colors(LCARSRenderer.RED, 0xFFFF6666)
                .textColor(0xFFFFFFFF)
                .build());

        // CLOSE button
        addRenderableWidget(LCARSButton.lcarsBuilder(
                Component.literal("CLOSE"),
                button -> onClose()
        ).bounds(contentX + contentW - 60, bottomY, 60, BUTTON_HEIGHT)
                .colors(LCARSRenderer.BLUE, LCARSRenderer.LAVENDER)
                .build());
    }

    private void buildSaveDialog() {
        int[] contentBounds = LCARSRenderer.getContentBounds(panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT);
        int contentX = contentBounds[0];
        int contentY = contentBounds[1];
        int contentW = contentBounds[2];
        int contentH = contentBounds[3];

        // Name input
        int inputY = contentY + 30;
        int inputWidth = contentW - 20;
        int inputX = contentX + 10;

        nameInput = new EditBox(this.font, inputX, inputY, inputWidth, 20, Component.literal("Name"));
        nameInput.setMaxLength(64);
        nameInput.setValue("");
        nameInput.setFocused(true);
        addRenderableWidget(nameInput);
        setInitialFocus(nameInput);

        // Buttons
        int buttonY = inputY + 35;
        int buttonSpacing = 10;

        // CONFIRM button
        addRenderableWidget(LCARSButton.lcarsBuilder(
                Component.literal("SAVE"),
                button -> confirmSave()
        ).bounds(contentX + 10, buttonY, 80, BUTTON_HEIGHT)
                .colors(LCARSRenderer.LAVENDER, LCARSRenderer.PURPLE)
                .build());

        // CANCEL button
        addRenderableWidget(LCARSButton.lcarsBuilder(
                Component.literal("CANCEL"),
                button -> cancelSave()
        ).bounds(contentX + 100, buttonY, 80, BUTTON_HEIGHT)
                .colors(LCARSRenderer.BLUE, LCARSRenderer.LAVENDER)
                .build());
    }

    private void selectItem(int index) {
        selectedIndex = index;
        setupWidgets();
    }

    private void scrollUp() {
        if (scrollOffset > 0) {
            scrollOffset--;
            setupWidgets();
        }
    }

    private void scrollDown() {
        if (scrollOffset + MAX_VISIBLE_ITEMS < holoprograms.size()) {
            scrollOffset++;
            setupWidgets();
        }
    }

    private void openSaveDialog() {
        currentState = ScreenState.SAVE_DIALOG;
        setupWidgets();
    }

    private void confirmSave() {
        String name = nameInput.getValue().trim();
        if (!name.isEmpty()) {
            PacketDistributor.sendToServer(new SaveHoloprogramPayload(controllerPos, name));

            // Add to local list if not present
            if (!holoprograms.contains(name)) {
                holoprograms.add(name);
                holoprograms.sort(String::compareToIgnoreCase);
            }
        }
        currentState = ScreenState.MAIN_MENU;
        setupWidgets();
    }

    private void cancelSave() {
        currentState = ScreenState.MAIN_MENU;
        setupWidgets();
    }

    private void loadSelected() {
        if (selectedIndex >= 0 && selectedIndex < holoprograms.size()) {
            String name = holoprograms.get(selectedIndex);
            PacketDistributor.sendToServer(new LoadHoloprogramPayload(controllerPos, name));
        }
    }

    private void deleteSelected() {
        if (selectedIndex >= 0 && selectedIndex < holoprograms.size()) {
            String name = holoprograms.get(selectedIndex);
            PacketDistributor.sendToServer(new DeleteHoloprogramPayload(controllerPos, name));

            // Remove from local list
            holoprograms.remove(selectedIndex);
            if (selectedIndex >= holoprograms.size()) {
                selectedIndex = holoprograms.size() - 1;
            }
            setupWidgets();
        }
    }

    private void clearHolodeck() {
        PacketDistributor.sendToServer(new ClearHolodeckPayload(controllerPos));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (currentState == ScreenState.SAVE_DIALOG) {
            if (keyCode == 257 || keyCode == 335) { // ENTER or NUMPAD_ENTER
                confirmSave();
                return true;
            }
            if (keyCode == 256) { // ESCAPE
                cancelSave();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (currentState == ScreenState.MAIN_MENU) {
            if (scrollY > 0) {
                scrollUp();
                return true;
            } else if (scrollY < 0) {
                scrollDown();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        // Fill panel background with black
        guiGraphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, 0xFF000000);

        // Draw LCARS frame
        String title = currentState == ScreenState.SAVE_DIALOG ? "SAVE HOLOPROGRAM" : "HOLODECK CONTROL";
        LCARSRenderer.drawLCARSFrame(guiGraphics, panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT, title, this.font);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int[] contentBounds = LCARSRenderer.getContentBounds(panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT);
        int contentX = contentBounds[0];
        int contentY = contentBounds[1];

        if (currentState == ScreenState.MAIN_MENU) {
            // Draw "HOLOPROGRAMS" label
            int labelY = contentY + BUTTON_HEIGHT + 12;
            guiGraphics.drawString(this.font, "HOLOPROGRAMS:", contentX, labelY - 5, LCARSRenderer.ORANGE);

            // Draw item count
            String countText = "(" + holoprograms.size() + " programs)";
            guiGraphics.drawString(this.font, countText, contentX + 100, labelY - 5, LCARSRenderer.PEACH);

            // If list is empty, show message
            if (holoprograms.isEmpty()) {
                int emptyY = labelY + 30;
                guiGraphics.drawString(this.font, "No holoprograms saved.", contentX + 20, emptyY, LCARSRenderer.LAVENDER);
                guiGraphics.drawString(this.font, "Build something and", contentX + 20, emptyY + 12, LCARSRenderer.LAVENDER);
                guiGraphics.drawString(this.font, "click SAVE to store it.", contentX + 20, emptyY + 24, LCARSRenderer.LAVENDER);
            }
        } else if (currentState == ScreenState.SAVE_DIALOG) {
            // Draw instruction
            guiGraphics.drawString(this.font, "Enter holoprogram name:", contentX + 10, contentY + 12, LCARSRenderer.ORANGE);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
