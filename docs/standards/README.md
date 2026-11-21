# NTM Doors standards

This directory is the project's reference for code, resources, documentation,
and releases. It describes the target style; existing code is migrated
gradually rather than reformatted in one pass.

## How to use it

- Read this index before a refactor or a new feature.
- Apply the rules to new and substantially edited files.
- When a rule cannot be followed, document the exception in the change or in
  the relevant standard.
- Correctness, world-data compatibility, and clear server behavior outrank
  cosmetic consistency.

## Index

1. [Repository layout](01-repository-layout.md)
2. [Java style](02-java-style.md)
3. [Documentation style](03-documentation.md)
4. [Assets and attribution](04-assets-and-attribution.md)
5. [Release and versioning](05-release-and-versioning.md)
6. [Review checklist](06-review-checklist.md)
7. [Examples](examples.md)

## Current migration targets

The standards are also a checklist for the later repository cleanup. Known
targets are:

- Remove the duplicate `LICENSE.LESSER` and `NOTICE.txt` copies under
  `src/main/resources`; the root files are canonical and Gradle packages them
  into `META-INF`.
- Archive old per-release CurseForge notes under `docs/releases/` instead of
  leaving them beside the current release.
- Collapse duplicate version headings in `CHANGELOG.md` into one section per
  release.
- Keep reference-only source art out of the packaged jar and attribute
  third-party material in `NOTICE`.
- Replace repeated asset stubs with shared models where the item registry
  allows it.

These are cleanup tasks, not requirements for the current change.
