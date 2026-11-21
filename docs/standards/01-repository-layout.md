# Repository layout

## Top level

```text
susy-ntmdoors/
  build.gradle              Gradle build and resource packaging
  settings.gradle           Project name
  gradle.properties         Single version source (mod_version)
  gradlew / gradlew.bat     Gradle wrapper
  gradle/                   Wrapper jar and properties
  src/main/java/            Java source
  src/main/resources/       Runtime assets, metadata, packaged notices
  source-art/               Source art, reference material, regeneration tools
  docs/standards/           This reference
  README.md                 Player and developer overview
  CHANGELOG.md              Canonical release history
  LICENSE.LESSER            LGPL-3.0 license text
  NOTICE                    Third-party attribution
  AI_DISCLOSURE.md          Scope of AI-assisted work
  CURSEFORGE-*.md           CurseForge description and release notes
  CONTRIBUTING.md           Short contributor guide
```

Build outputs such as `build/`, `.gradle/`, logs, crash reports, and local
screenshots are ignored and must not be committed.

## Java source

Packages are organised by domain:

```text
com.ntmdoors
  block/        Blocks and block-state behaviour
  tile/         Tile entities, NBT, server state
  item/         Items and item behaviour
  door/         Door declarations, geometry, placement
  client/       Client-only screens, renderers, overlays, sounds
  gui/          Containers and inventory wrappers
  network/      Messages and handlers
  monitor/      Terminal, security-net, notepad and backup data
  concrete/     Concrete building set
  hlblocks/     HL Blocks set
  event/        Forge event subscribers
  compat/       Optional mod integration
  util/         Small shared helpers
  proxy/        Common and client proxies
  registry/     Registration and recipes
```

Rules:

- Client-only classes are marked `@SideOnly(Side.CLIENT)` or live behind the
  client proxy.
- Server-authoritative state lives on tile entities, not in client fields.
- A package should own one feature area. Do not add a new package for a single
  small helper.

## Runtime resources

```text
src/main/resources/
  mcmod.info
  pack.mcmeta
  assets/ntmdoors/
    blockstates/    One file per block registry name
    models/block/   Block models
    models/item/    Item models
    models/doors/   OBJ door models
    textures/blocks/
    textures/items/
    textures/models/doors/
    textures/gui/
    textures/display/
    sounds/
    sounds.json
    lang/en_us.lang
```

- Model, blockstate, texture, sound, and lang names use `snake_case`.
- Names match the registry name unless the asset is explicitly a shared or
  technical asset.
- The `display/` textures are generated from `source-art/display/` at build
  time; do not add a second source copy under `src/main/resources`.

## Source art

`source-art/` holds material that is not loaded by the game:

- `.bbmodel` files and texture masters used to regenerate models.
- Reference screenshots and design previews.
- Regeneration scripts.
- `source-art/display/` is the exception: it is a real Gradle build input for
  the cosmetic monitor wallpapers.

Keep reference-only material out of the jar. If it is third-party, record its
source and terms in `NOTICE` even when it is not shipped.

## Canonical files

- `LICENSE.LESSER` and `NOTICE` at the repository root are canonical.
- `build.gradle` packages them into `META-INF` in the jar.
- Do not add a second copy under `src/main/resources`; existing duplicates are
  a legacy cleanup target.

## Documentation files

Root documents use `UPPER_CASE.md` for project-wide files. Supporting
documents use lowercase, hyphenated names under `docs/`.

- `README.md` is the entry point.
- `CHANGELOG.md` is the full history.
- `CONTRIBUTING.md` is the short workflow guide.
- `docs/standards/` is the long-form reference.
- `CURSEFORGE-DESCRIPTION.md` and the current release note are platform copies;
  they must not drift from the canonical README and changelog.
