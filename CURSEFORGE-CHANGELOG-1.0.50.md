# NTM Doors 1.0.50 — Complete Feature Changelog

Minecraft 1.12.2 / Forge 14.23.5.2847+ (tested on 14.23.5.2860)
Requires Chisel 1.12.2-1.0.2.45

**File:** `ntmdoors-1.0.50.jar`
**SHA-256:** `3E1CDAD34E8A944EEED1502627F660664C79F08B4A9EF098B247927B9FF6F2C0`

This is the complete feature changelog for 1.0.50. The first section lists what changed in this
build; the rest is the full feature inventory for players upgrading from 1.0.44 or earlier.

## New in 1.0.50

### Office doors

- Added **Office Door 1**, **Office Door 2** and **Office Door 3** as 1x2 swing doors.
- Added the **2x2 double** version of each office door.
- Added the **3x3 double** version of each office door. They reuse the 2x2 models at 1.5x scale and
  use a two-cell-deep front footprint so the longer open leaves keep working collision. The
  existing 2x2 doubles are unchanged, so both sizes are available.
- Office Door 1 is the Black Mesa-style metal door with straight front/back levers and a dark
  frame. Office Door 2 is the red painted office door with a wire-glass window. Office Door 3 is
  the brown wood office door with straight levers.
- Added screwdriver colour skin cycles:
  - **Office Door 1** — original plus dark blue, olive, taupe and black.
  - **Office Door 2** — original plus dark red, charcoal, green, yellow, light grey and black.
  - **Office Door 3** — original plus light grey, red, blue and black.
  - The double and 3x3 variants inherit the same skin sets.
- Office Door 1 handles are split into separate groups (`Handle` on the single door,
  `HandleLeft` / `HandleRight` on the doubles) so the leaf and hidden edge faces can be recoloured
  without tinting the metal hardware.
- Office door textures are now **32x64**, reducing their pixel count by 75%. The original 64x128
  atlases remain in `source-art/office-doors/64x128/`.
- Added proper inventory/hotbar item models for every 3x3 office door.

### Test Door

- The 3x3 red/white containment **Test Door** is shipped and documented as the 27th public door.
  It has normal, locked and security variants like every other door.

### Door fixes

- Added a placement guard that refuses block placement inside the swept volume of an open or moving
  door panel. Fully closed doors do not reserve their future sweep, so normal building next to
  closed doors still works.
- Open doors now count as passable for mob pathing; the large silo hatch also passes falling blocks.
- Fixed a server-side `NullPointerException` in the door selection-box code when a core position was
  not loaded.
- Fixed an empty-cell selection crash on doors.

### Rendering and data fixes

- Fixed the **Redstone Monocle** overlay leaking GL state into the next frame. The overlay now
  restores depth, cull, texture and blend state in a `finally` block, preventing
  missing-chunk-looking visual corruption.
- Removed UTF-8 BOMs from 45 block/item model and blockstate JSON files. Some model loaders rejected
  the BOM and left items with placeholder icons.
- Excluded stale `.bak` files from packaged resources.

### Internal

- New doors register after every existing block and item, so saved numeric block and item IDs do not
  shift.
- Monitor terminal display state is stored per physical screen in a `ScreenState` value object; the
  Display page was extracted into `GuiMonitorDisplayController`. One screen's page, net, tab or
  scroll position can no longer affect another screen.
- The jar contains no `.bak` files or UTF-8 BOMs.

## Doors

### The 27 door types

The 17 classic HBM doors:

- **Vault-Tec Blast Door** — 5x5 rolling gear door with an extended side track.
- **Sliding Blast Door** — 7x4 double-panel blast door.
- **Roller Blast Door** — 1x7 roller shutter.
- **Blast Shutter** — 3x3 vertical steel shutter, mounted flush to the wall behind it.
- **Secure Access Door** — 5x5 rising security door.
- **Fire Door** — 4x3 roller/fire door.
- **QE Sliding Door** — 2x2 double panel.
- **QE Sliding Door, 1x2 Column** — single-column version.
- **QE Containment Door** — 3x3 rising shield.
- **Sliding Seal Door** — 1x2 pocket door.
- **Round Airlock Door** — 4x4 split iris.
- **Cargo Door** — 3x3 two-stage lift.
- **Large Vehicle Door** — 7x6 split vehicle door.
- **Water Door** — 3x3 swinging watertight door.
- **Silo Hatch** — 5x5 tilting hatch.
- **Large Silo Hatch** — 7x7 tilting hatch.
- **Transition Seal** — huge 24-second keyframed opening sequence.

