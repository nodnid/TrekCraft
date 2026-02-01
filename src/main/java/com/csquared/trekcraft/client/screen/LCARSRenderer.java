package com.csquared.trekcraft.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.ResourceLocation;

/**
 * Utility class for rendering LCARS (Library Computer Access/Retrieval System)
 * UI elements in the TNG/DS9/Voyager style.
 */
public class LCARSRenderer {

    // LCARS Color Palette (TNG Era)
    public static final int ORANGE = 0xFFFF9966;
    public static final int PEACH = 0xFFFFCC99;
    public static final int LAVENDER = 0xFFCC99CC;
    public static final int BLUE = 0xFF9999FF;
    public static final int PURPLE = 0xFFCC6699;
    public static final int RED = 0xFFCC4444;
    public static final int GREEN = 0xFF66CC66;
    public static final int GRAY = 0xFF666666;
    public static final int BLACK = 0xFF000000;
    public static final int TEXT_DARK = 0xFF000000;
    public static final int TEXT_LIGHT = 0xFFFF9966;

    /**
     * Elbow corner positions for LCARS frame curves.
     */
    public enum ElbowCorner {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }

    /**
     * Layout constants for programmatic LCARS frame rendering.
     * These values create an authentic LCARS appearance with proper proportions.
     */
    public static class FrameLayout {
        public static final int BAR_HEIGHT = 20;           // Height of top/bottom horizontal bars
        public static final int SIDEBAR_WIDTH = 54;        // Width of left sidebar (matches texture)
        public static final int GAP = 3;                   // Critical LCARS gap between elements
        public static final int ELBOW_OUTER_RADIUS = 40;   // Outer curve radius
        public static final int ELBOW_INNER_RADIUS = 20;   // Inner curve radius (bar thickness in curve)
    }

    // Texture resource location
    public static final ResourceLocation LCARS_FRAME_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("trekcraft", "textures/gui/lcars_frame.png");

    // Texture dimensions (must match the actual texture file)
    public static final int TEXTURE_WIDTH = 256;
    public static final int TEXTURE_HEIGHT = 256;

    // Frame dimensions for texture-based rendering (proportional to 256x256 texture)
    // These define where the content area is within the texture
    // Adjust these values to match the actual texture layout
    private static final int TEX_SIDEBAR_WIDTH = 54;
    private static final int TEX_TOP_BAR_HEIGHT = 48;
    private static final int TEX_BOTTOM_BAR_HEIGHT = 52;
    private static final int TEX_CONTENT_PADDING = 6;

    // Frame dimensions for programmatic rendering (fallback)
    private static final int SIDEBAR_WIDTH = 20;
    private static final int TOP_BAR_HEIGHT = 24;
    private static final int BOTTOM_BAR_HEIGHT = 16;
    private static final int CORNER_RADIUS = 8;
    private static final int INNER_GAP = 4;

    // Flag to toggle between texture and programmatic rendering
    private static boolean useTexture = true;

    /**
     * Set whether to use texture-based rendering (true) or programmatic rendering (false).
     */
    public static void setUseTexture(boolean use) {
        useTexture = use;
    }

    /**
     * Check if texture-based rendering is enabled.
     */
    public static boolean isUsingTexture() {
        return useTexture;
    }

    /**
     * Draw a simulated rounded rectangle using overlapping fills.
     * Creates rounded corners by drawing corner circles approximated with small fills.
     */
    public static void drawRoundedRect(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        if (radius <= 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }

        // Clamp radius to half the smaller dimension
        radius = Math.min(radius, Math.min(w / 2, h / 2));

        // Main body (excluding corners)
        g.fill(x + radius, y, x + w - radius, y + h, color);
        g.fill(x, y + radius, x + radius, y + h - radius, color);
        g.fill(x + w - radius, y + radius, x + w, y + h - radius, color);

        // Draw corners using small rectangle approximations
        drawCornerTopLeft(g, x, y, radius, color);
        drawCornerTopRight(g, x + w - radius, y, radius, color);
        drawCornerBottomLeft(g, x, y + h - radius, radius, color);
        drawCornerBottomRight(g, x + w - radius, y + h - radius, radius, color);
    }

