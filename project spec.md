TrekCraft Transporters — Design Doc

Target: Minecraft Java 1.21.1, NeoForge
Primary goals: Teleportation first, simple UX, reliable long-distance use (works even when base chunks unload), fun Star Trek flavor.

0. Summary of new gameplay loop

Craft Latinum (slips/strips/bars).

Craft Tricorder (named items are encouraged).

Craft/place Transporter Pads (named destinations).

Craft/place exactly one Transporter Room (global controller + fuel source).

Put Latinum Strips into the Transporter Room to power teleports.

Use Tricorder to transport to pads / players / dropped tricorders, and to scan anomalies.

1. Content overview (registries)
1.1 Mod identifiers

modid: trekcraft (placeholder, must be consistent across code/resources)

1.2 New items

trekcraft:tricorder (non-stackable, has per-item data via Data Components)

trekcraft:latinum_slip

trekcraft:latinum_strip

1.3 New blocks

trekcraft:latinum_bar (storage block)

trekcraft:transporter_pad (destination marker, nameable)

trekcraft:transporter_room (global controller + fuel container, one per world)

1.4 Block entities

trekcraft:transporter_pad → TransporterPadBlockEntity (stores/display name; no fuel)

trekcraft:transporter_room → TransporterRoomBlockEntity (inventory + fuel tracking)

NeoForge registration approach

Use NeoForge registries and DeferredRegister (including the specialized variants for blocks/items).

2. Crafting and progression
2.1 Latinum crafting

Shapeless: 1 gold_ingot + 1 lapis_lazuli -> 18 latinum_slip

9 slips → 1 strip

9 strips → 1 latinum_bar (block)

Recommended quality-of-life

1 strip → 9 slips

1 bar → 9 strips

2.2 Tricorder recipe (early-ish)

2× trekcraft:latinum_strip

2× minecraft:iron_ingot

2.3 Transporter Pad recipe (as previously specified)

1× trekcraft:latinum_bar

1× minecraft:iron_ingot

1× minecraft:copper_ingot

1× minecraft:diamond

Balance note for agents: because the Transporter Room recipe uses 3 pads, this implies 3 diamonds + 3 latinum bars just to “turn on” transport. Keep as-spec for now, but the mod can optionally ship an alternate “cheaper pad” recipe behind a datapack later if this feels too steep.

2.4 Transporter Room recipe (new)

Crafted block: trekcraft:transporter_room
Recipe: 3 transporter_pad + 1 chest -> 1 transporter_room

The Transporter Room is the only fuel source.

Only one may exist per world at a time.

The transport system is disabled without an active Transporter Room.

3. Core rules (transport + scan)
3.1 Dimension restriction

All transport operations are Overworld only.

If source or destination is not the Overworld → fail with Trek-style message.

3.2 System enabled rule

Transport is enabled iff:

A valid Transporter Room exists in the Overworld (one-per-world rule), and

The Transporter Room has sufficient fuel (unless creative bypass is enabled).

3.3 Fuel rule

Teleport cost: 1 Latinum Strip per successful teleport

Fuel is paid by the Transporter Room (not pads, not player inventory)

Fuel is consumed only after teleport success

3.4 Riding rule

Transport is not allowed while riding (player is a passenger or has a vehicle).

Fail message: “Pattern lock failed: dismount required.”

3.5 Allowed destinations

From Tricorder, player can transport to:

Transporter Pad (named list)

Another player (if the target player has a tricorder anywhere in inventory)

Dropped tricorder signal (tricorder item entity on the ground)

3.6 Away team requests

Anyone can request anyone (private server assumption)

Recipient has Accept / Decline / Hold

Requests timeout after 10 seconds unless held

No stacking requests: one pending/held request per recipient

3.7 Scan rules

Forward-facing 10×10×10

Finds anomalies: ores, containers, spawners, players, mobs

Summarizes by quadrants (Gamma/Delta/Alpha/Beta)

Cooldown: 5 seconds

Cost: 1 Latinum Slip from player inventory (consumed on successful scan)

4. Persistence and data model
4.1 Tricorder identity + naming (Data Components)

Minecraft 1.21 uses Data Components for per-ItemStack custom data; NeoForge documents registering persistent/networked components via persistent(Codec) and networkSynchronized(StreamCodec).

Tricorder data component

Register a DataComponentType<TricorderData> as trekcraft:tricorder_data.

TricorderData fields

UUID tricorderId (required; stable identity)

Optional<String> label (optional; may mirror hover name, but keeping it explicit helps)

Naming tricorders

Players can rename tricorders via anvil (hover name), and/or:

Command /trek tricorder name <text> sets the hover name and label field

List displays prefer:

hover name, else

label, else

fallback Tricorder-AB12 (UUID prefix)

4.2 Transporter network SavedData (world-wide)

Use NeoForge Saved Data to persist global state on the Overworld (pads, room location, known signals, request state). NeoForge explicitly documents SavedData for saving additional level data.

SavedData: TransporterNetworkSavedData (Overworld only)

Fields

Optional<BlockPos> transporterRoomPos

int roomFuelStripsCached

authoritative fuel count for transport checks/deductions even if room chunk is unloaded

