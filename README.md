# TrekCraft

A Star Trek-themed Minecraft mod for NeoForge 1.21.1 that brings transporter technology and tricorder scanning to survival mode, making exploration and resource gathering more efficient.

## Features

### Transporter System
Fast-travel across your world using a network of transporter pads. Place pads at key locations, fuel your transporter room with Latinum Strips, and beam between destinations instantly.

- **Transporter Pads** - Place and name destination markers anywhere in the Overworld
- **Transporter Room** - Central hub that stores fuel and powers the network (one per world)
- **Signal Tracking** - Teleport to other players carrying tricorders or to dropped tricorders
- **Safe Landing** - Automatic safe spot detection prevents suffocation or fall damage

### Tricorder Scanning
Scan the area ahead of you to locate valuable resources without strip mining.

- **10x10x10 Area Scan** - Detects ores, spawners, containers, and mobs in your scan direction
- **3D Visualization** - Interactive display with layer-by-layer navigation
- **Resource Detection** - Finds coal, iron, copper, gold, redstone, lapis, diamond, emerald, and ancient debris

### Wormhole Portals
Create linked portal pairs for instant bidirectional travel between locations.

- **Cobblestone Frames** - Build rectangular frames and activate with a special tricorder
- **Bidirectional Links** - Step through one portal to arrive at its linked partner
- **Persistent Network** - Portals remain active across play sessions

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.72+

## Installation

1. Install NeoForge 1.21.1 for Minecraft
2. Download the TrekCraft mod JAR file
3. Place the JAR in your `mods` folder
4. Launch Minecraft with the NeoForge profile

## Getting Started

### Setting Up Transport

1. **Craft a Transporter Room** and place it in the Overworld - this is your network hub
2. **Stock it with Latinum Strips** - each teleport consumes fuel (configurable)
3. **Craft Transporter Pads** and place them at destinations you want to reach
4. **Name your pads** using an anvil or right-clicking with a name tag
5. **Craft a Tricorder** to access the transport menu and teleport between pads

### Using the Tricorder

Right-click with the tricorder to open the LCARS-style menu:
- **Pad List** - View and teleport to registered transporter pads
- **Signal List** - Track and teleport to other tricorders in the world
- **Scan Results** - View your most recent area scan

### Scanning for Resources

1. Hold a **Latinum Slip** in your inventory (consumed on scan)
2. Face the direction you want to scan
3. Run `/trek scan` or use the tricorder menu
4. Browse the 3D visualization to see what's ahead

### Creating Wormholes

1. Build a rectangular **cobblestone frame** with an air interior
2. Name a tricorder "Cleo" using `/trek tricorder name Cleo`
3. Right-click the frame with the Cleo tricorder to create a portal
4. Create a second portal elsewhere
5. Right-click an existing portal with the Cleo tricorder to open the linking screen
6. Select the destination portal to create a bidirectional link

## Items

| Item | Description |
|------|-------------|
| **Tricorder** | Multi-function tool for transport menu access and scanning |
| **Latinum Slip** | Currency consumed when scanning (1 per scan) |
| **Latinum Strip** | Fuel consumed when teleporting (1 per teleport) |

## Blocks

| Block | Description |
|-------|-------------|
| **Transporter Pad** | Teleportation destination marker - place and name these around your world |
| **Transporter Room** | Fuel storage and network controller - one per world, Overworld only |
| **Latinum Bar** | Decorative storage block |

## Commands

All commands use the `/trek` prefix.

### Tricorder
| Command | Description |
|---------|-------------|
| `/trek tricorder menu` | Open the tricorder menu screen |
| `/trek tricorder name <text>` | Rename the tricorder you're holding |

### Transport
| Command | Description |
|---------|-------------|
| `/trek transport listPads` | List all registered transporter pads |
| `/trek transport toPad <x> <y> <z>` | Teleport to a pad at the specified coordinates |
| `/trek transport listSignals` | List all tricorder signals (held and dropped) |
| `/trek transport toSignal <uuid>` | Teleport to a tricorder signal |

### Room
| Command | Description |
|---------|-------------|
| `/trek room status` | Show network status, fuel level, and range |
| `/trek room locate` | List all transporter rooms with coordinates |
| `/trek room reset` | Clear room registrations (requires op level 2) |

### Scanning
| Command | Description |
|---------|-------------|
| `/trek scan` | Perform a 10x10x10 scan in your facing direction |

## Configuration

The mod is configurable via the standard NeoForge config system.

### Transport Settings
- `transport.enabled` - Enable/disable the transport system
- `transport.teleportCost` - Latinum Strips consumed per teleport (default: 1)
- `transport.baseRange` - Range for player/signal transport (default: 500 blocks)
- `transport.padRange` - Range for pad transport (default: 1000 blocks)
- `transport.safeSearchRadius` - Safe landing search radius (default: 5 blocks)

### Scan Settings
- `scan.cost` - Latinum Slips consumed per scan (default: 1)
- `scan.cooldownTicks` - Cooldown between scans (default: 100 ticks / 5 seconds)
- `scan.range` - Scan area size (default: 10 blocks per axis)

### Creative Mode
- `creative.bypassFuel` - Creative players skip fuel requirements
- `creative.bypassScanCost` - Creative players skip scan costs

## Building from Source

```bash
# Clone the repository
git clone https://github.com/nodnid/trekcraft.git
cd trekcraft

# Build the mod
./gradlew build

# Run the client for testing
./gradlew runClient
```

The built JAR will be in `build/libs/`.

## License

MIT License - Do whatever you want with this code.

---

*Live long and prosper.*