The 10 additional public doors:

- **Test Door** — 3x3 red/white containment door.
- **Office Door 1** — 1x2 Black Mesa-style swing door.
- **Office Door 2** — 1x2 red painted office door with a wire-glass window.
- **Office Door 3** — 1x2 brown wood office door with straight levers.
- **Office Door 1 (Double)** — 2x2 mirrored-leaf version.
- **Office Door 2 (Double)** — 2x2 mirrored-leaf version.
- **Office Door 3 (Double)** — 2x2 mirrored-leaf version.
- **Office Door 1 (3x3)** — 3x3 scaled double with a two-cell-deep front footprint.
- **Office Door 2 (3x3)** — 3x3 scaled double with a two-cell-deep front footprint.
- **Office Door 3 (3x3)** — 3x3 scaled double with a two-cell-deep front footprint.

### Common door behaviour

- Places in one click, faces the player, and is removed as one structure.
- Core tile entity state machine: `CLOSED` / `OPENING` / `OPEN` / `CLOSING`, synced to clients.
- Every moving part animates with the original HBM timings, easing, clipping and sounds.
- State-dependent collision and selection boxes follow the animation continuously: walk through
  when open, click the remaining frame and gaps.
- Responds to redstone from any door cell (edge-triggered) and to right-click.
- Every door is registered as a normal door, a redstone-only `<door>_locked` twin, and a
  `<door>_security` twin. All 27 door types have all three forms.
- Security doors only open to a keycard scanner or the Creative Keycard by default. They record
  the player who placed them as their owner; that owner can sneak + right-click with the Redstone
  Monocle to allow redstone and Redstone Gate links. Security doors placed before owner tracking
  have no recorded owner and cannot be enabled until replaced.
- Security doors take three real hours of continuous mining with any tool and resist explosions.
  Normal and locked doors keep their original break times.
- Keypads can bind and drive normal and locked doors. Security doors are off limits to keypads, and
  a door is never driven by both a keypad and a scanner.
- Doors render their 3D item form in the inventory, hand and on the ground from the same OBJ model.
- The creative tab lists normal, locked and security forms, with each 3x3 office door placed next to
  its 2x2 counterpart.

### Door skins

Screwdriver + sneak + right-click cycles the skins:

- **Vault-Tec** — seven plate/label combinations.
- **Secure Access** — nine: red, yellow, green, blue, grey, black with white text, HBM yellow, white
  with black text, orange with white text.
- **Sliding Seal** — six: default, clean, trefoil, plain white, plain red, plain grey. The plain
  skins have no markings.
- **Fire Door** — five: default, black, orange, yellow, trefoil.
- **Round Airlock** — four: worn, clean, green, space, all keeping the grey concrete frame.
- **Sliding Blast** — three: default, black, grey.
- **QE Sliding Door / Column** — default and white.
- **QE Containment** — plain, trefoil, yellow trefoil.
- **Water Door** — default and clean.
- **Office Door 1** — original plus dark blue, olive, taupe and black.
- **Office Door 2** — original plus dark red, charcoal, green, yellow, light grey and black.
- **Office Door 3** — original plus light grey, red, blue and black.

The screwdriver also starts a keypad code reset, cycles an unused keypad's binding mode, and plays
`button9` for every interaction it handles so nearby players hear the tool being used.

## Keypad

A wall-mounted code panel that binds to the door it touches:

- 1–6 digit codes, physical buttons, LCD prompts, leading zeros preserved.
- A second keypad on the same door shares its code. Placing one next to an already-bound door
  auto-syncs and shows purple `Code synced`.
- The binding stays on the door if the keypad is broken, until the door itself is destroyed.
- `R` closes an open door when the pad's code matches the door.
- Screwdriver reset: confirm, enter the current code, and the door plus all linked pads are cleared.
- Sneak + screwdriver cycles binding modes while the pad is unused:
  - **Distance** — link one closed door within 32 blocks.
  - **Multi** — link up to 9 doors, adjacent or remote, all on one shared code.
