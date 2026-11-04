# NTM Doors — 6 Monitor Setup Performance Report

Status: **server-side complete; client FPS pass pending**

Date: 2026-11-04

## Executive summary

The 6 Monitor Setup is a decorative nine-block desk with no tile entity. On a dedicated
1.12.2 Forge server it is **effectively free to tick**, even at very large scale.

- **5,000 desks (45,000 blocks) in 70 loaded chunks:** 20.0 TPS, 0.050 ms mean tick time.
- **1,000 desks (9,000 blocks):** 20.0 TPS, 0.043 ms mean tick time.
- **100 desks (900 blocks):** 20.0 TPS, 0.120 ms mean tick time.
- **Idle subject server:** 20.0 TPS, 0.032 ms mean tick time.
- **5,000 desks + 200 zombies:** 20.0 TPS, 0.126 ms mean tick time.
- **1,000 desks broken (9,000 block removals):** 20.0 TPS, 0.142 ms mean tick time during the
  churn, 1,000 commands processed in 0.6 s (1,641 commands/s), no tick loss or exceptions.
- **Save/restart:** 5,000 desks load in 0.589 s; full server startup 6.1 s; world size 7.6 MB.
- **30-minute soak (180 samples):** 20.0 TPS throughout, 0.063 ms mean, 0.332 ms max; live heap
  372 MB, old generation 5.1%, zero full GCs.
- **Dedicated-server compatibility:** the mod loads and runs with Chisel + CTM with no
  client-class or missing-dependency errors.

The differences between 100, 1,000 and 5,000 desks are noise-level (0.04–0.14 ms), not a scaling
trend. The server-side limit is not tick logic; it is client rendering and chunk transfer. A
separate client FPS pass is still pending for very large builds.

## Scope

This pass covers **server-side performance and stability**:

- Dedicated Forge server tick rate and tick time.
- Memory and world size.
- Chunk load/save time.
- Bulk placement and break propagation.
- Entity collision against the new half-depth monitor hitboxes.
- Dedicated-server compatibility of the production jar.

Client-side FPS and rendering are not measured by a dedicated server. They are covered separately
in the client pass.

## Environment

| Item | Value |
|---|---|
| Minecraft | 1.12.2 |
| Forge | 14.23.5.2860 dedicated server |
| Java | Temurin 8u504, 64-bit |
| Heap | 4 GB (`-Xms4G -Xmx4G`, G1GC) |
| Mods | `ntmdoors-1.0.59`, Chisel 1.0.2.45, CTM 1.0.2.31 |
| World | Superflat, seed `ntmstress` |
| Server view distance | 6 chunks |
| Other settings | No natural mob spawning, no daylight/weather cycle, random tick speed 0 |
| Field | 150 × 100 blocks = 10 × 7 chunks (70 chunks) |

## Method

- The 5,000-desk field was built from a nine-block template with `/clone` so every cell matched a
  real placed desk (`facing=north`, `column=left/center/right`).
- The field is centred on world spawn and sits entirely inside the always-loaded spawn-chunk area.
- Four repeating command blocks at the field corners confirmed all corners were ticking:
  **844 ticks in ~10.5 s with four blocks ≈ 20.1 TPS**, so the field was genuinely loaded.
- Tick time and TPS were sampled from `/forge tps` every 10 seconds (1 second during churn) via
  RCON. CPU and working set were sampled from the Java process.
- The 30-minute soak samples the same metrics every 10 seconds.
- Break propagation was verified on one desk first (all nine cells removed), then 1,000 desks
  were broken in one batch and restored from the template row.

### Correction to earlier runs

The first pass placed the desk field at x −483…−287, z −423…−213, far outside the loaded spawn
chunks. No player was connected, so those chunks unloaded and the reported 5,000-desk TPS was
effectively an idle-server measurement. Those runs were discarded.

The corrected field (x −75…74, z −50…49, y 4…6) is inside the spawn chunks. The corner command
blocks above prove the chunks were loaded for every result in this report.

## Results

### Tick time and TPS

