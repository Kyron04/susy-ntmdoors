# Java style

The project targets Java 8 and Minecraft Forge 1.12.2. The style is deliberately
conservative: readable, server-safe, and friendly to old worlds.

## Formatting

- Four spaces, no tabs.
- Opening brace on the same line.
- One statement per line.
- No new wildcard imports. Prefer explicit imports; existing static wildcard
  imports are cleanup targets, not a pattern to copy.
- Import order: project packages, Minecraft/Forge, third-party libraries,
  Java/javax. Do not reorder an existing file solely for style.
- Soft line limit of 110 characters. Do not reflow an entire file just to meet
  the limit; wrap new code naturally.
- No trailing whitespace. Files end with a newline.

The repository also carries `.editorconfig` and `.gitattributes` for editors
and Git.

## Naming

| Kind | Pattern | Example |
|---|---|---|
| Block | `BlockX` | `BlockMotionSensor` |
| Tile entity | `TileEntityX` | `TileEntityKeycardScanner` |
| Item | `ItemX` | `ItemRedstoneRelayKey` |
| ItemBlock | `ItemBlockX` | `ItemBlockKeypad` |
| GUI | `GuiX` | `GuiEmancipationGrill` |
| Container | `ContainerX` | `ContainerSafe` |
| Network message | `MessageX` | `MessageGrillOption` |
| Renderer | `RenderX` | `RenderEmancipationField` |
| Event subscriber | `XEvents` | `SpeakerEvents` |
| Saved data | `XData` | `SecurityEventData` |
| Enum | singular noun | `FieldMode`, `FieldColour` |

- Registry IDs use `snake_case`: `sliding_blast_door`, `keycard_scanner`.
- NBT keys use `lowerCamelCase`: `fieldColour`, `boundScanners`.
- Constants use `UPPER_SNAKE_CASE`.
- Methods use `lowerCamelCase` and describe the action: `setFieldColour`,
  `isBoundToSafe`.
- Booleans read as predicates: `isEnabled`, `hasOwner`, `acceptsRedstone`.

## Class structure

- Keep fields near the top of the class, grouped by concern.
- Put public behavior before private helpers.
- Use `@Nullable` on methods and parameters that may return or accept null.
- Prefer `final` for fields that are assigned once.
- Avoid static mutable state. Shared helpers should be stateless.
- Do not add a class for a single trivial method; use an existing utility or
  keep the method private.

## Server and client

- The server owns state. Clients render and send requests.
- Packet handlers validate reach, ownership, console access, and any other
  permission on the server.
- Client-only code lives in `client/`, is marked `@SideOnly(Side.CLIENT)`, or
  sits behind the client proxy.
- Never let a client-only class be loaded on a dedicated server.
- Client GUIs may read synced tile-entity data; they must not mutate world
  state directly.

## NBT and world compatibility

- Treat NBT keys and registry names as a public save format.
- Read new keys with a default so old worlds still load.
- Keep migration code for renamed keys and legacy values.
- Do not reorder existing registrations. Append new blocks, items, sounds, and
  network IDs so numeric IDs and saved data remain stable.
- When a tile entity is replaced, decide explicitly whether old bindings should
  follow it. Document the choice.

## Registration

- Register blocks, items, tile entities, sounds, and messages in the existing
  registry classes.
- Append new entries at the end of a registration list.
- Keep client model registration in `ClientModels`, not in block constructors.
- Do not register the same object twice or add a second network ID by hand.

## Logging and errors

- Use `NTMDoors.LOGGER`.
- Log actionable warnings, not routine control flow.
- Never use `System.out` or `printStackTrace`.
- Do not swallow exceptions silently. If a failure is expected and harmless,
  leave a short comment explaining why.
- Do not leave commented-out code or unexplained `TODO` / `FIXME` markers.

## Comments and Javadoc

- Explain why, not what.
- Use Javadoc for public behavior, non-obvious constraints, and save-format
  rules.
- Keep comments accurate when behavior changes.
- Remove stale "future" comments once the feature exists.

## License header

New Java files start with:

```java
// SPDX-License-Identifier: LGPL-3.0-only
```

The project ships the LGPL version 3 text in `LICENSE.LESSER`. Apply the header
to new and substantially edited files first; do not mass-edit the existing tree
in the same change as a refactor. If the project ever adopts "or later", update
the identifier and notices together.

## Do not

- Mix a behavior change with a repository-wide reformat.
- Add a dependency for a small helper that already exists in the project.
- Copy a block/tile/item class as a template without removing its unused code.
- Hide a compatibility break behind a new NBT key.
- Use magic numbers for limits, ranges, or delays; name them.
