# NTM Doors (Minecraft 1.12.2, Forge 14.23.5.2847)

Standalone port of the HBM's Nuclear Tech Mod door system and concrete building set to Minecraft
1.12.2. The implementation and the included door, concrete and HL Blocks assets are used under the
LGPL-3.0. See `LICENSE.LESSER` and `NOTICE` for attribution and source information.

Current release: **1.0.31**

Source design reference: https://github.com/HbmMods/Hbm-s-Nuclear-Tech-GIT

## Build
Needs JDK 8 (ForgeGradle 2.3 and Gradle 4.x do not run on newer JDKs). Chisel 1.12.2-1.0.2.45 is
required at runtime. The Gradle wrapper is included, so:

    export JAVA_HOME=/path/to/jdk8
    ./gradlew setupDecompWorkspace   # one-time: downloads + decompiles Minecraft
    ./gradlew build                  # jar in build/libs
    ./gradlew runClient

The jar version lives in `gradle.properties` (`mod_version`). Every successful `build` writes its
jar with that version and then bumps the patch number, so the next jar is never the same version.
Pass `-PnoVersionBump` to rebuild the current version without advancing it.

## What is implemented
* Multiblock doors of any footprint, placed in one click, rotate with the player, removed as one piece
* Core tile entity with state machine CLOSED / OPENING / OPEN / CLOSING, synced to clients
* Redstone from any cell of the door (edge triggered), right click toggle
* Every door is also registered as a locked copy named `<door>_locked`, and as a `<door>_security`
  copy that only a keycard scanner (or the Creative Keycard directly) can open. Normal and locked
  doors respond to redstone; security doors refuse it, as do hand clicks. All 16 doors, including
  both silo hatches, now have a security variant. Security doors take three real hours to mine with
  any tool, and keypads may bind and drive locked doors exactly like normal ones; security doors
  are off limits to keypads.
* State dependent collision and selection boxes that follow the door animation continuously
  (walk through when open, click-through gaps)
* OBJ renderer (TESR) with per-group translate/rotate animations, X/Y/Z clipping and sounds
* 3D door item rendering (inventory, hand, ground) from the same OBJ model, matching HBM's door item renderer
* 16 multiblock OBJ doors: `vault_door`, `sliding_blast_door`, `qe_sliding_door`, `qe_sliding_panel`,
  `fire_door`, `secure_access_door`, `sliding_seal_door`, `round_airlock_door`, `silo_hatch`,
  `silo_hatch_large`, `large_vehicle_door`, `qe_containment`, `blast_door`, `cargo_door`,
  `water_door`, and `transition_seal`, plus a `_locked` copy of each
* Screwdriver: sneak + right click on a door with alternate skins cycles through them; right click
  a keypad to start its code-reset sequence; sneak + right click an unused keypad cycles its
  binding mode. Every interaction the tool handles plays `button9`, so nearby players hear the
  tool being used
* Keypad: wall-mounted code panel that locks/unlocks the door it is placed
  next to. Set a 1-6 digit code and enter it to toggle the door; `R` closes an open door. The first
  code set is bound to the door, and every keypad on that door must use the same code to open it.
  A keypad only binds or auto-syncs when it touches one of the door's cells; placing it next to an
  already-bound door copies the code automatically and shows `Code synced`. The binding stays on
  the door when a keypad is broken, until the door itself is destroyed. Every key press clicks, and
  each result chimes at the pad: the scanner's granted sound for an accepted code, a set or reset
  code, or an auto-synced code, and the denied sound for a wrong code or an impossible binding.
  With a screwdriver: first
  click shows `Reset code?`, second click confirms, then entering the current code clears the
  binding from the door, every keypad linked to it, and any further doors those keypads controlled
