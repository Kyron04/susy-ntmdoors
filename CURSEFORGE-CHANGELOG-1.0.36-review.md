# NTM Doors — CurseForge review changelog

**New file:** `ntmdoors-1.0.36.jar` (internal build 1.0.148; rename the CF file version if desired)  
**Baseline:** public **1.0.36** (internal build 1.0.36)  
**SHA-256:** `2FD45B24F1D71BB1E044CBA59D53192218F69ECDA54787294987E9679B5D3C61`

This is the cumulative change set since the public 1.0.36 release. The baseline jar was rebuilt
from the public 1.0.36 source revision so both sides use the same build system. The comparison
shows **no removed assets or classes**; all new content is additive: 1,205 entries versus 1,019
in the baseline, with 186 new entries and 610 updated entries.

## Monitor desks and terminal

- Added the **6 Monitor Setup** (`ntmdoors:monitor_setup`) and four smaller desks:
  **3 Monitor Setup**, **Dual Monitor Setup**, **Dual Monitor Setup (short table)** and
  **Monitor**. Each places as one item, faces the player, dismantles as a whole and uses a
  half-depth monitor wall for the upper cells.
- Added the password-gated **monitor terminal**. The first open offers password setup; later
  opens require the password. Passwords are SHA-256 hashes stored on the desk core and checked
  server-side.
- Added per-screen terminal pages: **Security**, **Opencomputers**, **Factory**, **Display**,
  **Notepad** and **Terminal Backup**.
- Added physical-screen mirroring of the live terminal page, with independent state for each of
  the six panels.
- Added terminal **Open** / **Close** buttons for individual Security net doors, including doors
  with no scanner or keypad binding.

## Security terminal and nets

- Added named **Security nets** with per-desk device lists.
- Added the **Terminal Relay Key** for binding doors, keypads, keycard scanners, motion sensors,
  Redstone Gates and speakers to a net, including a naming prompt for each device.
- Added net landing pages with **Devices** and **Events** tabs, net creation, renaming, deletion
  and key linking.
- Added Security net events:
  - **System Shutdown**
  - **System Lockdown**
  - **Trigger Alarms**
  - **Disable Keypads**
  - **Disable Scanners**
  - **Disable Sensors**
  - **Disable Redstone Gates**
- Added one-shot **Close all doors** and **Open all doors** actions.
- Event state is persisted in the world save and enforced while the terminal chunk is unloaded.
- Added remote device access from the terminal for the devices confirmed in a net.
- Lockdown now correctly blocks card scans, keypad input, redstone opening and scanner override
  requests, not only door movement.

## Terminal backup

- Added the **Terminal Backup Key**.
- Added **Initiate backup**: upload this terminal's complete snapshot to a key, then copy it to a
  blank key with the familiar ten-second progress bar, loop and completion chime.
- Added **Restore backup**: overwrite the current terminal from a key, including Security nets,
  device bindings and events, Notepad documents, wallpapers, saved screen pages, next IDs and the
  terminal password hash. The key is not consumed.
- Backups made before password support keep the target terminal's current password.

## Display wallpapers

- Added a per-screen **Display** task with five built-in wallpapers and a **Terminal** tile.
- Added custom `http://` / `https://` image wallpapers, downloaded locally by each client with
  8 MB / 4096 px limits.
- Added per-screen wallpaper persistence and a loading / unavailable status card.
- Downloader threads are bounded and URL textures are released when the client leaves the world.

## Notepad

- Added the shared **Notepad** task with up to 16 documents of 8,192 characters each.
- Added the sliding document sidebar with create, rename and delete.
- Added wrapped-line editing, scrolling, selection, copy/paste and unsaved-change prompts.
- Added optional monitor mirroring: leaving the notebook keeps the current document and document
  list visible on the physical screen.
- Holding a key now repeats typing in the Notepad and Documents Folder editors.

## Motion Sensor

- Added the bound-area console and the blue 5x5x5 projection overlay.
- Added entity detection for players, hostile mobs and neutral mobs.
- Added per-sensor redstone events and Redstone Gate binding.
- Added **Persist within bounds** and **Persist out of bounds** modes.
- Added placement on floors, walls and ceilings, with collision/selection boxes following the
  lens direction.
- Added the **Motion Relay Key** for selecting the watched area.
- Added the compact 8×8 housing, Fresnel lens and four status lights.

## Cards, doors and access

- Added the **Terminal Backup Key** and **Terminal Relay Key** items.
- Expanded the keycard programmer's second page to a scrollable 19-card palette:
  seven colours, five Tarkov-themed cards, six SCP clearance cards and the Googa card.
  Operator, Creative and Biometric cards remain excluded.
- Fixed the blue-marking card description to read **Sanitar**, not **Samitar**.
- Added **Biometric Safe** ownership binding and the safe's **void on destroy** option.
- Added the **Test Door** example, a 3×3 containment door using the supplied red/white texture.
  It is registered after every existing door so the example does not move saved door IDs.
- Added terminal/network compatibility for the existing keypad, scanner, safe, Redstone Gate and
  speaker device families.

## Fixes and polish from the final sweep

- Fixed the 6 Monitor Setup losing stored Terminal Backup Keys when any cell was broken.
- Fixed terminal-key link/unlink not updating the physical monitors.
- Fixed a dedicated-server speaker crash caused by initialised client-only fields
  (`NoSuchFieldError: loopSoundIndex`).
- Fixed net-wide lockdown gaps in scanners, keypads and redstone requests.
- Fixed terminal passwords being overwritable by repeating the setup action.
- Fixed the Security key-slot display leaving depth testing disabled while the physical monitor
  screen was drawn. This could make a nearby multi-part door or hatch render with every face
  visible (looking like a green crystal) after closing the terminal.
- Fixed upper monitor cells not giving the desk item on middle-click.
- Made idle monitor desks skip their entire screen tick loop.
- Removed quadratic Notepad line wrapping on long lines.
- Corrected full clean-build/reobfuscation so the production jar no longer hits the
  `ResourceLocation.getNamespace()` crash from the earlier unreobfuscated test build.

## Compatibility notes

- Minecraft **1.12.2**, Forge **14.23.5.2847** (tested against the 14.23.5.2860 runtime).
- **Chisel 1.12.2-1.0.2.45 is required**; CTM is supplied by Chisel at runtime.
- New blocks and items are registered after the existing entries, so saved numeric IDs are not
  moved. Existing worlds should load normally.
- The old SCP card names `scp_level_1` through `scp_level_6` are remapped to levels 0–5.
- The jar contains no test/stress harness classes. The temporary dedicated-server stress harness
  used during validation was removed before the final clean build; the content **Test Door** is
  the only intentionally named test item and is registered last.
