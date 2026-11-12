package com.ntmdoors.weapons;

import com.ntmdoors.block.BlockSpatialDisrupter;
import com.ntmdoors.item.ItemWeaponsRelayKey;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Dead Man's Hand trap enforcement via Fabric API interaction callbacks.
 * A bound explosive detonates when a player without the matching Weapons Relay Key interacts
 * with it or with a block next to it. The key holder can mine it safely.
 */
public final class WeaponsBindingEvents {

    /** Radius around a trap that reacts to block placement and breaking (Chebyshev distance). */
    public static final int TRAP_RADIUS = 1;

    public static void register() {
        // 1. Attack / Punch Block Interception
        AttackBlockCallback.EVENT.register((PlayerEntity player, World world, Hand hand, BlockPos pos, Direction direction) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (triggerAt(world, pos, player)) {
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        // 2. Use / Right-Click Block Interception
        UseBlockCallback.EVENT.register((PlayerEntity player, World world, Hand hand, BlockHitResult hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;

            ItemStack held = player.getStackInHand(hand);
            if (player.isSneaking() && held.getItem() instanceof ItemWeaponsRelayKey) {
                return ActionResult.PASS; // Permit item binding/unbinding logic
            }

            BlockPos pos = hitResult.getBlockPos();
            if (triggerAt(world, pos, player)) {
                return ActionResult.FAIL;
            }

            if (held.getItem() instanceof BlockItem) {
                BlockPos placed = pos.offset(hitResult.getSide());
                if (triggerNearby(world, placed, player)) {
                    return ActionResult.FAIL;
                }
            }
            return ActionResult.PASS;
        });

        // 3. Block Break Interception
        PlayerBlockBreakEvents.BEFORE.register((World world, PlayerEntity player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) -> {
            if (world.isClient()) return true;

            // Spatial Disrupter bedrock protection in survival
            if (state.getBlock() instanceof BlockSpatialDisrupter && !player.isCreative()) {
                return false;
            }

            WeaponsBindings.Binding direct = WeaponsBindings.find(world, pos);
            if (direct != null) {
                if (direct.mode == WeaponsMode.DEAD_MANS_HAND) {
                    if (WeaponsKeyData.hasKey(player, direct.keyId)) {
                        WeaponsBindings.unbind(world, pos);
                        return true;
                    } else {
                        WeaponsBindings.trigger(world, direct);
                        message(player);
                        return false;
                    }
                } else {
                    WeaponsBindings.unbind(world, pos);
                    return true;
                }
            }

            if (triggerNearby(world, pos, player)) {
                return false;
            }

            return true;
        });
    }

    /** Detonates the trap at pos when the player lacks its key. Returns true if it fired. */
    public static boolean triggerAt(World world, BlockPos pos, PlayerEntity player) {
        WeaponsBindings.Binding binding = WeaponsBindings.find(world, pos);
        if (binding == null || binding.mode != WeaponsMode.DEAD_MANS_HAND) return false;
        if (WeaponsKeyData.hasKey(player, binding.keyId)) return false;
        WeaponsBindings.trigger(world, binding);
        message(player);
        return true;
    }

    /** Detonates every Dead Man's Hand trap around pos that the player cannot disarm. */
    public static boolean triggerNearby(World world, BlockPos pos, PlayerEntity player) {
        boolean fired = false;
        for (WeaponsBindings.Binding binding : WeaponsBindings.findNear(world, pos, TRAP_RADIUS, WeaponsMode.DEAD_MANS_HAND)) {
            if (binding.pos.equals(pos)) continue;
            if (WeaponsKeyData.hasKey(player, binding.keyId)) continue;
            WeaponsBindings.trigger(world, binding);
            fired = true;
        }
        if (fired) message(player);
        return fired;
    }

    private static void message(PlayerEntity player) {
        if (player != null) {
            player.sendMessage(Text.translatable("ntmdoors.msg.weapons_trap_triggered"), true);
        }
    }

    private WeaponsBindingEvents() {}
}
