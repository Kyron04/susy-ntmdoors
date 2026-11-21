# NTM Doors 1.0.80 - CurseForge Release

Minecraft 1.12.2 / Forge 14.23.5.2847+ (tested on 14.23.5.2860)
Requires Chisel 1.12.2-1.0.2.45

**File:** `ntmdoors-1.0.80.jar`
**SHA-256:** `A27208105F62666F523081ECD5CB1606368CAD53E2981D50356D42F5FA173140`

This release adds the Emancipation Grill, a paired security field based on the Portal 2 fizzler,
with five field modes, item filtering, a five-page console, Redstone event outputs, terminal and
gate integration, and Portal 2 fizzler audio. It also fixes translucent field rendering and render
state leaks between device special renderers.

## Highlights

- Vertical 1x3x1 and horizontal 3x1x1 Emancipation Grill variants. Two units pair across an opening
  up to 10 blocks apart; the first unit placed owns the pair and breaking any cell dismantles it.
- Five field modes: Solid blocks unauthorized players and mobs, Neutral does nothing, Lethal kills
  unauthorized players and mobs and erases non-living entities, Wall blocks every detected entity
  without clearance checks, and Filter lets everyone pass while applying item lists. Creative
  players are unaffected by Lethal contact.
- Filter Mode with 54 whitelist and 54 blacklist patterns. Whitelist wins; blacklisted stacks in
  player inventories, mob equipment and inventories, dropped items and nested containers are
  voided with the disintegration sound. JEI/HEI dragging sets patterns.
- Console pages for keys and biometric IDs, modes and detection toggles, field colour, five Redstone
  event outputs, and the filter grids. Owners open it with the Emancipation Relay Key or an
  Operator Keycard; Creative Keycards bypass ownership.
- Security terminal and Redstone Gate integration, including net-wide field raise/drop actions and
  one-tick event pulses for authorized passage, blocked entities, lethal kills and Match User
  checks.
- Portal 2 fizzler start, loop and shutdown sounds, plus a disintegration one-shot for lethal and
  filter voids, with a three-block radius and an Audio toggle.
- Fixed the field being overwritten by doors and devices, and reset GL state between device special
  renderers so keypads, scanners, programmers, monitors, motion sensors and doors no longer pick up
  a red tint or change shade with render order.

## Compatibility

- Existing 1.0.79 worlds load normally. The Emancipation Grill and filter lists default safely.
- Clients and servers must both update for the Grill console packet changes.
- Chisel 1.12.2-1.0.2.45 remains required. No NTM installation is required.
- JEI or HEI remains optional; the filter overlay loads only when one is present.
