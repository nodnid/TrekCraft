# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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

## Architecture

### Core Systems

The mod has three main service classes in `service/`:
- **TransportService** - Teleportation logic, pre-flight checks, fuel consumption
- **RequestService** - Away team request handling (accept/decline/hold with timeouts)
- **ScanService** - 10x10x10 forward-facing area scan with quadrant-based results

### Fuel Cache System

The Transporter Room uses a dual-sync system to work when chunks are unloaded:
- `TransporterNetworkSavedData.cachedFuel` is the authoritative value for transport checks
- When room BlockEntity loads, it reconciles inventory to match cached fuel
- Teleports always deduct from cache; if BE is loaded, also remove from inventory

### One-Per-World Transporter Room

- Enforced in `TransporterRoomBlock.setPlacedBy()`
- `TransporterNetworkSavedData` tracks `transporterRoomPos`
- Placement denied if another room exists and is still valid

### Tricorder Identity

- Each tricorder has a UUID stored in `DataComponentType<TricorderData>`
- UUID assigned on first use via `TricorderItem.ensureTricorderData()`
- Dropped tricorders tracked as "signals" via `EntityJoinLevelEvent`/`EntityLeaveLevelEvent`

### Data Components (1.21+ Pattern)

Tricorder uses Data Components instead of NBT:
```java
DataComponentType.<TricorderData>builder()
    .persistent(TricorderData.CODEC)
    .networkSynchronized(TricorderData.STREAM_CODEC)
    .build()
```

### SavedData Pattern

`TransporterNetworkSavedData` extends `SavedData` and stores transporter room position, cached fuel, pad registry, dropped tricorder signals, and away team requests. Always access via `TransporterNetworkSavedData.get(serverLevel)` which routes to Overworld.

## Command Structure

All commands under `/trek`:
- `tricorder menu|name <text>`
- `transport listPads|toPad|listPlayers|toPlayer|listSignals|toSignal`
- `room status|locate|reset` (reset requires op level 2)
- `request send|accept|decline|hold|list`
- `scan`

## Event Subscriptions

In `TrekCraftEvents.java`:
- `RegisterCommandsEvent` → Register /trek commands
- `ServerTickEvent.Post` → Tick request timeouts
- `EntityJoinLevelEvent` → Track dropped tricorders as signals
- `EntityLeaveLevelEvent` → Remove signals when picked up

## Adding Content

### New Item
1. Register in `ModItems.java`
2. Add model: `assets/trekcraft/models/item/<name>.json`
3. Add texture: `assets/trekcraft/textures/item/<name>.png`
4. Add translation in `lang/en_us.json`
5. Add to `ModCreativeTabs.java`

### New Block
1. Register in `ModBlocks.java`
2. Register block item in `ModItems.java`
3. Add blockstate: `assets/trekcraft/blockstates/<name>.json`
4. Add block model, item model, loot table, translation

### New Command
1. Add to `TrekCommands.register()` using Brigadier
2. Handler returns `int` (1 = success, 0 = failure)
3. Use `ChatUi` for Trek-style message formatting

## Tricorder GUI Layout

The Tricorder uses an LCARS-style GUI in `TricorderScreen.java`:

**Panel Dimensions:**
- `PANEL_WIDTH = 250`, `PANEL_HEIGHT = 220`
- `BUTTON_WIDTH = 170`, `BUTTON_HEIGHT = 22`, `BUTTON_SPACING = 6`
- `BUTTON_Y_OFFSET = -16` (offset from contentY)

**Content Area:**
- The content area fits exactly **6 buttons** vertically (MAX_VISIBLE_BUTTONS = 6)
- Total vertical space: 6 × (22 + 6) = **168 pixels**
- Button positions: `buttonY = contentY + BUTTON_Y_OFFSET + (slot * (BUTTON_HEIGHT + BUTTON_SPACING))`
- Slots 0-5 are available; use slot 5 for action buttons on info screens

**Background:**
- Content area has a black background - use LCARS colors for text visibility
- Avoid `TEXT_DARK` (black) for text; use `ORANGE`, `PEACH`, `LAVENDER`, `BLUE`, `GREEN`, etc.

**Colors (from LCARSRenderer):**
- `ORANGE = 0xFFFF9966` - titles, highlights
- `PEACH = 0xFFFFCC99` - labels, available items
- `LAVENDER = 0xFFCC99CC` - body text, secondary info
- `BLUE = 0xFF9999FF` - rank info, emphasis
- `GREEN = 0xFF66CC66` - active status, XP, progress
- `RED = 0xFFCC4444` - warnings, abandon actions
- `GRAY = 0xFF666666` - locked/unavailable items

## Code Patterns

- Use records for immutable data (`TricorderData`, `PadRecord`, etc.)
- Prefer `Optional` over null returns for lookups
- All game logic server-side only - check `!level.isClientSide`
- Config values cached in static fields via `ModConfigEvent`
