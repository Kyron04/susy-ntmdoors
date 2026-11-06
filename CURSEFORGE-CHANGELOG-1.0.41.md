# NTM Doors 1.0.41

Minecraft 1.12.2 / Forge 14.23.5.2847+ (tested on 14.23.5.2860)  
Requires Chisel 1.12.2-1.0.2.45

Changes since the CurseForge **1.0.37** release. If you are upgrading from 1.0.36, the 1.0.37
changelog lists the security terminal and redstone fixes included in this build.

**File:** `ntmdoors-1.0.41.jar`  
**SHA-256:** `0FF585E246790B8B2E4EC96647B6A30F094A41C24B37C91E3A998875BCD7D978`

## New door: Blast Shutter

- Added the **Blast Shutter**, a 3x3 vertical shutter with a steel finish.
- The plate lifts into the frame over two seconds and is clipped above the lintel.
- Normal, locked, and security variants, plus the 3D door item renderer.
- Steel bottom bar; no caution stripes.

## Interface

- Centered the **Device Name** dialog's text field vertically and added the standard 4px side
  padding.
- Fixed the orange border fill that covered the text field.

## Compatibility

- No existing block or item IDs were moved; the new door is registered last.
- Existing Security nets, device bindings, terminal backups and world saves remain compatible.
- Minecraft **1.12.2**, Forge **14.23.5.2847+** (tested on 14.23.5.2860).
- Requires **Chisel 1.12.2-1.0.2.45**; CTM is supplied by Chisel at runtime.
