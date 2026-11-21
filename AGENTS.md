# Project instructions

This repository is the NTM Doors Minecraft 1.12.2 Forge mod.

## Before changing code

1. Read `CONTRIBUTING.md`.
2. Read `docs/standards/README.md` and the relevant page, especially
   `docs/standards/02-java-style.md` and
   `docs/standards/06-review-checklist.md`.
3. Check NBT, registry, network-ID, and old-world compatibility before changing
   behavior.

## Core rules

- Java 8, four spaces, no new wildcard imports, no mass reformat.
- Keep the server authoritative. Validate packets, reach, ownership, and
  console access on the server.
- Append new registrations. Do not move existing block, item, sound, or network
  IDs.
- New Java files start with `// SPDX-License-Identifier: LGPL-3.0-only`.
- No `System.out`, `printStackTrace`, commented-out code, or unexplained
  `TODO` / `FIXME`.
- Keep changes focused. Do not mix a refactor with a behavior change.
- Update README, changelog, assets, or `NOTICE` only when the change requires it.
- Do not describe unfinished features as available.

## Build and verification

- Use JDK 8.
- Test build: `.\gradlew.bat build -PnoVersionBump` on Windows, or
  `./gradlew build -PnoVersionBump` on Unix.
- Run the review checklist before calling a change finished.
- For releases, follow `docs/standards/05-release-and-versioning.md`.

When a standard conflicts with actual behavior or an explicit user request,
flag it and ask instead of silently breaking compatibility.
