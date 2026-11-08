HBM DOORS: 1.0.42
Minecraft 1.12.2 / Forge 14.23.5.2847

1.0.42
FEATURES
- Added Ultra High Performance Concrete: six grayscale shades (original plus five darker tones),
  three-hour mining time, and blast resistance that only allows ICBM nuclear, antimatter and red
  matter warheads through.
- Added the decorative UHPC Panel set with the same textures and vanilla-concrete hardness.
- Added the Weapons Relay Key: Regular Bounding links any explosive to Redstone Gates; Dead Man's
  Hand hard-locks an explosive so anyone without the matching key sets it off by interacting with
  it, while the key holder can mine it safely.
- Added the Spatial Disrupter: a bedrock-hard decorative machine that, when activated, converts
  every hard UHPC block in a 16x16 chunk area into the soft UHPC Panel and then consumes itself.
FIXES
- Removed the hard set's tooltip from the soft UHPC Panel.
- Fixed the Spatial Disrupter self-destruct to remove its block outside the tile-entity tick loop,
  avoiding a server-side ConcurrentModificationException.

1.0.36
CHANGES
- Pressed the Motion Sensor front assembly flush against the back plate, reducing its depth from
  2.25 to 1.375 pixels.
- Updated the collision and selection boxes and the centred inventory model to the new depth.

1.0.35
CHANGES
- Pressed the Motion Sensor's front assembly into the back plate, reducing its depth from 4.5 to
  2.25 pixels.
- Updated the collision and selection boxes and the centred inventory model to the new depth.
- Kept the 8x8 footprint, ringed lens and status-light behaviour.

1.0.34
CHANGES
- Reduced the Motion Sensor to a smoke-detector-sized 8x8 footprint and 4.5 pixel depth.
- Rebuilt the lens with a defined metal border and concentric Fresnel rings, removing the central
  cross.
- Added a separate centred model for the inventory icon so the shallow housing does not sit
  off-centre in the GUI.

1.0.33
CHANGES
- Scaled the Motion Sensor housing down to a 10x10 footprint and 10.5 pixel depth.
- Rebuilt the lens from the supplied rounded reference, with noise and a symmetrical
  four-quadrant Fresnel pattern.
- The Motion Sensor can now be placed on floors, walls and ceilings. The clicked face becomes the
  lens direction, and the collision and selection boxes follow it.
- The status light now lives on the tile entity and is applied through the actual block state, so
  all four colours work with six-way facing. Creative right-click cycles the preview.

1.0.32
FEATURES
- Added the Motion Sensor block with a 16x16 wall-mounted PIR model, a symmetrical Fresnel lens
  and four status-light textures: green for idle, blue for authorised personnel, yellow for
  maintenance and red for motion. Detection logic is not wired yet.
- Added the Motion Sensor to the NTM Doors creative tab.

The public 1.0.31 release is the cumulative build of the internal 1.0.31-1.0.47
changes. The public-facing CurseForge changelog is
`CURSEFORGE-CHANGELOG-1.0.31.md`; the internal history follows.

1.0.47
FIXES
- Concrete and HL Blocks now appear in the NTM Doors creative tab. The separate NTM Concrete tab
  and the vanilla Building Blocks placement were removed.
- Renamed the SCP access cards from levels 1-6 to 0-5, including their registry names, models,
  textures and display names. Existing level 6 cards are remapped to level 5 when an old world
  loads; the shared 1-5 registry names remain valid.

1.0.46
FEATURES
- Added the NTM CE concrete building set: plain, smooth, 16 dyed, Builder's Choice, bricks,
  pillar, rebar, asbestos, Über Concrete and Moldy Debris.
- Added the included HL Blocks: three simple blocks and seven metadata groups.
- Added metadata families for concrete cubes and slabs, flat Chisel carving groups, shift-scroll
  cycling, recipes and `concrete` / `blockConcrete` / `blockhl` ore dictionary entries.
- Added the single-slab blockstate key-order fix.
CHANGES
- Chisel 1.12.2-1.0.2.45 is now required.
- Concrete and HL Blocks register after all existing blocks and items, so saved numeric IDs do not
  move.
- Chisel stairs and slabs register one canonical state per texture instead of every orientation, so
  carving no longer cycles through stair facings or slab halves.
