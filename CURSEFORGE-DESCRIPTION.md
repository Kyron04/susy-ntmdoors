# NTM Doors

**HBM's doors, without the rest of the mod.**

NTM Doors is a standalone Forge 1.12.2 port of the multiblock door system and concrete building set from HBM's Nuclear Tech Mod. No NTM installation is required. Chisel 1.12.2-1.0.2.45 is required. Every model, texture and sound is bundled.

**Current version: 1.0.35**

## 16 multiblock doors

- **Vault-Tec Blast Door** — 5x5 rolling gear door
- **Sliding Blast Door** — 7x4 double-panel blast door
- **Roller Blast Door** — 1x7 roller shutter
- **Secure Access Door** — 5x5 rising security door
- **Fire Door** — 4x3 roller/fire door
- **QE Sliding Door** — 2x2 double panel
- **QE Sliding Door, 1x2 Column** — single-column version
- **QE Containment Door** — 3x3 rising shield
- **Sliding Seal Door** — 1x2 pocket door
- **Round Airlock Door** — 4x4 split iris
- **Cargo Door** — 3x3 two-stage lift
- **Large Vehicle Door** — 7x6 split vehicle door
- **Water Door** — 3x3 swinging watertight door
- **Silo Hatch** — 5x5 tilting hatch
- **Large Silo Hatch** — 7x7 tilting hatch
- **Transition Seal** — huge keyframed 24-second sequence

Every door:

- places in one click, faces the player, and is removed as a single structure
- animates every moving part with the original HBM timings and sounds
- has state-dependent collision and selection boxes — walk through when open, click-through gaps
- responds to redstone from any cell (edge-triggered) and right-click
- is also registered as a redstone-only `<door>_locked` twin for adventure maps
- has a `<door>_security` variant (all sixteen doors, including both silo hatches) that only a keycard scanner or the Creative Keycard can open
- security doors take three real hours of continuous mining with any tool and resist explosions; normal and locked doors keep their original break times

## Keypad

A wall-mounted code panel that binds to the door it touches:

- 1–6 digit codes, physical buttons, LCD prompts, leading zeros preserved
- a second keypad on the same door shares its code; placing one next to an already-bound door auto-syncs and shows purple `Code synced`
- the binding stays on the door if the keypad is broken, until the door itself is destroyed
- `R` closes an open door when the pad's code matches the door
- screwdriver reset: confirm, enter the current code, and the door plus all linked pads are cleared
- sneak + screwdriver cycles binding modes while the pad is unused:
  - **Distance** — link one closed door within 32 blocks
  - **Multi** — link up to 9 doors, adjacent or remote, all on one shared code
- after linking, `Confirm Door Bounding?` is shown and `E` accepts; the pad then shows `Input code` / `Input multi code`
- once a pad has a code or any link, its mode is locked and the screwdriver can only reset it
- keypads have a separate GUI security code in addition to the normal door code
- Redstone Relay Key: normal right-click starts/finishes a wireless gate-binding session
- Redstone Relay Key: sneak + right-click opens a security-code screen, then a two-page keypad screen. Page one sets the first door code; page two has `On fail` / `On success` gate outputs plus `Reset Code`, `Override Door Locks` and `Force Close`

Keypads can bind and drive locked doors. Security doors stay off limits, and a door is never driven by both a keypad and a scanner.

Screwdriver + sneak + right-click cycles door skins:

- **Vault-Tec** — seven plate/label combinations
- **Secure Access** — nine: red, yellow, green, blue, grey, black with white text, HBM yellow, white with black text, orange with white text
- **Sliding Seal** — six: default, clean, trefoil, plain white, plain red, plain grey (the plain skins have no markings)
- **Fire Door** — five: default, black, orange, yellow, trefoil
- **Round Airlock** — four: worn, clean, green, space, all keeping the grey concrete frame
- **Sliding Blast** — three: default, black, grey
- **QE Sliding Door / Column** — default and white
- **QE Containment** — plain, trefoil, yellow trefoil
- **Water Door** — default and clean

## Keycard access control

