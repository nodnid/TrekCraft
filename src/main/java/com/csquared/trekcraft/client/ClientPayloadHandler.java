package com.csquared.trekcraft.client;

import com.csquared.trekcraft.client.screen.TricorderScreen;
import com.csquared.trekcraft.network.OpenTricorderScreenPayload;
import com.csquared.trekcraft.network.ScanResultPayload;
import net.minecraft.client.Minecraft;

public class ClientPayloadHandler {

    public static void openScreen(OpenTricorderScreenPayload payload) {
        Minecraft.getInstance().execute(() -> {
            Minecraft.getInstance().setScreen(
                    new TricorderScreen(
                            payload.fuel(),
                            payload.hasRoom(),
                            payload.pads(),
                            payload.signals()
                    )
            );
        });
    }

    public static void handleScanResult(ScanResultPayload payload) {
        Minecraft.getInstance().execute(() -> {
            // Open TricorderScreen in scan results mode
            Minecraft.getInstance().setScreen(
                    TricorderScreen.createForScanResults(payload.facing(), payload.blocks())
            );
        });
    }
}
