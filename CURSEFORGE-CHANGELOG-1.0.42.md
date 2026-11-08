# NTM Doors 1.0.42

Minecraft 1.12.2 / Forge 14.23.5.2847+ (tested on 14.23.5.2860)
Requires Chisel 1.12.2-1.0.2.45

Changes since the CurseForge **1.0.41** release.

**File:** `ntmdoors-1.0.42.jar`
**SHA-256:** `52DB7A543639E90D7BB3FA2179CB7FEBD529FDE43CBB7E47AAC6F400952407BD`

## Ultra High Performance Concrete

- Added **Ultra High Performance Concrete**, a six-shade concrete family: the original texture plus
  five progressively darker grayscale shades.
- Every variant takes three real hours of continuous mining with any tool.
- Blast resistance is an explicit allow-list: only ICBM Classic **nuclear**, **antimatter** and
  **red matter** warheads can damage it. Thermobaric, TNT, creepers and every other explosion are
  fully resisted.
- Added a decorative **UHPC Panel** set with the same six textures and vanilla-concrete hardness,
  so it breaks and blasts apart normally.
- The hard set carries the `GBU-57 not included` tooltip; the soft panels do not.
- Registered under the `concrete` and `blockConcrete` ore dictionary entries and appended to the
  NTM Doors creative tab.

## Weapons Relay Key

- Added the **Weapons Relay Key**, a red creative key with the tooltip
  `Hard lock weapons so enemies can't disarm them`.
- Sneak + Right Click opens a mode screen.
- **Regular Bounding** links any explosive block to Redstone Gates: sneak + Right Click an
  explosive to start, then gates to link. A powered gate triggers the explosive immediately.
- **Dead Man's Hand** hard-locks the explosive: a player without the matching key detonates it by
  right-clicking, left-clicking, breaking it, or placing/breaking a block next to it. The key
  holder can mine it safely.
- Works with any explosive block, not only ICBM. Bindings are saved with the world and survive
  reloads.

## Spatial Disrupter

- Added the **Spatial Disrupter**, a decorative machine block: blue/red emblem on the sides and a
  grey plate on the top and bottom.
- Tooltip: `Weakens the structural integrity of UHPC`.
- Right-click opens a single-action screen with the warning
  `WARNING THIS OPERATION CANNOT BE REVERSED`.
- Activating it scans a **16x16 chunk** area and converts every hard UHPC block into the soft UHPC
  Panel of the same shade, then consumes itself with particles and a sound.
- The machine is bedrock-hard: it cannot be mined, blown up or pushed, and only its own completed
  operation removes it.
- The scan is incremental and skips unloaded chunks. A 131,072-block UHPC field converted in about
  4.6 seconds at 20 TPS in the dedicated-server stress test.

## Fixes and interface

- The soft UHPC Panel no longer shows the hard set's tooltip.
- Fixed the Spatial Disrupter self-destruct so it removes its own block outside the tile-entity
  tick loop, avoiding a server-side `ConcurrentModificationException`.
- Added the new blocks and key to the README and this changelog.

## Compatibility

- No existing block or item IDs were moved; all new content is registered last.
- Existing Security nets, device bindings, terminal backups and world saves remain compatible.
- Minecraft **1.12.2**, Forge **14.23.5.2847+** (tested on 14.23.5.2860).
- Requires **Chisel 1.12.2-1.0.2.45**; CTM is supplied by Chisel at runtime.