- After linking, `Confirm Door Bounding?` is shown and `E` accepts; the pad then shows
  `Input code` / `Input multi code`.
- Once a pad has a code or any link, its mode is locked and the screwdriver can only reset it.
- Keypads have a separate GUI security code in addition to the normal door code.
- Redstone Relay Key: normal right-click starts or finishes a wireless gate-binding session.
- Redstone Relay Key: sneak + right-click opens a security-code screen, then a two-page keypad
  screen. Page one sets the first door code; page two has `On fail` / `On success` gate outputs plus
  `Reset Code`, `Override Door Locks` and `Force Close`.
- Every key press clicks, and each result chimes at the pad: granted for an accepted code, set or
  reset code, or auto-synced code; denied for a wrong code or an impossible binding.

## Keycard access control

- **Keycard Scanner** — wall-mounted reader with an LCD, security stripe and green/yellow/red
  indicator lenses. Sneak + right-click with the screwdriver starts a binding session, then link up
  to nine closed doors within 32 blocks and confirm. Normal, locked and security doors can all be
  bound. Cards only open a scanner when they carry one of its assigned private keys, and the
  Creative Keycard opens everything. Bound doors close again after ten seconds.
- **Keycard Programmer** — desk console with a card slot, live screen and three GUI pages:
  - Page one binds a Relay Key's private key onto an Operator Keycard or a coloured RFID card.
  - Page two restyles a card from the 31-entry palette: seven colours, five themed cards, six SCP
    clearance cards, the Googa card and the Level 1–12 access cards. Operator, Creative, Biometric
    and Developer cards are excluded.
  - Page three is the Legacy Backup Programmer: Relay → Relay, Relay → Operator and
    Operator → Operator are allowed; Operator → Relay is not. It uses a five-frame, ten-second
    progress bar.
- **RFID cards** — blank plus red, violet, green, blue, black, orange and yellow variants.
- **Themed keycards** — Keycard with Blue Marking, Labyrinth, Engine Room, Terminal and Terragroup
  Labs Access, each with its own sprite and flavour line.
- **SCP access cards** — SCP Level 0–5 clearance cards, plus the Googa security keycard with its gold
  name and `Security is goog` tooltip.
- **Level 1–12 Access Keycards** — numbered cards that can be programmed and used like normal RFID
  cards and are available from the programmer's card palette.
- **Developer Keycard** — gold-named card for pack developers. It behaves like a normal RFID card,
  but displays stored private keys and biometric IDs as Unknown. Excluded from the programmer's card
  palette.
- **Operator Keycard** — aimed at a bound scanner, opens its security code screen; aimed at a
  Redstone Gate, opens its settings screen. Elsewhere it shows its private keys. The Biometric
  Operator Keycard does the same.
- **Creative Keycard** — bypasses card checks on bound doors, opens every scanner, opens both
  safes, and sneak + right-clicking a scanner opens its full console without a code or bound doors.
  It also opens the Redstone Gate screen.
- **Relay Key and Biometric Relay Key** — create named private keys; the biometric version captures
  the holder's identity instead. A Relay Key name can be up to 32 characters, and confirming it
  generates a ten-digit serial on the server. A key can be created once and never reset.
- **Biometric Keycard and Biometric Operator Keycard** — carry an identity, show the owner in the
  tooltip, and work with the scanner's Match biometric holder option. The Operator version also
  lists its stored private keys in the tooltip.
- **Keycard Holder** — carried 3x4 case for twelve keycards, with its own NBT storage, empty-slot
  ghosts and shift-click transfer. Stored cards survive inventory moves, death drops and world
  saves.
- **Keycard Viewer** — sneak-right-click a card to read every private key stored on it. The Creative
  Keycard shows the joke entry `THE ENTIRE LIBRARY OF BABEL` under
  `You have access to EVERYTHING!!!!!`.

### Scanner security code and assignment

- Security code screen: set a 1–6 digit code with one entry; later visits repeat it. Correct and
  wrong codes show `SUCCESS!!!` / `FAILED!!!` cards.
- Key assignment screen: `ASSIGNED KEYS` on the left, `NON-ASSIGNED KEYS` from the held Operator
  Keycard on the right, with click-to-move rows and wheel scrolling.
