package com.csquared.trekcraft;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber(modid = TrekCraftMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public class TrekCraftConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // Transport settings
    private static final ModConfigSpec.BooleanValue TRANSPORT_ENABLED = BUILDER
            .comment("Enable/disable the transport system")
            .define("transport.enabled", true);

    private static final ModConfigSpec.BooleanValue OVERWORLD_ONLY = BUILDER
            .comment("Restrict transport to Overworld only")
            .define("transport.overworldOnly", true);

    private static final ModConfigSpec.BooleanValue DENY_WHILE_RIDING = BUILDER
            .comment("Deny transport while player is riding/mounted")
            .define("transport.denyWhileRiding", true);

    private static final ModConfigSpec.IntValue TELEPORT_COST = BUILDER
            .comment("Number of Latinum Strips consumed per teleport")
            .defineInRange("transport.teleportCost", 1, 0, 64);

    private static final ModConfigSpec.IntValue SAFE_SEARCH_RADIUS = BUILDER
            .comment("Radius to search for safe landing spot")
            .defineInRange("transport.safeSearchRadius", 5, 1, 16);

    private static final ModConfigSpec.IntValue SAFE_SEARCH_VERTICAL = BUILDER
            .comment("Vertical range to search for safe landing spot")
            .defineInRange("transport.safeSearchVertical", 3, 1, 16);

    // Scan settings
    private static final ModConfigSpec.IntValue SCAN_COST = BUILDER
            .comment("Number of Latinum Slips consumed per scan")
            .defineInRange("scan.cost", 1, 0, 64);

    private static final ModConfigSpec.IntValue SCAN_COOLDOWN = BUILDER
            .comment("Scan cooldown in ticks (20 ticks = 1 second)")
            .defineInRange("scan.cooldownTicks", 100, 0, 6000);

    private static final ModConfigSpec.IntValue SCAN_RANGE = BUILDER
            .comment("Scan range in blocks (creates a cube of this size)")
            .defineInRange("scan.range", 10, 5, 32);

    // Request settings
    private static final ModConfigSpec.IntValue REQUEST_TIMEOUT = BUILDER
            .comment("Request timeout in ticks (20 ticks = 1 second)")
            .defineInRange("request.timeoutTicks", 200, 20, 6000);

    private static final ModConfigSpec.BooleanValue ALLOW_HOLD = BUILDER
            .comment("Allow players to hold requests for later")
            .define("request.allowHold", true);

    private static final ModConfigSpec.IntValue HELD_TIMEOUT = BUILDER
            .comment("Held request timeout in ticks")
            .defineInRange("request.heldTimeoutTicks", 6000, 200, 72000);

    // Creative bypass
    private static final ModConfigSpec.BooleanValue CREATIVE_BYPASS_FUEL = BUILDER
            .comment("Creative mode players bypass fuel requirements")
            .define("creative.bypassFuel", true);

    private static final ModConfigSpec.BooleanValue CREATIVE_BYPASS_SCAN_COST = BUILDER
            .comment("Creative mode players bypass scan cost")
            .define("creative.bypassScanCost", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    // Runtime config values
    public static boolean transportEnabled;
    public static boolean overworldOnly;
    public static boolean denyWhileRiding;
    public static int teleportCost;
    public static int safeSearchRadius;
    public static int safeSearchVertical;
    public static int scanCost;
    public static int scanCooldownTicks;
    public static int scanRange;
    public static int requestTimeoutTicks;
    public static boolean allowHold;
    public static int heldTimeoutTicks;
    public static boolean creativeBypassFuel;
    public static boolean creativeBypassScanCost;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        transportEnabled = TRANSPORT_ENABLED.get();
        overworldOnly = OVERWORLD_ONLY.get();
        denyWhileRiding = DENY_WHILE_RIDING.get();
        teleportCost = TELEPORT_COST.get();
        safeSearchRadius = SAFE_SEARCH_RADIUS.get();
        safeSearchVertical = SAFE_SEARCH_VERTICAL.get();
        scanCost = SCAN_COST.get();
        scanCooldownTicks = SCAN_COOLDOWN.get();
        scanRange = SCAN_RANGE.get();
        requestTimeoutTicks = REQUEST_TIMEOUT.get();
        allowHold = ALLOW_HOLD.get();
        heldTimeoutTicks = HELD_TIMEOUT.get();
        creativeBypassFuel = CREATIVE_BYPASS_FUEL.get();
        creativeBypassScanCost = CREATIVE_BYPASS_SCAN_COST.get();
    }
}
