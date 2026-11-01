HBM DOORS: REFRESHED 1.0.30
Minecraft 1.12.2 / Forge 14.23.5.2847

CURSEFORGE RELEASE NOTES
Changes since the 1.0.28 CurseForge release (1.0.29 + 1.0.30).

SAFES
- New Code Safe: a 1x1x1 steel safe with a keypad. The first accepted 1-6 digit code
  becomes its code; later entries must match. It has no owner restriction, so anyone
  who knows the code can open it.
- New Biometric Safe: the same shell and 27-slot container, opened by a bound
  Biometric Operator Keycard or the Creative Keycard. Match user restricts access to
  the card's enrolled holder; with it off, any bound biometric operator card works.
- Both safes have 27 slots, save their contents and settings, drop their inventory when
  broken, and show full item names and NBT tooltip lines in the container.
- Each safe has a second options page:
  - Code Safe: Binding Activities, Lockout mode and Reset code.
  - Biometric Safe: Match user, Binding Activities and Lockout mode.
- Lockout mode stops access for five minutes after three failed codes or cards. Reset
  code clears the code so the next entry sets a new one.
- The Creative Keycard now also opens both safes; it continues to open every scanner directly.

SAFE-TO-DOOR BINDING
- New Safe Binding Key, with a green name in menus and a tooltip explaining its controls.
- Sneak + right-click a safe to start or finish a binding session, then sneak + right-click
  closed doors within 32 blocks to link or unlink them.
- A safe can link several doors. A door belongs to at most one safe, but a door already
  controlled by a keypad or scanner keeps that binding; the safe link is additive and
  both systems remain respected.
- Both safes have a Binding Activities toggle, on by default. When on, the safe's lockout
  closes every linked door once. Turning it off prevents new binding sessions and pauses
  the lockout-door response without deleting existing links.
- Breaking a safe or a door clears the link.

NEW CARDS
- SCP Level 1-6 access keycards, with Level 1-6 clearance tooltips.
- Googa security keycard with a gold name and the tooltip "Security is goog".
- The Biometric Operator Keycard now lists its stored private keys in the tooltip.

FIXES
- Fixed the dedicated-server crash when using the Biometric Relay Key. The old check
  called a client-only ray trace; the key now records the player UUID and name on servers.
- Operator Keycard scanner lookup uses the same server-safe ray trace.
- Fixed the gap between the safe's success card and the container opening.
- Fixed safe item names and NBT tooltips in the container.
- Guarded late safe unlocks so a stale success screen cannot open a safe after the player
  has moved away.
- Proofread and wrapped item tooltips.
- Fixed the Googa card icon by giving it a clean 16x16 inventory sprite while preserving
  the supplied source art.
