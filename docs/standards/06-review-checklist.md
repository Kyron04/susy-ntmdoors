# Review checklist

Use this before calling a change finished. It is intentionally short enough to
run every time.

## Before coding

- [ ] I understand the current behavior and the intended behavior.
- [ ] I checked whether the change affects NBT, registry names, network IDs, or
      saved world data.
- [ ] I know whether the change is client-only, server-only, or both.
- [ ] I found the existing pattern for this feature instead of inventing a new
      one.

## Code

- [ ] Java 8, four spaces, no tabs, no wildcard imports.
- [ ] Names follow `BlockX`, `TileEntityX`, `ItemX`, `GuiX`, `MessageX`,
      `RenderX`, `XEvents`, `XData`.
- [ ] Server-side validation covers reach, ownership, console access, and
      packet values.
- [ ] No client-only class can load on a dedicated server.
- [ ] New NBT keys have defaults and old worlds still load.
- [ ] New registrations are appended; existing IDs did not move.
- [ ] No `System.out`, `printStackTrace`, commented-out code, or unexplained
      `TODO` / `FIXME`.
- [ ] Logs use `NTMDoors.LOGGER` and report actionable failures.
- [ ] New Java files carry `// SPDX-License-Identifier: LGPL-3.0-only`.
- [ ] The diff is focused; a refactor is not mixed with a behavior change.

## Assets

- [ ] Every new blockstate, model, texture, sound, and lang key is referenced.
- [ ] No duplicate stub or duplicate texture was added without a reason.
- [ ] Names are `snake_case` and match the registry or shared parent.
- [ ] The asset is in `src/main/resources`, not `source-art/`, if the game needs
      it.
- [ ] Source masters and reference art remain outside the jar.
- [ ] Third-party assets have a `NOTICE` entry.
- [ ] The asset was checked in-game, including inventory, hand, and world
      rendering where relevant.

## Documentation

- [ ] README describes the player-visible change, if any.
- [ ] `CHANGELOG.md` has an entry under the current version.
- [ ] CurseForge notes are updated only for a release and match the changelog.
- [ ] `NOTICE` and `AI_DISCLOSURE.md` are current.
- [ ] Links and file paths are valid.
- [ ] No unfinished feature is described as available.

## Build

- [ ] JDK 8 build succeeds.
- [ ] Test build used `-PnoVersionBump` unless this is a release.
- [ ] Version fields were not hand-edited.
- [ ] Jar contains the expected assets and no source, scripts, backups, or
      local files.
- [ ] Old-world and dedicated-server behavior was checked when relevant.

## Stop conditions

Do not merge or publish if:

- the build fails;
- an old world or NBT format can no longer load;
- a packet can change state without server-side permission checks;
- a third-party asset has no recorded source or terms;
- the README or changelog claims behavior the build does not have;
- the version or release SHA is inconsistent with the published jar.