* Keypad binding modes (sneak + right click with the screwdriver, only while the pad is unused):
  off → **Distance** → **Multi** → off. In either mode, sneak + right click each **closed** door
  within 32 blocks to link it. Distance binds one door; Multi holds up to nine, next to the pad or
  away from it. Both modes share one code, and the pad shows `Confirm Door Bounding?` (with
  `X Doors` under Multi) until `E` accepts the links; the LCD then shows `Input code` /
  `Input multi code`, and the code typed there toggles every linked door. Pressing any key ends
  linking. Once a code or link exists the mode is fixed and the screwdriver can only reset the
  pad, which clears every link and code. A keypad placed next to an already-bound door auto-syncs
  its code and skips the confirmation. A keypad never binds or drives a security door: placing one
  against a security door shows `Unable to bound` in red on the LCD. A door is also driven by
  keypads or keycard scanners, never both, so a pad refuses a door a scanner already controls.
  Keypads have two codes: the normal door code above, and a separate security code that gates the
  Redstone Relay Key screen. Sneak + right click a keypad with that key to enter (or first set) the
  security code; the screen then sets the first door code, toggles `On fail` / `On success`
  Redstone Gate outputs, resets the door code, overrides door locks, or force-closes its doors.
  A normal click with the Redstone Relay Key starts/finishes its wireless gate binding session.
* Keycard Scanner (`ntmdoors:keycard_scanner`): wall-mounted card reader with a black display, a
  blue security stripe and green / yellow / red indicator lenses. Sneak + right click it with the
  screwdriver to start a binding session, sneak + right click up to nine closed doors within 32
  blocks, then right click the scanner with the screwdriver again to confirm. Normal, locked and
  security doors can all be bound. Scanning a card shows `Scanning`; the Creative Keycard grants
  access to every door, and any other card opens the bound doors only when one of its private keys
  is assigned to that scanner. The doors close themselves again after ten seconds. The LCD shows
  `Please insert key` while idle, and the matching lens lights yellow while scanning/binding, green
  on access and red on denial. A card insert and a successful scan play `button3`, a denied scan
  plays `button8`, and `bell1` loops while the scan runs. Once at least one door is bound, sneak +
  right clicking the scanner while holding an Operator Keycard opens its security code screen: the
  first visit sets a 1-6 digit code with a single entry, and later visits must repeat it. The screen
  is a black panel in a light metal case with a green readout and twelve green keycaps, including
  `CLR` and `ENTER`; a correct code plays the granted chime, a wrong one plays the denied chime and
  clears the entry. The code lives only on the server and is never sent to clients. A correct code
  (or a newly set one) shows a glowing green `SUCCESS!!!` card, a wrong one a glowing red
  `FAILED!!!` card. Success then opens the `KEYCARD SCANNER` assignment screen: `ASSIGNED KEYS` on
  the left, `NON-ASSIGNED KEYS` from the held Operator Keycard on the right, with click-to-move rows
  and wheel scrolling. Assigned keys are stored on the scanner and copied from the card, so any
  keycard that carries one can open the scanner's doors; the screen stays usable for two minutes
  after the code. Placement and facing match the keypad; the item uses a matching 3D model in
  the inventory and hand.
* Scanner options: the assignment screen also carries the switches `Match biometric holder`,
  `Lockdown mode`, `Lockout mode`, `Ignore private keys`, `Ignore biometric IDs`, `Override door
  locks`, `Force close` and `Reset security code`. Lockdown is an emergency lock: raising it closes
  every bound door and keeps them shut, refusing cards, codes, keypads and redstone until it is
  lifted. Lockout mode locks the reader itself for five minutes after three bad cards or codes, and
  turning the mode off clears a running lockout. A lockout only shuts the reader; the Biometric
  Operator Keycard can still reach the code screen during one, and the `Override door locks` and
  `Force close` actions move the doors regardless of a lockdown. `Reset security code` clears the
  code on the scanner and its bound doors, so the next Operator Keycard visit sets a new one.
