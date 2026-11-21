# Documentation style

Documentation should let a new player install the mod and let a new developer
understand a change without reading the whole source tree. Write what the code
does, in the present tense.

## README structure

Use this order. Short projects can omit sections; long projects should keep it.

```markdown
# NTM Doors
One sentence describing the mod.

| Component | Version |
|---|---|
| Minecraft | 1.12.2 |
| Forge | 14.23.5.2847+ |
| Chisel | 1.12.2-1.0.2.45 |

## Overview
What the mod adds and who it is for.

## Requirements
Required mods and versions.

## Installation
Where the jar goes and what to do on first launch.

## Contents
A table of contents once the document is longer than a screen or two.

## Features
Grouped by player-facing area. Include behavior, limits, and interactions.

## Compatibility
Known mod interactions, world migration notes, and side requirements.

## Building from source
JDK version, Gradle command, and output path.

## Contributing
Link to `CONTRIBUTING.md` and `docs/standards/`.

## License and credits
Link to `LICENSE.LESSER` and `NOTICE`.
```

Rules:

- Put the most useful information first. Do not open with a changelog.
- Use tables for requirements and short option lists.
- Use fenced code blocks for commands and file paths.
- Use relative links to files in the repository; do not link to line numbers.
- Keep one blank line around headings, lists, and code blocks.
- Prefer concrete behavior over adjectives. "Blocks unauthorized players" is
  better than "powerful security."
- Do not describe unfinished features as available. Mark them as planned or
  leave them out.
- Match the spelling already used by a document; do not mix spellings within
  one section. Code identifiers keep their existing spelling.
- No emoji, no decorative badges that are not maintained, and no screenshots
  that are older than the model they show.

## Changelog

`CHANGELOG.md` is the canonical history. Newest release first, one section per
version, no duplicate version headings.

```text
1.0.79
FEATURES
- Added ...
FIXES
- Fixed ...
CHANGES
- Changed ...
COMPATIBILITY
- Existing worlds ...
INTERNAL
- Registration, refactor, or tooling notes ...
```

Rules:

- Use one category per bullet: `FEATURES`, `CHANGES`, `FIXES`,
  `COMPATIBILITY`, `INTERNAL`.
- Describe the player-visible result first; mention the implementation only
  when it affects compatibility.
- Do not repeat the same fix in several categories.
- Do not invent a release number. The number comes from `gradle.properties`.
- Keep old sections; do not rewrite history to make an old release look
  cleaner.

## Release notes

CurseForge release notes are a short, release-specific copy derived from the
changelog. They are not a second history.

```markdown
# NTM Doors 1.0.79 - CurseForge Release

Minecraft 1.12.2 / Forge 14.23.5.2847+ (tested on 14.23.5.2860)
Requires Chisel 1.12.2-1.0.2.45

**File:** `ntmdoors-1.0.79.jar`
**SHA-256:** `<hash of the published jar>`

One-paragraph summary.

## Highlights
## Changes
## Compatibility
```

- Regenerate the SHA-256 from the final published jar.
- Do not copy the previous release note and change only the version.
- Keep the current release note beside the current release; archive older ones
  under `docs/releases/`.

## NOTICE

`NOTICE` is the attribution file for third-party code, models, textures,
sounds, and reference material.

- One section per source or license family.
- List the affected paths.
- Include the source URL when one exists.
- State the license or permission. Do not imply a license that is not granted.
- Add "No endorsement is implied" for named games, studios, or authors.
- Keep the root `NOTICE` canonical. Gradle packages it into `META-INF`; do not
  maintain a second copy under `src/main/resources`.

## AI disclosure

Update `AI_DISCLOSURE.md` when a release includes AI-assisted code, text, or
assets. Describe the scope and the human review/testing that followed. Do not
use it as a substitute for the license or `NOTICE`.

## File names and links

- Root project documents use `UPPER_CASE.md`.
- Supporting documents use lowercase, hyphenated names under `docs/`.
- Release-specific notes use `CURSEFORGE-CHANGELOG-<version>.md` at the root or
  live under `docs/releases/`.
- Test relative links before committing. Broken links are worse than no link.
