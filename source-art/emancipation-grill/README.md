# Emancipation Grill - Portal 2-style emitter and field

The physical emitter and paired energy field implemented in this repository.

The visual target is the modern Portal 2 fizzler: a pale white-cyan, glassy curtain with soft
wisps and bright emitter seams, rather than a solid blue wall.

## In-game status

- **1x3x1 multiblock.** One item places the bottom, middle and top cells. Breaking any cell removes
  the whole column.
- **10-block coupling.** The second grill snaps to the first grill's bottom Y and wall line, faces
  it, and is placed in the mirrored state so the two emitter strips line up.
- **Field renderer.** One of four 256x256 seamless wisp textures is drawn as three UV-scrolled
  layers on a single plane, with a soft edge gradient, a crisp white-cyan core line and 16 drifting
  sparks. Each pair picks a variant, UV offset and slight scale jitter deterministically so
  adjacent fields do not repeat the same splash. The plane is drawn in the world-last pass so its
  transparency correctly blends over doors and devices drawn in the same frame.
- **Ownership.** The first grill placed owns the pair; the second grill adopts that owner. The
  field is solid by default. A player passes only with an assigned private key or biometric identity
  on an RFID/Operator Keycard, or a Creative Keycard. Ownership controls the console and Breaker
  permissions, not field passage.
- **Relay key console.** Sneak right-click a grill you own with the Emancipation Relay Key or an
  Operator Keycard to open the console. Page one assigns keys and IDs from an Operator Keycard in
  the player's inventory. Page two holds the field modes and access/destruction toggles. Page three
  sets the field colour. Page four controls redstone event outputs. Page five configures the Filter
  mode whitelist and blacklist.
- **Horizontal variant.** A second grill item places a 3x1x1 bar that runs left/right along the
  wall. Two horizontal bars pair across an opening and create a horizontal field sheet at the
  emitter line. It shares the console, ownership, Redstone links, terminal device type and detection
  toggles.
- **Field modes.** Solid blocks unauthorized players and mobs; Neutral renders the field but has no
  effect; Lethal kills unauthorized players and mobs, and erases non-living entities such as dropped
  items, projectiles, vehicles and item frames that touch the field; Wall Mode blocks every detected
  player and mob with no clearance, no killing and no item erasure; Filter Mode lets everyone pass
  and applies the page-five item lists. The field can also be enabled or disabled from page two.
  Ignore Items leaves dropped items alone in Lethal mode.
- **Access toggles.** Match User makes biometric cards personal; Ignore Private Key and Ignore
  Biometric ID remove those credentials from the clearance check. Ignore Inventory leaves items and
  armor alone when the Lethal field kills an unauthorized player. Creative players are unaffected by
  Lethal contact.
- **Detection toggles.** Detect Players, Detect Hostile Mobs and Detect Neutral Mobs decide which
  living entity categories the field acts on. The categories match the Motion Sensor: hostile mobs
  are `IMob`, neutral covers animals, villagers and NPCs.
- **Sound.** The field uses the Portal 2 fizzler start, loop and shutdown sounds, with the material
  emancipation one-shot for lethal disintegration. Sounds have a three-block audible radius and can
  be muted from the console. The loop follows each active field; start/shutdown play when the field
  becomes active or stops. A mob killed by Lethal gets one disintegration one-shot, not a loop.
- **Terminal and gate integration.** A Security terminal can list a grill as a device, toggle its
  field, bind it with a Terminal Relay Key, and run net-level Drop all fields / Raise all fields
  actions. A Redstone Gate can also link a grill as an output. Linking an off gate drops the field
  once so the console can be configured; afterwards gate activation can raise the field, but gate
  deactivation and the grill's own signal pulses leave the configured field alone.
- **Redstone event outputs.** Bind gates to the grill with the Redstone Relay Key to receive one-tick
  event pulses. The fourth console page enables five independent outputs: Solid pass (Y), Solid block
  (N), Lethal kill, Match User match (Y) and Match User mismatch (N). Match User outputs only apply
  while Match User is on and Ignore Biometric ID is off. All five default to off and persist.
- **Filter mode.** Page two includes a `Filter Mode` that lets everyone pass regardless of
  clearance. Page five configures two 9x6 grids (54 pattern slots each) of whitelist and blacklist
  patterns. Drag from JEI/HEI to set a pattern; right-click a slot to clear it. Clicking a slot does
  not take the held hotbar item. When a player contacts a Filter mode field, the inventory is scanned
  once per contact; crossing the field again rechecks it, even without leaving the wider detection
  radius. Blacklisted items are deleted, whitelisted items are preserved, and an item in both lists
  is preserved. Dropped item entities are checked the same way: blacklisted stacks are voided and
  whitelisted or unlisted stacks pass through. Living mobs are checked too: equipment slots,
  villager inventories, horse/donkey/llama item handlers and any standard item-handler capability
  are scanned, independently of the detection toggles. Container items are checked recursively
  through Forge's item-handler capability and the standard `Items` NBT layout, covering modded
  backpacks, toolbelts, vanilla shulker boxes and root-list backpack inventories. Whitelisting a
  container protects the container itself, not its contents; a blacklisted container is voided
  whole. Each void plays the same disintegration one-shot as Lethal mode, rate-limited by the grill.
  Matching uses the registered item and subtype metadata; stack size and NBT are ignored. Main
  inventory, armor and offhand are scanned; creative and spectator players are skipped. Ignore Items
  remains Lethal-only and does not disable Filter mode. The JEI/HEI overlay is positioned beside the
  console on page five. Page five warns when the field is disabled, Filter Mode is off, or the player
  is creative/spectator. Filter mode does not change the existing Lethal inventory wipe.
