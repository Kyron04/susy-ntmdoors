package com.ntmdoors.compat;

import com.ntmdoors.concrete.ConcreteSet;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import team.chisel.api.carving.CarvingUtils;
import team.chisel.api.carving.ICarvingGroup;
import team.chisel.api.carving.ICarvingRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers the concrete building set with the Chisel mod's carving registry.
 *
 * <p>Chisel groups are flat; the shared {@code concrete_} prefix is the only hierarchy Chisel
 * exposes. Cubes, stairs and slabs therefore stay in separate groups so a chisel never silently
 * changes a block's shape.</p>
 *
 * <p>Stairs and slabs are registered for every valid block state, so the chisel recognises them
 * regardless of facing or half. Pillar, rebar, asbestos and Uber Concrete are not registered.</p>
 */
public final class ChiselIntegration {

    private ChiselIntegration() {
    }

    public static void postInit(FMLPostInitializationEvent event) {
        ICarvingRegistry registry = CarvingUtils.getChiselRegistry();
        if (registry == null) return;

        List<IBlockState> states = new ArrayList<>();
        states.add(ConcreteSet.CONCRETE.getDefaultState());
        states.add(ConcreteSet.CONCRETE_SMOOTH.getDefaultState());
        group(registry, "concrete", states);

        states = new ArrayList<>();
        addAllStates(states, ConcreteSet.stairs("concrete_stairs"));
        addAllStates(states, ConcreteSet.stairs("concrete_smooth_stairs"));
        group(registry, "concrete_stairs", states);

        states = new ArrayList<>();
        addAllStates(states, ConcreteSet.slab("concrete_slab"));
        addAllStates(states, ConcreteSet.slab("concrete_smooth_slab"));
        group(registry, "concrete_slabs", states);

        states = new ArrayList<>();
        for (int meta = 0; meta < ConcreteSet.DYES.length; meta++) {
            states.add(ConcreteSet.CONCRETE_COLORED.getStateFromMeta(meta));
        }
        for (int meta = 0; meta < ConcreteSet.EXT_TYPES.length; meta++) {
            states.add(ConcreteSet.CONCRETE_COLORED_EXT.getStateFromMeta(meta));
        }
        group(registry, "concrete_colored", states);

        states = new ArrayList<>();
        for (String dye : ConcreteSet.DYES) {
            addAllStates(states, ConcreteSet.stairs("concrete_colored_stairs_" + dye));
        }
        for (String type : ConcreteSet.EXT_TYPES) {
            addAllStates(states, ConcreteSet.stairs("concrete_colored_ext_stairs_" + type));
        }
        group(registry, "concrete_colored_stairs", states);

        states = new ArrayList<>();
        for (String dye : ConcreteSet.DYES) {
            addAllStates(states, ConcreteSet.slab("concrete_" + dye + "_slab"));
        }
        group(registry, "concrete_colored_slabs", states);

        states = new ArrayList<>();
        states.add(ConcreteSet.BRICK_CONCRETE.getDefaultState());
        states.add(ConcreteSet.BRICK_CONCRETE_MOSSY.getDefaultState());
        states.add(ConcreteSet.BRICK_CONCRETE_CRACKED.getDefaultState());
        states.add(ConcreteSet.BRICK_CONCRETE_BROKEN.getDefaultState());
        group(registry, "concrete_bricks", states);

        states = new ArrayList<>();
        addAllStates(states, ConcreteSet.stairs("brick_concrete_stairs"));
        addAllStates(states, ConcreteSet.stairs("brick_concrete_mossy_stairs"));
        addAllStates(states, ConcreteSet.stairs("brick_concrete_cracked_stairs"));
        addAllStates(states, ConcreteSet.stairs("brick_concrete_broken_stairs"));
        group(registry, "concrete_bricks_stairs", states);

        states = new ArrayList<>();
        addAllStates(states, ConcreteSet.slab("brick_concrete_slab"));
        addAllStates(states, ConcreteSet.slab("brick_concrete_mossy_slab"));
        addAllStates(states, ConcreteSet.slab("brick_concrete_cracked_slab"));
        addAllStates(states, ConcreteSet.slab("brick_concrete_broken_slab"));
        group(registry, "concrete_bricks_slabs", states);
    }

    private static void addAllStates(List<IBlockState> states, Block block) {
        if (block != null) {
            states.addAll(block.getBlockState().getValidStates());
        }
    }

    private static void group(ICarvingRegistry registry, String name, List<IBlockState> states) {
        if (states.isEmpty()) return;
        ICarvingGroup group = registry.getGroup(name);
        if (group == null) {
            group = CarvingUtils.getDefaultGroupFor(name);
            if (group == null) return;
            registry.addGroup(group);
        }
        int order = 0;
        for (IBlockState state : states) {
            registry.addVariation(name, state, order++);
        }
    }
}
