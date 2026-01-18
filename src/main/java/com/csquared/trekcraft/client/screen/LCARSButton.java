package com.csquared.trekcraft.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Custom Button widget styled in the LCARS aesthetic.
 * Features rectangular bars with color shift on hover.
 */
public class LCARSButton extends Button {

    private final int normalColor;
    private final int hoverColor;
    private final int textColor;
    private final boolean leftAligned;

    /**
     * Create an LCARS-styled button with default orange color.
     */
    public LCARSButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        this(x, y, width, height, message, onPress, LCARSRenderer.PEACH, LCARSRenderer.ORANGE, LCARSRenderer.TEXT_DARK, true);
    }

    /**
     * Create an LCARS-styled button with custom colors.
     */
    public LCARSButton(int x, int y, int width, int height, Component message, OnPress onPress,
                       int normalColor, int hoverColor, int textColor, boolean leftAligned) {
        super(x, y, width, height, message, onPress, Button.DEFAULT_NARRATION);
        this.normalColor = normalColor;
        this.hoverColor = hoverColor;
        this.textColor = textColor;
        this.leftAligned = leftAligned;
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int color = isHovered() ? hoverColor : normalColor;

        // Draw LCARS-style button bar with slight rounding
        LCARSRenderer.drawRoundedRect(guiGraphics, getX(), getY(), getWidth(), getHeight(), 3, color);

        // Add subtle highlight on top edge when not hovered
        if (!isHovered()) {
            guiGraphics.fill(getX() + 3, getY(), getX() + getWidth() - 3, getY() + 1, brighten(color, 0.2f));
        }

        // Draw text
        Font font = Minecraft.getInstance().font;
        String text = getMessage().getString();
        int textWidth = font.width(text);
        int textX;
        int textY = getY() + (getHeight() - font.lineHeight) / 2;

        if (leftAligned) {
            textX = getX() + 8;
        } else {
            textX = getX() + (getWidth() - textWidth) / 2;
        }

        // Draw text with shadow for better readability on colored backgrounds
        guiGraphics.drawString(font, text, textX, textY, textColor, false);

        // Draw active state indicator (small bar on left)
        if (isHovered()) {
            guiGraphics.fill(getX(), getY() + 2, getX() + 3, getY() + getHeight() - 2, LCARSRenderer.ORANGE);
        }
    }

    /**
     * Brighten a color by a factor.
     */
    private int brighten(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        r = Math.min(255, (int) (r + (255 - r) * factor));
        g = Math.min(255, (int) (g + (255 - g) * factor));
        b = Math.min(255, (int) (b + (255 - b) * factor));

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Builder for creating LCARS buttons with the standard Minecraft pattern.
     */
    public static Builder lcarsBuilder(Component message, OnPress onPress) {
        return new Builder(message, onPress);
    }

    public static class Builder {
        private final Component message;
        private final OnPress onPress;
        private int x;
        private int y;
        private int width = 150;
        private int height = 20;
        private int normalColor = LCARSRenderer.PEACH;
        private int hoverColor = LCARSRenderer.ORANGE;
        private int textColor = LCARSRenderer.TEXT_DARK;
        private boolean leftAligned = true;

        public Builder(Component message, OnPress onPress) {
            this.message = message;
            this.onPress = onPress;
        }

        public Builder bounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder pos(int x, int y) {
            this.x = x;
            this.y = y;
            return this;
        }

        public Builder size(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder colors(int normalColor, int hoverColor) {
            this.normalColor = normalColor;
            this.hoverColor = hoverColor;
            return this;
        }

        public Builder textColor(int textColor) {
            this.textColor = textColor;
            return this;
        }

        public Builder centerAligned() {
            this.leftAligned = false;
            return this;
        }

        public LCARSButton build() {
            return new LCARSButton(x, y, width, height, message, onPress,
                    normalColor, hoverColor, textColor, leftAligned);
        }
    }
}
