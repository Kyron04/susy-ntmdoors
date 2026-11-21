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

- Complete attribution for display wallpapers, Portal 2 reference images and
  other supplied source material in `NOTICE`.
- Replace the remaining monitor composite item models only with a dedicated
  item renderer and full visual testing; they cannot be deduplicated as JSON.
- Review the remaining code duplication candidates (GUI widgets, facing
  helpers and desk placement) as separate, tested refactors.

These are cleanup tasks, not requirements for the current change.
