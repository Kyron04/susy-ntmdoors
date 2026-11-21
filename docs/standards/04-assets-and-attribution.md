# Assets and attribution

Assets are part of the public product. A clean asset tree is one where every
file has a purpose, every third-party file has a credit, and the jar contains
only what the game needs.

## Source versus shipped

| Location | Purpose | Packaged? |
|---|---|---|
| `source-art/` | Masters, reference images, previews, regeneration scripts | No, except `source-art/display/` |
| `src/main/resources/assets/ntmdoors/` | Runtime models, textures, sounds, lang | Yes |
| `source-art/display/` | Cosmetic monitor wallpapers copied by Gradle | Yes, under `textures/display/` |

- Keep `.bbmodel`, high-resolution atlases, and design references in
  `source-art/`.
- Do not ship source art unless the build explicitly copies it.
- If reference material is third-party, record its source and terms in
  `NOTICE`, even when it is not packaged.

## Naming

- `snake_case` for files, folders, blockstates, models, textures, sounds, and
  lang keys.
- The registry name is the base name: `keycard_scanner` produces
  `blockstates/keycard_scanner.json`, `models/block/keycard_scanner.json`, and
  the matching item model unless a shared parent is used.
- Use `_locked` and `_security` suffixes only for the generated door variants.
- Use `_2`, `_3`, `_4` only for numbered visual variants, not for duplicates.
- Do not use spaces, uppercase letters, or platform-specific characters.

## Reference chain

Every shipped file should be reachable:

```text
block registry name
  -> blockstates/<name>.json
  -> models/block/<model>.json
  -> textures/blocks/<texture>.png

item registry name
  -> models/item/<name>.json (or a shared parent)
  -> textures/items/<texture>.png

sound registry entry in sounds.json
  -> sounds/<category>/<file>.ogg

code constant
  -> lang key
  -> lang/en_us.lang
```

Rules:

- A model may be shared by several blockstates. That is different from a
  duplicate file with the same content under two names.
- A texture may be shared by several models. That is different from a copy.
- A blockstate may point to a model in `models/block/`; an item model may point
  to a block model or a shared item model.
- Delete orphan models, textures, and sounds. If a file is kept for source
  regeneration, move it to `source-art/`.

## Item models

Minecraft needs a model path per item registry name, but the file can be a
one-line stub that points at a shared parent.

Good:

```json
{
  "parent": "ntmdoors:item/door_item"
}
```

Avoid adding 80 copies of the same stub for every door variant. The standard
pattern is one shared parent plus a model registration rule in
`ClientModels` that points the door items at it.

## Duplicate assets

Consolidate only when the files are byte-identical and the UV or display
metadata does not depend on the file name.

- Duplicate texture atlases: point all users at one file, then delete the
  copies.
- Duplicate block models: keep one canonical model and update the blockstates.
- Duplicate source masters: keep one master and add a note if a tool expects a
  specific name.

Verify the result in-game. A byte-identical PNG can still be used with
different UV expectations.

## Language file

- `tile.ntmdoors.<name>.name` for blocks.
- `item.ntmdoors.<name>.name` for items that are not `ItemBlock` variants.
- `ntmdoors.msg.*` for chat/status messages.
- `key.ntmdoors.*` for key bindings.
- No duplicate keys, no empty values, no unused keys.
- Do not add an `item.` key for an `ItemBlock` when its block key already
  supplies the name.

## Sounds

- Every sound is registered in `sounds.json`.
- Every `.ogg` is referenced by exactly one registry entry unless it is a
  deliberate alias.
- Keep sound categories consistent: `doors/`, `speaker/`, `emancipation/`,
  `thermite/`, `ui/`.
- List third-party sounds in `NOTICE` with the source and license/permission.

## Attribution

Every third-party asset gets an entry in `NOTICE`:

```text
Speaker alarm sounds:

  * assets/ntmdoors/sounds/speaker/facility.ogg is derived from
    "Sirens and Alarm Noise" by aquinn (OpenGameArt, CC0 1.0).
  * assets/ntmdoors/sounds/speaker/air_raid.ogg, industrial.ogg and eas.ogg
    are derived from audio supplied by the project owner.

Emancipation Grill field sounds:

  * assets/ntmdoors/sounds/emancipation/fizzler_start.ogg, fizzler_loop.ogg,
    fizzler_shutdown.ogg and disintegrate.ogg were converted from Portal 2
    sound assets via https://github.com/sourcesounds/portal2.
  * Portal 2 is a property of Valve Corporation. No endorsement is implied.
```

Rules:

- Name the file or folder, the source, and the license or permission.
- Do not claim a license you cannot document.
- If a source is reference-only and not shipped, say so.
- Keep the attribution in sync with the files; remove the entry when the asset
  is removed.

## Large assets

Large OBJ, animation, or sound files are acceptable when they are the actual
feature. Do not delete or decimate them for repository size without a visual or
audio check. Prefer build-time minification for text formats and leave binary
assets alone.