    private static void drawCornerTopLeft(GuiGraphics g, int x, int y, int r, int color) {
        for (int row = 0; row < r; row++) {
            int dy = r - row;
            int dx = (int) Math.ceil(r - Math.sqrt(r * r - dy * dy));
            g.fill(x + dx, y + row, x + r, y + row + 1, color);
        }
    }

    private static void drawCornerTopRight(GuiGraphics g, int x, int y, int r, int color) {
        for (int row = 0; row < r; row++) {
            int dy = r - row;
            int dx = (int) Math.ceil(r - Math.sqrt(r * r - dy * dy));
            g.fill(x, y + row, x + r - dx, y + row + 1, color);
        }
    }

    private static void drawCornerBottomLeft(GuiGraphics g, int x, int y, int r, int color) {
        for (int row = 0; row < r; row++) {
            int dy = row + 1;
            int dx = (int) Math.ceil(r - Math.sqrt(r * r - dy * dy));
            g.fill(x + dx, y + row, x + r, y + row + 1, color);
        }
    }

    private static void drawCornerBottomRight(GuiGraphics g, int x, int y, int r, int color) {
        for (int row = 0; row < r; row++) {
            int dy = row + 1;
            int dx = (int) Math.ceil(r - Math.sqrt(r * r - dy * dy));
            g.fill(x, y + row, x + r - dx, y + row + 1, color);
        }
    }

    /**
     * Draw a horizontal pill shape (rounded on left and right ends).
     */
    public static void drawHorizontalPill(GuiGraphics g, int x, int y, int w, int h, int color) {
        int radius = h / 2;
        if (w < h) {
            // Too narrow, just draw a circle-ish shape
            drawRoundedRect(g, x, y, w, h, w / 2, color);
            return;
        }

        // Left semicircle
        drawSemicircleLeft(g, x, y, radius, color);
        // Middle rectangle
        g.fill(x + radius, y, x + w - radius, y + h, color);
        // Right semicircle
        drawSemicircleRight(g, x + w - radius, y, radius, color);
    }

    /**
     * Draw a vertical pill shape (rounded on top and bottom).
     */
    public static void drawVerticalPill(GuiGraphics g, int x, int y, int w, int h, int color) {
        int radius = w / 2;
        if (h < w) {
            // Too short, just draw a rounded shape
            drawRoundedRect(g, x, y, w, h, h / 2, color);
            return;
        }

        // Top semicircle
        drawSemicircleTop(g, x, y, radius, color);
        // Middle rectangle
        g.fill(x, y + radius, x + w, y + h - radius, color);
        // Bottom semicircle
        drawSemicircleBottom(g, x, y + h - radius, radius, color);
    }

    private static void drawSemicircleLeft(GuiGraphics g, int x, int y, int r, int color) {
        int diameter = r * 2;
        for (int row = 0; row < diameter; row++) {
            int dy = Math.abs(row - r);
            int dx = (int) Math.ceil(r - Math.sqrt(r * r - dy * dy));
            g.fill(x + dx, y + row, x + r, y + row + 1, color);
        }
    }

    private static void drawSemicircleRight(GuiGraphics g, int x, int y, int r, int color) {
        int diameter = r * 2;
        for (int row = 0; row < diameter; row++) {
            int dy = Math.abs(row - r);
            int dx = (int) Math.ceil(r - Math.sqrt(r * r - dy * dy));
            g.fill(x, y + row, x + r - dx, y + row + 1, color);
        }
    }

    private static void drawSemicircleTop(GuiGraphics g, int x, int y, int r, int color) {
        for (int row = 0; row < r; row++) {
            int dy = r - row;
            int halfWidth = (int) Math.floor(Math.sqrt(r * r - dy * dy));
            g.fill(x + r - halfWidth, y + row, x + r + halfWidth, y + row + 1, color);
        }
    }

