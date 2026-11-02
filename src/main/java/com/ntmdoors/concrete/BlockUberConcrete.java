package com.ntmdoors.concrete;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * "Über Concrete". Ported from NTM CE's {@code BlockUberConcrete}: every random tick it has a
 * chance to advance one of sixteen damage stages; the last stage crumbles into falling
 * {@link BlockConcreteDebris}, preferring an adjacent block that can drop away.
 */
public class BlockUberConcrete extends BlockConcreteVariant {

    private static final String[] MODELS = new String[16];
    private static final String[] NAMES = new String[16];

    static {
        for (int meta = 0; meta < 16; meta++) {
            NAMES[meta] = "concrete_super";
            if (meta <= 9) {
                MODELS[meta] = "concrete_super";
            } else if (meta <= 11) {
                MODELS[meta] = "concrete_super_m0";
            } else if (meta <= 13) {
                MODELS[meta] = "concrete_super_m1";
            } else if (meta == 14) {
                MODELS[meta] = "concrete_super_m2";
            } else {
                MODELS[meta] = "concrete_super_m3";
            }
        }
    }

    public BlockUberConcrete() {
        super("concrete_super", NAMES, MODELS, false, 150F, 1000F);
        setTickRandomly(true);
    }

    @Override
    public void updateTick(World world, BlockPos pos, IBlockState state, Random rand) {
        int meta = state.getValue(META);

        // Stage 0 always advances; higher stages get progressively slower.
        if (rand.nextInt(meta + 1) > 0) return;

        if (meta < 15) {
            world.setBlockState(pos, state.withProperty(META, meta + 1), 3);
            return;
        }

        world.setBlockToAir(pos);

        // If the block can fall, leave the debris in place so it drops like sand.
        if (world.isAirBlock(pos.down())) {
            world.setBlockState(pos, ConcreteSet.CONCRETE_SUPER_BROKEN.getDefaultState());
            return;
        }

        List<EnumFacing> sides = Arrays.asList(
                EnumFacing.EAST, EnumFacing.WEST, EnumFacing.SOUTH, EnumFacing.NORTH);
        Collections.shuffle(sides);

        for (EnumFacing dir : sides) {
            BlockPos target = pos.offset(dir);
            if (world.isAirBlock(target) && world.isAirBlock(target.down())) {
                EntityFallingBlock debris = new EntityFallingBlock(world,
                        target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5,
                        ConcreteSet.CONCRETE_SUPER_BROKEN.getDefaultState());
                debris.fallTime = 2;
                debris.setHurtEntities(false);
                debris.setDropItemsWhenDead(true);
                world.spawnEntity(debris);
                return;
            }
        }

        world.setBlockState(pos, ConcreteSet.CONCRETE_SUPER_BROKEN.getDefaultState());
    }
}
