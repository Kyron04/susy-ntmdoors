# NTM Doors 1.0.31

Minecraft 1.12.2 · Forge 14.23.5.2847  
Requires Chisel 1.12.2-1.0.2.45

Cumulative changes since 1.0.30.

## Redstone Gate and Relay Key

- Added the **Redstone Gate**, a full-cube redstone component with separate off and on textures. It starts off, turns on while powered, and emits a full-strength signal.
- Added the **Redstone Relay Key** for wireless links between gates and safes, scanners, keypads and speakers.
- Devices and gates can be linked many-to-many in the same dimension, with no range limit.
- Breaking a device or gate clears its reciprocal wireless links. Existing safe-to-door, scanner-to-door, keypad and safe-binding links are untouched.
- Added safe and scanner **Redstone pages** with red panel tinting, scrolling and event toggles:
  - Code Safe: Code fail, Code success, Binding activities, Lockout.
  - Biometric Safe: Scan fail, Scan success, Match holder fail, Match holder success, Lockout.
  - Keycard Scanner: Scan fail, Scan success, Match holder fail, Match holder success, Private key fail, Private key success, Biometric fail, Biometric success, Lockout.
- Event toggles pulse every linked gate for the gate's configured activation duration.
- Added the **Redstone Gate screen** for Operator and Creative Keycards:
  - **Stay active after signal**
  - **Invert operation**
  - Activation duration controls from 1 second to 5 minutes using +1/+5/+10/+20 second and matching subtract buttons.
- Inverted gates stay powered without input and switch off while a signal is present, including wireless pulses and connected redstone.
- Wireless events use an absolute expiry so pulses keep their full duration and additional events extend them.
- Normal and locked doors now respond to redstone. Security doors still require a keycard scanner or the Creative Keycard, and lockdown still blocks redstone edges.

## Keypad

- Keypads now work with the Redstone Relay Key. A normal click starts or finishes the wireless gate session.
- Sneak + right-click opens a two-page keypad screen:
  - Page one sets the first door code.
  - Page two has **On fail** / **On success** gate outputs, **Reset Code**, **Override Door Locks** and **Force Close**.
- Added a separate **keypad security code** for the Redstone Relay Key screen. The first entry sets it; later entries must match before the keypad screen opens.
- The security code is independent of the normal door code.
- Keypads can bind and drive locked doors. Security doors remain off limits, and a door is never driven by both a keypad and a scanner.

## Security doors and safes

- Added security variants for both silo hatches. All sixteen doors now have a `_security` form.
- Safes and security doors now take three real hours of continuous mining with any tool, with blast resistance raised so explosions cannot shortcut that.
- Normal and locked doors keep their existing break times.
- Safes no longer expose their slots to hoppers or logistics automation. Their sided inventory is empty and their item-handler capability exposes zero slots.

## Speaker

- Added the **Speaker**, a 1x1x1 black-ash cabinet with a woofer, tweeter and bass reflex port. It faces the player when placed.
- Shift-right-click opens a safe-style screen with:
  - **Enabled**
  - Four alarms: **Air Raid**, **Facility**, **Industrial** and **EAS**
  - **1–64 block range** slider, default 32
  - **0–100% volume** slider
- Anyone may use the speaker screen. Settings are saved with the block.
- While enabled and receiving redstone power, the speaker loops the selected alarm until the signal or Enabled is removed.
- Alarms use a client-side loop, so switching off redstone or Enabled stops the sound immediately.
- The speaker can join the Redstone Gate network with the Redstone Relay Key. Starting the alarm pulses every linked gate.
- The speaker uses the safe's non-opaque, non-full-cube rendering setup so its recessed front and driver details render correctly.

## Concrete and HL Blocks

- Added the full **NTM CE concrete building set**:
  - plain, smooth, 16 dyed, Builder's Choice
  - concrete bricks, pillar, rebar and asbestos
  - **Über Concrete** with sixteen decay stages
  - falling **Moldy Debris**
- Added the included **HL Blocks** set:
  - three simple blocks
  - seven metadata groups: freezer, fifties, crete, duct, lab, tunnel and generic
- Added metadata families for concrete cubes and slabs. Stairs remain one block ID per texture.
- Added flat **Chisel** carving groups for concrete cubes, stairs and slabs.
- Added **shift + scroll** cycling through a held concrete or HL stack's texture family. The cycle modifier is rebindable under Controls.
- Added concrete and HL recipes, plus `concrete`, `blockConcrete` and `blockhl` ore dictionary entries.
- Added the single-slab model fix by writing blockstate keys in the order Forge uses (`half=...,variant=...`).
- Added hazard cubes, stairs and six hazard slabs.
- Added Über Concrete decay: it advances through sixteen stages on random ticks, then crumbles into falling Moldy Debris.
- **Chisel 1.12.2-1.0.2.45 is now required.**
- Concrete and HL Blocks register after all existing blocks and items so previously saved numeric IDs do not move.
- Chisel stairs and slabs register one canonical state per texture instead of every orientation, so carving no longer cycles through stair facings or flips slab halves.

## Creative tab and cards

- All concrete and HL Blocks are now listed in the **NTM Doors** creative tab.
- Removed the separate **NTM Concrete** tab and the vanilla Building Blocks placement.
- Renamed the **SCP access cards** from levels 1–6 to **levels 0–5**, including registry names, models, textures and tooltips.
- Existing level 6 cards are remapped to level 5 when an old world loads; the shared level 1–5 registry names remain valid.

## Fixes and polish

- Fixed Redstone Relay Key sneak-right-click binding.
- Fixed Redstone Relay Key binding on Keycard Scanners.
- Fixed the Redstone Gate latching on when redstone dust sits on top of it.
- Fixed the redstone network not settling after a wireless pulse.
- Added missing item models for the security silo hatches and limited the silo item preview to the hatch lid.
- Fixed the concrete slab model lookup.
- Fixed multiplayer access and state-sync issues in scanners and cards.
- Fixed door lighting and collision issues, including per-side room lighting, glass QE doors passing light, the opened vault panel being solid, cargo doors opting out of light sealing, and doors staying lit when a block is placed against them.
- Fixed scanner and programmer item models and held transforms.
- Added keypad success and failure sounds.
- Halved the programmer backup-loop volume.
- Cached each door's normalized item-model bounds instead of recalculating them every frame.
- Door rendering now skips redundant texture binds when consecutive groups share a texture.
- Removed unused Redstone Gate and Redstone Binding accessors.

## Notes

- This public **1.0.31** release is the cumulative build of the internal 1.0.31–1.0.47 changes.
- Existing worlds keep their numeric block and item IDs for existing content.
- The concrete and HL Blocks sets are new in this release.