FIXES
- Fixed the concrete slab model lookup by writing blockstate keys in the order Forge uses.

1.0.45
FEATURES
- The Speaker now offers four selectable alarms: Air Raid, Facility, Industrial and EAS.
- Added an independent 0-100% volume slider next to the range slider.
- Speaker alarms now use a client-side loop, so switching off redstone or Enabled stops the
  selected sound immediately instead of letting a long alarm ring out.

1.0.44
FEATURES
- Added a 1-64 block range slider to the Speaker screen, defaulting to 32. The range controls how
  far the alarm is heard; the value is saved with the block.
- Speakers can now join the Redstone Gate network. A normal right-click with the Redstone Relay Key
  starts/finishes the speaker's binding session, then gates are linked as usual. Starting the alarm
  pulses every linked gate.

1.0.43
FEATURES
- The Speaker is now functional. Shift-right-click (any held item or empty hand) opens a
  safe-style toggle screen with Enabled and Sound; anyone may use it. While enabled and receiving
  redstone power, it loops the existing lockout alarm until the signal or Enabled is removed.
  The alarm is the first placeholder sound; the selected sound is saved with the block.

1.0.42
FIXES
- The Speaker now uses the safe's non-opaque, non-full-cube rendering/support setup, so its
  recessed front and shallow driver/trim details are not culled by neighbouring blocks.

1.0.41
FEATURES
- Added the decorative Speaker block (`ntmdoors:speaker`). It is a 1x1x1 cabinet with a woofer,
  tweeter and bass reflex port, faces the player when placed, and has no tile entity or behaviour.

1.0.40
CLEANUP
- Removed unused Redstone Gate and Redstone Binding accessors.
- Cached each door's normalized item-model bounds instead of recalculating them every frame.
- Door rendering now skips redundant texture binds when consecutive groups share a texture.
- Removed the stale version-specific CurseForge 1.0.30 notes; the changelog and current
  CurseForge description are the maintained release documents.

1.0.39
FEATURES
- Keypads now have a separate GUI security code. Sneak + right-click with the Redstone Relay Key
  opens a code screen: the first entry sets the code, later entries must match before the keypad
  screen opens. The normal door code remains a separate code.
- The Redstone Gate screen now has Stay active after signal, Invert operation, and activation
  duration controls: +1/+5/+10/+20 seconds and -1/-5/-10/-20 seconds, clamped to 1 second - 5
  minutes. Both the plain and Biometric Operator Keycard can open the gate screen.
- Inverted gates are powered without input and switch off while a signal is present, including
  wireless pulses and connected redstone.

FIXES
- Added the missing item models for the security silo hatches, and limited the silo item preview
  to the hatch lid so both silo variants render correctly in hand.

1.0.38
FEATURES
- Keypads now work with the Redstone Relay Key. A normal click starts/finishes the wireless gate
  session; sneak + right-click opens a two-page keypad screen. Page one sets the first code, and
  page two has the On fail / On success gate toggles plus Reset Code, Override Door Locks and Force
  Close actions.
- Added security variants for both silo hatches. All 16 doors now have a `_security` form.
- Safes and security doors now take three real hours of continuous mining with any tool to break,
  and their blast resistance is raised so explosions cannot shortcut that.
- Normal and locked doors keep their existing break times.

1.0.37
FIX
- The whole redstone network attached to a Redstone Gate now settles after a wireless pulse.
  The gate ignores input for one tick while it is forced off, so lines beyond the first dust
  drop to 0 and the gate cannot latch itself back on.

1.0.36
FIX
- Fixed the Redstone Gate latching on when redstone dust sits on top of it. The pulse expiry
  now forces the gate off before it checks for real external power, so the dust can no longer
  feed the gate's own output back in as an input.

1.0.35
FEATURES
- The Creative Keycard now opens the scanner console directly on sneak + right-click, with no
  security code and even when the scanner has no bound doors.
- The Redstone Gate now has a screen, opened by sneak + right-clicking it with a Biometric
  Operator Keycard or the Creative Keycard. It uses the scanner's red page and is intentionally
  empty for now, ready for gate-specific controls.
