# NTM Doors 1.0.78 — CurseForge Release

Minecraft 1.12.2 / Forge 14.23.5.2847+ (tested on 14.23.5.2860)
Requires Chisel 1.12.2-1.0.2.45

**File:** `ntmdoors-1.0.78.jar`
**SHA-256:** `A95F5F7C4BEB4A016585DD7717AB3ACA89CACE82140A635C7EF14F4D46ED4865`

This release adds a dark mode toggle to both document editors.

## Documents Folder

- New `DARK` / `LIGHT` button in the top-right bar.
- Dark mode changes the writing field to `#3E434B` with white text.
- The folder body, tabs, borders and red accents switch to a complementary dark palette.
- The choice is stored in the folder's NBT and reopens with it.
- Light mode is unchanged.

## Monitor Notepad

- New `DARK` / `LIGHT` button in the top-right bar.
- Only the paper page changes: `#3E434B` background with white text. The casing, title bar, sidebar
  and buttons keep their existing colours.
- The theme is stored on the desk, shared by everyone using that terminal, and mirrored on the
  physical monitor screen.
- Light mode is unchanged.

## Compatibility

- Existing 1.0.77 worlds load normally.
- Clients and servers must both update. Older jars keep the old behavior.
- Chisel 1.12.2-1.0.2.45 remains required. No NTM installation is required.