* Keycard Programmer (`ntmdoors:keycard_programmer`): a low desk console that sits inside one block
  and rotates to face the player, with a flat OBJ body, a sloped screen recess and no card reader
  tower. The front carries a card slit with an `Insert here` label and two buttons on the player's
  left, plus a green indicator and a small USB port on the right; the vent slots are on the back.
  The screen shows `Keycard Programmer N4` over `Insert keycard`, drawn live with the font renderer
  like the scanner's. Right clicking opens a three-page GUI: page one has the `Relay Key` /
  `Operator Keycard` and `Relay Key` / `RFID Card` binding rows, where Bind instantly copies the
  Relay Key's private key onto the card and keeps any keys the card already holds, page two has the RFID card
  slot plus seven colour squares that swap the card's cosmetic colour, and page three is the
  `Legacy Backup Programmer` with a used source slot on the left, a fresh target slot on the right,
  a `Backup` button and a five-frame ten-second progress bar. A backup copies the source item's NBT
  to the target: Relay → Relay, Relay → Operator and Operator → Operator are allowed, Operator →
  Relay is not. The source needs NBT and the target must have none. Changing either slot cancels the
  backup, and the scanner loop plays during the fill at half the chime volume, with the scanner
  denied/granted sounds on cancellation/success. Empty named slots on pages one and two show a
  dimmed, desaturated copy of
  the item they expect, and every machine slot only accepts its allowed item type or types. Shift
  clicking moves items between the player inventory and the slots of the page on screen, so nothing
  is dropped into a hidden page. The GUI darkens the world like a normal container GUI and draws
  item tooltips above JEI/HEI. There is no recipe; it is in the NTM Doors creative tab with a flat
  16x16 icon derived from the model.
* Safe (`ntmdoors:safe`): a 1x1x1 steel safe with a keypad front and two small USB ports under
  the pad. Right clicking opens the same keypad screen as the scanner. The first accepted code
  becomes the safe's code; later entries must match. The code safe has no owner restriction, so
  anyone who knows the code can open it. A correct code shows the `SUCCESS!!!` card and then opens
  a 27-slot container. Page two of the container has `Binding Activities`, `Lockout mode`, and
  `Reset code`; `Binding Activities` enables door linking and the lockout-door response, `Lockout
  mode` locks the keypad for five minutes after three bad codes, and `Reset code` clears the code so
  the next entry sets a new one. The inventory and the code are saved with the block, and breaking
  the safe drops its contents. A safe takes three real hours of continuous mining with any tool.
  Neither safe exposes its slots to hoppers or logistics automation; only the opened container can
  access them. The Creative Keycard bypasses the keypad and opens the container immediately.
* Biometric Safe (`ntmdoors:biometric_safe`): the same shell, inventory and options page, but its
  reader accepts the Biometric Operator Keycard, or the Creative Keycard. Right clicking with a
  bound biometric card shows a short `SCANNING...` screen, then the same `SUCCESS!!!` / `FAILED!!!`
  card before opening the container. `Match user` limits the card to the identity it carries; with
  it off, any bound Biometric Operator Keycard opens the safe. `Lockout mode` works like the
  scanner's, and the card's owner can still bypass a lockout to turn it off. The normal Biometric
  Keycard and the plain Operator Keycard are refused; the Creative Keycard opens it directly.
* Safe Binding Key (`ntmdoors:safe_binding_key`): a small green USB key with a green name. Sneak
  right-click a safe to start or finish a binding session, then sneak right-click closed doors to
  link or unlink them. A door can belong to one safe, but a door already controlled by a keypad or
  scanner keeps that binding and can still be linked to a safe. With `Binding Activities` on, the
  safe's lockout closes every linked door once. Breaking the safe clears the links.
* Speaker (`ntmdoors:speaker`): a 1x1x1 black-ash cabinet with a woofer, tweeter and bass reflex
  port. Shift-right-click (with any item or an empty hand) opens a safe-style toggle screen with
  `Enabled`, four selectable sounds (`Air Raid`, `Facility`, `Industrial`, `EAS`), a 1-64 block
  range slider and a 0-100% volume slider; anyone may use it. While enabled and receiving redstone
  power, it loops the selected alarm until the signal or `Enabled` is removed. The sound, range,
  volume and enabled state are saved with the block. A normal right-click with the Redstone Relay
  Key starts/finishes a wireless binding session, and starting the alarm pulses every linked
  Redstone Gate.
* Concrete and HL Blocks: the NTM CE concrete building set and decorative HL Blocks, with metadata
  families, flat Chisel carving groups, shift-scroll cycling, recipes, and `concrete` /
  `blockConcrete` / `blockhl` ore dictionary entries. Chisel is required.
