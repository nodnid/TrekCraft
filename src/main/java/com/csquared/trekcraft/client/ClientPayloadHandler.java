package com.csquared.trekcraft.client;

import com.csquared.trekcraft.client.screen.HolodeckControllerScreen;
import com.csquared.trekcraft.client.screen.NamingScreen;
import com.csquared.trekcraft.client.screen.TricorderScreen;
import com.csquared.trekcraft.network.OpenHolodeckScreenPayload;
import com.csquared.trekcraft.network.OpenNamingScreenPayload;
import com.csquared.trekcraft.network.OpenTricorderScreenPayload;
import com.csquared.trekcraft.network.ScanResultPayload;
import net.minecraft.client.Minecraft;

import java.util.List;

public class ClientPayloadHandler {

    // Cached scan results for "View Last Scan" functionality
    private static String cachedScanFacing = null;
    private static List<ScanResultPayload.ScannedBlock> cachedScanBlocks = null;
    private static List<ScanResultPayload.ScannedEntity> cachedScanEntities = null;

    public static boolean hasCachedScan() {
        return cachedScanBlocks != null;
    }

    public static String getCachedFacing() {
        return cachedScanFacing;
    }

    public static List<ScanResultPayload.ScannedBlock> getCachedBlocks() {
        return cachedScanBlocks;
    }

    public static List<ScanResultPayload.ScannedEntity> getCachedEntities() {
        return cachedScanEntities;
    }

    public static void openScreen(OpenTricorderScreenPayload payload) {
        Minecraft.getInstance().execute(() -> {
            Minecraft.getInstance().setScreen(
                    new TricorderScreen(
                            payload.fuel(),
                            payload.slips(),
                            payload.hasRoom(),
                            payload.pads(),
                            payload.signals()
                    )
            );
        });
    }

    public static void handleScanResult(ScanResultPayload payload) {
        // Cache the scan results before opening screen
        cachedScanFacing = payload.facing();
        cachedScanBlocks = payload.blocks();
        cachedScanEntities = payload.entities();

        Minecraft.getInstance().execute(() -> {
            // Open TricorderScreen in scan results mode
            Minecraft.getInstance().setScreen(
                    TricorderScreen.createForScanResults(payload.facing(), payload.blocks(), payload.entities())
            );
        });
    }

    public static void openNamingScreen(OpenNamingScreenPayload payload) {
        Minecraft.getInstance().execute(() -> {
            switch (payload.getNamingType()) {
                case TRICORDER -> {
                    if (payload.getTricorderId() != null) {
                        Minecraft.getInstance().setScreen(
                                NamingScreen.forTricorder(payload.getTricorderId(), payload.defaultName())
                        );
                    }
                }
                case PAD -> {
                    if (payload.getPadPos() != null) {
                        Minecraft.getInstance().setScreen(
                                NamingScreen.forPad(payload.getPadPos(), payload.defaultName())
                        );
                    }
                }
                case WORMHOLE -> {
                    if (payload.getWormholeId() != null) {
                        Minecraft.getInstance().setScreen(
                                NamingScreen.forWormhole(payload.getWormholeId(), payload.defaultName())
                        );
                    }
                }
            }
        });
    }

    public static void openWormholeLinkScreen(com.csquared.trekcraft.network.OpenWormholeLinkScreenPayload payload) {
        Minecraft.getInstance().execute(() -> {
            Minecraft.getInstance().setScreen(
                    new com.csquared.trekcraft.client.screen.WormholeLinkScreen(payload)
            );
        });
    }

    public static void openContributionScreen(com.csquared.trekcraft.network.OpenContributionScreenPayload payload) {
        Minecraft.getInstance().execute(() -> {
            Minecraft.getInstance().setScreen(
                    new com.csquared.trekcraft.client.screen.ContributionScreen(payload)
            );
        });
    }

    public static void openHolodeckScreen(OpenHolodeckScreenPayload payload) {
        Minecraft.getInstance().execute(() -> {
            Minecraft.getInstance().setScreen(
                    new HolodeckControllerScreen(payload)
            );
        });
    }

    // ===== Mission System Handlers =====

    public static void openStarfleetCommand(com.csquared.trekcraft.network.mission.OpenStarfleetCommandPayload payload) {
        Minecraft.getInstance().execute(() -> {
            // Update TricorderScreen if it's open
            if (Minecraft.getInstance().screen instanceof TricorderScreen tricorderScreen) {
                tricorderScreen.updateStarfleetRecord(
                        payload.rankName(),
                        payload.totalXp(),
                        payload.activeMissionCount(),
                        payload.completedMissionCount()
                );
            }
        });
    }

    public static void openMissionBoard(com.csquared.trekcraft.network.mission.OpenMissionBoardPayload payload) {
        Minecraft.getInstance().execute(() -> {
            // Update TricorderScreen if it's open
            if (Minecraft.getInstance().screen instanceof TricorderScreen tricorderScreen) {
                tricorderScreen.updateMissionBoard(payload.missions());
            }
        });
    }

    public static void openMissionLog(com.csquared.trekcraft.network.mission.OpenMissionLogPayload payload) {
        Minecraft.getInstance().execute(() -> {
            // Update TricorderScreen if it's open
            if (Minecraft.getInstance().screen instanceof TricorderScreen tricorderScreen) {
                tricorderScreen.updateMissionLog(payload.activeMissions());
            }
        });
    }

    public static void handleMissionProgressUpdate(com.csquared.trekcraft.network.mission.MissionProgressUpdatePayload payload) {
        // Update any open mission screens
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().screen instanceof TricorderScreen tricorderScreen) {
                // Request updated mission data
                // The screen will be refreshed when the payload arrives
            }
        });
    }

    public static void openServiceRecord(com.csquared.trekcraft.network.mission.OpenServiceRecordPayload payload) {
        Minecraft.getInstance().execute(() -> {
            // Update TricorderScreen if it's open
            if (Minecraft.getInstance().screen instanceof TricorderScreen tricorderScreen) {
                tricorderScreen.updateServiceRecord(payload);
            }
        });
    }

    public static void openMissionInfo(com.csquared.trekcraft.network.mission.OpenMissionInfoPayload payload) {
        Minecraft.getInstance().execute(() -> {
            // Update TricorderScreen if it's open
            if (Minecraft.getInstance().screen instanceof TricorderScreen tricorderScreen) {
                tricorderScreen.updateMissionInfo(payload);
            }
        });
    }
}