    private static void drawSemicircleBottom(GuiGraphics g, int x, int y, int r, int color) {
        for (int row = 0; row < r; row++) {
            int dy = row + 1;
            int halfWidth = (int) Math.floor(Math.sqrt(r * r - dy * dy));
            g.fill(x + r - halfWidth, y + row, x + r + halfWidth, y + row + 1, color);
        }
    }

    /**
     * Draw the complete LCARS frame with sidebar, top bar, and bottom bar.
     * Uses texture-based rendering if enabled, otherwise falls back to programmatic rendering.
     *
     * @param g      GuiGraphics context
     * @param x      Left position of entire frame
     * @param y      Top position of entire frame
     * @param w      Total width of frame
     * @param h      Total height of frame
     * @param title  Title text displayed in top right
     * @param font   Font for rendering title
     */
    public static void drawLCARSFrame(GuiGraphics g, int x, int y, int w, int h, String title, Font font) {
        if (useTexture) {
            drawLCARSFrameTextured(g, x, y, w, h, title, font);
        } else {
            drawLCARSFrameProgrammatic(g, x, y, w, h, title, font);
        }
    }

    /**
     * Draw the LCARS frame using a texture image.
     * Uses segmented rendering to preserve elbow curves while allowing the frame to scale.
     */
    public static void drawLCARSFrameTextured(GuiGraphics g, int x, int y, int w, int h, String title, Font font) {
        drawLCARSFrameSegmented(g, x, y, w, h, title, font);
    }

    /**
     * Draw the LCARS frame using segmented texture rendering.
     * Splits the texture into three horizontal strips to preserve elbow curves
     * while allowing the frame to scale properly.
     */
    public static void drawLCARSFrameSegmented(GuiGraphics g, int x, int y, int w, int h, String title, Font font) {
        // Texture coordinate constants
        int TEX_W = TEXTURE_WIDTH;
        int TEX_H = TEXTURE_HEIGHT;
        int ELBOW_WIDTH = 80;      // Width of elbow curve region
        int END_CAP_WIDTH = 10;    // Rounded right end of bars
        int TOP_HEIGHT = 48;       // Top strip height in texture
        int BOTTOM_HEIGHT = 52;    // Bottom strip height in texture
        int SIDEBAR_TEX_WIDTH = 54; // Sidebar width in texture

        // Calculate scaled heights for output
        float scaleY = (float) h / TEX_H;
        int scaledTopH = (int)(TOP_HEIGHT * scaleY);
        int scaledBottomH = (int)(BOTTOM_HEIGHT * scaleY);
        int middleH = h - scaledTopH - scaledBottomH;

        // Calculate bar destination width
        int barDestW = w - ELBOW_WIDTH - END_CAP_WIDTH;

        // 1. TOP STRIP
        // Left elbow (preserve aspect ratio)
        g.blit(LCARS_FRAME_TEXTURE, x, y, ELBOW_WIDTH, scaledTopH,
               0, 0, ELBOW_WIDTH, TOP_HEIGHT, TEX_W, TEX_H);
        // Stretched middle bar
        g.blit(LCARS_FRAME_TEXTURE, x + ELBOW_WIDTH, y, barDestW, scaledTopH,
               ELBOW_WIDTH, 0, TEX_W - ELBOW_WIDTH - END_CAP_WIDTH, TOP_HEIGHT, TEX_W, TEX_H);
        // Right end cap
        g.blit(LCARS_FRAME_TEXTURE, x + w - END_CAP_WIDTH, y, END_CAP_WIDTH, scaledTopH,
               TEX_W - END_CAP_WIDTH, 0, END_CAP_WIDTH, TOP_HEIGHT, TEX_W, TEX_H);

        // 2. MIDDLE STRIP (sidebar only, stretched vertically)
        int middleY = y + scaledTopH;
        int texMiddleH = TEX_H - TOP_HEIGHT - BOTTOM_HEIGHT;
        g.blit(LCARS_FRAME_TEXTURE, x, middleY, SIDEBAR_TEX_WIDTH, middleH,
               0, TOP_HEIGHT, SIDEBAR_TEX_WIDTH, texMiddleH, TEX_W, TEX_H);

        // 3. BOTTOM STRIP
        int bottomY = y + h - scaledBottomH;
        int bottomTexY = TEX_H - BOTTOM_HEIGHT;
        // Left elbow
        g.blit(LCARS_FRAME_TEXTURE, x, bottomY, ELBOW_WIDTH, scaledBottomH,
               0, bottomTexY, ELBOW_WIDTH, BOTTOM_HEIGHT, TEX_W, TEX_H);
        // Stretched middle bar
        g.blit(LCARS_FRAME_TEXTURE, x + ELBOW_WIDTH, bottomY, barDestW, scaledBottomH,
               ELBOW_WIDTH, bottomTexY, TEX_W - ELBOW_WIDTH - END_CAP_WIDTH, BOTTOM_HEIGHT, TEX_W, TEX_H);
        // Right end cap
        g.blit(LCARS_FRAME_TEXTURE, x + w - END_CAP_WIDTH, bottomY, END_CAP_WIDTH, scaledBottomH,
               TEX_W - END_CAP_WIDTH, bottomTexY, END_CAP_WIDTH, BOTTOM_HEIGHT, TEX_W, TEX_H);

        // Title text (right-aligned in top bar area, scaled smaller)
        if (title != null && font != null) {
            float textScale = 0.85f;
            int scaledWidth = (int)(font.width(title) * textScale);
            int titleX = x + w - scaledWidth - 45;  // Moved more inward
            int titleY = y + (scaledTopH - (int)(font.lineHeight * textScale)) / 2 - 8;
            g.pose().pushPose();
            g.pose().translate(titleX, titleY, 0);
            g.pose().scale(textScale, textScale, 1.0f);
            g.drawString(font, title, 0, 0, TEXT_DARK, false);
            g.pose().popPose();
        }
    }

