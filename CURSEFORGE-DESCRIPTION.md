# NTM Doors

**HBM's doors, without the rest of the mod.**

NTM Doors is a standalone Forge 1.12.2 port of the multiblock door system, security equipment and concrete building set from HBM's Nuclear Tech Mod. No NTM installation is required. Chisel 1.12.2-1.0.2.45 is required. Every model, texture and sound is bundled.

**Current version: 1.0.51**

## 27 multiblock doors

- **Vault-Tec Blast Door** — 5x5 rolling gear door
- **Sliding Blast Door** — 7x4 double-panel blast door
- **Roller Blast Door** — 1x7 roller shutter
- **Blast Shutter** — 3x3 vertical steel shutter
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
- **Test Door** — 3x3 red/white containment door
- **Office Door 1** — 1x2 Black Mesa style swing door
- **Office Door 2** — 1x2 red painted office door with a wire-glass window
- **Office Door 3** — 1x2 brown wood office door with straight levers
- **Office Door 1/2/3 (Double)** — 2x2 mirrored-leaf versions of each office door
- **Office Door 1/2/3 (3x3)** — 3x3 scaled double versions with a two-cell-deep front footprint

Every door:

- places in one click, faces the player, and is removed as a single structure
- animates every moving part with the original HBM timings and sounds
- has state-dependent collision and selection boxes — walk through when open, click-through gaps
- responds to redstone from any cell (edge-triggered) and right-click
- is also registered as a redstone-only `<door>_locked` twin for adventure maps
- has a `<door>_security` variant (all twenty-seven doors, including both silo hatches, the Test Door and the office-door family) that by default only a keycard scanner or the Creative Keycard can open
- security doors record the player who placed them as their owner; the owner can sneak + right-click with the Redstone Monocle to allow redstone and Redstone Gate links. Older security doors without an owner cannot be enabled until replaced
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
- **QE Containment** - plain, trefoil, yellow trefoil
- **Water Door** - default and clean
- **Office Door 1** - original plus dark blue, olive, taupe and black
- **Office Door 2** - original plus dark red, charcoal, green, yellow, light grey and black
- **Office Door 3** - original plus light grey, red, blue and black

## Keycard access control

- **Keycard Scanner** — wall-mounted reader with an LCD, security stripe and green/yellow/red lenses. Bind up to nine closed doors within 32 blocks using the screwdriver; bound doors close again after ten seconds. Cards only open a scanner when they carry one of its assigned private keys, and the Creative Keycard opens everything.
- **Keycard Programmer** — desk console with a card slot, live screen and three GUI pages. Bind Relay Keys to Operator Keycards and RFID cards, restyle a card from the 31-entry palette (seven colours, five themed cards, six SCP clearance cards, the Googa card and the Level 1–12 access cards), and run the Legacy Backup Programmer with its five-stage progress bar.
- **RFID cards** — blank plus red, violet, green, blue, black, orange and yellow variants.
- **Themed keycards** — Keycard with Blue Marking, Labyrinth, Engine Room, Terminal and Terragroup Labs Access.
- **Operator Keycard** - aimed at a bound scanner, opens its security code screen; aimed at a Redstone Gate, opens its settings screen. The Biometric Operator Keycard does the same. Elsewhere it shows its private keys.
- **Creative Keycard** - bypasses card checks on bound doors, opens every scanner, opens both safes, and sneak + right-clicking a scanner opens its full console without a code or bound doors. It also opens the Redstone Gate screen.
- **Relay Key and Biometric Relay Key** — create named private keys; the biometric version captures the holder's identity instead.
- **Biometric Keycard and Biometric Operator Keycard** — carry an identity, show the owner in the tooltip, and work with the scanner's Match biometric holder option. The Operator version also lists its stored private keys in the tooltip.
- **Keycard Holder** — carried 3x4 case for twelve keycards, with its own NBT storage, empty-slot ghosts and shift-click transfer.
- **Keycard Viewer** — sneak-right-click a card to read every private key stored on it.
- **SCP access cards** — SCP Level 0–5 clearance cards, plus the Googa security keycard with its gold name and `Security is goog` tooltip.
- **Level 1–12 Access Keycards** — numbered cards that can be programmed and used like normal RFID cards, available from the programmer's card palette.
- **Developer Keycard** — a gold-named pack-development card that behaves like a normal RFID card but displays stored private keys and biometric IDs as Unknown. Excluded from the programmer's card palette.

