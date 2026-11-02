package com.ntmdoors.concrete;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.item.ItemBlockConcreteVariant;
import com.ntmdoors.util.Names;
import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemSlab;
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
 * The NTM concrete building set: the block definitions, their items and their creative order.
 *
 * <p>Names, metadata order and material values follow HBM's Nuclear Tech CE so the same block
 * paths, textures and structure data map one-to-one. The Java implementation is a fresh 1.12.2
 * port; see NOTICE for the asset attribution.</p>
 */
public final class ConcreteSet {

    /** Vanilla dye metadata order, used by the coloured concrete and its stairs/slabs. */
    public static final String[] DYES = {
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "silver", "cyan", "purple", "blue", "brown", "green", "red", "black"
    };

    /** Builder's Choice order: CE's eight types, then the hazard colour variants. */
    public static final String[] EXT_TYPES = {
            "machine", "machine_stripe", "indigo", "purple", "pink", "hazard", "sand", "bronze",
            "hazard_red", "hazard_green", "hazard_blue", "hazard_white", "hazard_purple"
    };

    public static final CreativeTabs TAB = new CreativeTabs(NTMDoors.MODID + ".concrete") {
        @Override
        public ItemStack createIcon() {
            return new ItemStack(Item.getItemFromBlock(CONCRETE));
        }

        @Override
        public String getTranslationKey() {
            String key = "itemGroup." + getTabLabel();
            return Names.canTranslate(key) ? key : "NTM Concrete";
        }

        @Override
        public void displayAllRelevantItems(NonNullList<ItemStack> items) {
            items.addAll(CREATIVE);
        }
    };

    private static final List<Block> BLOCKS = new ArrayList<>();
    private static final List<Item> ITEMS = new ArrayList<>();
    private static final List<ItemStack> CREATIVE = new ArrayList<>();
    /** Stairs and single slabs by registry path, so the recipe table can reach them without 53 fields. */
    private static final Map<String, Block> STAIRS = new LinkedHashMap<>();
    private static final Map<String, Block> SLABS = new LinkedHashMap<>();

    // --- plain cubes -------------------------------------------------------------------------
    public static final BlockConcreteVariant CONCRETE = cube("concrete", 15F, 140F);
    public static final BlockConcreteVariant CONCRETE_SMOOTH = cube("concrete_smooth", 15F, 140F);
    public static final BlockConcretePillar CONCRETE_PILLAR = new BlockConcretePillar("concrete_pillar");
    public static final BlockConcreteVariant CONCRETE_REBAR = cube("concrete_rebar", 50F, 240F);
    public static final BlockConcreteVariant CONCRETE_ASBESTOS = cube("concrete_asbestos", 15F, 1500F);

    // --- variant cubes -----------------------------------------------------------------------
    public static final BlockConcreteVariant CONCRETE_COLORED = new BlockConcreteVariant(
            "concrete_colored", DYES, prefixed("concrete_", DYES), true, 15F, 140F);
    public static final BlockConcreteVariant CONCRETE_COLORED_EXT = new BlockConcreteVariant(
            "concrete_colored_ext", EXT_TYPES, prefixed("concrete_colored_ext_", EXT_TYPES),
            true, 15F, 140F);

    public static final BlockConcreteDebris CONCRETE_SUPER_BROKEN =
            new BlockConcreteDebris("concrete_super_broken");
    public static final BlockUberConcrete CONCRETE_SUPER = new BlockUberConcrete();

    // --- concrete bricks ---------------------------------------------------------------------
    public static final BlockConcreteVariant BRICK_CONCRETE = cube("brick_concrete", 15F, 160F);
    public static final BlockConcreteVariant BRICK_CONCRETE_MOSSY = cube("brick_concrete_mossy", 15F, 160F);
    public static final BlockConcreteVariant BRICK_CONCRETE_CRACKED = cube("brick_concrete_cracked", 15F, 60F);
    public static final BlockConcreteVariant BRICK_CONCRETE_BROKEN = cube("brick_concrete_broken", 15F, 45F);