- Options: Match biometric holder, Lockdown mode, Lockout mode, Ignore private keys, Ignore
  biometric IDs, Override door locks, Force close and Reset security code.
- Lockdown closes every bound door and refuses cards, codes, keypads and redstone until it is
  lifted.
- Lockout shuts the reader for five minutes after three bad cards or codes; the Biometric Operator
  Keycard can still reach the code screen.
- Scanner audio: `button3` on insert/success, `button8` on denial and a looping `bell1` while
  scanning.

## Safes

- **Code Safe** — a 1x1x1 steel safe with a keypad front. The first accepted 1–6 digit code becomes
  its code; later entries must match. There is no owner restriction, so anyone who knows the code
  can open it. A correct code shows the `SUCCESS!!!` card and then opens a 27-slot container.
- **Biometric Safe** — the same shell and 27-slot container, opened by a bound Biometric Operator
  Keycard or the Creative Keycard. A newly placed safe records the placer's biometric ID, and only a
  card carrying that ID can open it. Safes placed before this change stay unowned and accept any
  valid biometric card. `Match user` restricts access to the card's enrolled holder; with it off,
  any bound biometric operator card carrying the safe owner's ID opens the safe.
- Both safes save their contents and settings, show full item names and NBT tooltip lines in the
  container, and keep their slots hidden from hoppers and logistics automation.
- Both safes take three real hours of continuous mining with any tool and resist explosions.
- Each safe has a second options page:
  - Code Safe: Binding Activities, Lockout mode, Reset code and Void on destroy.
  - Biometric Safe: Match user, Binding Activities, Lockout mode and Void on destroy.
- Lockout mode stops access for five minutes after three failed codes or cards. Reset code clears
  the code so the next entry sets a new one.
- `Void on destroy` starts on: breaking the safe voids the safe item and everything inside it.
  Switching it off makes the broken safe drop its contents again.

### Safe-to-door binding

- **Safe Binding Key** — a small green USB key with a green name and a tooltip explaining its
  controls.
- Sneak + right-click a safe to start or finish a binding session, then sneak + right-click closed
  doors within 32 blocks to link or unlink them.
- A safe can link several doors. A door belongs to at most one safe, but a door already controlled by
  a keypad or scanner keeps that binding; the safe link is additive and both systems remain
  respected.
- Both safes have a **Binding Activities** toggle, on by default. When on, the safe's lockout closes
  every linked door once. Turning it off prevents new binding sessions and pauses the lockout-door
  response without deleting existing links.
- Breaking a safe or a door clears the link.

## Monitor desks and security terminal

### Desks

- **6 Monitor Setup** (`ntmdoors:monitor_setup`, 3x3x1) — six monitors, a keyboard, mouse and
  mousepad. The bottom row is solid, while the two upper rows use a half-depth collision wall over
  the monitors so the player can stand and jump on the front half of the desk but cannot walk
  through the screens.
- **3 Monitor Setup** (`ntmdoors:triple_monitor_setup`, 3x2x1).
- **Dual Monitor Setup** (`ntmdoors:dual_monitor_setup`, 3x2x1, with the open right-middle cell left
  free).
- **Dual Monitor Setup (short table)** (`ntmdoors:dual_monitor_setup_2x2`, 2x2x1).
- **Monitor** (`ntmdoors:single_monitor_setup`, 2x2x1).
- All five place as one item, face the player, reserve their full footprint, and break as one
  structure when any cell is broken.
- Every monitor cell opens the same password-gated terminal. The desk and keyboard row stays inert,
  and sneak-right-click still lets blocks be placed against the monitors.
- The 6-monitor desk is split into nine cell models so every element stays inside the 1.12 model
  limits. The supplied 15-degree side-monitor yaw is rounded to 22.5 degrees, the closest legal JSON
  angle.
- Three block types (bottom/middle/top) each carry three columns, so the part and facing fit in the
  four metadata bits. The bottom core carries the terminal tile entity.

### Terminal access

- The first open offers to set a per-desk password; later opens require it. Passwords are stored as
  SHA-256 hashes on the desk core, checked server-side, and never sent to clients.