| Run | Desks | Blocks | Samples | Mean tick | p95 tick | Max tick | Mean TPS | Min TPS |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Control idle | 0 | 0 | 72 | 0.045 ms | 0.060 ms | 0.354 ms | 20.000 | 20.000 |
| Subject idle | 0 | 0 | 30 | 0.032 ms | 0.219 ms | 0.222 ms | 20.000 | 20.000 |
| 100 desks | 100 | 900 | 18 | 0.120 ms | 0.307 ms | 0.307 ms | 20.000 | 20.000 |
| 1,000 desks | 1,000 | 9,000 | 12 | 0.043 ms | 0.214 ms | 0.214 ms | 20.000 | 20.000 |
| 5,000 desks | 5,000 | 45,000 | 30 | 0.050 ms | 0.234 ms | 0.239 ms | 20.000 | 20.000 |
| 5,000 + 200 zombies | 5,000 | 45,000 | 18 | 0.126 ms | 0.429 ms | 0.429 ms | 20.000 | 20.000 |
| 1,000-desk break churn | 5,000 | 45,000 | 30 | 0.142 ms | 0.360 ms | 0.360 ms | 20.000 | 20.000 |
| After churn restore | 5,000 | 45,000 | 6 | 0.134 ms | 0.310 ms | 0.310 ms | 20.000 | 20.000 |
| 5,000 after restart | 5,000 | 45,000 | 18 | 0.107 ms | 0.350 ms | 0.350 ms | 20.000 | 20.000 |
| 5,000 after 100-desk rerun | 5,000 | 45,000 | 6 | 0.100 ms | 0.104 ms | 0.104 ms | 20.000 | 20.000 |

All runs stayed at 20.0 TPS. Even the worst observed tick time (0.429 ms) is under 1% of the
50 ms tick budget.

### Memory and world size

| Run | Working set | Notes |
|---|---:|---|
| Control idle | ~1.11 GB | Spawn chunks only |
| Subject idle | ~1.11 GB | Spawn chunks only |
| 100 desks | ~1.03 GB | 900 blocks |
| 1,000 desks | ~1.23 GB | 9,000 blocks |
| 5,000 desks | ~1.24 GB | 45,000 blocks |
| 5,000 + 200 zombies | ~1.03 GB | Entity test after GC |
| 5,000 after restart | ~1.11 GB | Fresh JVM |
| 5,000 30-minute soak | 1.11 → 1.18 GB | Stable; live heap 372 MB |

The JVM reserves a 4 GB heap (`-Xmx4G`), so the private-memory column sits near 4.45 GB in every
run; that is committed address space, not live heap. The working set stays around 1.1–1.24 GB.

At the end of the 30-minute soak, `jcmd GC.heap_info` reported **372 MB live heap**, the old
generation at **5.1%**, and `jstat` reported **zero full GCs** (13 young GCs, 0.109 s total). The
working set grew from 1.11 GB to 1.18 GB and then flattened; there is no evidence of a leak.

The 5,000-desk world is **7.6 MB** on disk.

### Chunk and save/load

| Metric | Value |
|---|---:|
| Field chunks | 70 (10 × 7) |
| World load time | 0.589 s |
| Full server startup | 6.1 s |
| World size with 5,000 desks | 7.6 MB |
| Startup with/without the field | 6.1 s / 6.1 s |

The field adds no measurable startup cost. The 0.589 s world load is the normal spawn-chunk load.

### Break propagation and churn

- One desk broken from one cell: **all nine cells became air**, as intended.
- 1,000 desks broken in one command batch:
  - 1,000 commands in **0.6 s** (1,641 commands/s).
  - 9,000 block removals total.
  - 20.0 TPS throughout; mean 0.142 ms, max 0.360 ms.
  - No `Can't keep up!`, no exceptions.
- Restored with 69 clone commands (50 template clones + 19 row clones); 20.0 TPS after restore.

### Entity collision

- 200 zombies were spawned in small batches (10/s) on and around the 5,000-desk field.
- 20.0 TPS, 0.126 ms mean, 0.429 ms max, no collision-related exceptions.
- A first attempt that summoned all 200 zombies in one RCON burst triggered a **vanilla**
  `ConcurrentModificationException` in `EntityTracker` (the stack did not touch `ntmdoors`).
  Repeating the test with batched spawning, as a real server would experience, was stable. This
  is a vanilla mass-summon edge case, not a monitor hitbox bug.