### Scanner security code and assignment

- Security code screen: set a 1–6 digit code with one entry; later visits repeat it. Correct and wrong codes show `SUCCESS!!!` / `FAILED!!!` cards.
- Key assignment screen: `ASSIGNED KEYS` on the left, `NON-ASSIGNED KEYS` from the held Operator Keycard on the right, with click-to-move rows and wheel scrolling.
- Options: Match biometric holder, Lockdown mode, Lockout mode, Ignore private keys, Ignore biometric IDs, Override door locks, Force close and Reset security code.
- Lockdown closes every bound door and refuses cards, codes, keypads and redstone until it is lifted.
- Lockout shuts the reader for five minutes after three bad cards or codes; the Biometric Operator Keycard can still reach the code screen.
- Scanner audio: button3 on insert/success, button8 on denial and a looping bell1 while scanning.

## Safes

- **Code Safe** - a 1x1x1 steel safe with a keypad. The first accepted 1-6 digit code becomes its code; later entries must match. There is no owner restriction, so anyone who knows the code can open it. A correct code shows the `SUCCESS!!!` card and then opens a 27-slot container. Safes take three real hours of continuous mining with any tool and resist explosions.
- **Biometric Safe** — the same shell and 27-slot container, opened by a bound Biometric Operator Keycard or the Creative Keycard. A newly placed safe records the placer's biometric ID, and only a card carrying that ID can open it; safes placed before this change stay unowned and accept any valid biometric card. `Match user` restricts access to the card's enrolled holder; with it off, any bound biometric operator card carrying the safe owner's ID opens the safe.
- Both safes save their contents and settings, show full item names and NBT tooltip lines in the container, and keep their slots hidden from hoppers and logistics automation.
- Each safe has a second options page:
  - Code Safe: Binding Activities, Lockout mode, Reset code and Void on destroy.
  - Biometric Safe: Match user, Binding Activities, Lockout mode and Void on destroy.
- Lockout mode stops access for five minutes after three failed codes or cards. Reset code clears the code so the next entry sets a new one.
- `Void on destroy` starts on: breaking the safe voids the safe item and everything inside it. Switching it off makes the broken safe drop its contents again.

## Monitor desks and security terminal

- **Monitor desks** — five placeable desk variants: **6 Monitor Setup** (3x3x1), **3 Monitor Setup** (3x2x1), **Dual Monitor Setup** (3x2x1), **Dual Monitor Setup (short table)** (2x2x1) and **Monitor** (2x2x1). Each places as one item, faces the player, reserves its full footprint and breaks as one structure. The upper monitor cells use a half-depth collision wall so you can stand on the front of the desk but cannot walk through the screens.
- Every monitor cell opens the same **password-gated terminal**. The first open offers to set a per-desk password; later opens require it. Passwords are stored as SHA-256 hashes on the desk core and checked server-side; a successful check creates a short-lived session, and terminal actions sent without that session are ignored.
- The terminal task menu lists **Security**, **Opencomputers**, **Factory**, **Display**, **Notepad** and **Terminal Backup**. Opencomputers and Factory are reserved entries; the other four are fully implemented.
- Physical monitor screens mirror the live terminal page. Each screen keeps its own page, selected Security net, tab and scroll position, so using one monitor never changes another.
- **Security nets** — named device lists stored per desk. Add, rename and delete nets, then bind doors, keypads, keycard scanners, motion sensors, Redstone Gates and speakers with the **Terminal Relay Key**. A net page has a key slot with Bind, Rename and Unlink controls.
- Security net **Devices** tab: Open/Close buttons drive any linked door directly, even one with no scanner or keypad binding; every other device has an ON/OFF switch that disables its input, detection, output or sound. The switch state is saved on the device.
- Security net **Events** tab: System Shutdown, System Lockdown, Trigger Alarms, Disable Keypads, Disable Scanners, Disable Sensors and Disable Redstone Gates, plus one-shot Close all doors and Open all doors. Event state is stored in the world save and stays enforced while the desk chunk is unloaded.
- Remote access: click a linked device in the terminal to open its screen while you remain at the desk. The session requires you to stay within reach of the desk and the device to remain in the confirmed net, and expires after ten minutes of inactivity. Keypads and scanners still ask for their security codes.
- Each physical screen renders a passive `SECURITY` status panel with net and device counts and the active event state; clicking any screen opens the terminal.
- **Display** — a per-screen wallpaper chooser with five built-in images and a `Terminal` tile. Only packaged images are offered; custom URLs are not supported, so a monitor never makes an outbound network request. The choice is saved on the desk.
- **Notepad** — a shared editor storing up to 16 documents of 8,192 characters each. The sidebar creates, renames and deletes documents; the paper page supports wrapped-line editing, scrolling, Save and an unsaved-changes prompt. The current document and list can stay visible on the physical monitor, and a saved wallpaper returns on the task menu.
- **Terminal Backup** — the **Terminal Backup Key** stores a complete snapshot: Security nets, device bindings, event state, Notepad documents, wallpapers, saved screen pages, next IDs and the terminal password hash. `Initiate backup` copies the snapshot to a blank key with a ten-second progress bar; `Restore backup` overwrites the terminal from a key. The key is not consumed.