Map<BlockPos, PadRecord> pads

Map<UUID, SignalRecord> signals (dropped tricorders)

Map<UUID, RequestRecord> incomingRequestsByRecipient (or separate service with serialization)

PadRecord

BlockPos pos

String name

long createdGameTime (optional)

SignalRecord

UUID tricorderId

String displayName

BlockPos lastKnownPos

long lastSeenGameTime

RequestRecord

UUID requester

UUID recipient

BlockPos requesterAnchorPos (or requester UUID only; compute at accept-time)

long createdGameTime

long expiresGameTime

Status enum: PENDING | HELD

4.3 Block entities (pads + transporter room)

NeoForge documents block entities as the correct place for inventories and complex per-block state.

TransporterPadBlockEntity

Responsibilities:

store/display pad name locally

write name into SavedData pad map (for global lists even when chunks unload)

No fuel inventory.

TransporterRoomBlockEntity

Responsibilities:

Inventory that accepts only latinum_strip stacks (fuel)

On creation/placement:

enforce one per world via SavedData

register transporterRoomPos

Keep SavedData roomFuelStripsCached accurate and consistent

Inventory implementation

Use a Container-style inventory (NeoForge docs explain Container usage for item storage).

Optionally expose capability for hoppers (up to you; default can allow hoppers for convenience)

5. One-per-world Transporter Room rules
5.1 Placement behavior

When a player places a transporter_room:

If placed outside Overworld → cancel placement (or immediately drop as item) with message

If SavedData has transporterRoomPos and that position currently contains a transporter_room block:

deny placement, drop item back, message: “There is only one Transporter Room per world.”

Else:

set transporterRoomPos = thisPos, initialize cached fuel from its inventory

5.2 Breaking behavior

When transporter room is broken:

clear transporterRoomPos

cached fuel becomes 0 (or preserved until new placement — recommend reset to 0 for simplicity and to avoid “ghost fuel”)

transport immediately becomes disabled

5.3 Fuel cache consistency strategy

Goal: transport must still work when the Transporter Room chunk is unloaded.

Authoritative value for transport checks: roomFuelStripsCached in SavedData.

Sync rules

When room BE is loaded:

“Reconcile” its inventory to match roomFuelStripsCached by removing excess strips if necessary.

Then on any inventory change, recompute and set roomFuelStripsCached = countStrips(inventory).

When a teleport occurs:

Always check/deduct from roomFuelStripsCached (SavedData)

If room BE is loaded, also remove 1 strip from inventory

If room BE is not loaded, only decrement cache; reconciliation will remove the corresponding strip later when room loads

This yields consistent fuel use without requiring the room chunk to stay loaded.

6. UX and commands (simple + robust)
6.1 Tricorder right-click menu (chat-based)

On use (server-side), send clickable chat menu:

[Transport → Pad]

[Transport → Player]

[Transport → Signal]

[Scan]

[Requests] (optional quick view)

Fuel: <cachedFuel> strips (informational line)

Buttons run commands via RUN_COMMAND.

6.2 Commands registration

NeoForge exposes RegisterCommandsEvent for registering Brigadier commands.

Root command: /trek

Tricorder

/trek tricorder menu

/trek tricorder name <text>

Transport

/trek transport listPads

/trek transport toPad <x> <y> <z>

/trek transport listPlayers

/trek transport toPlayer <player>

/trek transport listSignals

/trek transport toSignal <tricorderUUID>

Transporter Room

/trek room status (shows pos, fuel)

/trek room locate (prints coordinates)

/trek room reset (operator-only safety valve; clears saved room pos + fuel if bugged)

Requests

/trek request send <player>

/trek request accept

/trek request decline

/trek request hold

/trek request acceptNow (alias accept if held)

Scan

/trek scan

7. Transport logic and safety
7.1 Pre-flight checks (all teleports)

Server-side validate:

Transporter Room exists and is valid

Overworld-only

roomFuelStripsCached >= 1 (unless creative bypass)

player is not riding

destination is valid (pad exists / player online+has tricorder / signal still exists)

safe landing location exists

7.2 Safe landing search

Implement a safe-spot finder:

Spiral around anchor position (radius configurable)

Check:

solid block below

2-block-high collision-free space

avoid lava/fire/cactus/damaging blocks (basic blacklist)

If none found: fail message “No safe rematerialization point found.”

7.3 Signal (dropped tricorder) tracking events

Use NeoForge events. NeoForge docs explain the general event system.

For signal tracking:

Subscribe to EntityJoinLevelEvent and EntityLeaveLevelEvent

Important: EntityJoinLevelEvent may fire before the chunk is FULL; its Javadoc warns that heavy world interaction can cause chunk-loading deadlocks—so do minimal work there (just record pos/name/UUID; verify entities later when teleporting).

8. Scan system details
8.1 Volume orientation

Use player horizontal facing (N/E/S/W). Compute a 10×10×10 volume “in front” of player.

8.2 Quadrants

Near vs Far: depth 1–5 vs 6–10

Left vs Right: relative to facing

Quadrant names:

Gamma = far-left

Delta = far-right

Alpha = near-left

