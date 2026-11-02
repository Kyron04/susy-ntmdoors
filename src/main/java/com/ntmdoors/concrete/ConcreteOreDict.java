package com.ntmdoors.concrete;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

/**
 * Registers the concrete set in the common ore dictionaries from Java, without a Groovy script.
 *
 * <p>Every cube, stair and single slab is added to {@code concrete}. The full cubes are also added
 * to {@code blockConcrete}, which is the name the pack's existing recipes use for a full concrete
 * block. Uber Concrete, its Moldy Debris and the technical double-slab blocks are left out.</p>
 */
public final class ConcreteOreDict {

    public static final String CONCRETE = "concrete";
    public static final String BLOCK_CONCRETE = "blockConcrete";

    private ConcreteOreDict() {
    }

    public static void register() {
        cube(ConcreteSet.CONCRETE);
        cube(ConcreteSet.CONCRETE_SMOOTH);
        cube(ConcreteSet.CONCRETE_PILLAR);
        cube(ConcreteSet.CONCRETE_REBAR);
        cube(ConcreteSet.CONCRETE_ASBESTOS);
        for (int meta = 0; meta < ConcreteSet.DYES.length; meta++) {
            cube(ConcreteSet.CONCRETE_COLORED, meta);
        }
        for (int meta = 0; meta < ConcreteSet.EXT_TYPES.length; meta++) {
            cube(ConcreteSet.CONCRETE_COLORED_EXT, meta);
        }
        cube(ConcreteSet.BRICK_CONCRETE);
        cube(ConcreteSet.BRICK_CONCRETE_MOSSY);
        cube(ConcreteSet.BRICK_CONCRETE_CRACKED);
        cube(ConcreteSet.BRICK_CONCRETE_BROKEN);

        // Stairs and slabs are concrete, but they are not full blocks: generic dictionary only.
        for (Block stairs : ConcreteSet.allStairs()) {
            ore(stairs);
        }
        for (Block slab : ConcreteSet.allSlabs()) {
            ore(slab);
        }
    }

    private static void cube(Block block) {
        cube(block, 0);
    }

    private static void cube(Block block, int meta) {
        ItemStack stack = new ItemStack(block, 1, meta);
        OreDictionary.registerOre(CONCRETE, stack);
        OreDictionary.registerOre(BLOCK_CONCRETE, stack);
    }

    private static void ore(Block block) {
        OreDictionary.registerOre(CONCRETE, new ItemStack(block, 1, 0));
    }
}
