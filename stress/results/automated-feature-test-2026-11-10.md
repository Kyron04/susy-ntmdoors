# NTM Doors — automated feature test

Date: 2026-11-10
Status: **51 / 51 server-side integration tests passed**

## Environment

| Item | Value |
|---|---|
| Minecraft | 1.12.2 |
| Forge | 14.23.5.2847 (ForgeGradle 2.3 dev server) |
| Java | Temurin 8u512 (JDK 8; ForgeGradle 2.3 requirement) |
| Runtime mods | NTM Doors, Chisel 1.0.2.45, CTM 1.0.2.31 |
| World | fresh flat world, no structures/mobs, test platform at y=50 |
| Test source | current tree at `1.0.44` **including** the uncommitted redstone/security changes (`DoorSecurityAccess`, `TimedSources`, door/gate unlink fixes) |

The test ran in an isolated git worktree at
`C:\Users\goods\AppData\Local\Temp\opencode\ntmdoors-test`; the production working tree contains
the fix described below. The harness is `com.ntmdoors.test.AutomatedFeatureTest`, registered only
when `-Dntmdoors.autotest=true` is set.

## Method

The harness starts a real dedicated Forge server, creates a flat test platform, uses a Forge
`FakePlayer` for placement and interaction, and drives the mod's server-side tile/block APIs. It
runs one named test per server tick so scheduled block updates and tile-entity updates also run.
The result is written to `run/autotest-report.txt` and the server shuts itself down.

## Result

```
Passed: 51
Failed: 0
Total:  51
```

Raw report: `C:\Users\goods\AppData\Local\Temp\opencode\ntmdoors-test\run\autotest-report.txt`

## Coverage

| Area | Tests |
|---|---|
| Content/registration | 18 door declarations, 54 door blocks (normal/locked/security), items, creative tab membership, model/texture/blockstate/item-model/lang/sound assets, recipes, ore dictionary |
| Doors | one-click item placement and full footprint for every door and variant; open/close animation; animated collision; normal/locked redstone; security ownership and redstone opt-in; non-owner refusal; skin cycling; breach; breaking one cell removes the whole door; **placement inside an open door panel is refused (open and closed states)** |
| Keypads | first code, code entry/toggle, wrong code, reset flow, multi-door binding, distance binding, security-code console, force open/close, disabled pad, refusal to bind security doors |
| Keycard Scanner | door binding, assigned private keys, card scan/grant, wrong-card denial, lockout mode, lockdown, creative card, auto-close, security-code sync to doors and reset, options, NBT |
| Safes | first/correct/wrong code, access session, 27-slot inventory, no hopper automation, lockout, options, void-on-destroy, binding to doors, lockout closes bound doors, biometric owner/match rules, NBT |
| Redstone Gates | options, pulse, enabled/inverted/stay-active, redstone edge on/off, gate-to-gate links, feedback-loop refusal, gate-to-door links |
| Speaker | sound/range/volume options, enabled state, redstone on/off, NBT |
| Motion Sensor | 5×5 area, selected cells, private-key clearance, hostile detection, linked gate pulse, within-bounds and out-of-bounds persistence holds |
| Thermite Charge | five-second fuse, door control lockout while fused, door breach, normal-block destruction |
| Spatial Disrupter | UHPC conversion in the chunk area and self-removal after completion |
| Monitor desks | 6-monitor, 3-monitor, dual, 2×2 dual and single desk placement; dismantle from any cell; SHA-256 password; security nets/events, lockdown/open/close; notepad create/save/rename/delete and NBT persistence; wallpapers (builtin/URL/normalisation); terminal key linking; backup/restore and backup key |
| Concrete / HL Blocks | family and variant counts, chisel groups, stairs/slabs, recipes, ore dictionary, Über Concrete decay to Moldy Debris, UHPC hardness and blast resistance |
| Items | private-key serial/name rules, biometric identity copy/matching, keycard holder, documents folder, keycard programmer bind/legacy backup, weapons relay bindings/TNT trigger, creative keycard, developer keycard redaction, NBT round-trips |

## Fix verified by this pass

- Added `DoorPlacementGuard`: block placement is refused in the volume the moving panel occupies
  while opening, open or closing, including the sampled panel sweep. This covers every door
  without per-door parking coordinates, and the volume is reserved even while the door is closed
  so a later opening cannot clip into a block. The guard is enforced by Forge placement events
  (including multi-block and non-player placement), and the mod's custom door, monitor-desk and
  thermite items check it directly.

## Notes and limitations

- The placement guard applies to Forge placement events and the mod's own multi-block items. Direct
  `setBlockState` calls from commands or other mods bypass it, as they bypass ordinary protection
  events.
- This is a **server-side integration pass**. Client-only behaviour was not exercised: GUI
  screens, JEI/HEI, models/renderers, sounds, the Redstone Monocle overlay, motion-sensor
  projection, terminal rendering and client FPS.
- No real multiplayer connection was used; interactions use a Forge `FakePlayer`.
- ICBM-specific blast classes were not present, so UHPC was checked by hardness/resistance and
  the generic explosion path rather than an actual ICBM nuclear/antimatter blast.
- Persistence was checked by tile-entity NBT round-trips, not by a second full server restart.
- One early test attempt placed a redstone block inside a door footprint; the door was correctly
  dismantled by the mod. The test was moved outside the footprint. This is expected behaviour,
  not a mod defect.
- The source tree contains 18 door declarations (including the `test_door` example), not the 17
  listed in the README. All 18 × 3 variants are covered.