* Redstone Gate (`ntmdoors:redstone_gate`): a full-cube redstone component with separate off and on
  textures. It starts off, turns on while it receives redstone power or a wireless pulse, and emits
  a full-strength signal while on. A wireless event keeps it powered for its configured duration;
  another event extends the pulse. Sneak + right-click it with an Operator Keycard, Biometric
  Operator Keycard, or the Creative Keycard to open its settings screen. The screen has `Stay
  active after signal` (hold the gate on until switched off there), `Invert operation` (powered
  when there is no input), and activation duration controls from 1 second up to 5 minutes using
  +1/+5/+10/+20 second and matching subtract buttons. It has a red menu name and the tooltip
  `Bound activities from Keypads, Keycard Scanners, and Safes will emit a redstone signal`.
* Redstone Relay Key (`ntmdoors:redstone_relay_key`): a red-named creative key with the tooltip
  `Allows Keypads, Keycard Scanners, Safes, and Speakers to be bound to the Redstone Gate`. Sneak +
  right-click a safe or scanner to start/finish a wireless binding session, then sneak + right-click
  a gate to link or unlink it. On a keypad or speaker, a normal right-click starts/finishes the
  binding session; sneak + right-click opens the keypad configuration screen, while the speaker
  uses shift-right-click for its own screen. Devices and gates can be linked many-to-many in the
  same dimension.
* Both safes and the keycard scanner have a third `Redstone` page with a red panel tint and
  scrolling. Its event toggles start off; enabling one pulses every linked gate for that gate's
  configured activation duration.
  - Code Safe: Code fail, Code success, Binding activities, Lockout.
  - Biometric Safe: Scan fail, Scan success, Match holder fail, Match holder success, Lockout.
  - Keycard Scanner: Scan fail, Scan success, Match holder fail, Match holder success, Private key
    fail, Private key success, Biometric fail, Biometric success, Lockout.
  - Keypad (Redstone Relay Key screen): On fail, On success.
  The safe's Redstone page does not show the player inventory. Breaking a safe, scanner, keypad or
  gate clears the reciprocal wireless links; door/keypad/scanner bindings are untouched.
* RFID cards: one blank card plus red, violet, green, blue, black, orange and yellow variants.
  Each colour is a separate item (`ntmdoors:red_rfid_card` and so on) sharing a single item class
  and one hand-display model, so they all sit in the hand identically. The scanner recognises them
  and opens its bound doors when one of the card's private keys is assigned to that scanner. Sneak
  right-clicking a coloured card opens the read-only card viewer, which lists every private key
  stored on it; the blank card has no viewer.
* Themed keycards (`ntmdoors:keycard_blue_marking`, `ntmdoors:labyrinth_keycard`,
  `ntmdoors:engine_room_keycard`, `ntmdoors:terminal_keycard`,
  `ntmdoors:terragroup_labs_access_keycard`): five fixed-name cards, each with its own 16x16 sprite
  and a line of flavour text. They behave exactly like the colour cards - the programmer copies
  private keys onto them and the scanner reads them - but they are not part of the recolour palette.
* SCP access cards (`ntmdoors:scp_level_0_keycard` through `ntmdoors:scp_level_5_keycard`): six
  fixed-clearance cards in grey, yellow, amber, orange, dark orange and red, with `Level 1` to
  `Level 6 clearance` tooltips. Also included is `ntmdoors:googa_security_keycard`, whose tooltip
  reads `Security is goog` and whose name is gold. They use the same private-key storage, programmer
  binding, holder and scanner behaviour as the other cards, and are not recolourable.
* Operator Keycard (`ntmdoors:operator_keycard`): a dark card with an orange stripe and three orange
  dots. It shares the RFID hand-display model and, aimed at a bound scanner, opens that scanner's
  security code screen; anywhere else sneak right-click opens the same private-key viewer as a
  colour card.
* Biometric items (`ntmdoors:biometric_keycard`, `ntmdoors:biometric_operator_keycard`,
  `ntmdoors:biometric_relay_key`): a Biometric Relay Key collects your identity on sneak right-click
  instead of creating a private key, and the programmer binds that identity onto a card. Biometric
  cards keep it next to any private keys, show the owner in the tooltip, turn the name yellow (aqua
  for the Operator version) and can be restricted to their owner with the scanner's
  `Match biometric holder` option. The Biometric Operator Keycard also lists its private keys in
  the tooltip. The relay key's right-click check uses server-safe geometry, so collecting an
  identity also works on a dedicated server.