    static {
        // Cubes first: the flat building blocks.
        addCube(CONCRETE);
        addCube(CONCRETE_SMOOTH);
        addBlock(CONCRETE_PILLAR);
        addCube(CONCRETE_REBAR);
        addCube(CONCRETE_ASBESTOS);
        addCube(CONCRETE_COLORED);
        addCube(CONCRETE_COLORED_EXT);
        addCube(CONCRETE_SUPER);
        addBlock(CONCRETE_SUPER_BROKEN);
        addCube(BRICK_CONCRETE);
        addCube(BRICK_CONCRETE_MOSSY);
        addCube(BRICK_CONCRETE_CRACKED);
        addCube(BRICK_CONCRETE_BROKEN);

        // Stairs, in the same family order as the cubes.
        addStairs(BRICK_CONCRETE, "brick_concrete_stairs", 15F, 95F);
        addStairs(BRICK_CONCRETE_MOSSY, "brick_concrete_mossy_stairs", 15F, 94F);
        addStairs(BRICK_CONCRETE_CRACKED, "brick_concrete_cracked_stairs", 15F, 40F);
        addStairs(BRICK_CONCRETE_BROKEN, "brick_concrete_broken_stairs", 15F, 30F);
        addStairs(CONCRETE, "concrete_stairs", 15F, 94F);
        addStairs(CONCRETE_SMOOTH, "concrete_smooth_stairs", 15F, 94F);
        addStairs(CONCRETE_ASBESTOS, "concrete_asbestos_stairs", 15F, 94F);
        for (String dye : DYES) {
            addStairs(CONCRETE_COLORED, "concrete_colored_stairs_" + dye, 15F, 94F);
        }
        for (String type : EXT_TYPES) {
            addStairs(CONCRETE_COLORED_EXT, "concrete_colored_ext_stairs_" + type, 15F, 94F);
        }

        // Slabs. Builder's Choice has stairs in CE but no slabs.
        addSlab("brick_concrete_slab", 15F, 70F);
        addSlab("brick_concrete_mossy_slab", 15F, 70F);
        addSlab("brick_concrete_cracked_slab", 15F, 30F);
        addSlab("brick_concrete_broken_slab", 15F, 22F);
        addSlab("concrete_slab", 15F, 70F);
        addSlab("concrete_smooth_slab", 15F, 70F);
        addSlab("concrete_asbestos_slab", 15F, 70F);
        for (String dye : DYES) {
            addSlab("concrete_" + dye + "_slab", 15F, 70F);
        }
        // Hazard stripe is the only Builder's Choice family with slabs; add yellow + the five
        // colour variants. These are new ids, so no existing slab id moves.
        for (String type : EXT_TYPES) {
            if (isHazard(type)) {
                addSlab("concrete_colored_ext_slab_" + type, 15F, 70F);
            }
        }
    }

    private ConcreteSet() {
    }

    /** Called from {@code ModContent} after the existing blocks, so saved numeric ids stay put. */
    public static void registerBlocks(RegistryEvent.Register<Block> e) {
        for (Block block : BLOCKS) e.getRegistry().register(block);
    }

    /** Called from {@code ModContent} after the existing items, so saved numeric ids stay put. */
    public static void registerItems(RegistryEvent.Register<Item> e) {
        for (Item item : ITEMS) e.getRegistry().register(item);
    }

    /** Stairs block by registry path, or null; used by {@link ConcreteRecipes}. */
    public static Block stairs(String name) {
        return STAIRS.get(name);
    }

    /** Single slab block by registry path, or null; used by {@link ConcreteRecipes}. */
    public static Block slab(String name) {
        return SLABS.get(name);
    }

    /** True for the yellow hazard stripe and its five colour variants. */
    public static boolean isHazard(String type) {
        return "hazard".equals(type) || type.startsWith("hazard_");
    }

    /** Every stair block, in registration order; used by the ore dictionary registration. */
    public static Collection<Block> allStairs() {
        return Collections.unmodifiableCollection(STAIRS.values());
    }

    /** Every single-slab block, in registration order; used by the ore dictionary registration. */
    public static Collection<Block> allSlabs() {
        return Collections.unmodifiableCollection(SLABS.values());
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

    private static void addStairs(Block base, String name, float hardness, float resistance) {
        BlockConcreteStairs block = new BlockConcreteStairs(base, name, hardness, resistance);
        BLOCKS.add(block);
        STAIRS.put(name, block);
        ItemBlock item = new ItemBlock(block);
        item.setRegistryName(block.getRegistryName());
        ITEMS.add(item);
        CREATIVE.add(new ItemStack(item));
    }

    private static void addSlab(String name, float hardness, float resistance) {
        BlockConcreteSlab single = new BlockConcreteSlab(name, hardness, resistance);
        BlockConcreteSlab doubled = new BlockConcreteSlab.Double(name + "_double_slab", single,
                hardness, resistance);
        BLOCKS.add(single);
        BLOCKS.add(doubled);
        SLABS.put(name, single);
        ItemSlab item = new ItemSlab(single, single, doubled);
        item.setRegistryName(single.getRegistryName());
        ITEMS.add(item);
        CREATIVE.add(new ItemStack(item));
    }

    private static String[] prefixed(String prefix, String[] names) {
        String[] result = new String[names.length];
        for (int i = 0; i < names.length; i++) result[i] = prefix + names[i];
        return result;
    }
}
