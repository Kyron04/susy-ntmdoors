package com.ntmdoors.concrete;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

/**
 * Registers the concrete set in the common ore dictionaries from Java, without a Groovy script.
 * Full cubes go into {@code concrete} and {@code blockConcrete}; stairs and slabs only into
 * {@code concrete}.
 */
public final class ConcreteOreDict {

    public static final String CONCRETE = "concrete";
    public static final String BLOCK_CONCRETE = "blockConcrete";

    private ConcreteOreDict() {
    }

    public static void register() {
        cube(ConcreteSet.CONCRETE, 2);
        cube(ConcreteSet.CONCRETE_COLORED, ConcreteSet.DYES.length);
        cube(ConcreteSet.CONCRETE_COLORED_EXT, ConcreteSet.EXT_TYPES.length);
        cube(ConcreteSet.CONCRETE_BRICKS, ConcreteSet.BRICK_TYPES.length);
        cube(ConcreteSet.CONCRETE_PILLAR, 1);
        cube(ConcreteSet.CONCRETE_REBAR, 1);
        cube(ConcreteSet.CONCRETE_ASBESTOS, 1);
        cube(ConcreteSet.ULTRA_HIGH_PERFORMANCE_CONCRETE, 6);
        cube(ConcreteSet.ULTRA_HIGH_PERFORMANCE_CONCRETE_PANEL, 6);

        for (Block stairs : ConcreteSet.allStairs()) {
            OreDictionary.registerOre(CONCRETE, new ItemStack(stairs));
        }
        for (ConcreteSet.SlabGroup group : ConcreteSet.slabGroups()) {
            for (int meta = 0; meta < group.variantNames.length; meta++) {
                OreDictionary.registerOre(CONCRETE, new ItemStack(group.single, 1, meta));
            }
        }
    }

    private static void cube(Block block, int count) {
        for (int meta = 0; meta < count; meta++) {
            ItemStack stack = new ItemStack(block, 1, meta);
            OreDictionary.registerOre(CONCRETE, stack);
            OreDictionary.registerOre(BLOCK_CONCRETE, stack);
        }
    }
}