- The unlocked task menu lists **Security**, **Opencomputers**, **Factory**, **Display**,
  **Notepad** and **Terminal Backup**. Opencomputers and Factory are reserved menu entries and do
  not open a page yet; the other four are fully implemented.
- Every page has an `EXIT` control and uses the supplied mouse-click sound for buttons and rows;
  the Notepad's `EXIT` sits in its title bar.
- Physical monitor screens mirror the live terminal page. Each physical screen keeps its own page,
  selected Security net, tab and scroll position, so using one screen never changes the others.
  This per-screen state is the 1.0.50 `ScreenState` / `GuiMonitorDisplayController` refactor.

### Security nets

- Security is organised into named **Security nets** with per-desk device lists. The landing page
  lists each net with its device count; an `Add security net` row sits beneath them.
- Adding a net prompts for a name and uses `Security net N` when the name is left blank.
- Each net page has a Terminal Relay Key slot: hold a key, click the slot, then press `Bind` to link
  the key to that net. `Rename` changes the linked key's display name and `Unlink` clears the link.
  `Delete net` asks for confirmation, then removes the net and its device list and unlinks its key.
- A `Devices` / `Events` tab row switches between the device list and per-net event controls.
- **Terminal Relay Key** — sneak + right-click a monitor desk to start a binding session, then
  sneak + right-click doors, keypads, keycard scanners, motion sensors, Redstone Gates and speakers
  to add them, with a naming prompt for each device. Return to the same desk and sneak + right-click
  it again to confirm the whole list. Starting at a different desk discards an unconfirmed session.
- Door rows carry `Open` and `Close` buttons that control that door directly, including doors with
  no scanner or keypad binding; the terminal bypasses those local locks.
- Every other device row has an `ON` / `OFF` switch: off makes a keypad or scanner ignore input,
  stops a motion sensor detecting, forces a Redstone Gate output low, and mutes a speaker. The
  switch state is saved on the device.
- Events: **System Shutdown** idles every device in the net; **System Lockdown** closes all net
  doors and keeps them shut; **Trigger Alarms** sounds every speaker; **Disable Keypads**,
  **Disable Scanners**, **Disable Sensors** and **Disable Redstone Gates** switch off those device
  families. **Close all doors** and **Open all doors** are one-shot actions.
- Event state is stored in the world save and stays enforced while the desk chunk is unloaded.
  Toggling an event never changes device bindings.
- A key links to one net at a time and a net accepts one key. Starting a linked key at another desk
  reports `Key is already linked`.
- Remote access: clicking a linked device opens its screen while the player remains at the terminal.
  The session requires the player to stay within reach of the desk and the device to remain in the
  confirmed net; it expires after ten minutes of inactivity. Keypads and scanners still ask for
  their security codes. A linked door opens the first loaded scanner controlling it, or reports
  `No scanner`. Escape returns to that net's linked-device list.
- Each physical screen renders a passive `SECURITY` status panel with net and device counts plus the
  active event state. Clicking any screen opens the normal terminal GUI.

### Display wallpapers

- A per-screen **Display** task: five built-in wallpapers plus a `Terminal` tile that restores the
  normal terminal page.
- Selecting a tile applies it immediately to the screen that was clicked.
- The custom tab accepts a direct `http://` or `https://` PNG/JPG URL. Each viewing client downloads
  it locally with 8 MB / 4096 px limits. Static images only; video and animated GIFs are not
  supported.
- The choice is stored on the desk and survives restarts. Built-in source images live in
  `source-art/display/` and are packaged under `assets/ntmdoors/textures/display/`.

### Notepad

- A shared multi-document editor stored on the desk: up to 16 documents of 8,192 characters each.
- The sliding left sidebar lists documents, creates new ones, and renames or deletes the selected
  document.
- Notes open as a blank paper page with keyboard editing, wrapped lines and scrolling. `Save` is at
  the bottom right and `EXIT` is in the title bar.
- Closing or switching documents with unsaved changes offers `Save`, `Discard` and `Cancel`.
- Leaving the terminal with `EXIT` keeps the current document and the document list displayed on the
  physical monitor screen. A saved wallpaper steps aside while the notebook is shown and returns on
  the task menu.
- Documents are stored in the desk's world NBT and shared by everyone using that terminal. Holding a
  key repeats typing.