## Concrete and HL Blocks

- **Concrete** - the full NTM CE set: plain, smooth, 16 dyed, Builder's Choice, concrete bricks, pillar, rebar, asbestos, Über Concrete and Moldy Debris. Cubes and slabs use metadata families; stairs stay one id per texture.
- **HL Blocks** - three simple blocks and seven metadata groups: freezer, fifties, crete, duct, lab, tunnel and generic.
- **Ultra High Performance Concrete** — six grayscale shades (original plus five darker tones).
  Every variant takes three real hours of continuous mining, and only ICBM nuclear, antimatter and
  red matter warheads can damage it; every other explosion is fully resisted. The hard set carries
  the `GBU-57 not included` tooltip. A decorative **UHPC Panel** set shares the same textures with
  vanilla-concrete hardness and no tooltip.
- Concrete has recipes and flat Chisel carving groups; both concrete and HL Blocks register `concrete` / `blockConcrete` / `blockhl` ore dictionary entries. HL Blocks are creative-only.
- All concrete and HL Blocks are listed in the NTM Doors creative tab.
- Shift + scroll cycles a held concrete or HL stack through its texture family. The cycle modifier is rebindable under Controls.
- Chisel 1.12.2-1.0.2.45 is required.

## Decorative blocks

- **Speaker** - a 1x1x1 black-ash cabinet with a woofer, tweeter and bass reflex port. Shift-right-click (with any item or an empty hand) opens a safe-style toggle screen with **Enabled**, four selectable sounds (**Air Raid**, **Facility**, **Industrial**, **EAS**), a 1-64 block range slider and a 0-100% volume slider; anyone may use it. While enabled and receiving redstone power, it loops the selected alarm until the signal or **Enabled** is removed. The sound, range, volume and enabled state are saved with the block. A normal right-click with the Redstone Relay Key starts/finishes a wireless binding session, and starting the alarm pulses every linked Redstone Gate.
- **Motion Sensor** - a compact 8x8x1.375 surface-mounted PIR sensor with an off-white ABS housing, charcoal bezel, ringed concentric Fresnel lens and a status light. It can be placed on floors, walls and ceilings; the clicked face becomes the lens direction. Green means no motion, blue means authorised personnel, yellow means maintenance and red means motion detected. Creative right-click cycles the light for preview. The sensor scans its configured 5x5 area, checks the cards and biometric IDs assigned to it, and drives linked Redstone Gates. A **Motion Relay Key** opens its console for the 5x5 depth grid, player/hostile/neutral detection toggles, persist-within/persist-out-of-bounds modes and the four redstone entry events.
  - [Final model preview](https://github.com/Kyron04/susy-ntmdoors/blob/main/source-art/motion-sensor/motion-sensor-model-preview-final.png)
- **Spatial Disrupter** - a decorative machine block with a blue/red emblem on the sides and a grey plate on the top and bottom. Tooltip: `Weakens the structural integrity of UHPC`. Right-click opens a one-action screen; activating it scans a 16x16 chunk area and turns every hard UHPC block into the soft UHPC Panel of the same shade, then consumes itself. The machine is bedrock-hard in survival and cannot be blown up or pushed; creative players can break it, and its own completed operation also removes it.
- **Thermite Charge** - a framed breaching charge that mounts flush to the clicked block or door face in any direction. Flint and steel lights a five-second fuse with a beep countdown, smoke and flame particles; the attached door locks its controls while burning, and breaking the charge defuses it. Detonation removes the door's moving panel and collision while leaving the static frame, permanently disabling that door; on a normal block it destroys the attached block. Silo hatches, the transition seal, the roller blast door and the Vault-Tec door refuse placement. The placed block uses a 3D model, while the item form uses a flat 16x16 sprite.

## Redstone

- Normal and locked doors respond to redstone. Security doors require a keycard scanner or the Creative Keycard by default; their owner can enable redstone and gate links with the Redstone Monocle. Lockdown still blocks redstone edges.
- **Redstone Gate** - a full-cube redstone component with separate off and on textures. It starts off, turns on while it receives redstone power or a wireless pulse, and emits a full-strength signal while on. A wireless event powers it for the gate's configured duration, and another event extends the pulse. Both Operator Keycards and the Creative Keycard open its settings screen: `Stay active after signal`, `Invert operation`, and +1/+5/+10/+20 second activation duration controls with matching subtract buttons. A gate can also be the start of a relay-key session: link other gates and doors to its output, and they follow it on and off. Links are directional, work in the same dimension, and a link that would close a feedback loop is refused.
- **Redstone Relay Key** - a red-named creative key. Sneak + right-click a safe, keycard scanner or motion sensor to start/finish a wireless binding session, then sneak + right-click a Redstone Gate to link or unlink it. On a keypad or speaker, a normal right-click starts/finishes the session; sneak + right-click opens the keypad screen, while the speaker uses shift-right-click for its own screen. Sneak + right-click a gate to start/finish a gate session, then sneak + right-click other gates to relay the signal or doors to open and close them with that gate. Devices and gates can be linked many-to-many in the same dimension; doors are driven by gate sessions only, breached doors refuse the link, and security doors accept it only after their owner enables Redstone access with the Monocle.
- **Redstone Monocle** - a red-named creative diagnostic item. While held in either hand it draws the loaded wireless links through walls: red for gate-to-gate, amber for gate-to-door, and blue for device-to-gate. Sneak + right-click a security door as its owner to toggle whether that door accepts redstone and gate links; other players are refused.
- **Weapons Relay Key** - a red creative key. Sneak + right-click opens a mode screen: **Regular Bounding** links any explosive block to Redstone Gates (a powered gate triggers it immediately), and **Dead Man's Hand** hard-locks the explosive so anyone without the matching key sets it off by right-clicking, left-clicking, breaking it, or placing/breaking a block next to it. The key holder can mine it safely. Works with any explosive block.
- Both safes and the keycard scanner have a third **Redstone** page with a red panel tint and scrolling. Event toggles start off and pulse every linked gate for that gate's configured activation duration when enabled:
  - Code Safe: Code fail, Code success, Binding activities, Lockout.
  - Biometric Safe: Scan fail, Scan success, Match holder fail, Match holder success, Lockout.
  - Keycard Scanner: Scan fail, Scan success, Match holder fail, Match holder success, Private key fail, Private key success, Biometric fail, Biometric success, Lockout.
  - Keypad (Redstone Relay Key screen): On fail, On success.
- Safe-to-door, scanner-to-door, keypad and safe-binding links are untouched. Breaking a safe, scanner, keypad, gate or linked door clears its reciprocal wireless links.

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
Office-door models and textures are original recreations inspired by the Black Mesa/Half-Life office-door style; no Black Mesa or Half-Life game assets are included.

Developers of the port:

- Lead Developer: KYN4
- Advisors and Contributors: Oliver509, Unitazer, Yordi111, mxobxx
- And an Unknown third party

Playtesters: Neeyo, Creg2, SuperObama, Juniper Kynour, Gloo

AI tools were used in mod development. All features were extensively tested in singleplayer and on dedicated servers, quality assured, and swept multiple times for issues. All assets, materials, functionality and features were derived from human developers, with AI assisting in augmenting materials for development purposes.

Based on the door system and assets of **HBM's Nuclear Tech Mod** (LGPL-3.0). See `LICENSE.LESSER` and `NOTICE` for attribution. This project is not affiliated with the original author.
