# NTM Doors 1.0.51 — Security Release

Minecraft 1.12.2 / Forge 14.23.5.2847+ (tested on 14.23.5.2860)
Requires Chisel 1.12.2-1.0.2.45

**File:** `ntmdoors-1.0.51.jar`
**SHA-256:** `517A30457A445D180FD01C712578567FE6880ADDA1098BE0721827440DA1F59A`

This is a security release. The full 1.0.50 feature set is unchanged.

## Security fixes

- Removed the monitor's custom URL wallpaper import. The Display page now offers only the packaged
  built-in images, so clients no longer make outbound HTTP requests for monitor content.
- Monitor terminal actions now require a server-side session created by a successful password
  check or by opening a passwordless desk. Direct action packets sent without unlocking are
  ignored.
- These changes close the monitor URL request-forgery/tracking path and the terminal-password
  bypass for Security-net actions, Notepad, Terminal Backup and wallpaper changes.

## Compatibility

- Existing 1.0.50 worlds load normally.
- The door, keycard, safe, monitor, redstone, concrete and Documents Folder feature sets are
  unchanged.
- Clients and servers must both update. Older jars keep the old behavior.
