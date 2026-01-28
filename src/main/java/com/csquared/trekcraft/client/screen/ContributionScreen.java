package com.csquared.trekcraft.client.screen;

import com.csquared.trekcraft.data.ContributorRank;
import com.csquared.trekcraft.network.OpenContributionScreenPayload;
import com.csquared.trekcraft.network.OpenContributionScreenPayload.LeaderboardEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * LCARS-styled screen showing player contribution status and leaderboard.
 */
public class ContributionScreen extends Screen {

    private static final int PANEL_WIDTH = 280;
    private static final int PANEL_HEIGHT = 260;

    private final long totalDeposited;
    private final long totalWithdrawn;
    private final long netContribution;
    private final int freeTransportsRemaining;
    private final int freeTransportsUsed;
    private final int totalFreeTransportsEarned;
    private final ContributorRank highestRank;
    private final List<LeaderboardEntry> leaderboard;

    private int panelLeft;
    private int panelTop;

    public ContributionScreen(OpenContributionScreenPayload payload) {
        super(Component.translatable("screen.trekcraft.contribution"));
        this.totalDeposited = payload.totalDeposited();
        this.totalWithdrawn = payload.totalWithdrawn();
        this.netContribution = payload.getNetContribution();
        this.freeTransportsRemaining = payload.getFreeTransportsRemaining();
        this.freeTransportsUsed = payload.freeTransportsUsed();
        this.totalFreeTransportsEarned = payload.getTotalFreeTransportsEarned();
        this.highestRank = payload.getHighestRank();
        this.leaderboard = payload.leaderboard();
    }

    @Override
    protected void init() {
        super.init();
        panelLeft = (this.width - PANEL_WIDTH) / 2;
        panelTop = (this.height - PANEL_HEIGHT) / 2;

        // Close button in the bottom bar
        int[] bottomBarBounds = LCARSRenderer.getBottomBarBounds(panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT);
        int bottomBarY = bottomBarBounds[1];
        int bottomBarH = bottomBarBounds[3];
        int closeButtonWidth = 65;
        int closeButtonHeight = 15;

        int[] contentBounds = LCARSRenderer.getContentBounds(panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT);
        int contentX = contentBounds[0];
        int contentW = contentBounds[2];

        int closeX = contentX + contentW - closeButtonWidth;
        int closeY = bottomBarY + (bottomBarH - closeButtonHeight) / 2 + 12;

        addRenderableWidget(LCARSButton.lcarsBuilder(
                Component.literal("CLOSE"),
                button -> this.onClose()
        ).bounds(closeX, closeY, closeButtonWidth, closeButtonHeight)
                .colors(LCARSRenderer.BLUE, LCARSRenderer.LAVENDER)
                .centerAligned()
                .build());
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, 0xFF000000);
        LCARSRenderer.drawLCARSFrame(guiGraphics, panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT, "STARFLEET", this.font);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        int[] contentBounds = LCARSRenderer.getContentBounds(panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT);
        int contentX = contentBounds[0];
        int contentY = contentBounds[1];
        int contentW = contentBounds[2];

        int y = contentY + 5;
        int lineHeight = 11;

        // === YOUR STATUS ===
        drawCenteredText(g, "YOUR STATUS", contentX, contentW, y, LCARSRenderer.ORANGE);
        y += lineHeight + 4;

        // Rank with star decoration
        String rankTitle = highestRank.getTitle().toUpperCase();
        drawCenteredText(g, "* " + rankTitle + " *", contentX, contentW, y, LCARSRenderer.PEACH);
        y += lineHeight + 6;

        // Stats in two columns
        int col1X = contentX + 5;
        int col2X = contentX + contentW / 2 + 5;

        // Deposited / Withdrawn
        g.drawString(this.font, "Deposited:", col1X, y, LCARSRenderer.LAVENDER, false);
        g.drawString(this.font, String.valueOf(totalDeposited), col1X + 65, y, 0xFF00FF00, false);
        g.drawString(this.font, "Withdrawn:", col2X, y, LCARSRenderer.LAVENDER, false);
        g.drawString(this.font, String.valueOf(totalWithdrawn), col2X + 65, y, 0xFFFF6666, false);
        y += lineHeight;

        // Net contribution
        g.drawString(this.font, "Net:", col1X, y, LCARSRenderer.LAVENDER, false);
        int netColor = netContribution >= 0 ? 0xFF66FFFF : 0xFFFF6666;
        g.drawString(this.font, String.valueOf(netContribution), col1X + 65, y, netColor, false);

