# TrekCraft - Claude Code Guide

## Project Overview

TrekCraft is a Star Trek themed Minecraft mod for NeoForge 1.21.1. It implements a teleportation system with Transporter Pads, a global Transporter Room controller, Tricorder tools, and scanning functionality.

**Mod ID:** `trekcraft`
**Package:** `com.csquared.trekcraft`
**Minecraft:** 1.21.1
**NeoForge:** 21.1.72

## Build Commands

```bash
./gradlew build          # Compile the mod
./gradlew runClient      # Launch Minecraft with the mod
./gradlew runServer      # Launch dedicated server
./gradlew runData        # Generate data (recipes, loot tables, etc.)
```

## Project Structure

```
src/main/java/com/csquared/trekcraft/
├── TrekCraftMod.java           # Main mod entry point
├── TrekCraftConfig.java        # Server configuration
├── TrekCraftEvents.java        # NeoForge event handlers
├── registry/                   # Deferred registries
│   ├── ModItems.java           # Items (latinum_slip, latinum_strip, tricorder)
│   ├── ModBlocks.java          # Blocks (latinum_bar, transporter_pad, transporter_room)
│   ├── ModBlockEntities.java   # Block entity types
│   ├── ModDataComponents.java  # Data components (tricorder_data)
│   └── ModCreativeTabs.java    # Creative mode tab
├── content/
│   ├── item/
│   │   └── TricorderItem.java  # Tricorder with UUID identity
│   ├── block/
│   │   ├── TransporterPadBlock.java   # Destination marker
│   │   └── TransporterRoomBlock.java  # Global controller (one per world)
│   └── blockentity/
│       ├── TransporterPadBlockEntity.java   # Stores pad name
│       └── TransporterRoomBlockEntity.java  # Fuel inventory + cache sync
├── data/
│   ├── TricorderData.java              # Record with UUID + label
│   └── TransporterNetworkSavedData.java # World-level persistence
├── service/
│   ├── TransportService.java   # Teleportation logic + fuel consumption
│   ├── RequestService.java     # Away team request handling
│   └── ScanService.java        # 10x10x10 area scanning
├── command/
│   └── TrekCommands.java       # /trek command tree
└── util/
    ├── ChatUi.java             # Clickable chat menus
    └── SafeTeleportFinder.java # Safe landing spot detection
```

## Key Architecture Decisions

### Fuel Cache System
The Transporter Room uses a **dual-sync fuel system** to work when chunks are unloaded:
- `TransporterNetworkSavedData.cachedFuel` is the authoritative value for transport checks
- When room BE loads, it reconciles inventory to match cached fuel
- Teleports always deduct from cache; if BE is loaded, also remove from inventory

### One-Per-World Transporter Room
- Enforced in `TransporterRoomBlock.setPlacedBy()`
- SavedData tracks `transporterRoomPos`
- Placement denied if another room exists and is still valid

### Tricorder Identity
- Each tricorder has a UUID stored in `DataComponentType<TricorderData>`
- UUID assigned on first use via `TricorderItem.ensureTricorderData()`
- Dropped tricorders tracked as "signals" via entity events

## Data Components (1.21+ Pattern)

Tricorder uses the new Data Components system instead of NBT:
```java
// Registration in ModDataComponents.java
DataComponentType.<TricorderData>builder()
    .persistent(TricorderData.CODEC)
    .networkSynchronized(TricorderData.STREAM_CODEC)
    .build()
```

## SavedData Pattern

`TransporterNetworkSavedData` extends `SavedData` and stores:
- Transporter room position + cached fuel
- Map of pad positions → names
- Map of dropped tricorder UUIDs → signal info
- Map of player UUIDs → away team requests

Always access via `TransporterNetworkSavedData.get(serverLevel)` which routes to Overworld.

## Command Structure

All commands under `/trek`:
- `tricorder menu|name <text>`
- `transport listPads|toPad|listPlayers|toPlayer|listSignals|toSignal`
- `room status|locate|reset` (reset requires op)
- `request send|accept|decline|hold|list`
- `scan`

## Event Subscriptions

In `TrekCraftEvents.java`:
- `RegisterCommandsEvent` → Register /trek commands
- `ServerTickEvent.Post` → Tick request timeouts
- `EntityJoinLevelEvent` → Track dropped tricorders as signals
- `EntityLeaveLevelEvent` → Remove signals when picked up

## Resources

```
src/main/resources/
├── META-INF/neoforge.mods.toml    # Mod metadata
├── assets/trekcraft/
│   ├── blockstates/               # Block state definitions
│   ├── models/block/              # Block models
│   ├── models/item/               # Item models
│   ├── textures/block/            # Block textures (16x16 PNG)
│   ├── textures/item/             # Item textures (16x16 PNG)
│   └── lang/en_us.json            # Translations
└── data/trekcraft/
    ├── recipe/                    # Crafting recipes (JSON)
    ├── loot_table/blocks/         # Block drops
    └── tags/block/scan_ores.json  # Ores detected by scan
```

## Configuration

Server config in `TrekCraftConfig.java`:
- `transport.enabled` - Enable/disable transport system
- `transport.overworldOnly` - Restrict to Overworld
- `transport.teleportCost` - Strips per teleport (default: 1)
- `scan.cost` - Slips per scan (default: 1)
- `scan.cooldownTicks` - Scan cooldown (default: 100 = 5s)
- `request.timeoutTicks` - Request timeout (default: 200 = 10s)
- `creative.bypassFuel` - Creative mode skips fuel

## Common Modifications

### Adding a new item
1. Register in `ModItems.java` using `ITEMS.register()` or `ITEMS.registerSimpleItem()`
2. Add model in `assets/trekcraft/models/item/<name>.json`
3. Add texture in `assets/trekcraft/textures/item/<name>.png`
4. Add translation in `lang/en_us.json`
5. Add to creative tab in `ModCreativeTabs.java`

### Adding a new block
1. Register block in `ModBlocks.java`
2. Register block item in `ModItems.java`
3. Add blockstate in `assets/trekcraft/blockstates/<name>.json`
4. Add block model in `models/block/<name>.json`
5. Add item model (usually parent of block model)
6. Add loot table in `data/trekcraft/loot_table/blocks/<name>.json`
7. Add translation

### Adding a new command
1. Add to `TrekCommands.register()` using Brigadier
2. Create handler method returning `int` (1 = success, 0 = failure)
3. Use `ChatUi` for consistent message formatting

## Testing Tips

- Use Creative mode to bypass fuel requirements
- `/trek room status` shows current fuel and room location
- `/trek room reset` clears room data if bugged (op only)
- Dropped tricorders appear in signal list after ~1 second
- Transporter pads can be renamed with Name Tags

## Code Style

- Use records for immutable data (`TricorderData`, `PadRecord`, etc.)
- Prefer `Optional` over null returns for lookups
- Server-side logic only - check `!level.isClientSide` before game logic
- Use `ChatUi` for player messages with Trek-style formatting
- Config values cached in static fields, updated via `ModConfigEvent`
