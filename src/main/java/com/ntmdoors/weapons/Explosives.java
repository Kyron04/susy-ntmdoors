package com.ntmdoors.weapons;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Explosive detection and triggering that is not tied to one mod. ICBM Classic blocks are
 * triggered through their own static hook; vanilla TNT is primed; other mods are probed for the
 * common trigger method names before a small vanilla explosion is used as a fallback.
 */
public final class Explosives {

    public static boolean isExplosive(World world, BlockPos pos) {
        if (world == null || pos == null) return false;
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        if (block == Blocks.TNT) return true;

        ResourceLocation id = block.getRegistryName();
        if (id != null) {
            String path = id.getPath().toLowerCase(Locale.ROOT);
            if (path.contains("tnt") || path.contains("explosive")
                    || path.contains("bomb") || path.contains("warhead")) {
                return true;
            }
        }

        TileEntity te = world.getTileEntity(pos);
        if (te != null
                && te.getClass().getName().toLowerCase(Locale.ROOT).contains("explosive")) {
            return true;
        }
        return block.getClass().getName().toLowerCase(Locale.ROOT).contains("explosive");
    }

    public static boolean trigger(World world, BlockPos pos) {
        if (world == null || world.isRemote || pos == null) return false;
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        if (block == Blocks.TNT) {
            world.setBlockToAir(pos);
            EntityTNTPrimed tnt = new EntityTNTPrimed(world,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, null);
            tnt.setFuse(80);
            world.spawnEntity(tnt);
            return true;
        }

        // ICBM Classic's BlockExplosive exposes this static trigger.
        if (invokeStatic(block, "triggerExplosive", world, pos, false)) return true;

        // Common per-block trigger names used by other explosive mods.
        for (String name : new String[]{"prime", "trigger", "explode", "detonate", "ignite"}) {
            if (invokeInstance(block, name, world, pos)) return true;
        }

        // Last resort: remove the block and create a small vanilla explosion.
        world.setBlockToAir(pos);
        world.createExplosion(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4F, true);
        return true;
    }

    private static boolean invokeStatic(Block block, String name, World world, BlockPos pos,
                                        boolean fire) {
        try {
            Method method = block.getClass().getMethod(name, World.class, BlockPos.class,
                    boolean.class);
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

    private Explosives() {
    }
}