- **Keycard Scanner** — wall-mounted reader with an LCD, security stripe and green/yellow/red lenses. Bind up to nine closed doors within 32 blocks using the screwdriver; bound doors close again after ten seconds. Cards only open a scanner when they carry one of its assigned private keys, and the Creative Keycard opens everything.
- **Keycard Programmer** — desk console with a card slot, live screen and three GUI pages. Bind Relay Keys to Operator Keycards and RFID cards, recolour cards with seven colour options, and run the Legacy Backup Programmer with its five-stage progress bar.
- **RFID cards** — blank plus red, violet, green, blue, black, orange and yellow variants.
- **Themed keycards** — Keycard with Blue Marking, Labyrinth, Engine Room, Terminal and Terragroup Labs Access.
- **Operator Keycard** - aimed at a bound scanner, opens its security code screen; aimed at a Redstone Gate, opens its settings screen. The Biometric Operator Keycard does the same. Elsewhere it shows its private keys.
- **Creative Keycard** - bypasses card checks on bound doors, opens every scanner, opens both safes, and sneak + right-clicking a scanner opens its full console without a code or bound doors. It also opens the Redstone Gate screen.
- **Relay Key and Biometric Relay Key** — create named private keys; the biometric version captures the holder's identity instead.
- **Biometric Keycard and Biometric Operator Keycard** — carry an identity, show the owner in the tooltip, and work with the scanner's Match biometric holder option. The Operator version also lists its stored private keys in the tooltip.
- **Keycard Holder** — carried 3x4 case for twelve keycards, with its own NBT storage, empty-slot ghosts and shift-click transfer.
- **Keycard Viewer** — sneak-right-click a card to read every private key stored on it.
- **SCP access cards** — SCP Level 0–5 clearance cards, plus the Googa security keycard with its gold name and `Security is goog` tooltip.

### Scanner security code and assignment

- Security code screen: set a 1–6 digit code with one entry; later visits repeat it. Correct and wrong codes show `SUCCESS!!!` / `FAILED!!!` cards.
- Key assignment screen: `ASSIGNED KEYS` on the left, `NON-ASSIGNED KEYS` from the held Operator Keycard on the right, with click-to-move rows and wheel scrolling.
- Options: Match biometric holder, Lockdown mode, Lockout mode, Ignore private keys, Ignore biometric IDs, Override door locks and Force close.
- Lockdown closes every bound door and refuses cards, codes, keypads and redstone until it is lifted.
- Lockout shuts the reader for five minutes after three bad cards or codes; the Biometric Operator Keycard can still reach the code screen.
- Scanner audio: button3 on insert/success, button8 on denial and a looping bell1 while scanning.

## Safes

- **Code Safe** - a 1x1x1 steel safe with a keypad. The first accepted 1-6 digit code becomes its code; later entries must match. There is no owner restriction, so anyone who knows the code can open it. A correct code shows the `SUCCESS!!!` card and then opens a 27-slot container. Safes take three real hours of continuous mining with any tool and resist explosions.
- **Biometric Safe** — the same shell and 27-slot container, opened by a bound Biometric Operator Keycard or the Creative Keycard. `Match user` restricts access to the card's enrolled holder; with it off, any bound biometric operator card opens the safe.
- Both safes save their contents and settings, drop their inventory when broken, and show full item names and NBT tooltip lines in the container. Their slots are not exposed to hoppers or logistics automation; only the opened container can access them.
- Each safe has a second options page:
  - Code Safe: Binding Activities, Lockout mode and Reset code.
  - Biometric Safe: Match user, Binding Activities and Lockout mode.
- Lockout mode stops access for five minutes after three failed codes or cards. Reset code clears the code so the next entry sets a new one.

## Concrete and HL Blocks

- **Concrete** - the full NTM CE set: plain, smooth, 16 dyed, Builder's Choice, concrete bricks, pillar, rebar, asbestos, Über Concrete and Moldy Debris. Cubes and slabs use metadata families; stairs stay one id per texture.
- **HL Blocks** - three simple blocks and seven metadata groups: freezer, fifties, crete, duct, lab, tunnel and generic.
- Both sets have recipes, `concrete` / `blockConcrete` / `blockhl` ore dictionary entries, and flat Chisel carving groups.
- All concrete and HL Blocks are listed in the NTM Doors creative tab.
- Shift + scroll cycles a held concrete or HL stack through its texture family. The cycle modifier is rebindable under Controls.
- Chisel 1.12.2-1.0.2.45 is required.

## Decorative blocks

- **Speaker** - a 1x1x1 black-ash cabinet with a woofer, tweeter and bass reflex port. Shift-right-click (with any item or an empty hand) opens a safe-style toggle screen with **Enabled**, four selectable sounds (**Air Raid**, **Facility**, **Industrial**, **EAS**), a 1-64 block range slider and a 0-100% volume slider; anyone may use it. While enabled and receiving redstone power, it loops the selected alarm until the signal or **Enabled** is removed. The sound, range, volume and enabled state are saved with the block. A normal right-click with the Redstone Relay Key starts/finishes a wireless binding session, and starting the alarm pulses every linked Redstone Gate.
- **Motion Sensor** - a compact 8x8x2.25 surface-mounted PIR sensor with an off-white ABS housing, charcoal bezel, ringed concentric Fresnel lens and a status light. It can be placed on floors, walls and ceilings; the clicked face becomes the lens direction. Green means no motion, blue means authorised personnel, yellow means maintenance and red means motion detected. Creative right-click cycles the light for preview; detection logic is not implemented yet.

