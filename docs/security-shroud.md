# Security Shroud configuration

The Security Shroud and every behavior it shares with other armor sets are tuned in
`config/ntmdoors.cfg`. Forge creates the file on the first launch. Restart the game after editing it;
values are read once during pre-init.

Forge writes category names in lowercase, so the generated file contains `securityshroud` with
nested `items`, `helmet`, `chestplate`, `leggings`, `boots` and `unity` blocks. Property names keep
their capitalisation. The dotted paths below name the property within those nested blocks.

## Defaults

| Category / key | Default | Meaning |
|---|---:|---|
| `securityshroud.Enabled` | `true` | Master switch for all effects. Items and their stats still exist when false. |
| `securityshroud.ShowTooltips` | `true` | Show the built-in tooltips, including durability and damage resistance. |
| `securityshroud.ShowUnityTooltip` | `true` | Show the full-set Unity line. |
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
| `securityshroud.boots.PreventLethalFallDamage` | `true` | Cap fall damage before it can take the player below the configured health. |
| `securityshroud.boots.MinimumHealthAfterFall` | `2.0` | Health left after a prevented lethal fall; `2.0` is one heart. |
| `securityshroud.unity.EnableUnity` | `true` | Enable the full-set effect. |
| `securityshroud.unity.RequireWorn` | `true` | Require all four pieces to be equipped. When false, they may be carried anywhere in the inventory. |
| `securityshroud.unity.IgnoreMotionSensors` | `true` | Full-set players are skipped by Motion Sensors, including Match User. |
| `securityshroud.unity.IgnoreEmancipationGrills` | `true` | Full-set players are skipped by Emancipation Grills in every mode. |

The default protection values total 20 armor points, the vanilla armor cap; higher configured totals
are still subject to that cap. Toughness defaults to `0.0`; raise it if high-damage attacks still
hurt too much. The built-in pieces use `EnumRarity.RARE`, so their item names are aqua. Each built-in
tooltip shows its current/max durability and that piece's armor points, plus the configured full-set
total.

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
are harmless but never match; they are reported in the log at post-init. Use a different item in
each list; the full-set check treats the four lists separately. To use another set only for Unity,
add all four pieces and disable the per-piece toggles (`HideNameTags`, `BypassWeaponsBindings`,
`MovementSpeedBonus` and `PreventLethalFallDamage`); the four item lists still form the full set.

## Full-set behavior

Unity requires one configured item in each of the four armor slots. With `RequireWorn` set to
`false`, the four pieces may instead be anywhere in the player's main inventory, armor slots or
offhand. The pieces do not have to come from the same mod or set; each slot is checked against its
own list.

When both Unity toggles are enabled:

* Motion Sensors do not see the player and do not fire authorized, unauthorized or Match User
  events for them.
* Emancipation Grills ignore the player in every field mode. This includes Solid/Wall clamping,
  Lethal disintegration, Filter inventory scans and Match User redstone signals.

## Multiplayer note

The client reads the same config for name-tag concealment and client-side Emancipation Grill
movement. Keep `config/ntmdoors.cfg` identical on the client and server, or a full-set player may
see name tags or client-side field resistance that the server does not agree with.


