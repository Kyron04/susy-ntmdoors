# NTM Doors 1.0.53

Minecraft 1.12.2 / Forge 14.23.5.2847+ (tested on 14.23.5.2860)
Requires Chisel 1.12.2-1.0.2.45

Changes since the CurseForge **1.0.42** release.

**File:** `ntmdoors-1.0.53.jar`
**SHA-256:** `73E5D7FDAD2E647D00AE57AA16B3CCD486375A1529E230E9BCF0BACA18C4C091`

## Thermite Charge

- Added the **Thermite Charge**, a framed breaching charge with a flat 16x16 item sprite and a
  supplied 3D placed model.
- Mounts flush to the clicked block or door face in all six directions and sits on the door's
  hitbox surface. Silo hatches, the transition seal, the roller blast door and the Vault-Tec door
  refuse placement.
- Flint and steel lights a five-second fuse with a beep countdown, smoke, flame particles and a
  supplied explosion sound. While it burns, the attached door ignores hand clicks, redstone,
  keypads, scanners, safes, security nets and the screwdriver; breaking the charge defuses it.
- Detonation breaches a supported door: the animated moving panel and its collision are removed
  while every static frame cell and frame box stays, and the door becomes permanently unusable.
  Breached doors count as open for mob pathing.
- A charge on a normal block destroys that block; unbreakable blocks are left alone. A burning
  charge that loses its support or attached door detonates instead of dropping.

## Fixes

- The Spatial Disrupter can now be broken in creative mode while remaining unbreakable in
  survival.