### Terminal backup

- **Terminal Backup Key** — a black and red USB key. A blank key shows no data; an uploaded key
  lists its saved net and document counts in the tooltip.
- **Initiate backup** — put a Terminal Backup Key in the left slot and press `Upload` to write every
  Security net, device binding, event state, Notepad document, wallpaper, saved screen page, next ID
  and the terminal password hash onto that key. Put a blank Terminal Backup Key in the right slot
  and press `Backup`; the copy takes ten seconds and uses the same progress bar, loop and completion
  chime as the keycard programmer's legacy backup.
- **Restore backup** — opens the same console with a single key slot. Press `Restore` to overwrite
  the current terminal with the key's saved data. The key is not consumed. Backups made before
  password support keep the target terminal's current password.

## Speaker

- A 1x1x1 black-ash cabinet with a woofer, tweeter and bass reflex port.
- Shift-right-click with any item or an empty hand opens a safe-style toggle screen with **Enabled**,
  four selectable sounds (**Air Raid**, **Facility**, **Industrial**, **EAS**), a 1–64 block range
  slider and a 0–100% volume slider. Anyone may use it.
- While enabled and receiving redstone power, it loops the selected alarm until the signal or
  **Enabled** is removed. The alarm uses a client-side loop, so switching off redstone or Enabled
  stops it immediately instead of letting a long alarm ring out.
- The alarm is mixed at the listener, so looking toward or away from the speaker does not change its
  loudness; only distance inside the configured range fades it.
- The sound, range, volume and enabled state are saved with the block.
- A normal right-click with the Redstone Relay Key starts or finishes a wireless binding session, and
  starting the alarm pulses every linked Redstone Gate.

## Motion Sensor

- A compact 8x8x1.375 surface-mounted PIR sensor with an off-white ABS housing, charcoal bezel,
  ringed concentric Fresnel lens and a status light.
- It can be placed on floors, walls and ceilings; the clicked face becomes the lens direction. The
  collision and selection boxes follow the lens direction.
- It scans the cells selected inside a 5x5 footprint that projects five blocks away from the
  mounting surface along the sensor's facing, and checks every card in a player's inventory against
  the private keys and biometric IDs assigned through its console. Detection goes through walls.
- A normal right-click with the **Motion Relay Key** opens the console. Page three is a 5x5 grid:
  left-click adds one block of depth to a column, right-click removes one, and opacity shows the
  selected depth. The centre cell keeps the green sensor dot. Sneak + right-click the sensor toggles
  a client-side transparent blue projection of the selected cells; sneak + right-click again hides
  it.
- Page two has `Detect players`, `Detect hostile mobs` and `Detect neutral mobs`; all three start on,
  so every living entity type is covered. Non-player living entities are always unauthorized
  intruders, while players use the card rules above.
- `Persist within bounds` is also on page two: when on, a linked gate stays powered while any entity
  matching an enabled entry event remains inside, and releases as soon as the area is empty.
  `Persist out of bounds` is the inverse and mutually exclusive with it: the gate is powered while
  the area is empty and the hold drops as soon as anything steps in. When several out-of-bounds
  sensors share one gate, every one of them must be empty.
- Status light: green means idle, blue means authorised players are inside, red means any
  unauthorized player or mob is inside, and yellow means the sensor is disabled or has no area
  bound. Creative right-click cycles the light for preview.
- Page four is the red `REDSTONE` page with `Authorized entry`, `Unauthorized entry`, `Sensor
  disabled` and `Sensor enabled` event switches. Sneak + right-click the sensor with the Redstone
  Relay Key to start or finish a gate-binding session, then sneak + right-click gates to link them.
  With both persistence modes off, each enabled event pulses every linked gate on the matching entry
  edge.

## Redstone and wireless control

- **Redstone Gate** — a full-cube redstone component with separate off and on textures. It starts
  off, turns on while it receives redstone power or a wireless pulse, and emits a full-strength
  signal while on. Redstone dust sitting on top is refreshed when the gate switches, so it cannot
  stay latched after the signal or a hold is removed.
