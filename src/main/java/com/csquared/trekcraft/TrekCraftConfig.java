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

    private static final ModConfigSpec.IntValue TRANSPORT_BASE_RANGE = BUILDER
            .comment("Base range in blocks for transport to players/signals")
            .defineInRange("transport.baseRange", 500, 100, 10000);

    private static final ModConfigSpec.IntValue TRANSPORT_PAD_RANGE = BUILDER
            .comment("Range in blocks for transport to pads (should be higher than base)")
            .defineInRange("transport.padRange", 1000, 100, 20000);

    private static final ModConfigSpec.BooleanValue TRACK_HELD_TRICORDERS = BUILDER
            .comment("Track tricorders in player inventory as signals")
            .define("transport.trackHeldTricorders", true);

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

    // Creative bypass
    private static final ModConfigSpec.BooleanValue CREATIVE_BYPASS_FUEL = BUILDER
            .comment("Creative mode players bypass fuel requirements")
            .define("creative.bypassFuel", true);

    private static final ModConfigSpec.BooleanValue CREATIVE_BYPASS_SCAN_COST = BUILDER
            .comment("Creative mode players bypass scan cost")
            .define("creative.bypassScanCost", true);

    // Mobile emitter settings
    private static final ModConfigSpec.IntValue MOBILE_EMITTER_MAX_X = BUILDER
            .comment("Maximum X dimension for mobile emitter holo-area")
            .defineInRange("mobileEmitter.maxX", 256, 16, 1024);

    private static final ModConfigSpec.IntValue MOBILE_EMITTER_MAX_Z = BUILDER
            .comment("Maximum Z dimension for mobile emitter holo-area")
            .defineInRange("mobileEmitter.maxZ", 256, 16, 1024);

    private static final ModConfigSpec.IntValue MOBILE_EMITTER_MAX_Y = BUILDER
            .comment("Maximum Y height for mobile emitter holo-area (from emitter to world height)")
            .defineInRange("mobileEmitter.maxY", 128, 16, 384);

    public static final ModConfigSpec SPEC = BUILDER.build();

    // Runtime config values
    public static boolean transportEnabled;
    public static boolean denyWhileRiding;
    public static int teleportCost;
    public static int safeSearchRadius;
    public static int safeSearchVertical;
    public static int transportBaseRange;
    public static int transportPadRange;
    public static boolean trackHeldTricorders;
    public static int scanCost;
    public static int scanCooldownTicks;
    public static int scanRange;
    public static boolean creativeBypassFuel;
    public static boolean creativeBypassScanCost;
    public static int mobileEmitterMaxX;
    public static int mobileEmitterMaxZ;
    public static int mobileEmitterMaxY;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        transportEnabled = TRANSPORT_ENABLED.get();
        denyWhileRiding = DENY_WHILE_RIDING.get();
        teleportCost = TELEPORT_COST.get();
        safeSearchRadius = SAFE_SEARCH_RADIUS.get();
        safeSearchVertical = SAFE_SEARCH_VERTICAL.get();
        transportBaseRange = TRANSPORT_BASE_RANGE.get();
        transportPadRange = TRANSPORT_PAD_RANGE.get();
        trackHeldTricorders = TRACK_HELD_TRICORDERS.get();
        scanCost = SCAN_COST.get();
        scanCooldownTicks = SCAN_COOLDOWN.get();
        scanRange = SCAN_RANGE.get();
        creativeBypassFuel = CREATIVE_BYPASS_FUEL.get();
        creativeBypassScanCost = CREATIVE_BYPASS_SCAN_COST.get();
        mobileEmitterMaxX = MOBILE_EMITTER_MAX_X.get();
        mobileEmitterMaxZ = MOBILE_EMITTER_MAX_Z.get();
        mobileEmitterMaxY = MOBILE_EMITTER_MAX_Y.get();
    }
}
