package com.ntmdoors.weapons;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Explosive detection and triggering for Fabric 1.20.1.
 * Supports vanilla TNT, probes common modded trigger method names, and provides a createExplosion fallback.
 */
public final class Explosives {

    public static boolean isExplosive(World world, BlockPos pos) {
        if (world == null || pos == null) return false;
        BlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        if (block == Blocks.TNT) return true;

        Identifier id = Registries.BLOCK.getId(block);
        if (id != null) {
            String path = id.getPath().toLowerCase(Locale.ROOT);
            if (path.contains("tnt") || path.contains("explosive")
                    || path.contains("bomb") || path.contains("warhead")) {
                return true;
            }
        }

        BlockEntity be = world.getBlockEntity(pos);
        if (be != null && be.getClass().getName().toLowerCase(Locale.ROOT).contains("explosive")) {
            return true;
        }
        return block.getClass().getName().toLowerCase(Locale.ROOT).contains("explosive");
    }

    public static boolean trigger(World world, BlockPos pos) {
        if (world == null || world.isClient() || pos == null) return false;
        BlockState state = world.getBlockState(pos);
        Block block = state.getBlock();

        if (block == Blocks.TNT) {
            world.removeBlock(pos, false);
            TntEntity tnt = new TntEntity(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, null);
            tnt.setFuse(80);
            world.spawnEntity(tnt);
            world.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    SoundEvents.ENTITY_TNT_PRIMED, SoundCategory.BLOCKS, 1.0F, 1.0F);
            return true;
        }

        // Static trigger probe
        if (invokeStatic(block, "triggerExplosive", world, pos, false)) return true;

        // Instance trigger probe
        for (String name : new String[]{"prime", "trigger", "explode", "detonate", "ignite"}) {
            if (invokeInstance(block, name, world, pos)) return true;
        }

        // Fallback: remove the block and create TNT explosion
        world.removeBlock(pos, false);
        world.createExplosion(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4.0F, World.ExplosionSourceType.TNT);
        return true;
    }

    private static boolean invokeStatic(Block block, String name, World world, BlockPos pos, boolean fire) {
        try {
            Method method = block.getClass().getMethod(name, World.class, BlockPos.class, boolean.class);
            method.setAccessible(true);
            method.invoke(null, world, pos, fire);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean invokeInstance(Block block, String name, World world, BlockPos pos) {
        try {
            Method method = block.getClass().getMethod(name, World.class, BlockPos.class);
            method.setAccessible(true);
            method.invoke(block, world, pos);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private Explosives() {}
}
