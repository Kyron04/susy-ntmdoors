HBM DOORS: REFRESHED 1.0.28
Minecraft 1.12.2 / Forge 14.23.5.2847

Everything below is new since the 1.0.26 CurseForge release. The 1.0.26 jar contained
the doors, keypad and screwdriver only; 1.0.28 adds the full keycard access-control layer
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
- White writing sheet with a thin ink border; the whole screen is scaled up 1.5x with a
  fit guard for smaller windows.
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
- Version reset to 1.0.28 for this release (previous CurseForge build: 1.0.26).