    /**
     * Draw the LCARS frame using programmatic fill operations (fallback).
     */
    public static void drawLCARSFrameProgrammatic(GuiGraphics g, int x, int y, int w, int h, String title, Font font) {
        // Background - black interior
        g.fill(x, y, x + w, y + h, BLACK);

        // Left sidebar (vertical pill)
        drawVerticalPill(g, x, y, SIDEBAR_WIDTH, h, ORANGE);

        // Top horizontal bar - connects to sidebar
        int topBarLeft = x + SIDEBAR_WIDTH - CORNER_RADIUS;
        int topBarWidth = w - SIDEBAR_WIDTH + CORNER_RADIUS;
        drawRoundedRect(g, topBarLeft, y, topBarWidth, TOP_BAR_HEIGHT, CORNER_RADIUS, ORANGE);
        // Fill the corner gap between sidebar and top bar
        g.fill(x + SIDEBAR_WIDTH - CORNER_RADIUS, y, x + SIDEBAR_WIDTH, y + TOP_BAR_HEIGHT, ORANGE);

        // Bottom horizontal bar
        int bottomBarLeft = x + SIDEBAR_WIDTH - CORNER_RADIUS;
        int bottomBarWidth = w - SIDEBAR_WIDTH + CORNER_RADIUS;
        int bottomBarTop = y + h - BOTTOM_BAR_HEIGHT;
        drawRoundedRect(g, bottomBarLeft, bottomBarTop, bottomBarWidth, BOTTOM_BAR_HEIGHT, CORNER_RADIUS / 2, BLUE);
        // Fill the corner gap
        g.fill(x + SIDEBAR_WIDTH - CORNER_RADIUS, bottomBarTop, x + SIDEBAR_WIDTH, y + h, BLUE);

        // Elbow cutout - black rounded corner between sidebar and bars
        // Top elbow cutout
        int elbowX = x + SIDEBAR_WIDTH;
        int elbowY = y + TOP_BAR_HEIGHT;
        drawRoundedRect(g, elbowX, elbowY, CORNER_RADIUS + INNER_GAP, CORNER_RADIUS + INNER_GAP, CORNER_RADIUS, BLACK);

        // Bottom elbow cutout
        int bottomElbowY = bottomBarTop - CORNER_RADIUS - INNER_GAP;
        drawRoundedRect(g, elbowX, bottomElbowY, CORNER_RADIUS + INNER_GAP, CORNER_RADIUS + INNER_GAP, CORNER_RADIUS, BLACK);

        // Inner content area black background (already covered by main fill, but ensure gap)
        int contentX = x + SIDEBAR_WIDTH + INNER_GAP;
        int contentY = y + TOP_BAR_HEIGHT + INNER_GAP;
        int contentW = w - SIDEBAR_WIDTH - INNER_GAP * 2;
        int contentH = h - TOP_BAR_HEIGHT - BOTTOM_BAR_HEIGHT - INNER_GAP * 2;
        g.fill(contentX, contentY, contentX + contentW, contentY + contentH, BLACK);

        // Title text (right-aligned in top bar)
        if (title != null && font != null) {
            int titleWidth = font.width(title);
            int titleX = x + w - titleWidth - 8;
            int titleY = y + (TOP_BAR_HEIGHT - font.lineHeight) / 2;
            g.drawString(font, title, titleX, titleY, TEXT_DARK, false);
        }

        // Decorative elements - small accent bars in sidebar
        int accentY = y + h / 3;
        g.fill(x + 3, accentY, x + SIDEBAR_WIDTH - 3, accentY + 3, PEACH);
        accentY = y + h / 3 + 6;
        g.fill(x + 3, accentY, x + SIDEBAR_WIDTH - 3, accentY + 2, LAVENDER);
        accentY = y + 2 * h / 3;
        g.fill(x + 3, accentY, x + SIDEBAR_WIDTH - 3, accentY + 3, PEACH);
    }

