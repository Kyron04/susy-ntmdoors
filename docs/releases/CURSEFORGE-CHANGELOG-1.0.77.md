# NTM Doors 1.0.77 — CurseForge Release

Minecraft 1.12.2 / Forge 14.23.5.2847+ (tested on 14.23.5.2860)
Requires Chisel 1.12.2-1.0.2.45

**File:** `ntmdoors-1.0.77.jar`
**SHA-256:** `0C59C713E633A7BC743801B7C93A4D16F6A1FA1EBC8EEA80FF2A1C169DB039A9`

This release collects every change since the 1.0.51 CurseForge build. Existing 1.0.51 worlds load
normally; clients and servers should update together. The 1.0.51 monitor URL and terminal-session
security fixes are included and unchanged.

## Highlights

- All 27 doors now have normal, locked and security recipes.
- Added Yellow, Blue and Red UAC Keycards and the O5 Keycard.
- Added the Undefined Relay Key and Breaker Relay Key.
- The Keycard Holder now carries keycards and relay keys in two 4x4 sections.
- Added six crafting components and recipes for the mod's components, cards, keys, machines and
  decorative blocks.
- Added desk-only and monitor-only decorative blocks.
- Fixed the Documents Folder model and editor text, the Vault-Tec closing sound, and several GUI
  and interaction bugs.

## Keycards, keys and safes

- **Yellow, Blue and Red UAC Keycards** — ordinary RFID cards using the supplied sprites, with the
  same private-key storage, viewer, scanner, holder and programmer-palette behaviour as the colour
  cards.
- **O5 Keycard** — a black RFID card using the supplied sprite. Its tooltip reads
  `For the 13 most powerful beings in the Foundation`.
- **Undefined Relay Key** — a grey programming key. Sneak + right-click opens a nine-entry picker
  for the six relay keys plus the Safe Binding, Terminal Backup and Breaker Relay Keys. Choosing a
  row replaces the held key with a fresh blank key of that type. It is crafted shapeless from a
  Circuit Board, an Uncased USB and an ID Chip.
- **Breaker Relay Key** — an orange relay key. Sneak + right-click a security door or safe to open
  a confirmation screen, then confirm to break it instantly. Ownerless security doors can be
  broken by anyone with the key; an owned security door or a safe is restricted to its recorded
  owner. Security doors drop their door item, and safes drop the safe item plus every stored stack
  even when Void on Destroy is enabled. Code safes now record their placer as owner, and biometric
  safes record the placer's biometric ID; legacy ownerless safes refuse the key.
- **Keycard Holder** — now two 4x4 grids: 16 keycards and 16 relay/utility keys. Each section has
  its own ghost icon and count, and the tooltip includes `Also holds Relay Keys`. Invalid hotbar
  and offhand swaps are rejected.
- **Safe Binding Key** — now requires the safe's owner or a player who has already unlocked it
  before starting a binding session. Legacy ownerless safes remain public.
- **Redstone Monocle** — wireless links are now drawn only when both endpoints are within four
  chunks of the player. This is a rendering limit; link behaviour and range are unchanged.
- Newly confirmed non-door devices in a Security net start with their switch OFF. Existing links
  and doors keep their current state.

## Crafting and components

- Added **ID Chip**, **Monitor Screen**, **Uncased USB**, **Circuit Board**, **Card Reader** and
  **RFID Chip**. The ID Chip, Monitor Screen and RFID Chip recipes produce two items. The five
  existing component sprites were replaced with the supplied 16x16 art, copied byte-for-byte.
- Added crafting recipes for the six components, craftable keycards, craftable utility keys,
  Keycard Holder, Documents Folder, security machines, monitor desks, decorative desks and
  monitor-only blocks.
- Added **desk-only blocks**: Single Desk (1x1), 1x2 Desk and 1x3 Desk. They reuse the monitor
  desk's bottom row with no monitors, terminal or tile entity, and the multi-block versions place
  and break as one structure.