* Relay Key and Creative Keycard (`ntmdoors:relay_key`, `ntmdoors:creative_keycard`): the Relay Key
  creates the private keys that scanners check. The Creative Keycard is the debug item for testing:
  its name is purple, and right clicking any door opens or closes it regardless of locked/security
  variants, keypad codes or redstone, with a purple `Door overridden` status message. It also
  bypasses both safes and every scanner, and sneak + right-clicking a scanner opens its full
  console without a code or bound doors. Sneak + right-clicking a Redstone Gate opens the gate
  screen. A blank Relay Key creates one private key when the player
  sneak right-clicks it away from any machine: a naming screen takes up to 32 characters, and
  confirming it generates a ten digit serial on the server and stores both in the key's NBT. A key
  can be created once and never reset. Its name is yellow, and its tooltip carries a grey
  description above the white name and serial of its private key. Page one of the programmer copies
  that private key onto Operator Keycards and coloured RFID cards, while page three can back the
  whole key up onto another Relay Key. Sneak right-clicking the Creative Keycard opens the same
  viewer with the joke entry `THE ENTIRE LIBRARY OF BABEL` under `You have access to EVERYTHING!!!!!`.
  Tools, machines and cards each carry a short grey description tooltip; the Operator Keycard's
  name is orange.
* Keycard Holder (`ntmdoors:keycard_holder`): a carried 3x4 case for twelve keycards, opened with a
  right click. The stored cards live in the holder's own NBT, so they survive inventory moves, death
  drops and world saves. Only keycards are accepted, each slot holds one, and shift-click moves
  cards between the player inventory and the holder.
* Documents Folder (`ntmdoors:documents_folder`): carried folder item drawn as three stacked sheet
  layers, 3 px deep in total, with only the middle layer's page edge protruding. Right-clicking opens
  a twenty-page editor styled as an open manila folder in the item's own tan, grey and dark red
  palette, with the writing sheet in white behind a thin ink border. There is no signing step:
  Escape or the Close button both save, the pages live in the
  stack's NBT, and Enter starts a new line (press it twice for a paragraph gap). Long lines word-wrap
  inside the sheet, the bottom index tabs jump straight to any page, the top-right folder name can be
  clicked and renamed, reopening returns to the page you closed on, and the tooltip counts the pages
  written.
* The secure access door ships nine skins: red, yellow, green, blue, grey, black (white writing),
  HBM yellow, white (black writing) and orange (white writing). The colour variants are generated
  by recolouring only the red warning paint; all other pixels are unchanged. Base colours: green
  `#2b401a`, blue `#162138`, yellow `#4a3f1a` (dark muted industrial yellow)
* The sliding blast door ships default, black and grey schemes. The sliding seal door ships
  default, clean, nuclear trefoil, and plain white, red and grey; the plain skins have no letters,
  numbers, trefoil or hazard stripes.

## Included door assets
Models live under `src/main/resources/assets/ntmdoors/models/doors/`, textures under
`textures/models/doors/`, and sounds under `sounds/doors/`. Door items do **not** need flat
`textures/items/<door>.png` icons: the inventory/hand item is rendered from the same OBJ model
and texture as the placed door. Only the screwdriver, the keypad, the cards, the Keycard Holder and
the Documents Folder use flat icons (`textures/items/screwdriver.png`,
`textures/items/sliding_blast_door_keypad.png`, `textures/items/blank_rfid_card.png`,
`textures/items/operator_keycard.png`, `textures/items/rfid_card_<colour>.png` plus the themed,
biometric and folder sprites beside them). The keycard programmer has a flat 16x16 icon derived
from its model; the scanner uses a 3D model in the inventory and hand too, like the doors.

