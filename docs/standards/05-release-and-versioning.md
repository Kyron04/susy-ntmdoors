# Release and versioning

The project has one version source and one release history. Everything else is
generated from them or checked against them.

## Version source

- `gradle.properties` contains `mod_version`.
- `build.gradle` copies it into the jar name and expands `mcmod.info`.
- The `bumpVersion` task updates `mod_version` and the `@Mod` annotation in
  `NTMDoors.java` after a jar is written.
- Do not edit those two version fields by hand in a feature branch.

Test builds:

```bash
./gradlew build -PnoVersionBump
```

Release builds omit `-PnoVersionBump`; the patch number advances after the jar
is written.

## Versioning

- Patch: bug fix, compatibility fix, asset correction, documentation-only
  release.
- Minor: new feature or new content that is backward compatible.
- Major: a save-format or behavior break that requires migration.
- Do not reuse a version number for a different jar.
- Do not renumber an already published release to make a changelog neater.

## Release checklist

1. Update `CHANGELOG.md` with one section for the release.
2. Update the current CurseForge release note from that section.
3. Update the current version in `CURSEFORGE-DESCRIPTION.md`.
4. Confirm the README feature list and requirements match the build.
5. Confirm `NOTICE` covers every third-party asset in the jar.
6. Update `AI_DISCLOSURE.md` if the release includes AI-assisted work.
7. Build from a clean tree with JDK 8.
8. Inspect the jar: no `.java`, `.ps1`, `.bak`, `source-art/`, or local files.
9. Record the jar SHA-256 in the release note.
10. Tag the release and publish the matching source.

## CurseForge files

- The description is a platform copy of the README's player-facing summary.
- The current release note is a short copy of the matching changelog section.
- Older release notes belong under `docs/releases/`; do not leave a growing row
  of version files beside the current one.
- A release note must not describe a previous release. If the highlights did not
  change, the version did not change.
- The SHA-256 is for the published jar, not for a local test build.

## Compatibility notes

Every release note should state:

- Minecraft and Forge versions.
- Required mods and versions.
- Whether existing worlds load.
- Whether clients and servers must update together.
- Any registry or NBT migration.

## Reproducibility

- Build with the documented JDK and Gradle wrapper.
- Do not commit `build/`, `.gradle/`, or generated resources.
- Keep `gradle/wrapper/gradle-wrapper.jar` and its properties in the repository.
- If a build changes generated resources, inspect the diff before committing.

## After release

- Keep the released source and tag available.
- Move the previous release note to `docs/releases/` if the root is getting
  crowded.
- Do not silently rewrite published changelog entries; add a correction note
  if something was wrong.
