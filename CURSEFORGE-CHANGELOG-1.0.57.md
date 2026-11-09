# NTM Doors 1.0.57

Minecraft 1.12.2 / Forge 14.23.5.2847+ (tested on 14.23.5.2860)
Requires Chisel 1.12.2-1.0.2.45

Changes since the CurseForge **1.0.43** release.

**File:** `ntmdoors-1.0.57.jar`
**SHA-256:** `D606EEF4A186CA1172A9D9CDB16DE580640E7122444C3C8C5B96511F1CDEBD2D`

## Security Door Ownership & Redstone Linking

- Security doors now record the player who placed them as their **owner**.
- By default, security doors still require a keycard scanner or the Creative Keycard. The owner can opt a security door in to **redstone** and **Redstone Gate** links by sneak + right-clicking it with the **Redstone Monocle**; other players are refused.
- Security doors placed before owner tracking was added have no recorded owner and cannot be enabled for redstone/link access until replaced.
- The **Redstone Relay Key** can now start a binding session from a **Redstone Gate** (sneak + right-click the gate), then link other gates to relay its signal or link doors to open/close with that gate. Links are directional, work in the same dimension, and a link that would create a feedback loop is refused.
- Breached doors refuse gate-door links. Security doors accept gate-door links only after their owner enables Redstone access with the Monocle.
- Existing safe/scanner/keypad/speaker/sensor device-to-gate bindings, and safe-to-door/scanner-to-door/keypad/safe-binding links, are unchanged.

## Redstone Monocle

- Added the **Redstone Monocle**, a red-named creative diagnostic item.
- While held in either hand, it draws loaded wireless Redstone links through walls: **red** for gate-to-gate, **amber** for gate-to-door, and **blue** for device-to-gate.
- Sneak + right-click an owned security door with the Monocle to toggle whether that door accepts redstone and gate links.

## Keycards

- Added the **Developer Keycard** for pack developers integrating security systems into structures. It behaves like a normal RFID card (can hold private keys and biometric data, be programmed, and be used by scanners) but displays stored private keys and biometric IDs as **Unknown**. It has a gold name and is excluded from the keycard programmer's card page.
- Added **Level 1–12 Access Keycards**, numbered access keycards that can be programmed and used like normal RFID cards and are available from the keycard programmer's card page.