        // Free transports
        g.drawString(this.font, "Free Xports:", col2X, y, LCARSRenderer.LAVENDER, false);
        int freeColor = freeTransportsRemaining > 0 ? 0xFF00FF00 : 0xFFFF6666;
        g.drawString(this.font, freeTransportsRemaining + "/" + totalFreeTransportsEarned, col2X + 75, y, freeColor, false);
        y += lineHeight + 4;

        // Progress bar to next rank
        ContributorRank nextRank = highestRank.getNextRank();
        if (nextRank != null) {
            long stripsNeeded = nextRank.getThreshold() - netContribution;
            double progress = ContributorRank.getProgressToNextRank(netContribution);

            g.drawString(this.font, "Next: " + nextRank.getTitle(), col1X, y, LCARSRenderer.LAVENDER, false);
            g.drawString(this.font, "(" + stripsNeeded + " more)", col1X + 85, y, 0xFFAAAAAA, false);
            y += lineHeight;

            // Progress bar
            int barX = col1X;
            int barW = contentW - 10;
            int barH = 8;
            drawProgressBar(g, barX, y, barW, barH, progress);
            y += barH + 2;
        } else {
            g.drawString(this.font, "Maximum rank achieved!", col1X, y, LCARSRenderer.PEACH, false);
            y += lineHeight;
        }

        y += 8;

        // === LEADERBOARD ===
        drawCenteredText(g, "LEADERBOARD", contentX, contentW, y, LCARSRenderer.ORANGE);
        y += lineHeight + 4;

        // Separator line
        g.fill(contentX + 10, y, contentX + contentW - 10, y + 1, LCARSRenderer.LAVENDER);
        y += 4;

        if (leaderboard.isEmpty()) {
            drawCenteredText(g, "No contributions yet", contentX, contentW, y, 0xFF888888);
        } else {
            int rank = 1;
            for (LeaderboardEntry entry : leaderboard) {
                if (entry.netContribution() <= 0) continue;
                if (y > panelTop + PANEL_HEIGHT - 60) break; // Don't overflow

                // Rank indicator
                String rankPrefix;
                int rankColor;
                switch (rank) {
                    case 1 -> { rankPrefix = "1."; rankColor = 0xFFFFD700; } // Gold
                    case 2 -> { rankPrefix = "2."; rankColor = 0xFFC0C0C0; } // Silver
                    case 3 -> { rankPrefix = "3."; rankColor = 0xFFCD7F32; } // Bronze
                    default -> { rankPrefix = rank + "."; rankColor = 0xFFAAAAAA; }
                }

                g.drawString(this.font, rankPrefix, contentX + 5, y, rankColor, false);
                g.drawString(this.font, entry.playerName(), contentX + 22, y, 0xFFFFFFFF, false);

                // Right-aligned: rank title and net
                String info = entry.getRank().getTitle() + " (" + entry.netContribution() + ")";
                int infoWidth = this.font.width(info);
                g.drawString(this.font, info, contentX + contentW - infoWidth - 5, y, LCARSRenderer.LAVENDER, false);

                y += lineHeight;
                rank++;
            }
        }
    }

    private void drawCenteredText(GuiGraphics g, String text, int areaX, int areaW, int y, int color) {
        int textWidth = this.font.width(text);
        g.drawString(this.font, text, areaX + (areaW - textWidth) / 2, y, color, false);
    }

    private void drawProgressBar(GuiGraphics g, int x, int y, int w, int h, double progress) {
        // Background
        g.fill(x, y, x + w, y + h, 0xFF333333);

        // Fill
        int fillW = (int)(w * Math.min(1.0, progress));
        if (fillW > 0) {
            // Gradient from orange to gold
            g.fill(x, y, x + fillW, y + h, LCARSRenderer.ORANGE);
        }

        // Border
        g.fill(x, y, x + w, y + 1, LCARSRenderer.LAVENDER);
        g.fill(x, y + h - 1, x + w, y + h, LCARSRenderer.LAVENDER);
        g.fill(x, y, x + 1, y + h, LCARSRenderer.LAVENDER);
        g.fill(x + w - 1, y, x + w, y + h, LCARSRenderer.LAVENDER);

        // Percentage text
        String pctText = String.format("%.0f%%", progress * 100);
        int textX = x + (w - this.font.width(pctText)) / 2;
        g.drawString(this.font, pctText, textX, y, 0xFFFFFFFF, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
