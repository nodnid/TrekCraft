package com.csquared.trekcraft.client;

import com.csquared.trekcraft.client.screen.TricorderScreen;
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
}
