# Security Shroud configuration

The Security Shroud and every behavior it shares with other armor sets are tuned in
`config/ntmdoors.cfg`. Forge creates the file on the first launch. Restart the game after editing it;
values are read once during startup before item registration.

Forge writes category names in lowercase, so the generated file contains `securityshroud` with
nested `items`, `helmet`, `chestplate`, `leggings`, `boots` and `unity` blocks. Property names keep
their capitalisation. The dotted paths below name the property within those nested blocks.

## Defaults

| Category / key | Default | Meaning |
|---|---:|---|
| `securityshroud.Enabled` | `true` | Master switch for all effects. Items and their stats still exist when false. |
| `securityshroud.ShowTooltips` | `true` | Master tooltip switch for the built-in pieces, including durability and damage resistance. |
| `securityshroud.ShowUnityTooltip` | `true` | Show the full-set Unity line when `ShowTooltips` is true. |
| `securityshroud.Durability` | `2500` | Exact maximum durability of each built-in piece. |
| `securityshroud.HelmetProtection` | `3` | Armor points for the helmet. |
| `securityshroud.ChestplateProtection` | `8` | Armor points for the chestplate. |
| `securityshroud.LeggingsProtection` | `6` | Armor points for the leggings. |
| `securityshroud.BootsProtection` | `3` | Armor points for the boots. |
| `securityshroud.Enchantability` | `0` | Enchantability of the built-in material. |
| `securityshroud.Toughness` | `0.0` | Armor toughness of the built-in material. |
| `securityshroud.helmet.HideNameTags` | `true` | Hide name tags for any living entity wearing a configured helmet. |
| `securityshroud.chestplate.BypassWeaponsBindings` | `true` | Ignore Dead Man's Hand bindings while wearing a configured chestplate. |
| `securityshroud.leggings.MovementSpeedBonus` | `0.5` | Movement multiplier; `0.5` is +50%, or 1.5x normal speed. |
| `securityshroud.boots.PreventLethalFallDamage` | `true` | Cap fall damage before it can take the entity below the configured health. |
| `securityshroud.boots.MinimumHealthAfterFall` | `2.0` | Health left after a prevented lethal fall; `2.0` is one heart. |
| `securityshroud.boots.FallProtectionCooldownTicks` | `1200` | Ticks before the boots can cap another lethal fall for the same entity; `1200` is one minute. `0` disables the cooldown. |
| `securityshroud.unity.EnableUnity` | `true` | Enable the full-set effect. |
| `securityshroud.unity.RequireWorn` | `true` | Require all four pieces to be equipped. When false, players may carry them in the inventory; non-players must wear them. |
| `securityshroud.unity.IgnoreMotionSensors` | `true` | Full-set entities are skipped by Motion Sensors, including Match User. |
| `securityshroud.unity.IgnoreEmancipationGrills` | `true` | Full-set entities are skipped by Emancipation Grills in every mode. |

The default protection values total 20 armor points, the vanilla armor cap; higher configured totals
are still subject to that cap. Toughness defaults to `0.0`; raise it if high-damage attacks still
hurt too much. Durability is capped at `32767` because 1.12 stores item damage as a short. The
built-in pieces use `EnumRarity.RARE`, so their item names are aqua. Each built-in tooltip shows its
current/max durability and that piece's armor points, plus the effective full-set total, capped at
20. Name-tag concealment is a client-side render effect: clients without the mod, or with
`HideNameTags` disabled, still see the name. It also does not hide names in the tab list, chat,
death messages or command output.

The fall cap is per entity and starts its cooldown only when it actually reduces a fall that would
otherwise take health below `MinimumHealthAfterFall`. While the cooldown runs, ordinary fall damage
is applied in full, so a second lethal fall within one minute still kills. The cooldown is stored in
the entity's ForgeData and saved with it. Set `FallProtectionCooldownTicks` to `0` for continuous
protection. The boots tooltip shows the configured cooldown.

## Item lists

Each behavior is attached to a configured item list, not to the built-in item classes.

| Category / key | Default |
|---|---|
| `securityshroud.items.HelmetItems` | `ntmdoors:security_shroud_helmet` |
| `securityshroud.items.ChestplateItems` | `ntmdoors:security_shroud_chestplate` |
| `securityshroud.items.LeggingsItems` | `ntmdoors:security_shroud_leggings` |
| `securityshroud.items.BootsItems` | `ntmdoors:security_shroud_boots` |

List entries are registry names:

* `modid:item` - one item.
* `modid:*` - every item from that mod. Use this only when the whole mod's armor should share the
  behavior.
* A trailing metadata value such as `modid:item:1` is accepted and reduced to `modid:item`.
* A missing namespace is treated as `minecraft:`.

The default lists contain only the Security Shroud pieces. Append another mod's armor to a list to
give it that piece's effect and let it count toward Unity:

```text
securityshroud {
    items {
        S:HelmetItems <
            ntmdoors:security_shroud_helmet
            examplemod:kevlar_helmet
        >
        S:ChestplateItems <
            ntmdoors:security_shroud_chestplate
            examplemod:kevlar_chestplate
        >
        S:LeggingsItems <
            ntmdoors:security_shroud_leggings
            examplemod:kevlar_leggings
        >
        S:BootsItems <
            ntmdoors:security_shroud_boots
            examplemod:kevlar_boots
        >
    }
}
```

Do not add a list entry if the item does not exist on both the client and server. Unknown entries
are harmless but never match; they are reported in the log at post-init. An empty list disables that
slot's behavior and makes the full set impossible. Use a different item in each list; the full-set
check treats the four lists separately and verifies the armor slot, so a helmet listed in
`ChestplateItems` does not count. Entries must be wearable `ItemArmor` pieces for the slot. For
metadata, include the namespace explicitly, e.g. `minecraft:stone:1`. To use another set only for
Unity,
add all four pieces and disable `HideNameTags`, `BypassWeaponsBindings` and
`PreventLethalFallDamage`, and set `MovementSpeedBonus` to `0`; the four item lists still form the
full set.

## Full-set behavior

Unity requires one configured item in each of the four armor slots. With `RequireWorn` set to
`false`, a player may instead carry the four pieces in their main inventory, armor slots or offhand;
non-player entities must wear them. The pieces do not have to come from the same mod or set; each
slot is checked against its own list.

When `IgnoreMotionSensors` and `IgnoreEmancipationGrills` are enabled:

* Motion Sensors do not see the armored entity and do not fire authorized, unauthorized or Match
  User events for it.
* Emancipation Grills ignore the armored entity in every field mode. This includes Solid/Wall
  clamping, Lethal disintegration, Filter inventory scans and Match User redstone signals.

This applies to players and to armored mobs/enemies. Only the armored living entity is skipped;
mounts, vehicles, projectiles and dropped items are still handled by the normal Motion Sensor and
Emancipation Grill rules.

## Multiplayer note

The client reads the same config for name-tag concealment, the movement speed modifier and
client-side Emancipation Grill movement. Keep `config/ntmdoors.cfg` identical on the client and
server, including the item lists, `RequireWorn` and the Unity toggles, or a player may see different
name tags, speed or field resistance than the server applies. Server-side Unity also covers armored
mobs, so a mismatched client can predict a different result for those entities.
