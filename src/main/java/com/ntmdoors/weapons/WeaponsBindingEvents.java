// SPDX-License-Identifier: LGPL-3.0-only
package com.ntmdoors.weapons;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.armor.SecurityShroudLogic;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.UUID;

/**
 * Dead Man's Hand trap enforcement.
 *
 * <p>A bound explosive detonates when a player without the matching Weapons Relay Key interacts
 * with it or with a block next to it. The key holder can mine it safely; the break event removes
 * the binding instead of detonating it. A configured Security Shroud chestplate ignores the
 * binding in the same way, so the player can disarm or pass the trap without carrying the key.
 * Regular bindings have no trap behaviour.</p>
 */
@Mod.EventBusSubscriber(modid = NTMDoors.MODID)
public final class WeaponsBindingEvents {

    /** Radius around a trap that reacts to block placement and breaking. */
    private static final int TRAP_RADIUS = 1;

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getWorld().isRemote) return;
        World world = event.getWorld();
        EntityPlayer player = event.getPlayer();

        WeaponsBindings.Binding direct = WeaponsBindings.find(world, event.getPos());
        if (direct != null) {
            if (direct.mode == WeaponsMode.DEAD_MANS_HAND) {
                if (hasAccess(player, direct.keyId)) {
                    // The key holder or a Shroud chestplate wearer can remove the trap safely.
                    WeaponsBindings.unbind(world, event.getPos());
                } else {
                    event.setCanceled(true);
                    WeaponsBindings.trigger(world, direct);
                    message(player);
                }
            } else {
                // Regular bindings allow a normal break; drop the record with the block.
                WeaponsBindings.unbind(world, event.getPos());
            }
            return;
        }

        // Breaking a block next to a trap sets it off.
        if (triggerNearby(world, event.getPos(), player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.PlaceEvent event) {
        if (event.getWorld().isRemote) return;
        if (triggerNearby(event.getWorld(), event.getPos(), event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getWorld().isRemote) return;
        if (triggerAt(event.getWorld(), event.getPos(), event.getEntityPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getWorld().isRemote) return;
        if (triggerAt(event.getWorld(), event.getPos(), event.getEntityPlayer())) {
            event.setCanceled(true);
        }
    }

    /** Detonates the trap at {@code pos} when the player lacks its key. Returns true if it fired. */
    private static boolean triggerAt(World world, BlockPos pos, EntityPlayer player) {
        WeaponsBindings.Binding binding = WeaponsBindings.find(world, pos);
        if (binding == null || binding.mode != WeaponsMode.DEAD_MANS_HAND) return false;
        if (hasAccess(player, binding.keyId)) return false;
        WeaponsBindings.trigger(world, binding);
        message(player);
        return true;
    }

    /** Detonates every Dead Man's Hand trap around {@code pos} that the player cannot disarm. */
    private static boolean triggerNearby(World world, BlockPos pos, EntityPlayer player) {
        boolean fired = false;
        for (WeaponsBindings.Binding binding :
                WeaponsBindings.findNear(world, pos, TRAP_RADIUS, WeaponsMode.DEAD_MANS_HAND)) {
            if (binding.pos.equals(pos)) continue;
            if (hasAccess(player, binding.keyId)) continue;
            WeaponsBindings.trigger(world, binding);
            fired = true;
        }
        if (fired) message(player);
        return fired;
    }

    /** True when the player holds the matching key or wears a configured Security Shroud chestplate. */
    private static boolean hasAccess(EntityPlayer player, UUID keyId) {
        return WeaponsKeyData.hasKey(player, keyId)
                || SecurityShroudLogic.bypassesWeaponsBindings(player);
    }

    private static void message(EntityPlayer player) {
        player.sendStatusMessage(new TextComponentTranslation(
                "ntmdoors.msg.weapons_trap_triggered"), true);
    }

    private WeaponsBindingEvents() {
    }
}
