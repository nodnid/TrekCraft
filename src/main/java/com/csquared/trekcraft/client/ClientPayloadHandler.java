package com.csquared.trekcraft.client;

import com.csquared.trekcraft.client.screen.TricorderScreen;
import com.csquared.trekcraft.network.OpenTricorderScreenPayload;
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
}
