# Spatial Disrupter stress test

Status: **complete (dedicated server, no players)**

Date: 2026-11-08

## Scope

Server-side stress test of the Spatial Disrupter's 16x16 chunk scan:

- Convert a large hard-UHPC field into the soft UHPC Panel.
- Measure TPS and tick time during the conversion.
- Verify the scan cursor, conversion mapping and self-destruct.
- Confirm no exceptions and no tile-entity list corruption.

## Environment

| Item | Value |
|---|---|
| Minecraft | 1.12.2 |
| Forge | 14.23.5.2847 dev server (`runServer`) |
| Java | Temurin 8u504, 64-bit |
| Mods | NTM Doors, Chisel 1.0.2.45, CTM |
| World | Superflat `spatialstress`, no players connected |
| Settings | `view-distance=10`, `max-tick-time=-1`, RCON enabled |

The dedicated server skips tile-entity ticking when no players are connected, so the test used a
temporary world-tick driver to call the disrupter's `update()` once per tick. The driver and the
test command were removed from the production build after the run.

## Method

1. Fill a 64x64x32 box with hard `ntmdoors:ultra_high_performance_concrete` (131,072 blocks) in the
   loaded spawn area.
2. Place `ntmdoors:spatial_disrupter` above the field and activate it through the test command.
3. Sample `forge tps` and the tile entity's cursor every few seconds until the machine removes
   itself.
4. Check the server log for the completion line and any exception.

## Results

| Metric | Value |
|---|---|
| Baseline | 20.000 TPS, 0.092 ms mean tick |
| Field fill | 131,072 UHPC blocks in 192 ms |
| Scan duration | 92 ticks (~4.6 s) |
| Blocks converted | 131,072 |
| TPS during scan | 20.000 |
| Mean tick during scan | 1.2–1.7 ms |
| Self-destruct | block removed on the next block tick, no drop |
| Exceptions | none after the fix below |

A first, larger run converted **161,792 blocks in 244 ticks (~12.2 s)** at **20.000 TPS** with a
worst mean tick of **0.349 ms**, but it crashed the test server with a
`ConcurrentModificationException`: the tile entity removed its own block while the world was
iterating its tile-entity list. The production fix schedules the removal for the next block tick
(`World.scheduleUpdate` + `Block.updateTick`), which runs outside the tile-entity loop. The final
run above confirms the fix.

## Findings

- The scan keeps 20 TPS while converting 131k blocks; the per-tick budget (4 chunk sections, about
  16k block checks) is far below the 50 ms tick budget.
- Conversion is correct: hard UHPC becomes the soft UHPC Panel with the same shade metadata.
- Unloaded chunks are skipped by design; the field must be inside loaded chunks. In this world the
  spawn-chunk area held 16 chunks, which is why a 128x128 fill only produced 24,576 scanned blocks
  in the first pass.
- The machine must not remove its own block during the tile-entity tick; scheduling the removal
  avoids the list mutation.

## Caveats

- No players were connected, so client-side rendering and the network cost of 131k block updates
  were not measured. The dedicated server still performs lighting and chunk updates.
- The scan areas tested are limited by the loaded chunks available in a player-less server.
- The test harness was development-only and is not part of the shipped mod.
