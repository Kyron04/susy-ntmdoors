# Contributing to NTM Doors

NTM Doors is a Minecraft 1.12.2 Forge mod. The project values stable world data,
predictable server behavior, and readable code over clever shortcuts.

This file is the short version. The full reference lives in
[`docs/standards/`](docs/standards/README.md).

## Before you start

1. Read [`docs/standards/README.md`](docs/standards/README.md).
2. Check the review checklist in
   [`docs/standards/06-review-checklist.md`](docs/standards/06-review-checklist.md).
3. Build with JDK 8 before and after your change.

## Build

Windows:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk8'
.\gradlew.bat build -PnoVersionBump
```

Unix:

```bash
./gradlew build -PnoVersionBump
```

`-PnoVersionBump` keeps the current version number for test builds. A release
build omits it and advances the patch version.

## Code

- Java 8, four-space indentation, braces on the same line.
- No new wildcard imports. Prefer explicit imports and keep import groups readable.
- Follow the existing naming patterns: `BlockX`, `TileEntityX`, `ItemX`,
  `GuiX`, `ContainerX`, `MessageX`, `RenderX`, `XEvents`, `XData`.
- Keep the server authoritative. Validate packets, reach, ownership, and
  console access on the server.
- Preserve NBT compatibility and registration order. New blocks and items go
  at the end of their registration lists.
- No `System.out`, `printStackTrace`, commented-out code, or unexplained
  `TODO` / `FIXME` markers. Use `NTMDoors.LOGGER`.
- New Java files start with:

```java
// SPDX-License-Identifier: LGPL-3.0-only
```

Do not mix a large reformat with a behavior change. Keep the diff focused.

## Assets

- Source art belongs in `source-art/`.
- Shipped assets belong under
  `src/main/resources/assets/ntmdoors/`.
- Every blockstate, model, texture, sound, and lang key should be reachable
  from code or another asset.
- Third-party assets need a `NOTICE` entry with the source, path, and license
  or permission.

## Documentation

- Update `README.md` when player-facing behavior changes.
- Add a changelog entry under the current version.
- Update CurseForge release notes only when preparing a release.
- Document what the code actually does. Avoid marketing language and
  "coming soon" claims for unfinished features.

## Commits and pull requests

- Make small, focused commits with imperative subjects.
- Describe the behavior change and how it was tested.
- Do not bump the version in a pull request unless it is a release.
- Mention any NBT, registry, or compatibility impact.

## License

The Java code is released under the GNU Lesser General Public License,
version 3. See [`LICENSE.LESSER`](LICENSE.LESSER). Third-party assets remain
under their own terms; see [`NOTICE`](NOTICE).