    /**
     * Get the content area bounds for placing widgets inside an LCARS frame.
     * Returns bounds appropriate for the current rendering mode (texture or programmatic).
     *
     * @return int array: [x, y, width, height] of content area
     */
    public static int[] getContentBounds(int frameX, int frameY, int frameW, int frameH) {
        if (useTexture) {
            return getContentBoundsTextured(frameX, frameY, frameW, frameH);
        } else {
            return getContentBoundsProgrammatic(frameX, frameY, frameW, frameH);
        }
    }

    /**
     * Get content bounds for texture-based rendering.
     * Uses segment-based calculations to match the segmented rendering approach.
     */
    public static int[] getContentBoundsTextured(int frameX, int frameY, int frameW, int frameH) {
        // Segment constants (must match drawLCARSFrameSegmented)
        int TOP_HEIGHT = 48;       // Top strip height in texture
        int BOTTOM_HEIGHT = 52;    // Bottom strip height in texture

        // Calculate scaled heights for the segments
        float scaleY = (float) frameH / TEXTURE_HEIGHT;
        int scaledTopH = (int)(TOP_HEIGHT * scaleY);
        int scaledBottomH = (int)(BOTTOM_HEIGHT * scaleY);

        // Content area calculation
        // Sidebar width stays at original size (54px), not scaled
        int contentX = frameX + TEX_SIDEBAR_WIDTH + TEX_CONTENT_PADDING;
        int contentY = frameY + scaledTopH + TEX_CONTENT_PADDING;
        int contentW = frameW - TEX_SIDEBAR_WIDTH - TEX_CONTENT_PADDING * 2;
        int contentH = frameH - scaledTopH - scaledBottomH - TEX_CONTENT_PADDING * 2;

        return new int[]{contentX, contentY, contentW, contentH};
    }

    /**
     * Get content bounds for programmatic rendering.
     */
    public static int[] getContentBoundsProgrammatic(int frameX, int frameY, int frameW, int frameH) {
        int contentX = frameX + SIDEBAR_WIDTH + INNER_GAP + 4;
        int contentY = frameY + TOP_BAR_HEIGHT + INNER_GAP + 4;
        int contentW = frameW - SIDEBAR_WIDTH - INNER_GAP * 2 - 8;
        int contentH = frameH - TOP_BAR_HEIGHT - BOTTOM_BAR_HEIGHT - INNER_GAP * 2 - 8;
        return new int[]{contentX, contentY, contentW, contentH};
    }