- Wireless events now keep the gate powered for a full 20 ticks using an absolute expiry, so the
  pulse cannot lose a tick depending on tile-entity update order. Further events extend it.

1.0.34
FIXES
- Fixed Redstone Relay Key binding on Keycard Scanners. The scanner right-click guard
  canceled any held item except its allow-list, and the Redstone Relay Key was missing.
- Safes no longer expose their slots to hoppers or logistics automation. Their sided
  inventory is empty and their item-handler capability exposes zero slots.

1.0.33
FIX
- Fixed the Redstone Relay Key not starting a binding session on sneak + right-click. Vanilla
  skips block activation while sneaking, so the Redstone binding now runs from the key's
  item-use path, matching the Safe Binding Key.

1.0.32
REDSTONE NETWORK
- Both safes and the keycard scanner have a third Redstone page with scrolling and a red
  panel tint. The safe page does not show the player inventory.
- Safe event toggles: Code fail, Code success, Binding activities and Lockout on the code
  safe; Scan fail, Scan success, Match holder fail, Match holder success and Lockout on the
  biometric safe.
- Scanner event toggles: Scan fail, Scan success, Match holder fail, Match holder success,
  Private key fail, Private key success, Biometric fail, Biometric success and Lockout.
- Event toggles are off by default. Enabled events pulse every linked Redstone Gate for 20
  ticks.
- Sneak + right-click a safe or scanner with the Redstone Relay Key to start/finish binding,
  then sneak + right-click a Redstone Gate to link or unlink it. Devices and gates can be
  linked many-to-many, in the same dimension, with no range limit.
- Breaking a safe, scanner or gate clears its reciprocal links. Existing safe-door,
  scanner-door, keypad and safe-binding links are untouched.

1.0.31
REDSTONE
- Normal and locked doors now both respond to redstone. Security doors still require a
  keycard scanner or the Creative Keycard, and lockdown still blocks redstone edges.
- Added the Redstone Relay Key. It has a red menu name and the tooltip "Allows Keypads,
  Keycard Scanners, and Safes to be bound to the Redstone Gate". The binding logic is not
  implemented yet.
- Added the Redstone Gate. It has a red menu name, starts off, switches to its on texture
  while receiving redstone, and emits a full-strength signal while on. The bound-activity
  logic is not implemented yet.

1.0.30
SAFE BINDING
- The Safe Binding Key now has a green name and lists its sneak + right-click controls in the
  tooltip. Sneak right-click a safe to start or finish a binding session, then sneak right-click
  closed doors to link or unlink them.
- A safe can link several doors. A door belongs to at most one safe, and can still be controlled
  by its keypad or scanner; those bindings are left untouched.
- Both safes have a Binding Activities toggle on the options page. When on, the safe's lockout
  closes every linked door once. Turning it off pauses linking and the lockout-door response
  without deleting existing links. Breaking a safe or door clears the link.

1.0.29
SAFES
- Code Safe: a 1x1x1 steel safe with a keypad front. The first accepted 1-6 digit code becomes
  the safe's code; later entries must match. There is no owner restriction, so anyone who knows
  the code can open it.
- The safe container has 27 slots and a second page with Lockout mode and Reset code. Lockout
  mode stops code entry for five minutes after three wrong codes; Reset code clears the code so
  the next entry sets a new one.
- Biometric Safe: the same shell and 27-slot container, opened by a bound Biometric Operator
  Keycard or the Creative Keycard. Its second page has Match user and Lockout mode.
- Both safes save their contents and settings, drop their inventory when broken, and show full
  item names and NBT tooltip lines in the container.
- The Creative Keycard opens both safes and every scanner directly.

CARDS
- SCP Level 1-6 access keycards, with Level 1-6 clearance tooltips.
- Googa security keycard, with the tooltip Security is goog and a gold name.
- The Biometric Operator Keycard lists its stored private keys in the tooltip.

FIXES
- The Biometric Relay Key now records the player UUID and name on dedicated servers. The old
  right-click check called a client-only ray trace and crashed the server.
- Operator Keycard scanner lookup uses the same server-safe ray trace.

Everything below is new since the 1.0.26 CurseForge release. The 1.0.26 jar contained
the doors, keypad and screwdriver only; 1.0.28 added the full keycard access-control layer
and the Documents Folder.

