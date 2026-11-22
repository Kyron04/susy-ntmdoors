# NTM Doors 1.0.81 - CurseForge Release

Minecraft 1.12.2 / Forge 14.23.5.2847+ (tested on 14.23.5.2860)
Requires Chisel 1.12.2-1.0.2.45

**File:** `ntmdoors-1.0.81.jar`
**SHA-256:** `B01C27E61552B5C306B67CCDCD8026AF71BEDFE0262BE7DF3665BE9FEE620242`

This release adds Contraband Containment to the Emancipation Grill. In Filter Mode, blacklisted
stacks from player inventories, nested containers and mob inventories can be inserted into a
container bound to the Grill instead of being destroyed.

## Highlights

- New sixth Grill console page with a Contraband Containment toggle, container status and a Begin
  Binding Session button.
- Binding workflow: start at the Grill, let the server move the Emancipation Relay Key to the main
  hand, sneak + right-click a compatible container within 8 chunks in the same dimension, then
  return to the original Grill and sneak + right-click it to confirm.
- The final binding is stored on the Grill's security core and survives world saves. The key only
  carries the pending five-minute session.
- Containers use Forge's `IItemHandler` capability. Vanilla chests, hoppers and furnaces work;
  automation-blocked containers, locked containers and blocks without an item handler are rejected.
- Player main inventory, armor, offhand, nested container items, mob equipment and mob inventories
  are routed through the bound container.
- Items are inserted up to the container's capacity. When no valid container is bound, or the
  container is full, missing, unloaded, out of range or in another dimension, the remainder is
  voided. Loose dropped items keep the normal Filter Mode void behavior.

## Compatibility

- Existing 1.0.80 worlds load normally. The containment toggle defaults off and no container is
  bound until the player confirms the binding.
- Clients and servers must both update for the new binding packets.
- Chisel 1.12.2-1.0.2.45 remains required. No NTM installation is required.
- JEI or HEI remains optional; the filter overlay loads only when one is present.
