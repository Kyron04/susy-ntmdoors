# Emancipation Grill - Portal 2-style emitter and field

The physical emitter and paired energy field implemented in `../susy-ntmdoors`.

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
  is the redstone placeholder.
- **Field modes.** Solid blocks unauthorized players and mobs; Neutral renders the field but has no
  effect; Lethal kills unauthorized players and mobs, and erases non-living entities such as dropped
  items, projectiles, vehicles and item frames that touch the field. The field can also be enabled or
  disabled from page two. Ignore Items leaves dropped items alone in Lethal mode.
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
- **Barrier.** In Solid mode unauthorized players and mobs are stopped at the field plane; authorized
  players pass. Lethal mode kills instead of pushing back, and Neutral mode does neither. The thin
  field cell remains exposed by `TileEntityEmancipationGrill#getFieldBox()`.
- **Breaker and placement guard.** Breaker keys treat an owned grill like an owned security block.
  Blocks cannot be placed in the one-block doorway slice at the field line while the pair is
  active.
- **Performance.** The renderer stays at three layered quads, edge strips and a handful of sparks.
  The server checks players inside the field and scans their inventories each tick; there are no
  shaders or per-frame texture uploads.

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
| `tools/FieldTextureGenerator.java` | Generates the field, edge and spark textures. |

## Rebuilding the model assets

From this folder:

```powershell
powershell -ExecutionPolicy Bypass -File tools\make_mod_assets.ps1
```

This writes into `..\susy-ntmdoors\src\main\resources\assets\ntmdoors`.

`make_mod_assets.ps1` does **not** generate the field effect textures; those are handled by the
Java tool below so the two pipelines can be tuned independently.

## Rebuilding the field textures

Use any JDK 8 or newer. From this folder:

```powershell
$jdk = 'C:\path\to\jdk8'
& "$jdk\bin\javac.exe" -d tools\build tools\FieldTextureGenerator.java
& "$jdk\bin\java.exe" -cp tools\build FieldTextureGenerator ..\susy-ntmdoors\src\main\resources\assets\ntmdoors\textures\blocks
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
