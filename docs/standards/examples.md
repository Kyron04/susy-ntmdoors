# Examples

These snippets show the target style. They are illustrative; use the real
registry and packet classes when implementing a change.

## Java class header and fields

Good:

```java
// SPDX-License-Identifier: LGPL-3.0-only
package com.ntmdoors.tile;

/**
 * Stores a device setting on the tile entity and syncs it to nearby clients.
 */
public class TileEntityExample extends TileEntitySynced implements ITickable {

    private static final int DEFAULT_RANGE = 32;
    private static final int MIN_RANGE = 1;
    private static final int MAX_RANGE = 64;

    private int range = DEFAULT_RANGE;
    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public int getRange() {
        return range;
    }
}
```

Avoid:

```java
public class TileEntityExample {
    public int r = 32;          // no constant, no range, public mutable state
    public boolean e = true;    // unclear name
    // System.out.println("created");  // debug leftover
}
```

## Server-side packet handling

Good:

```java
private static void apply(EntityPlayerMP player, MessageExample message) {
    if (message.pos == null) return;
    if (!NetworkUtil.isWithinReach(player, message.pos)) return;

    TileEntity te = player.world.getTileEntity(message.pos);
    if (!(te instanceof TileEntityExample)) return;

    TileEntityExample tile = (TileEntityExample) te;
    if (!tile.hasConsoleAccess(player)) return;

    tile.setEnabled(message.enabled);
}
```

Avoid:

```java
private static void apply(EntityPlayerMP player, MessageExample message) {
    TileEntityExample tile = (TileEntityExample) player.world.getTileEntity(message.pos);
    tile.setEnabled(message.enabled); // no reach, type, or access check
}
```

## NBT compatibility

Good:

```java
range = tag.hasKey("range", Constants.NBT.TAG_INT)
        ? MathHelper.clamp(tag.getInteger("range"), MIN_RANGE, MAX_RANGE)
        : DEFAULT_RANGE;
```

Avoid:

```java
range = tag.getInteger("newRange"); // old saves lose the value
```

When a key is renamed, read the old key as a fallback and write only the new
key:

```java
if (tag.hasKey("oldRange")) {
    range = tag.getInteger("oldRange");
}
```

## Registration order

Good:

```java
// New entries go at the end so existing numeric IDs do not move.
public static final BlockExample EXAMPLE = new BlockExample("example");
```

Avoid:

```java
// Inserting before existing entries shifts their numeric IDs.
public static final BlockExample EXAMPLE = new BlockExample("example");
public static final BlockDoorGeneric DOOR = new BlockDoorGeneric(...);
```

## README section

Good:

```markdown
## Requirements

| Component | Version |
|---|---|
| Minecraft | 1.12.2 |
| Forge | 14.23.5.2847+ |
| Chisel | 1.12.2-1.0.2.45 |

Chisel is required. No NTM installation is required.
```

Avoid:

```markdown
## Requirements
You need the right versions of everything or it will not work!!!
```

## Changelog entry

Good:

```text
1.0.80
FEATURES
- Added a field colour page to the Emancipation Grill console.
COMPATIBILITY
- Existing 1.0.79 worlds load normally.
```

Avoid:

```text
1.0.79
FEATURES
- ...
1.0.78
...
1.0.79
FEATURES
- ... again
```

## Release note

Good:

```markdown
# NTM Doors 1.0.80 - CurseForge Release

Minecraft 1.12.2 / Forge 14.23.5.2847+ (tested on 14.23.5.2860)
Requires Chisel 1.12.2-1.0.2.45

**File:** `ntmdoors-1.0.80.jar`
**SHA-256:** `<hash of the published jar>`

This release adds the Emancipation Grill console and field modes.

## Highlights
## Compatibility
```

Avoid:

```markdown
# NTM Doors 1.0.80
This is the same text as 1.0.79.
```

## NOTICE entry

Good:

```text
Speaker alarm sounds:

  * assets/ntmdoors/sounds/speaker/facility.ogg is derived from
    "Sirens and Alarm Noise" by aquinn (OpenGameArt, CC0 1.0).
  * assets/ntmdoors/sounds/speaker/air_raid.ogg, industrial.ogg and eas.ogg
    are derived from audio supplied by the project owner.
```

Avoid:

```text
Sounds by someone, somewhere.
```

## Asset tree

Good:

```text
blockstates/example_device.json
models/block/example_device.json
models/item/example_device.json
textures/blocks/example_device.png
```

Avoid:

```text
models/item/example_device.json
models/item/example_device_copy.json
models/item/example_device_copy_2.json
textures/blocks/example_device.png
textures/items/example_device.png
```

If two files must be identical, document why or point both references at one
shared file.

## Good versus avoid

| Area | Good | Avoid |
|---|---|---|
| Java | Named constants, predicates, server checks | Magic numbers, public mutable fields, client authority |
| NBT | Defaults and migrations | Renaming keys without a fallback |
| Registry | Append new entries | Reorder existing IDs |
| Assets | One canonical file per texture/model | Orphan files and copy-paste stubs |
| Documentation | Concrete behavior and limits | Marketing fluff and stale claims |
| Attribution | Source, path, license, no endorsement | "Credit to the authors" with no terms |
| Git | Focused behavior change | Reformat plus behavior change in one commit |

## Commit subjects

Good:

```text
Add field colour console page
Fix monitor page reverting after dark mode
Cache door item model bounds
```

Avoid:

```text
stuff
more changes
fix
```