### 30-minute soak

`desks-5000-soak-30min.csv` contains 180 samples over 1,800 seconds with 5,000 desks loaded.

| Metric | Value |
|---|---:|
| Mean tick time | 0.063 ms |
| p95 tick time | 0.287 ms |
| Max tick time | 0.332 ms |
| Mean / min TPS | 20.000 / 20.000 |
| Working set start → end | 1.108 → 1.180 GB |
| Live heap after soak | 372 MB |
| Old generation | 5.12% |
| Full GCs | 0 |
| Young GCs | 13 (0.109 s total) |

The periodic 0.2–0.3 ms samples are GC/autosave noise, not desk work. The field stayed at 20 TPS
for the entire 30 minutes and the heap did not grow after the initial warm-up.

## Findings

1. **No per-desk tick cost.** 100, 1,000 and 5,000 desks all sit in the same 0.04–0.14 ms band.
   The desk is nine blockstates with no tile entity, so there is no block-entity tick loop to
   scale with the number of desks.
2. **The hitboxes are cheap.** The half-depth monitor wall is a cached `AxisAlignedBB`; 200
   zombies did not produce a measurable collision problem.
3. **Break propagation is cheap and correct.** A single cell removes all nine cells; 1,000 desks
   broke in under a second of wall time without losing ticks.
4. **Load/save cost is tiny.** 45,000 blocks fit in a 7.6 MB world and load in 0.589 s.
5. **The limiting factor will be the client.** The dedicated server does no model rendering. The
   84-element item model and the populated 58-element centre cell are client-side costs. At
   large build sizes, client FPS and chunk transfer will matter long before server TPS does.
6. **Memory is stable.** After 30 minutes at 5,000 desks, only 372 MB of the 4 GB heap was live,
   the old generation was at 5.1%, and there were no full GCs. The working set flattened at
   ~1.18 GB; no leak was observed.
7. **Dedicated-server compatibility is clean.** The production jar loaded with Chisel + CTM and
   ran the full matrix without `NoClassDefFoundError`, missing registries or server-side crashes.

## Recommendations

- **No server-side changes are needed** for 5,000 desks. The mod is safe at that scale on a
  normal 4 GB server heap; steady-state live heap was only 372 MB.
- For builds much larger than 5,000 desks, spread them over a sensible number of chunks and
  check client FPS; the server tick budget is not the concern.
- If a server uses mass commands to place or remove desks, the work is already fast. For mass
  **entity** summons, stagger the commands to avoid the vanilla `EntityTracker` race.
- Keep the desk's nine-cell break propagation; it is correct and does not create tick spikes.

## Raw data

All files are in `stress/results/`:

- `baseline-idle-5min.csv` — control server, no mod, 5 min.
- `subject2-idle-5min.csv` — mod server, no desks, 5 min.
- `desks-100-clean-3min.csv` — 100 desks, clean run.
- `desks-1000-nocmd-2min.csv` — 1,000 desks, clean run.
- `desks-5000-loaded-5min.csv` — 5,000 desks, 5 min.
- `desks-5000-200zombies-batched-3min.csv` — 5,000 desks + 200 zombies.
- `churn-break-1000-30s.csv` — 1,000-desk break churn.
- `post-churn-restore-1min.csv` — after restoring the 1,000 desks.
- `desks-5000-restart-3min.csv` — after save/restart.
- `post-restore-5000-1min.csv` — after restoring the 5,000-desk field.
- `desks-5000-soak-30min.csv` — 30-minute soak, 180 samples.

## Caveats

- Server-side only. The dedicated server never renders the desk model; the client FPS pass is
  still pending.
- The TPS runs had no players connected. The field was kept loaded by the spawn chunks, which is
  representative of a loaded area but does not include chunk-send/network cost. The client pass
  covers that.
- The 1.0.60 monitor-desk build was compiled on the same machine during the soak. TPS stayed at
  20.0 and no sample exceeded 0.332 ms, so the compile did not distort the result.
- The server used a 4 GB heap. Working set stayed around 1.1–1.24 GB; a smaller heap may be
  possible, but 4 GB leaves comfortable headroom.
- The 200-zombie test is an entity-collision stress, not a realistic mob farm. It is intentionally
  harsher than normal play.
