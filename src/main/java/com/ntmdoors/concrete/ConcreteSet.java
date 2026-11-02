package com.ntmdoors.concrete;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.item.ItemBlockConcreteVariant;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.event.RegistryEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The NTM concrete building set. Cubes and slabs are grouped by chisel family into metadata
 * variants to keep the block id count down; stairs stay one id per variant because
 * {@code BlockStairs} already spends its four metadata bits on facing and half.
 */
public final class ConcreteSet {

    public static final String[] DYES = {
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "silver", "cyan", "purple", "blue", "brown", "green", "red", "black"
    };

    public static final String[] EXT_TYPES = {
            "machine", "machine_stripe", "indigo", "purple", "pink", "hazard", "sand", "bronze",
            "hazard_red", "hazard_green", "hazard_blue", "hazard_white", "hazard_purple"
    };

    public static final String[] BRICK_TYPES = {
            "brick_concrete", "brick_concrete_mossy", "brick_concrete_cracked", "brick_concrete_broken"
    };

    /** A metadata slab family: up to eight variants plus its technical double block. */
    public static final class SlabGroup {
        public final String name;
        public final String[] variantNames;
        public final String[] itemModels;
        public final Block single;
        public final Block doubled;

        private SlabGroup(String name, String[] variantNames, String[] itemModels,
                          Block single, Block doubled) {
            this.name = name;
            this.variantNames = variantNames;
            this.itemModels = itemModels;
            this.single = single;
            this.doubled = doubled;
        }
    }

    /** A stair block and the cube variant its recipe uses. */
    public static final class StairSpec {
        public final Block stairs;
        public final Block base;
        public final int baseMeta;

        private StairSpec(Block stairs, Block base, int baseMeta) {
            this.stairs = stairs;
            this.base = base;
            this.baseMeta = baseMeta;
        }
    }

    private static final List<Block> BLOCKS = new ArrayList<>();
    private static final List<Item> ITEMS = new ArrayList<>();
    private static final List<ItemStack> CREATIVE = new ArrayList<>();
    private static final Map<String, Block> STAIRS = new LinkedHashMap<>();
    private static final List<StairSpec> STAIR_SPECS = new ArrayList<>();
    private static final List<SlabGroup> SLAB_GROUPS = new ArrayList<>();
    private static final Map<String, SlabGroup> SLAB_BY_NAME = new LinkedHashMap<>();

    // --- grouped cubes -----------------------------------------------------------------------
    public static final BlockConcreteVariant CONCRETE = new BlockConcreteVariant(
            "concrete", new String[]{"concrete", "concrete_smooth"},
            new String[]{"concrete", "concrete_smooth"}, true, 15F, 140F);
    public static final BlockConcreteVariant CONCRETE_COLORED = new BlockConcreteVariant(
            "concrete_colored", DYES, prefixed("concrete_", DYES), true, 15F, 140F);
    public static final BlockConcreteVariant CONCRETE_COLORED_EXT = new BlockConcreteVariant(
            "concrete_colored_ext", EXT_TYPES, prefixed("concrete_colored_ext_", EXT_TYPES),
            true, 15F, 140F);
    public static final BlockConcreteVariant CONCRETE_BRICKS = new BlockConcreteVariant(
            "concrete_bricks", BRICK_TYPES, BRICK_TYPES, true, 15F, 160F);

    // --- standalone concrete blocks ----------------------------------------------------------
    public static final BlockConcretePillar CONCRETE_PILLAR = new BlockConcretePillar("concrete_pillar");
    public static final BlockConcreteVariant CONCRETE_REBAR = cube("concrete_rebar", 50F, 240F);
    public static final BlockConcreteVariant CONCRETE_ASBESTOS = cube("concrete_asbestos", 15F, 1500F);
    public static final BlockConcreteDebris CONCRETE_SUPER_BROKEN =
            new BlockConcreteDebris("concrete_super_broken");
    public static final BlockUberConcrete CONCRETE_SUPER = new BlockUberConcrete();

