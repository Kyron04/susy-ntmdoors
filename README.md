# NTM Doors (Minecraft 1.12.2, Forge 14.23.5.2847)

Standalone port of the HBM's Nuclear Tech Mod door system to Minecraft 1.12.2.
The implementation and the included door models, textures and sounds are used under the
LGPL-3.0. See `LICENSE.LESSER` and `NOTICE` for attribution and source information.

Source design reference: https://github.com/HbmMods/Hbm-s-Nuclear-Tech-GIT

## Build
Needs JDK 8 (ForgeGradle 2.3 and Gradle 4.x do not run on newer JDKs). The Gradle wrapper is included, so:

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
* Every door is also registered as a locked, redstone-only copy named `<door>_locked`, and as a
  `<door>_security` copy that only a keycard scanner (or the Creative Keycard directly) can open:
  keypads in every binding mode, redstone and hand clicks never move it. The two silo hatches have
  no security variant. Keypads may bind and drive locked doors exactly like normal ones (a bound
  door is then code-driven rather than redstone-driven); security doors are off limits to keypads.
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
  binding mode
* Keypad: wall-mounted code panel that locks/unlocks the door it is placed
  next to. Set a 1-6 digit code and enter it to toggle the door; `R` closes an open door. The first
  code set is bound to the door, and every keypad on that door must use the same code to open it.
  A keypad only binds or auto-syncs when it touches one of the door's cells; placing it next to an
  already-bound door copies the code automatically and shows `Code synced`. The binding stays on
  the door when a keypad is broken, until the door itself is destroyed. With a screwdriver: first
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
  keypads or keycard scanners, never both, so a pad refuses a door a scanner already controls
* Keycard Scanner (`ntmdoors:keycard_scanner`): wall-mounted card reader with a black display, a
  blue security stripe and green / yellow / red indicator lenses. Sneak + right click it with the
  screwdriver to start a binding session, sneak + right click up to nine closed doors within 32
  blocks, then click the scanner with an empty hand to confirm. Normal, locked and security doors
  can all be bound. Scanning a card shows `Scanning`; the Creative Keycard grants access, opens
  every bound door and closes them again after ten seconds, and any other card is denied until the
  keycard access rules are added. The LCD shows `Please insert key` while idle, and the matching
  lens lights yellow while scanning/binding, green on access and red on denial. A card insert and a
  successful scan play `button3`, a denied scan plays `button8`, and `bell1` loops while the scan
  runs. Placement and facing match the keypad; the item uses a matching 3D model in the inventory
  and hand.
* RFID cards: one blank card plus red, violet, green, blue, black, orange and yellow variants.
  Each colour is a separate item (`ntmdoors:red_rfid_card` and so on) sharing a single item class
  and one hand-display model, so they all sit in the hand identically. The scanner recognises them
  but denies them until the keycard access rules are added.
* Relay Key and Creative Keycard (`ntmdoors:relay_key`, `ntmdoors:creative_keycard`): groundwork for
  the keycard system. The Creative Keycard is the debug item for testing: its name is purple, and
  right clicking any door opens or closes it regardless of locked/security variants, keypad codes or
  redstone, with a purple `Door overridden` status message. It is also the only card the scanner
  currently accepts. The Relay Key has no behaviour yet.
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
and texture as the placed door. Only the screwdriver, the keypad and the RFID cards use flat icons
(`textures/items/screwdriver.png`, `textures/items/sliding_blast_door_keypad.png`, and
`textures/items/blank_rfid_card.png` plus `textures/items/rfid_card_<colour>.png`). The keycard
scanner uses a 3D model in the inventory and hand too, like the doors.

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
Crafting recipes for the doors (the originals need NTM materials), configs, NEI/JEI, and other
NTM items.
