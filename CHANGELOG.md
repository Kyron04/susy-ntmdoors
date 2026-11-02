HBM DOORS: REFRESHED 1.0.34
Minecraft 1.12.2 / Forge 14.23.5.2847

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