KEYCARD ACCESS CONTROL
- Keycard Scanner: wall-mounted reader with an LCD, security stripe and green/yellow/red
  lenses. Bind up to nine closed doors within 32 blocks using the screwdriver; bound
  doors close again after ten seconds. Cards only open a scanner when they carry one of
  its assigned private keys, and the Creative Keycard opens everything.
- Keycard Programmer: desk console with a card slot, live screen and three GUI pages.
  Bind Relay Keys to Operator Keycards and RFID cards, recolour cards with seven colour
  options, and run the Legacy Backup Programmer with its five-stage progress bar.
- RFID cards: blank plus red, violet, green, blue, black, orange and yellow variants.
- Themed keycards: Keycard with Blue Marking, Labyrinth, Engine Room, Terminal and
  Terragroup Labs Access.
- Operator Keycard: aimed at a bound scanner, opens its security code screen; elsewhere
  it shows its private keys.
- Creative Keycard: bypasses card checks on bound doors.
- Relay Key and Biometric Relay Key: create named private keys; the biometric version
  captures the holder's identity instead.
- Biometric Keycard and Biometric Operator Keycard: carry an identity, show the owner in
  the tooltip, and work with the scanner's Match biometric holder option.
- Keycard Holder: carried 3x4 case for twelve keycards, with its own NBT storage,
  empty-slot ghosts and shift-click transfer.
- Keycard Viewer: sneak-right-click a card to read every private key stored on it.
- Security door variants: fourteen doors now have a _security version that only a
  scanner (or the Creative Keycard) can open. The two silo hatches have no security
  variant.
- Keypads can bind and drive locked doors; security doors stay off limits, and a door is
  never driven by both a keypad and a scanner.

SCANNER CONTROL
- Security code screen: set a 1-6 digit code with one entry; later visits repeat it.
  Correct and wrong codes show SUCCESS!!! / FAILED!!! cards.
- Key assignment screen: ASSIGNED KEYS on the left, NON-ASSIGNED KEYS from the held
  Operator Keycard on the right, with click-to-move rows and wheel scrolling.
- Options: Match biometric holder, Lockdown mode, Lockout mode, Ignore private keys,
  Ignore biometric IDs, Override door locks and Force close.
- Lockdown closes every bound door and refuses cards, codes, keypads and redstone until
  it is lifted.
- Lockout shuts the reader for five minutes after three bad cards or codes; the
  Biometric Operator Keycard can still reach the code screen.
- Scanner audio: button3 on insert/success, button8 on denial and a looping bell1 while
  scanning.

DOCUMENTS FOLDER
- New carried folder item with a three-layer paper model.
- Right-click opens a twenty-page editor styled as an open manila folder in the item's
  tan, grey and dark-red palette.
- Typed caret with click-to-place, arrows, Home/End, Delete/Backspace, Ctrl+V paste and
  word wrap.
- Enter starts a new line; press it twice for a blank paragraph gap.
- Bottom index tabs jump to any page; arrows, Page Up/Down and the mouse wheel also flip
  pages.
- Auto-saves on close, with no signing step. Pages, custom folder name and last page are
  stored in NBT and synchronised to the server.
- Reopens on the page you closed on.
- Top-right title is editable: click to rename, Enter saves, Esc cancels, clearing
  restores the default name.
- The editor screen is scaled up 1.5x, with a fit guard for smaller windows.
- Tooltip shows the number of pages written.

FIXES AND POLISH
- Fixed multiplayer access holes and kept scanner/keycard state in sync.
- Tightened keypad/scanner access tiers and restored keypad control of locked doors.
- Fixed keypad binding, reset scope, model caching and collision-box clipping.
- Door lighting and collision fixes: each side lights from its own room, glass QE doors
  pass light, the opened vault panel is solid, cargo doors opt out of light sealing, and
  doors stay lit when a block is placed against them.
- Fixed scanner and programmer item models and held transforms; programmer tooltips stay
  above JEI and keep their light rig.
- Halved the programmer backup loop volume.
- Added keypad success and failure sounds.
- Added item tooltips and card name colours.
- Version reset to 1.0.28 for the previous release (the CurseForge build before it: 1.0.26).