| door | animated OBJ groups | notes |
|---|---|---|
| `vault_door` | `Door`, `Label` | rolls sideways along its extended track |
| `sliding_blast_door` | `LeftDoor`, `RightDoor`, `LeftLock`, `RightLock` | 7x4 two-panel blast door; skins: default, black, grey |
| `qe_sliding_door` | `Left`, `Right` | 2x2 QE sliding door; skins: default, white |
| `qe_sliding_panel` | `Right` | 1x2 single-column QE sliding door (frame clipped to one cell); skins: default, white |
| `fire_door` | `Door` | roller/fire door |
| `secure_access_door` | `Door` | vertical security door; skins: red, yellow, green, blue, grey, black (white writing), HBM yellow, white (black writing), orange (white writing) |
| `sliding_seal_door` | `Door` | retracts into a wall pocket; skins: default, clean, nuclear trefoil, plain white, plain red, plain grey |
| `round_airlock_door` | `Left`, `Right` | split iris airlock; skins: worn, clean, green, space (black/white) |
| `silo_hatch` | `Hatch` | tilting horizontal hatch |
| `silo_hatch_large` | `Hatch` | 7x7 large tilting horizontal hatch |
| `cargo_door` | `DoorBot`, `DoorTop` | 3x3 lifting cargo door |
| `large_vehicle_door` | `Left`, `Right` | 7x6 vehicle door |
| `qe_containment` | `Door` | 3x3 containment shield |
| `blast_door` | `Tooth`, `Slider1`-`Slider4` | 1x7 roller shutter (Roller Blast Door) |
| `water_door` | `Door_Cube.003`, `Bolts`, `Top`, `Bottom` | 3x3 swinging water-tight door |
| `transition_seal` | 20 rigid keyframed groups | converted from HBM `seal.dae`; full Collada action replayed as per-group matrices |

`sounds.json` and `lang/en_us.lang` are included.

## Tuning to match the real models
Everything per door lives in one entry of `door/DoorDecls.java`: footprint, collision boxes,
open/close time, hardness, animated group names and motion. Use `.modelOffset(x,y,z)` if a model's origin is
not at the core cell's bottom centre, `.modelYaw(...)` for models authored in another direction, and
`.modelPath(...)` to point at different file names. Clipping is configured with `.clipX(...)`, `.clipY(...)`
and `.clipZ(...)` where the original HBM renderer used a clip plane. Moving parts use
`.movingGroup("Group")` (the box is read from the OBJ group bounds) or `.movingBox("Group", box(...))`
for explicit model-space bounds; `.collisionSplits(n)` tightens rotating parts by slicing their AABB.

## Not included
Crafting recipes for the doors (the originals need NTM materials), for the keypad, programmer,
scanner, cards, Keycard Holder and Documents Folder; configs, NEI/JEI, and other NTM items. Only the
screwdriver has a recipe. The concrete and HL Block sets added in 1.0.46 do have recipes, listed
below.

## Concrete and HL Blocks

The 1.0.46 concrete and HL Blocks update adds the NTM concrete building set and the included HL
Blocks to the door port:

- The full NTM CE concrete set: plain, smooth, dyed, Builder's Choice, bricks, pillar, rebar,
  asbestos, Über Concrete and its falling Moldy Debris.
- The HL Blocks set: three simple blocks and seven metadata groups.
- A metadata layout that replaces the old one-id-per-variant blocks: cubes and slabs are grouped
  by Chisel family, so one block id holds a whole texture family.
- Shift-scroll cycling, flat Chisel carving groups, `concrete` / `blockConcrete` / `blockhl` ore
  dictionary entries, and the concrete recipes.
- A fix for the single-slab model lookup: the blockstate variant keys are written in the order
  Forge actually uses (`half=...,variant=...`).

The concrete set has its own recipes, listed below; the doors, cards and machines still have no
recipes.

#### Block layout

Cubes are one block id per family; metadata selects the texture. Stairs stay one block id per
texture because `BlockStairs` already uses its metadata bits for facing and half. Slabs use one
block id per family with the `variant` and `half` properties, plus a technical `_double_slab`
block for the double form.

