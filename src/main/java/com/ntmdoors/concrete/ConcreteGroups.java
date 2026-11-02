package com.ntmdoors.concrete;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The shared concrete families used by both the Chisel integration and the shift-scroll cycling.
 *
 * <p>Each group keeps two parallel views: one representative item per variant for cycling, and the
 * full block-state list for Chisel. Stairs and slabs contribute every valid state to the latter so
 * a chisel can recognise them regardless of facing or half.</p>
 *
 * <p>Groups stay flat and shape-specific: cubes never mix with stairs or slabs. The names match the
 * Chisel carving groups.</p>
 */
public final class ConcreteGroups {

    public static final class Group {
        private final String name;
        private final List<ItemStack> items;
        private final List<IBlockState> states;

        private Group(String name, List<ItemStack> items, List<IBlockState> states) {
            this.name = name;
            this.items = Collections.unmodifiableList(items);
            this.states = Collections.unmodifiableList(states);
        }

        public String name() {
            return name;
        }

        public List<ItemStack> items() {
            return items;
        }

        public List<IBlockState> states() {
            return states;
        }

        public int size() {
            return items.size();
        }
    }

    private static final class Match {
        private final Group group;
        private final int index;

        private Match(Group group, int index) {
            this.group = group;
            this.index = index;
        }
    }

    private static final List<Group> GROUPS = new ArrayList<>();
    private static final Map<String, Group> BY_KEY = new LinkedHashMap<>();

    static {
        List<ItemStack> items = new ArrayList<>();
        List<IBlockState> states = new ArrayList<>();
        cube(items, states, ConcreteSet.CONCRETE, 0);
        cube(items, states, ConcreteSet.CONCRETE_SMOOTH, 0);
        addGroup("concrete", items, states);

        items = new ArrayList<>();
        states = new ArrayList<>();
        stairs(items, states, ConcreteSet.stairs("concrete_stairs"));
        stairs(items, states, ConcreteSet.stairs("concrete_smooth_stairs"));
        addGroup("concrete_stairs", items, states);

        items = new ArrayList<>();
        states = new ArrayList<>();
        slab(items, states, ConcreteSet.slab("concrete_slab"));
        slab(items, states, ConcreteSet.slab("concrete_smooth_slab"));
        addGroup("concrete_slabs", items, states);

        items = new ArrayList<>();
        states = new ArrayList<>();
        for (int meta = 0; meta < ConcreteSet.DYES.length; meta++) {
            cube(items, states, ConcreteSet.CONCRETE_COLORED, meta);
        }
        for (int meta = 0; meta < ConcreteSet.EXT_TYPES.length; meta++) {
            cube(items, states, ConcreteSet.CONCRETE_COLORED_EXT, meta);
        }
        addGroup("concrete_colored", items, states);

        items = new ArrayList<>();
        states = new ArrayList<>();
        for (String dye : ConcreteSet.DYES) {
            stairs(items, states, ConcreteSet.stairs("concrete_colored_stairs_" + dye));
        }
        for (String type : ConcreteSet.EXT_TYPES) {
            stairs(items, states, ConcreteSet.stairs("concrete_colored_ext_stairs_" + type));
        }
        addGroup("concrete_colored_stairs", items, states);

        items = new ArrayList<>();
        states = new ArrayList<>();
        for (String dye : ConcreteSet.DYES) {
            slab(items, states, ConcreteSet.slab("concrete_" + dye + "_slab"));
        }
        addGroup("concrete_colored_slabs", items, states);

        items = new ArrayList<>();
        states = new ArrayList<>();
        cube(items, states, ConcreteSet.BRICK_CONCRETE, 0);
        cube(items, states, ConcreteSet.BRICK_CONCRETE_MOSSY, 0);
        cube(items, states, ConcreteSet.BRICK_CONCRETE_CRACKED, 0);
        cube(items, states, ConcreteSet.BRICK_CONCRETE_BROKEN, 0);
        addGroup("concrete_bricks", items, states);

        items = new ArrayList<>();
        states = new ArrayList<>();
        stairs(items, states, ConcreteSet.stairs("brick_concrete_stairs"));
        stairs(items, states, ConcreteSet.stairs("brick_concrete_mossy_stairs"));
        stairs(items, states, ConcreteSet.stairs("brick_concrete_cracked_stairs"));
        stairs(items, states, ConcreteSet.stairs("brick_concrete_broken_stairs"));
        addGroup("concrete_bricks_stairs", items, states);

        items = new ArrayList<>();
        states = new ArrayList<>();
        slab(items, states, ConcreteSet.slab("brick_concrete_slab"));
        slab(items, states, ConcreteSet.slab("brick_concrete_mossy_slab"));
        slab(items, states, ConcreteSet.slab("brick_concrete_cracked_slab"));
        slab(items, states, ConcreteSet.slab("brick_concrete_broken_slab"));
        addGroup("concrete_bricks_slabs", items, states);
    }

    private ConcreteGroups() {
    }

    public static List<Group> all() {
        return Collections.unmodifiableList(GROUPS);
    }

    /** True when the stack has a group with more than one variant. */
    public static boolean isCycleable(ItemStack stack) {
        Match match = match(stack);
        return match != null && match.group.size() > 1;
    }

    /**
     * The next stack in the held item's group, preserving the stack size, or null when the stack is
     * not part of a multi-variant group.
     */
    public static ItemStack next(ItemStack current, int offset) {
        Match match = match(current);
        if (match == null || match.group.size() <= 1) return null;
        ItemStack next = match.group.items.get(
                Math.floorMod(match.index + offset, match.group.size())).copy();
        next.setCount(current.getCount());
        return next;
    }

    private static Match match(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        ResourceLocation id = stack.getItem().getRegistryName();
        if (id == null) return null;
        Group group = BY_KEY.get(id + ":" + stack.getMetadata());
        if (group == null) return null;
        for (int i = 0; i < group.items.size(); i++) {
            ItemStack item = group.items.get(i);
            if (item.getItem() == stack.getItem() && item.getMetadata() == stack.getMetadata()) {
                return new Match(group, i);
            }
        }
        return null;
    }

    private static void cube(List<ItemStack> items, List<IBlockState> states, Block block, int meta) {
        items.add(new ItemStack(block, 1, meta));
        states.add(block.getStateFromMeta(meta));
    }

    private static void stairs(List<ItemStack> items, List<IBlockState> states, Block block) {
        if (block == null) throw new IllegalStateException("Missing concrete stairs");
        items.add(new ItemStack(block));
        states.addAll(block.getBlockState().getValidStates());
    }

    private static void slab(List<ItemStack> items, List<IBlockState> states, Block block) {
        if (block == null) throw new IllegalStateException("Missing concrete slab");
        items.add(new ItemStack(block));
        states.addAll(block.getBlockState().getValidStates());
    }

    private static void addGroup(String name, List<ItemStack> items, List<IBlockState> states) {
        if (items.isEmpty()) throw new IllegalStateException("Empty concrete group " + name);
        Group group = new Group(name, items, states);
        GROUPS.add(group);
        for (ItemStack item : items) {
            ResourceLocation id = item.getItem().getRegistryName();
            if (id != null) BY_KEY.put(id + ":" + item.getMetadata(), group);
        }
    }
}