- **Barrier.** In Solid mode unauthorized players and mobs are stopped at the field plane; authorized
  players pass. Wall Mode stops everyone detected, regardless of clearance. Lethal mode kills instead
  of pushing back, while Neutral and Filter mode do neither. The thin field cell remains exposed by
  `TileEntityEmancipationGrill#getFieldBox()`.
- **Breaker and placement guard.** Breaker keys treat an owned grill like an owned security block.
  Blocks cannot be placed in the one-block doorway slice at the field line while a Solid, Lethal or
  Wall pair is active.
- **Performance.** The renderer stays at three layered quads, edge strips and a handful of sparks.
  The server checks entities near the field each tick; Filter mode scans player and mob inventories
  and equipment once per contact, checks nearby dropped item entities each tick, and bounds recursive
  container scans by depth and slot count. There are no shaders or per-frame texture uploads.

The preview's **glass bend** is a preview-only effect. The in-game version deliberately avoids
framebuffer and shader work.

## Files

| Path | Purpose |
|---|---|
| `emancipation_grill.bbmodel` | Blockbench source model. Open this to edit the grill. |
| `textures/grill_*.png` | Source 16x16 textures (also embedded in the `.bbmodel`). |
| `refs/` | Portal 2 reference images. |
| `previews/emancipation_grill_preview.png` | Static model preview. |
| `preview/field_preview.html` | Interactive field design target. Open it in a browser or the OpenCode Review pane. |
| `tools/generate_grill_textures.ps1` | Rebuilds the generated source texture set. The warning sign is supplied art. |
| `tools/make_grill_bbmodel.ps1` | Rebuilds the `.bbmodel` from the element table. |
| `tools/render_grill_preview.ps1` | Optional software preview render. |
| `tools/inspect_grill_model.ps1` | Checks coplanar face overlaps and UV aspect ratios. |
| `tools/make_mod_assets.ps1` | Bakes the `.bbmodel` into the three layer models, mirrored variants, blockstates and item icon. |
| `tools/make_horizontal_assets.ps1` | Rolls the same `.bbmodel` into the 3x1x1 horizontal left/centre/right models, blockstates and item icon. |
| `tools/FieldTextureGenerator.java` | Generates the field, edge and spark textures. |

## Rebuilding the model assets

From this folder:

```powershell
powershell -ExecutionPolicy Bypass -File tools\make_mod_assets.ps1
powershell -ExecutionPolicy Bypass -File tools\make_horizontal_assets.ps1
```

This writes into `..\..\src\main\resources\assets\ntmdoors`.

`make_mod_assets.ps1` does **not** generate the field effect textures; those are handled by the
Java tool below so the two pipelines can be tuned independently.

## Rebuilding the field textures

Use any JDK 8 or newer. From this folder:

```powershell
$jdk = 'C:\path\to\jdk8'
& "$jdk\bin\javac.exe" -d tools\build tools\FieldTextureGenerator.java
& "$jdk\bin\java.exe" -cp tools\build FieldTextureGenerator ..\..\src\main\resources\assets\ntmdoors\textures\blocks
```

Outputs:

- `emancipation_grill_field.png` - variant 1, 256x256 seamless wisp texture.
- `emancipation_grill_field_2.png` - variant 2.
- `emancipation_grill_field_3.png` - variant 3.
- `emancipation_grill_field_4.png` - variant 4.
- `emancipation_grill_field_edge.png` - 64x16 soft edge gradient.
- `emancipation_grill_field_spark.png` - 16x16 spark sprite.

## Model notes

- 1 block wide x 3 blocks high x 1 block deep (16 x 48 x 16 model units).
- Front is north; the wall plate is on the south face.
- 13 parts, with seven embedded 16x16 textures.
- The emitter strip is on the inner edge of the unit. The opposite-wall unit uses the mirrored
  model so both strips line up.
- The 48-unit model is split into three 16-high layer models for Minecraft 1.12's model height
  limit. Each layer has a mirrored variant and its own blockstate.
- The horizontal variant is the same model rolled 90 degrees around its front axis, then split into
  three 16-wide cells. It uses the same textures and a 3x1x1 blockstate; `mirrored` only affects
  pairing, not the horizontal model.

## Field notes

- The rendered plane sits in the middle of the 1/16-thick field cell. The cell remains the logical
  volume for future fizzle, collision or entity checks.
- The field is drawn once per pair per frame, by whichever grill the renderer visits first. This
  keeps it visible when the lower grill's chunk is off screen but the opposite grill is in view.
- The renderer picks one of four field variants per pair, plus a deterministic starting UV offset
  and a small scale jitter. Two fields using the same variant still show different patches.
- The field texture is tileable; the three layers use different UV scales and scroll speeds.
- Layer colours, alphas, UV scales and speeds are constants at the top of
  `RenderEmancipationField`.

## Model audit

`tools/inspect_grill_model.ps1` checks every face pair for coplanar overlaps and compares each
face's UV aspect ratio to its geometry. The current model has no coplanar face overlaps. The only
deliberate UV stretch is the emitter strip's front/back, which uses the full 16x16 glow texture
across the narrow strip.