Beta = near-right

8.3 Anomaly definitions

Ores: via block tags (e.g., #trekcraft:scan_ores). NeoForge documents tags as lists used for membership checks.

Containers: blocks with container-like block entities

Spawners: mob spawner block

Entities: players, mobs (LivingEntity excluding players)

8.4 Scan cost/cooldown

cooldown 5s/player

consume 1 slip on success (or bypass in creative if enabled)

9. Configuration (server-focused)

NeoForge config should expose:

Transport enable/disable

Overworld-only toggle

Riding denial toggle

Teleport cost item + amount (default: strip ×1)

Scan cost item + amount (default: slip ×1)

Scan cooldown

Request timeout

Allow hold

List size limits

Safe search radius/vertical range

Creative bypass toggles

(Agents: implement as server/common config; keep defaults matching spec.)

10. Suggested code layout

com.yourname.trekcraft

TrekCraftMod (main)

registry/ (ModItems, ModBlocks, ModBlockEntities, ModDataComponents)

content/item/TricorderItem

content/block/TransporterPadBlock

content/block/TransporterRoomBlock

content/blockentity/TransporterPadBlockEntity

content/blockentity/TransporterRoomBlockEntity

data/TransporterNetworkSavedData

service/TransportService

service/RequestService

service/ScanService

util/SafeTeleportFinder

util/FacingMath

util/ChatUi

command/TrekCommands

Implementation Checklist (updated)
A. Project setup

 NeoForge 1.21.1 workspace boots, “hello mod” runs

 Choose final modid and package name

B. Registries and assets

 Register items: latinum_slip, latinum_strip, tricorder

 Register blocks: latinum_bar, transporter_pad, transporter_room

 Register block entities: pad BE, room BE

 Basic textures/models/lang (placeholders acceptable)

 JSON recipes:

 Latinum conversion recipes (and recommended reverse)

 Tricorder recipe

 Pad recipe

 Transporter Room recipe (3 pads + chest)

 Loot tables for blocks

C. Data Components (tricorder identity + label)

 Register trekcraft:tricorder_data via DataComponentType builder with persistence + network sync

 Ensure each tricorder gets a UUID on craft/first use if missing

 Implement /trek tricorder name <text> sets hover name + label

D. SavedData: transporter network + room

 Implement TransporterNetworkSavedData for Overworld

 Track:

 transporterRoomPos

 roomFuelStripsCached

 pad records (name + pos)

 signal records (dropped tricorders)

 request records (optional persistent; can be runtime-only too)

E. Transporter Pad

 Place/break updates pad map in SavedData

 Naming:

 anvil rename on place uses item hover name

 Name Tag interaction to rename

 No fuel logic

F. Transporter Room (global controller)

 On placement:

 deny if not Overworld

 enforce one-per-world using SavedData (deny placement if active room exists elsewhere)

 set transporterRoomPos

 On break:

 clear transporterRoomPos

 set cached fuel to 0 (or explicitly defined behavior)

 Implement inventory using Container concepts

 Inventory only accepts latinum_strip (recommended)

 Sync strategy:

 onLoad reconcile inventory to cached fuel

 on inventory change set cached fuel = inventory strip count

G. Events: dropped tricorder signals

 Subscribe to NeoForge event bus (Events system)

 EntityJoinLevelEvent:

 if ItemEntity is tricorder, record/update signal (minimal work only)

 avoid heavy world interactions (chunk may not be FULL)

 EntityLeaveLevelEvent:

 mark/remove signal record

H. Commands + chat UI

 Register /trek commands in RegisterCommandsEvent

 Tricorder use sends clickable menu (RUN_COMMAND actions)

 Add /trek room status|locate|reset

I. Transport service

 Implement pre-flight checks:

 room exists + overworld-only

 cached fuel >= 1 (unless creative bypass)

 not riding

 destination valid

 Implement safe landing finder

 Deduct fuel:

 decrement cached fuel on success

 if room BE loaded, also remove strip from inventory

 Implement transports:

 to pad

 to player

 to signal

J. Away team requests

 Implement RequestService (one request per recipient)

 Prompt recipient with Accept/Decline/Hold

 Timeout after 10s unless HELD

 Accept teleports recipient near requester (uses TransportService)

 Fuel paid by Transporter Room (same as all teleports)

K. Scan service

 Implement forward 10×10×10 scan with quadrants

 Add tag trekcraft:scan_ores for ore membership

 Detect containers/spawners/entities

 Cooldown per-player 5s

 Consume 1 slip on success

L. Config

 Server/common config for:

 enable transport, overworld-only, deny-while-riding

 teleport cost item+amount (default strip×1)

 scan cost/cooldown/range

 request timeout/hold

 creative bypass

 safe search parameters

Notes for AI agents (important implementation constraints)

Server-authoritative: all checks, fuel deduction, and teleports must occur server-side.

Signal events caution: do not trigger chunk loads or deep world reads inside EntityJoinLevelEvent (its docs warn this can deadlock).

SavedData is the authoritative “always accessible” state for the Transporter Room’s fuel and existence.

Data Components are the correct way to store per-tricorder UUID/label in 1.21+.