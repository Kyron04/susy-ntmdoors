# NTM Doors 1.0.72

Minecraft 1.12.2 / Forge 14.23.5.2847+ (tested on 14.23.5.2860)
Requires Chisel 1.12.2-1.0.2.45

**File:** `ntmdoors-1.0.72.jar`
**SHA-256:** `753D0CAF46B7E4E4A18C601376182C1900BBB012ED70EAFACBF566C9A6E6049D`

## Office doors

- Added screwdriver colour skin cycles to **Office Door 1**, **Office Door 2** and **Office Door 3**.
- Office Door 1 keeps its original metal handles while the leaf and its hidden edge faces are recoloured.
- Office Door 2 gains red, charcoal, green, yellow, light grey and black paint variants.
- Office Door 3 gains light grey, red, blue and black wood variants.
- The double office doors inherit the same skin sets.

## 3x3 office doors

- Added **Office Door 1 (3x3)**, **Office Door 2 (3x3)** and **Office Door 3 (3x3)**.
- Each is a 1.5x scaled version of the matching 2x2 double door.
- The 3x3 versions use a two-cell-deep front footprint so the longer open leaves keep working collision.
- The existing 2x2 double doors are unchanged, so both sizes are available.

## Textures

- Office door textures are now **32x64**, reducing their pixel count by 75%.
- The original 64x128 atlases are preserved in `source-art/office-doors/64x128`.
- Added proper inventory/hotbar item models for every 3x3 door variant.

## Fixes

- Fixed the **Redstone Monocle** overlay leaking GL state into the next frame. The link overlay now restores depth, cull, texture and blend state in a `finally` block, preventing missing-chunk-looking visual corruption.
- Stale `.bak` files are no longer packaged into the jar.

## Internal

- New doors register after every existing block and item, so previously saved numeric block and item IDs do not shift.
- Monitor terminal display state is stored per screen in a `ScreenState` object, with the Display page extracted into `GuiMonitorDisplayController`.