    static {
        addCube(CONCRETE);
        addCube(CONCRETE_COLORED);
        addCube(CONCRETE_COLORED_EXT);
        addCube(CONCRETE_BRICKS);
        addBlock(CONCRETE_PILLAR);
        addCube(CONCRETE_REBAR);
        addCube(CONCRETE_ASBESTOS);
        addCube(CONCRETE_SUPER);
        addBlock(CONCRETE_SUPER_BROKEN);

        // Plain stairs.
        addStairs(CONCRETE, 0, "concrete_stairs", 15F, 94F);
        addStairs(CONCRETE, 1, "concrete_smooth_stairs", 15F, 94F);
        addStairs(CONCRETE_ASBESTOS, 0, "concrete_asbestos_stairs", 15F, 94F);
        // Brick stairs.
        addStairs(CONCRETE_BRICKS, 0, "brick_concrete_stairs", 15F, 95F);
        addStairs(CONCRETE_BRICKS, 1, "brick_concrete_mossy_stairs", 15F, 94F);
        addStairs(CONCRETE_BRICKS, 2, "brick_concrete_cracked_stairs", 15F, 40F);
        addStairs(CONCRETE_BRICKS, 3, "brick_concrete_broken_stairs", 15F, 30F);
        // Coloured stairs.
        for (int meta = 0; meta < DYES.length; meta++) {
            addStairs(CONCRETE_COLORED, meta, "concrete_colored_stairs_" + DYES[meta], 15F, 94F);
        }
        // Builder's Choice stairs.
        for (int meta = 0; meta < EXT_TYPES.length; meta++) {
            addStairs(CONCRETE_COLORED_EXT, meta,
                    "concrete_colored_ext_stairs_" + EXT_TYPES[meta], 15F, 94F);
        }
    }

    // Slab fields are initialized after the static block so cubes and stairs keep the earlier
    // numeric ids and creative-tab order; slabGroup() registers the slabs as a side effect.
    public static final SlabGroup CONCRETE_SLABS = slabGroup(
            "concrete_slabs", new String[]{"concrete", "concrete_smooth"},
            new String[]{"concrete_slab", "concrete_smooth_slab"});
    public static final SlabGroup CONCRETE_COLORED_SLABS = slabGroup(
            "concrete_colored_slabs", slice(DYES, 0, 8), dyeSlabModels(0, 8));
    public static final SlabGroup CONCRETE_COLORED_SLABS_2 = slabGroup(
            "concrete_colored_slabs_2", slice(DYES, 8, 16), dyeSlabModels(8, 16));
    public static final SlabGroup CONCRETE_COLORED_EXT_SLABS = slabGroup(
            "concrete_colored_ext_slabs", hazardTypes(), hazardSlabModels());
    public static final SlabGroup CONCRETE_BRICKS_SLABS = slabGroup(
            "concrete_bricks_slabs", BRICK_TYPES, prefixed("", BRICK_TYPES, "_slab"));
    public static final SlabGroup CONCRETE_ASBESTOS_SLABS = slabGroup(
            "concrete_asbestos_slab", new String[]{"concrete_asbestos"},
            new String[]{"concrete_asbestos_slab"});

    private ConcreteSet() {
    }

    public static void registerBlocks(RegistryEvent.Register<Block> e) {
        for (Block block : BLOCKS) e.getRegistry().register(block);
    }

    public static void registerItems(RegistryEvent.Register<Item> e) {
        for (Item item : ITEMS) e.getRegistry().register(item);
    }

    /** Adds every concrete cube, stair and single-slab stack to the main creative tab. */
    public static void addCreativeStacks(NonNullList<ItemStack> items) {
        items.addAll(CREATIVE);
    }

    public static Block stairs(String name) {
        return STAIRS.get(name);
    }

    public static SlabGroup slabGroup(String name) {
        return SLAB_BY_NAME.get(name);
    }

    public static boolean isHazard(String type) {
        return "hazard".equals(type) || type.startsWith("hazard_");
    }