| Family | Block id | Variants |
|---|---|---|
| Plain concrete | `ntmdoors:concrete` | 0 tile, 1 smooth |
| Dyed concrete | `ntmdoors:concrete_colored` | 16 dyes |
| Builder's Choice | `ntmdoors:concrete_colored_ext` | 13 variants: machine, machine stripe, indigo, purple, pink, sand, bronze, and six hazard colours (plain, red, green, blue, white, purple) |
| Concrete bricks | `ntmdoors:concrete_bricks` | brick, mossy, cracked, broken |
| Special cubes | `ntmdoors:concrete_pillar`, `ntmdoors:concrete_rebar`, `ntmdoors:concrete_asbestos` | 1 each |
| Über Concrete | `ntmdoors:concrete_super` | 16 decay stages |
| Moldy Debris | `ntmdoors:concrete_super_broken` | 1 |
| Stairs | 36 ids such as `concrete_stairs`, `concrete_smooth_stairs`, `concrete_colored_stairs_white`, `concrete_colored_ext_stairs_hazard`, `brick_concrete_stairs` | one id per texture |
| Slabs | `concrete_slabs` (2), `concrete_colored_slabs` (8), `concrete_colored_slabs_2` (8), `concrete_colored_ext_slabs` (6 hazard), `concrete_bricks_slabs` (4), `concrete_asbestos_slab` (1) | `variant` + `half`; each has a `_double_slab` technical block |
| HL simple blocks | `ntmdoors:hlblocks_ca1x_con2`, `hlblocks_drkmtlt_flr01`, `hlblocks_crate09` | 1 each |
| HL metadata groups | `hlblocks_freezer` (2), `hlblocks_fifties` (3), `hlblocks_crete` (3), `hlblocks_duct` (1), `hlblocks_lab` (3), `hlblocks_tunnel` (4), `hlblocks_generic` (3) | metadata |

That is 61 placeable block ids, plus the 6 technical double slabs, covering 142 texture/metadata
variants. Creative and HEI list 127 of them; Über Concrete stages 1-15 are deliberately hidden and
remain reachable by metadata.

#### In-game behaviour

- **Creative tabs.** All concrete and HL Blocks are listed in the `NTM Doors` creative tab.
- **Shift-scroll cycling.** Hold a concrete or HL stack and scroll with the cycle modifier
  (left shift by default, rebindable under Controls → HL Blocks). It cycles every texture in the
  family in both hands, and the server applies the change.
- **Chisel.** Chisel is a required dependency. The families are registered as flat carving groups:
  `concrete`, `concrete_stairs`, `concrete_slabs`, `concrete_colored`,
  `concrete_colored_stairs`, `concrete_colored_slabs`, `concrete_bricks`,
  `concrete_bricks_stairs` and `concrete_bricks_slabs`. Pillar, rebar, asbestos and Über Concrete
  are not registered. Chisel groups are flat, so the shared `concrete_` prefix is the only
  hierarchy the mod can expose.
- **Ore dictionary.** Every concrete cube, stair and single slab is registered under `concrete`;
  full cubes are also under `blockConcrete`. Every included HL block is under `blockhl`. Über
  Concrete, Moldy Debris and the technical double slabs are excluded. No Groovy script is
  involved.
- **Recipes.** Blocks make slabs and stairs, never the reverse. Smooth, asbestos and rebar concrete
  use half-scale crafting-table recipes: clay stands in for cement powder, string for asbestos and
  iron bars for rebar. Über Concrete and Moldy Debris have no CE recipe and stay creative-only.
  The recipes follow NTM CE's tile, pillar, bricks, dyed and Builder's Choice layouts.
- **Über Concrete.** It decays through sixteen stages on random ticks; the last stage crumbles into
  Moldy Debris that falls like sand or gravel.
- **Hazard variants.** The hazard cubes, stairs and the six hazard slabs are part of the same
  families and stay in the Chisel groups.
- **World migration.** The grouped metadata ids are new in 1.0.46. If you used a pre-release fork
  build with per-variant concrete ids, those old ids are not migrated; new worlds get the grouped
  ids.

#### Slab model note

Forge builds the blockstate lookup key from a sorted property map, so a slab key must be written
`half=bottom,variant=1`. The generated blockstates originally used `variant=1,half=bottom`, which
made every single slab fall back to the missing model even though the model files were present. The
committed blockstates use the sorted order.

#### Attribution

The concrete assets come from HBM's Nuclear Tech Mod Community Edition. The HL Blocks assets and
the original block definitions come from NeoRussia's HL Blocks project. Both are used under
LGPL-3.0; see `NOTICE` for the exact file lists and links.

### AI assistance

The concrete and HL Blocks contribution was prepared with AI assistance. See
[`AI_DISCLOSURE.md`](AI_DISCLOSURE.md) for the tool, the extent of use and the human review.
