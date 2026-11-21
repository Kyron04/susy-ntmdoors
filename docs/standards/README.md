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

- Collapse duplicate version headings in `CHANGELOG.md`; the public and
  internal histories currently share version numbers and need clearer labels.
- Keep reference-only source art out of the packaged jar and attribute
  third-party material in `NOTICE`.
- Replace repeated asset stubs with shared models where the item registry
  allows it.

These are cleanup tasks, not requirements for the current change.
