# NTM Doors 1.0.82 Pre Release

Minecraft 1.12.2 / Forge 14.23.5.2847+ (tested on 14.23.5.2860)
Requires Chisel 1.12.2-1.0.2.45

**File:** `ntmdoors-1.0.82.jar`
**SHA-256:** `196989FD33162008D382ED4EAB4096C0C2C5CC6B7B12599ACDA55D7F986091D5`

This pre-release adds the Security Shroud armor set and its config system, plus fixes and behavior
changes since 1.0.81.

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

## Emancipation Grill

- Solid and Wall fields now stop non-living entities and projectiles at the plane.
- Lethal with `Ignore Items` now stops dropped items at the plane instead of erasing them.
- Confiscated Filter Mode items now show `Contraband confiscated` when the bound container accepts
  everything, or `Contraband eliminated` when any part is voided.
- Filter mode passes every entity; the living detection toggles still decide Solid, Lethal and Wall.

## Fixes

- Corrected the Security Shroud armor-stand head pivot and shared body/arm pivots so armor-stand
  renderers and 3D inventory previews such as Obscure Tooltips align correctly.
- Draw the armor double-sided like vanilla entity layers, so armor-stand previews no longer show the
  stand through the armor.
- Marked item tooltip overrides as client-only to avoid dedicated-server class-loading errors.
- Added the missing concrete metadata blockstate variants, including asbestos, brick, rebar,
  colored/hazard and ultra-high-performance concrete blocks.

## Compatibility

- Existing 1.0.81 worlds load normally. No registry IDs or NBT formats moved; the Security Shroud
  items are appended registrations.
- `config/ntmdoors.cfg` is generated on first launch. Keep it identical on the client and server so
  name-tag, speed, fall-protection and Unity behavior agree.
- Clients and servers must both update.
- Chisel 1.12.2-1.0.2.45 remains required. No NTM installation is required.
- JEI or HEI remains optional; the Grill filter overlay loads only when one is present.

## Pre-release note

This build is marked as a pre-release so the Security Shroud can be tested in packs before a stable
release. Please report issues with the mod version, a log and reproduction steps.
