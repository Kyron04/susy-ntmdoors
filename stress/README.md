# Dedicated-server performance test

This directory contains the dedicated-server stress test for the 6 Monitor Setup.

- `results/report.md` — full report, methodology and findings.
- `results/*.csv` — raw tick-time, TPS, memory and CPU samples.

Test environment: Minecraft 1.12.2, Forge 14.23.5.2860, Java 8, 4 GB heap, Chisel 1.0.2.45 and
CTM 1.0.2.31. The 5,000-desk field was placed inside the always-loaded spawn chunks and validated
with corner command blocks before any numbers were recorded.

The report is server-side only; client FPS is a separate test.