    /**
     * Draw a simple fuel/status bar in LCARS style.
     */
    public static void drawStatusBar(GuiGraphics g, int x, int y, int w, int h, float value, float maxValue, int color) {
        // Background
        g.fill(x, y, x + w, y + h, 0xFF333333);
        // Filled portion
        int fillWidth = (int) ((value / maxValue) * w);
        if (fillWidth > 0) {
            g.fill(x, y, x + fillWidth, y + h, color);
        }
        // Border
        g.fill(x, y, x + w, y + 1, ORANGE);
        g.fill(x, y + h - 1, x + w, y + h, ORANGE);
        g.fill(x, y, x + 1, y + h, ORANGE);
        g.fill(x + w - 1, y, x + w, y + h, ORANGE);
    }

    /**
     * Get the sidebar width for positioning calculations.
     */
    public static int getSidebarWidth() {
        return SIDEBAR_WIDTH;
    }

    /**
     * Get the top bar height for positioning calculations.
     */
    public static int getTopBarHeight() {
        return TOP_BAR_HEIGHT;
    }

    /**
     * Get the bottom bar height for positioning calculations.
     */
    public static int getBottomBarHeight() {
        return BOTTOM_BAR_HEIGHT;
    }

    /**
     * Get the top bar bounds for placing elements in the yellow top bar.
     * Returns bounds appropriate for the current rendering mode.
     *
     * @return int array: [x, y, width, height] of top bar area (content area, excluding sidebar)
     */
    public static int[] getTopBarBounds(int frameX, int frameY, int frameW, int frameH) {
        if (useTexture) {
            // Segment constants (must match drawLCARSFrameSegmented)
            int TOP_HEIGHT = 48;

            // Calculate scaled height for the top segment
            float scaleY = (float) frameH / TEXTURE_HEIGHT;
            int scaledTopH = (int)(TOP_HEIGHT * scaleY);

            // Sidebar width stays at original size (54px), not scaled
            int barX = frameX + TEX_SIDEBAR_WIDTH;
            int barY = frameY;
            int barW = frameW - TEX_SIDEBAR_WIDTH;
            int barH = scaledTopH;

            return new int[]{barX, barY, barW, barH};
        } else {
            int barX = frameX + SIDEBAR_WIDTH;
            int barY = frameY;
            int barW = frameW - SIDEBAR_WIDTH;
            int barH = TOP_BAR_HEIGHT;
            return new int[]{barX, barY, barW, barH};
        }
    }

    /**
     * Get the bottom bar bounds for placing elements in the blue bottom bar.
     * Returns bounds appropriate for the current rendering mode.
     *
     * @return int array: [x, y, width, height] of bottom bar area (content area, excluding sidebar)
     */
    public static int[] getBottomBarBounds(int frameX, int frameY, int frameW, int frameH) {
        if (useTexture) {
            // Segment constants (must match drawLCARSFrameSegmented)
            int BOTTOM_HEIGHT = 52;

            // Calculate scaled height for the bottom segment
            float scaleY = (float) frameH / TEXTURE_HEIGHT;
            int scaledBottomH = (int)(BOTTOM_HEIGHT * scaleY);

            // Sidebar width stays at original size (54px), not scaled
            int barX = frameX + TEX_SIDEBAR_WIDTH;
            int barY = frameY + frameH - scaledBottomH;
            int barW = frameW - TEX_SIDEBAR_WIDTH;
            int barH = scaledBottomH;

            return new int[]{barX, barY, barW, barH};
        } else {
            int barX = frameX + SIDEBAR_WIDTH;
            int barY = frameY + frameH - BOTTOM_BAR_HEIGHT;
            int barW = frameW - SIDEBAR_WIDTH;
            int barH = BOTTOM_BAR_HEIGHT;
            return new int[]{barX, barY, barW, barH};
        }
    }
}