- Added **monitor-only blocks**: Single Monitor (1x1), 1x2 Monitor and 1x3 Monitor. They reuse the
  monitor geometry without a desk, terminal or tile entity, and use the same half-depth collision
  as the monitor desks. A Single Monitor is crafted from a Monitor Screen, a Circuit Board and an
  iron ingot; the 1x2 and 1x3 versions combine two or three Single Monitors.
- Fixed the 1x2 and 1x3 Desk item icons to show the desk-only model instead of a keyboard and
  mouse.

## Door recipes

All 27 doors now have normal, locked and security recipes. Locked variants add two redstone;
security variants add two ID Chips. Office-door variants use full shaped recipes; industrial and
large-door upgrades are shapeless.

- **Office Door 1/2/3** — every single, 2x2 double and 3x3 form. Office Doors 1 and 3 use a
  wood-plank frame around a vanilla iron or wooden door; Office Door 2 uses a red concrete frame
  around a vanilla iron door.
- **Industrial doors** — Blast Shutter, Water Door, Sliding Seal, Cargo Door, QE Sliding Door and
  QE Sliding Panel. They use vanilla iron-door cores with iron bars, ingots, glass, iron blocks and
  pistons.
- **Large doors** — Vault Door, Sliding Blast Door, Fire Door, Secure Access Door, Round Airlock
  Door, both Silo Hatches, Large Vehicle Door, QE Containment, Blast Door, Transition Seal and
  Test Door. Most use vanilla iron-door cores; both Silo Hatches use iron blocks with an empty
  center (the smaller hatch also uses iron bars), and the Fire Door uses iron ingots instead of
  glass.

The 1.0.77 patterns are:

- **Silo Hatch** — `BKB / K0K / BKB`, where `B` is iron bars, `K` is an iron block and `0` is
  empty (4 bars, 4 blocks).
- **Large Silo Hatch** — `KKK / K0K / KKK`, eight iron blocks with an empty center.
- **Fire Door** — `IBI / KDK / IBI`, where `I` is an iron ingot, `B` is iron bars, `K` is an iron
  block and `D` is a vanilla iron door (4 ingots, 2 bars, 2 blocks, 1 door).

## Monitors, terminals and text

- Monitor-only blocks now use the same half-depth monitor collision as the monitor desks, leaving
  the front half of the cell walkable.
- The Keycard Programmer page-two layout was reworked: the RFID card slot is at the top-left, the
  card-style palette is on the right, and the palette shows six columns by four rows.
- The `CARD STYLE` label is right-aligned so it no longer overlaps the `RFID Card` heading.
- Tooltips in the Keycard Programmer, safes, Keycard Holder, Terminal Backup and Documents Folder
  now draw inside a scoped GL state save/restore. Hovering a slot no longer leaks lighting, depth,
  blend, alpha or rescale state into the next frame.
- The Documents Folder and Notepad editors draw all interface text without the vanilla font drop
  shadow. The Documents heading is a single flat label.
- The Documents Folder's right page edge now spans the full height of the folder, and the two
  transparent corner pixels used by the 3D front face are filled. The held model no longer has a
  missing corner.

## Fixes and polish

- The Vault-Tec door's closing scrape now starts on the first tick of the close instead of 30 ticks
  in, so the beginning of the closing movement is no longer silent.
- The Breaker Relay Key sneak-click now claims the high-priority block interaction event, so
  clicking a door cell opens the confirmation screen instead of being consumed by the door.
- Removed UTF-8 BOMs from 13 model JSON files.
- Corrected the SCP card level labels and several stale item, scanner and Keycard Holder
  descriptions.

## Compatibility

- Existing 1.0.51 worlds load normally. Blocks, items and tile entities keep their saved data.
- Clients and servers must both update. Older jars keep the old behavior.
- Chisel 1.12.2-1.0.2.45 remains required. No NTM installation is required.
