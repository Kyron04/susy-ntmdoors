# NTM Doors 1.0.37

Minecraft 1.12.2 · Forge 14.23.5.2847+ (tested on 14.23.5.2860)  
Requires Chisel 1.12.2-1.0.2.45

Changes since the CurseForge **1.0.36** release.

**File:** `ntmdoors-1.0.37.jar`  
**SHA-256:** `480CB9755F38559DDA05ABD704D99112D54B8E98A57D31A8976A94BE56005C07`

## Security terminal and nets

- Added per-device **ON / OFF** switches to every linked device except doors:
  - **Keypad** — off ignores key presses and shows `Keypad Disabled`.
  - **Keycard Scanner** — off refuses card, key and biometric scans.
  - **Motion Sensor** — off stops detection.
  - **Redstone Gate** — off forces its output low and ignores input and wireless pulses.
  - **Speaker** — off mutes the alarm.
- Switch state is saved on the device itself. Existing devices default to **ON**, and devices in unloaded chunks show `OFFLINE`.
- Doors keep their existing **Open / Close** buttons.

## Fixes

- Fixed redstone dust sitting on top of a Redstone Gate staying powered after the gate switched off. The gate now refreshes that dust directly and lets the attached wire settle before reading input again.
- Fixed **Stay active after signal** powering an idle Redstone Gate as soon as the toggle was enabled. The gate now only latches after it actually receives a signal.
- Fixed speakers not being powered by adjacent redstone dust. The speaker now checks adjacent dust power directly as well as the normal block-power query.
- Fixed speaker alarms changing loudness as the player turned toward or away from the speaker. The alarm is now mixed at the listener and fades only by distance inside the configured range.
- Fixed the speaker volume slider affecting range as well as loudness; range and volume are now independent.
- Fixed the speaker loop not restarting when the player re-entered its range.

## Compatibility

- No existing block or item IDs were moved.
- Existing Security nets, device bindings, terminal backups and world saves remain compatible.
- Minecraft **1.12.2**, Forge **14.23.5.2847+** (tested on 14.23.5.2860).
- Requires **Chisel 1.12.2-1.0.2.45**; CTM is supplied by Chisel at runtime.
