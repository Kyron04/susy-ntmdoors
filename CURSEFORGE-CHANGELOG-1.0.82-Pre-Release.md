# NTM Doors 1.0.82 Pre Release

Minecraft 1.12.2 / Forge 14.23.5.2847+ (tested on 14.23.5.2860)
Requires Chisel 1.12.2-1.0.2.45

**File:** `ntmdoors-1.0.82.jar`
**SHA-256:** `7B975FE5EA3CA8F11EC3C8C4A0F0F7EE9C3030E438A77438972814EA71A08FA9`

This pre-release collects every change since the 1.0.77 CurseForge build, covering the
1.0.78–1.0.82 development releases. It adds the Emancipation Grill, contraband containment, the
Security Shroud and dark mode for the document editors, plus the fixes accumulated since 1.0.77.
Existing 1.0.77 worlds load normally.

## Emancipation Grill

The Emancipation Grill is a new paired security field based on the Portal 2 fizzler.

- Vertical 1x3x1 and horizontal 3x1x1 variants. Two units pair across an opening up to 10 blocks
  apart. The first unit placed owns the pair and opens the console; breaking any cell dismantles the
  whole grill and returns one core item.
- Five field modes:
  - **Solid** blocks unauthorized players and mobs.
  - **Neutral** renders the field but does nothing.
  - **Lethal** kills unauthorized players and mobs and erases non-living entities. Creative players
    are unaffected. `Ignore Inventory` keeps a killed player's inventory and armor for the normal
    death drop. `Ignore Items` stops dropped items at the plane instead of erasing them.
  - **Wall** blocks every detected player and mob with no clearance checks, no killing and no item
    erasure.
  - **Filter** lets every entity pass and applies the page-five item lists.
- Solid and Wall fields also stop non-living entities and projectiles at the plane.
- Six-page console: keys and biometric IDs, modes and detection toggles, field colour (red, green or
  blue), five Redstone event outputs, the filter lists, and contraband containment. Owners open it
  with the Emancipation Relay Key or an Operator Keycard; a Creative Keycard bypasses ownership.
- Filter Mode has two 9x6 grids (54 whitelist and 54 blacklist patterns). JEI/HEI dragging sets a
  pattern, right-click clears one, and clicking a slot does not take the held hotbar item. The
  whitelist wins.
- Filter scanning covers the main inventory, armor, offhand, dropped item entities, mob equipment,
  villager inventories, horse/donkey/llama item handlers, standard mob inventories and nested
  containers. Creative and spectator players are skipped. Spawn egg patterns preserve their entity
  data, so the ghost icon keeps its colours.
- Blacklisted Filter Mode stacks are voided, or inserted into the bound contraband container when
  containment is enabled and the container can accept them. Loose dropped items keep the normal
  Filter Mode void behavior.
- **Contraband Containment** binds one container to the Grill. Enable the toggle, press Begin
  Binding Session, sneak + right-click a compatible container within 8 chunks in the same dimension,
  then return and sneak + right-click the original Grill to confirm. The binding is stored on the
  Grill's security core; the key only carries the five-minute pending session.
- Containers use Forge's `IItemHandler` capability. Vanilla chests, hoppers and furnaces work;
  safes, locked containers and automation-blocked blocks are rejected. Overflow, missing, unloaded,
  out-of-range or wrong-dimension containers void the remainder.
- Confiscated items show `Contraband confiscated` when everything was stored, or `Contraband
  eliminated` when any part was voided.
- Terminal and Redstone Gate integration: terminals can raise or drop a Grill field and run net-wide
  Drop all fields / Raise all fields actions. Gates can drive a field and receive the Grill's
  one-tick event pulses for authorized passage, blocked entities, lethal kills and Match User
  checks. Starting a Redstone Relay Key session on an owned Grill is owner-only.
- Security terminal access: click a net-linked Grill in the Devices tab to open its full console
  while you remain at the desk. The terminal session authorizes remote access, so no Relay Key is
  needed to open it; assigning keys still needs an Operator Keycard in the inventory and starting a
  contraband binding session still needs a Relay Key there. Escape returns to the same net.