## Redstone

- Normal and locked doors respond to redstone. Security doors still require a keycard scanner or the Creative Keycard, and lockdown still blocks redstone edges.
- **Redstone Gate** - a full-cube redstone component with separate off and on textures. It starts off, turns on while it receives redstone power or a wireless pulse, and emits a full-strength signal while on. A wireless event powers it for the gate's configured duration, and another event extends the pulse. Both Operator Keycards and the Creative Keycard open its settings screen: `Stay active after signal`, `Invert operation`, and +1/+5/+10/+20 second activation duration controls with matching subtract buttons.
- **Redstone Relay Key** - a red-named creative key. Sneak + right-click a safe or keycard scanner to start/finish a wireless binding session, then sneak + right-click a Redstone Gate to link or unlink it. On a keypad or speaker, a normal right-click starts/finishes the session; sneak + right-click opens the keypad screen, while the speaker uses shift-right-click for its own screen. Devices and gates can be linked many-to-many in the same dimension.
- Both safes and the keycard scanner have a third **Redstone** page with a red panel tint and scrolling. Event toggles start off and pulse every linked gate for that gate's configured activation duration when enabled:
  - Code Safe: Code fail, Code success, Binding activities, Lockout.
  - Biometric Safe: Scan fail, Scan success, Match holder fail, Match holder success, Lockout.
  - Keycard Scanner: Scan fail, Scan success, Match holder fail, Match holder success, Private key fail, Private key success, Biometric fail, Biometric success, Lockout.
  - Keypad (Redstone Relay Key screen): On fail, On success.
- Safe-to-door, scanner-to-door, keypad and safe-binding links are untouched. Breaking a safe, scanner, keypad or gate clears its reciprocal wireless links.

## Safe-to-door binding

- **Safe Binding Key** — a small green USB key with a green name and a tooltip explaining its controls.
- Sneak + right-click a safe to start or finish a binding session, then sneak + right-click closed doors within 32 blocks to link or unlink them.
- A safe can link several doors. A door belongs to at most one safe, but a door already controlled by a keypad or scanner keeps that binding; the safe link is additive and both systems remain respected.
- Both safes have a **Binding Activities** toggle, on by default. When on, the safe's lockout closes every linked door once. Turning it off prevents new binding sessions and pauses the lockout-door response without deleting existing links.
- Breaking a safe or a door clears the link.

## Documents Folder

- New carried folder item with a three-layer paper model.
- Right-click opens a twenty-page editor styled as an open manila folder in the item's tan, grey and dark-red palette.
- Typed caret with click-to-place, arrows, Home/End, Delete/Backspace, Ctrl+V paste and word wrap.
- Enter starts a new line; press it twice for a blank paragraph gap.
- Bottom index tabs jump to any page; arrows, Page Up/Down and the mouse wheel also flip pages.
- Auto-saves on close, with no signing step. Pages, custom folder name and last page are stored in NBT and synchronised to the server.
- Reopens on the page you closed on.
- Top-right title is editable: click to rename, Enter saves, Esc cancels, clearing restores the default name.
- The editor screen is scaled up 1.5x, with a fit guard for smaller windows.
- Tooltip shows the number of pages written.

## Technical

- Minecraft **1.12.2**, Forge **14.23.5.2847**, Chisel **1.12.2-1.0.2.45**
- OBJ/TESR rendering with per-group translation/rotation, clipping, and 3D inventory/hand/ground items
- 16 shared door sound events
- No configs and no NTM dependency

## Credits

HBM Nuclear Tech, and their contributors.
HBM GitHub repo: https://github.com/hbmmods/hbm-s-nuclear-tech-git
HBM CurseForge page: https://www.curseforge.com/minecraft/mc-mods/hbms-nuclear-tech-mod

Concrete assets and reference behaviour: HBM's Nuclear Tech Mod Community Edition.
HL Blocks assets and original block definitions: NeoRussia's HL Blocks project.

Developers of the port:

- Lead Developer: KYN4
- Advisors and Contributors: Oliver509, Unitazer, Yordi111, mxobxx
- And an Unknown third party

Playtesters: Neeyo, Creg2, SuperObama, Juniper Kynour, Gloo

AI tools were used in mod development. All features were extensively tested in singleplayer and on dedicated servers, quality assured, and swept multiple times for issues. All assets, materials, functionality and features were derived from human developers, with AI assisting in augmenting materials for development purposes.

Based on the door system and assets of **HBM's Nuclear Tech Mod** (LGPL-3.0). See `LICENSE.LESSER` and `NOTICE` for attribution. This project is not affiliated with the original author.