    public static Collection<Block> allStairs() {
        return Collections.unmodifiableCollection(STAIRS.values());
    }

    public static List<StairSpec> stairSpecs() {
        return Collections.unmodifiableList(STAIR_SPECS);
    }

    public static List<SlabGroup> slabGroups() {
        return Collections.unmodifiableList(SLAB_GROUPS);
    }

    public static Collection<Block> allSlabs() {
        List<Block> result = new ArrayList<>();
        for (SlabGroup group : SLAB_GROUPS) result.add(group.single);
        return Collections.unmodifiableList(result);
    }

    private static BlockConcreteVariant cube(String name, float hardness, float resistance) {
        return new BlockConcreteVariant(name, new String[]{name}, new String[]{name},
                false, hardness, resistance);
    }

    private static void addBlock(Block block) {
        BLOCKS.add(block);
        ItemBlock item = new ItemBlock(block);
        item.setRegistryName(block.getRegistryName());
        ITEMS.add(item);
        CREATIVE.add(new ItemStack(item));
    }

    private static void addCube(BlockConcreteVariant block) {
        BLOCKS.add(block);
        ItemBlockConcreteVariant item = new ItemBlockConcreteVariant(block);
        item.setRegistryName(block.getRegistryName());
        ITEMS.add(item);
        for (int meta = 0; meta < block.creativeVariantCount(); meta++) {
            CREATIVE.add(new ItemStack(item, 1, meta));
        }
    }

    private static void addStairs(Block base, int baseMeta, String name,
                                  float hardness, float resistance) {
        BlockConcreteStairs block = new BlockConcreteStairs(base, name, hardness, resistance);
        BLOCKS.add(block);
        STAIRS.put(name, block);
        STAIR_SPECS.add(new StairSpec(block, base, baseMeta));
        ItemBlock item = new ItemBlock(block);
        item.setRegistryName(block.getRegistryName());
        ITEMS.add(item);
        CREATIVE.add(new ItemStack(item));
    }

    private static SlabGroup slabGroup(String name, String[] variantNames, String[] itemModels) {
        BlockConcreteSlab single = new BlockConcreteSlab(name, 15F, 70F);
        BlockConcreteSlab doubled = new BlockConcreteSlab.Double(name + "_double_slab", single,
                15F, 70F);
        ItemConcreteSlab item = new ItemConcreteSlab(single, doubled, variantNames, itemModels);
        item.setRegistryName(single.getRegistryName());
        BLOCKS.add(single);
        BLOCKS.add(doubled);
        ITEMS.add(item);
        for (int meta = 0; meta < variantNames.length; meta++) {
            CREATIVE.add(new ItemStack(item, 1, meta));
        }
        SlabGroup group = new SlabGroup(name, variantNames, itemModels, single, doubled);
        SLAB_GROUPS.add(group);
        SLAB_BY_NAME.put(name, group);
        return group;
    }

    private static String[] hazardTypes() {
        List<String> types = new ArrayList<>();
        for (String type : EXT_TYPES) {
            if ("hazard".equals(type) || type.startsWith("hazard_")) types.add(type);
        }
        return types.toArray(new String[0]);
    }

    private static String[] hazardSlabModels() {
        String[] types = hazardTypes();
        String[] models = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            models[i] = "concrete_colored_ext_slab_" + types[i];
        }
        return models;
    }

    private static String[] dyeSlabModels(int from, int to) {
        String[] models = new String[to - from];
        for (int i = from; i < to; i++) models[i - from] = "concrete_" + DYES[i] + "_slab";
        return models;
    }

    private static String[] slice(String[] source, int from, int to) {
        String[] result = new String[to - from];
        System.arraycopy(source, from, result, 0, result.length);
        return result;
    }

    private static String[] prefixed(String prefix, String[] names) {
        return prefixed(prefix, names, "");
    }

    private static String[] prefixed(String prefix, String[] names, String suffix) {
        String[] result = new String[names.length];
        for (int i = 0; i < names.length; i++) result[i] = prefix + names[i] + suffix;
        return result;
    }
}