- A wireless event keeps the gate powered for its configured duration; another event extends the
  pulse. The settings screen has `Stay active after signal`, `Invert operation`, and activation
  duration controls from 1 second to 5 minutes using +1/+5/+10/+20 second and matching subtract
  buttons. Both plain and Biometric Operator Keycards, and the Creative Keycard, open the screen.
- A gate can also be the start of a relay-key session: link other gates and doors to its output, and
  they follow it on and off. Links are directional, work in the same dimension, and a link that
  would close a feedback loop is refused.
- **Redstone Relay Key** — a red-named creative key. Sneak + right-click a safe, scanner or motion
  sensor to start or finish a wireless binding session, then sneak + right-click a Redstone Gate to
  link or unlink it. On a keypad or speaker, a normal right-click starts or finishes the session;
  sneak + right-click opens the keypad screen, while the speaker uses shift-right-click for its own
  screen. Sneak + right-click a gate to start or finish a gate session, then sneak + right-click
  other gates to relay the signal or doors to open and close them with that gate. Devices and gates
  can be linked many-to-many in the same dimension. Doors are driven by gate sessions only, breached
  doors refuse the link, and security doors accept it only after their owner enables Redstone access
  with the Monocle.
- **Redstone Monocle** — a red-named creative diagnostic item. While held in either hand it draws
  the loaded wireless links through walls: red for gate-to-gate, amber for gate-to-door, and blue
  for device-to-gate. Sneak + right-click a security door as its owner to toggle whether that door
  accepts redstone and gate links; other players are refused.
- **Weapons Relay Key** — a red creative key. Sneak + right-click opens a mode screen:
  **Regular Bounding** links any explosive block to Redstone Gates (a powered gate triggers it
  immediately), and **Dead Man's Hand** hard-locks the explosive so anyone without the matching key
  sets it off by right-clicking, left-clicking, breaking it, or placing/breaking a block next to it.
  The key holder can mine it safely. Works with any explosive block, not only ICBM.
- Both safes and the keycard scanner have a third `Redstone` page with a red panel tint and
  scrolling. Event toggles start off and pulse every linked gate for that gate's configured
  activation duration when enabled:
  - Code Safe: Code fail, Code success, Binding activities, Lockout.
  - Biometric Safe: Scan fail, Scan success, Match holder fail, Match holder success, Lockout.
  - Keycard Scanner: Scan fail, Scan success, Match holder fail, Match holder success, Private key
    fail, Private key success, Biometric fail, Biometric success, Lockout.
  - Keypad (Redstone Relay Key screen): On fail, On success.
- Safe-to-door, scanner-to-door, keypad and safe-binding links are untouched. Breaking a safe,
  scanner, keypad, gate or linked door clears its reciprocal wireless links.

## Breaching and demolition

- **Thermite Charge** — a framed breaching charge with a flat 16x16 item sprite and a 3D placed
  model. It mounts flush to the clicked block or door face in all six directions and sits on the
  door's hitbox surface. Silo hatches, the transition seal, the roller blast door and the Vault-Tec
  door refuse placement.
- Flint and steel lights a five-second fuse with a beep countdown, smoke, flame particles and a
  supplied explosion sound. While it burns, the attached door ignores hand clicks, redstone,
  keypads, scanners, safes, security nets and the screwdriver; breaking the charge defuses it.
- Detonation breaches a supported door: the animated moving panel and its collision are removed
  while every static frame cell and frame box stays, and the door becomes permanently unusable.
  Breached doors count as open for mob pathing.
- A charge on a normal block destroys that block; unbreakable blocks are left alone. A burning
  charge that loses its support or attached door detonates instead of dropping.

## Building sets

### Concrete

- The full NTM CE concrete building set: plain, smooth, 16 dyed, Builder's Choice, concrete bricks,
  pillar, rebar, asbestos, Über Concrete and Moldy Debris.
- 61 placeable block ids plus 6 technical double slabs, covering 142 texture/metadata variants.
  Creative and HEI list 127 of them; Über Concrete stages 1–15 are deliberately hidden and remain
  reachable by metadata.
- Cubes are one block id per family; metadata selects the texture. Stairs stay one block id per
  texture because `BlockStairs` already uses its metadata bits for facing and half. Slabs use one
  block id per family with `variant` and `half`, plus a technical `_double_slab` block for the double
  form.