- Portal 2 fizzler start, loop and shutdown sounds, plus a disintegration one-shot for lethal and
  filter voids. Three-block audible radius with an Audio toggle.
- The field visual uses scrolling wisp layers, edge lines and drifting sparks, with no shaders or
  per-frame texture uploads. Horizontal fields keep players and entities planted on the sheet and
  align with the emitter strip.

## Security Shroud

- Four-piece utility armor set with custom OBJ models, layer textures, item icons, armor-stand and
  crouch support. It is creative-only and has no recipes.
- 2500 durability per piece by default. Default protection is 3/8/6/3 for 20 armor points, the
  vanilla cap. Item names use rare (aqua) rarity, and tooltips show current/max durability, each
  piece's damage resistance and the effective full-set total.
- **Helmet** conceals the wearer's name tag.
- **Chestplate** ignores Dead Man's Hand weapons-relay bindings when interacting with bound
  explosives.
- **Leggings** grant 1.5x normal movement speed.
- **Boots** prevent a lethal fall from taking the wearer below one heart. The cap has a per-entity
  one-minute cooldown; falls during the cooldown deal full damage. The cooldown is configurable and
  the boots tooltip shows it.
- **Full-set Unity:** Motion Sensors and Emancipation Grills ignore the armored living entity. This
  applies to players and to armored mobs/enemies and covers sensor detection, Match User events,
  grill clearance, blocking, Lethal damage, Filter inventory scans and Match User signals.
- `config/ntmdoors.cfg` controls durability, protection, effect values, cooldowns and the per-slot
  armor item lists. Other mods' armor can be added to the same lists and mixed into a full set.

## Documents Folder and Notepad

- Added a `DARK` / `LIGHT` toggle to the Documents Folder and the monitor Notepad.
- The dark writing field uses `#3E434B` with white text. The Documents Folder also uses a
  complementary dark palette; the Notepad casing, sidebar and buttons keep their existing colours.
- The Documents Folder stores its theme in item NBT. The Notepad stores it on the desk, shares it
  with everyone using that terminal, and mirrors it on the physical monitor screen.
- The Notepad toggle applies immediately and the desk sync confirms it, so the page and physical
  screen no longer revert to light after a moment.

## Other fixes and changes

- Added an Aperture Laboratories wallpaper to the monitor Display task. The Display grid now uses
  four columns, so the original five wallpapers and the Terminal tile keep their height.
- Office Door 1 now uses the `office_door_1` registry name and assets, previously `black_mesa_door`.
  Existing 1.0.77 items and placed blocks are remapped automatically through Forge's missing
  mappings.
- Restored the Thermite Charge item texture.
- Added the missing concrete metadata blockstate variants, including asbestos, brick, rebar,
  colored/hazard and ultra-high-performance concrete blocks.
- Fixed the Emancipation field being overwritten by doors and devices. Device special renderers now
  start and end on a known GL baseline, so a tile entity rendered earlier in the frame cannot leave
  a tint, disabled lighting or changed depth/cull state behind.
- Fixed horizontal field rotation, emitter-strip alignment and player/entity movement on the field.
- Marked item tooltip overrides as client-only to avoid dedicated-server class-loading errors.
- Hardened the Emancipation Relay Key with a reach check and guarded malformed UUID NBT in keycard
  scanner and weapons key data.
- Hardened contraband container target validation before trusting an item-handler view.
- Removed a duplicate door security check and stale comments; no behavior change.

## Compatibility

- Existing 1.0.77 worlds load normally. The Office Door 1 rename is remapped automatically; no
  other registry IDs or saved NBT formats moved. New Emancipation Grill and Security Shroud items
  are appended registrations.
- `config/ntmdoors.cfg` is generated on first launch. Keep it identical on the client and server so
  name-tag, speed, fall-protection and Unity behavior agree.
- Clients and servers must both update. Older jars keep the old behavior.
- Chisel 1.12.2-1.0.2.45 remains required. No NTM installation is required.
- JEI or HEI remains optional; the Grill filter overlay loads only when one is present.

## Pre-release note

This build is marked as a pre-release so the Emancipation Grill and Security Shroud can be tested in
packs before a stable release. Please report issues with the mod version, a log and reproduction
steps.