- Über Concrete decays through sixteen stages on random ticks; the last stage crumbles into Moldy
  Debris that falls like sand or gravel.
- Concrete has crafting recipes, including half-scale crafting-table stand-ins for CE's chemical
  plant and crystallizer steps. Smooth, asbestos and rebar concrete use clay, string and iron bars
  respectively. Blocks make slabs and stairs, never the reverse.
- Every concrete cube, stair and single slab is registered under `concrete`; full cubes are also
  under `blockConcrete`.
- The concrete families are registered as flat Chisel carving groups:
  `concrete`, `concrete_stairs`, `concrete_slabs`, `concrete_colored`, `concrete_colored_stairs`,
  `concrete_colored_slabs`, `concrete_bricks`, `concrete_bricks_stairs` and
  `concrete_bricks_slabs`. Pillar, rebar, asbestos and Über Concrete are not registered.
- Shift + scroll cycles a held concrete stack through its texture family. The cycle modifier is
  rebindable under Controls → HL Blocks.

### HL Blocks

- The included HL Blocks set: three simple blocks and seven metadata groups: freezer, fifties,
  crete, duct, lab, tunnel and generic.
- Every included HL block is registered under the `blockhl` ore dictionary entry.
- HL Blocks are creative-only and are listed in the NTM Doors creative tab. Shift + scroll also
  cycles their metadata families.

### Ultra High Performance Concrete

- **Ultra High Performance Concrete** — six grayscale shades: original, light gray, gray, dark gray,
  charcoal and black. Every variant takes three real hours of continuous mining with any tool.
- Blast resistance is an explicit allow-list: only ICBM Classic nuclear, antimatter and red matter
  warheads can damage it. Thermobaric, TNT, creepers and every other explosion are fully resisted.
  The hard set reports iron as its material so ICBM's exothermic/endothermic terrain beams skip it.
- **UHPC Panel** — a decorative set with the same six textures and vanilla-concrete hardness, so it
  breaks and blasts apart normally.
- The hard set carries the `GBU-57 not included` tooltip; the soft panels do not.
- Both sets register under `concrete` and `blockConcrete`, are creative-only and are not registered
  in a Chisel group.

## Documents Folder

- A carried folder item drawn as three stacked sheet layers, 3 px deep in total, with only the
  middle layer's page edge protruding.
- Right-click opens a twenty-page editor styled as an open manila folder in the item's tan, grey and
  dark-red palette, with the writing sheet in white behind a thin ink border.
- Typed caret with click-to-place, arrows, Home/End, Delete/Backspace, Ctrl+V paste and word wrap.
- Enter starts a new line; press it twice for a blank paragraph gap.
- Bottom index tabs jump to any page; arrows, Page Up/Down and the mouse wheel also flip pages.
- Auto-saves on close, with no signing step. Pages, custom folder name and last page are stored in
  NBT and synchronised to the server.
- Reopens on the page you closed on. The top-right title is editable: click to rename, Enter saves,
  Esc cancels, clearing restores the default name.
- The editor screen is scaled up 1.5x, with a fit guard for smaller windows. The tooltip shows the
  number of pages written.

## Rendering, sounds and compatibility

- OBJ/TESR door rendering with per-group translate/rotate animations, X/Y/Z clipping and shared door
  sound events. 28 sound entries cover doors, alarms, scanners, keypads and machines.
- 3D door item rendering in the inventory, hand and on the ground from the same OBJ model, matching
  HBM's door item renderer.
- Minecraft **1.12.2**, Forge **14.23.5.2847+** (tested on 14.23.5.2860).
- Requires **Chisel 1.12.2-1.0.2.45**. No NTM installation is required. Every model, texture and
  sound is bundled.
- No configs and no NTM dependency. The mod adds no global worldgen.
- New blocks and items register after the existing entries, so saved numeric IDs keep their
  positions. Existing 1.0.44 worlds load normally.
- The screwdriver and the concrete set have crafting recipes. Doors, keypads, scanners, programmers,
  safes, cards, holders, folders, monitors, redstone devices, speakers, sensors, Thermite Charges,
  Spatial Disrupters and UHPC are creative-only unless a pack adds its own recipes.
- All doors, tools, cards, machines, monitors, concrete and HL Blocks are listed in the NTM Doors
  creative tab.
