package com.ntmdoors.concrete;

import com.ntmdoors.NTMDoors;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

/**
 * The concrete recipes from NTM CE's {@code CraftingManager}, with CE's machine-only base blocks
 * folded into the crafting table.
 *
 * <p>Everything that CE crafts in a normal crafting table is copied one-to-one: the tile, pillar,
 * bricks, coloured concrete, Builder's Choice, and the block-to-slab and block-to-stairs recipes.
 * Like vanilla, stairs and slabs do not convert back into blocks or into each other. CE makes
 * smooth concrete and asbestos concrete in the chemical plant and rebar concrete in the
 * crystallizer; those machines are not part of this mod, so the three base recipes are half-scale
 * crafting-table versions. Clay stands in for cement powder and string for asbestos, the two CE
 * materials that have no equivalent here.</p>
 *
 * <p>Uber Concrete and Moldy Debris have no CE recipe; they stay creative-only.</p>
 */
public final class ConcreteRecipes {

    /**
     * Vanilla dye ore-dictionary names in concrete metadata order (0 = white ... 15 = black).
     * CE's {@code dyes[15 - i]} produces the same order, but spells light gray "dyeLightGray".
     */
    private static final String[] DYE_ORE = {
            "dyeWhite", "dyeOrange", "dyeMagenta", "dyeLightBlue", "dyeYellow", "dyeLime",
            "dyePink", "dyeGray", "dyeLightGray", "dyeCyan", "dyePurple", "dyeBlue",
            "dyeBrown", "dyeGreen", "dyeRed", "dyeBlack"
    };

    /** Builder's Choice dye pairs, in CE's {@code EnumConcreteType} order. */
    private static final String[][] EXT_DYES = {
            {"dyeBrown", "dyeGray"},     // machine
            {"dyeBrown", "dyeBlack"},    // machine_stripe
            {"dyeBlue", "dyePurple"},    // indigo
            {"dyePurple", "dyePurple"},  // purple
            {"dyePink", "dyeRed"},       // pink
            {"dyeYellow", "dyeBlack"},   // hazard
            {"dyeYellow", "dyeGray"},    // sand
            {"dyeOrange", "dyeBrown"}    // bronze
    };

    private ConcreteRecipes() {
    }

    public static void register(RegistryEvent.Register<IRecipe> e) {
        baseRecipes(e);
        blockRecipes(e);
        slabStairRecipes(e);
        coloredRecipes(e);
        extRecipes(e);
    }

    /** The three machine-only bases, adapted to the crafting table. */
    private static void baseRecipes(RegistryEvent.Register<IRecipe> e) {
        // CE chemical plant: 1 cement + 8 gravel + 8 sand + water -> 16 smooth concrete.
        // Half scale, with clay for cement; the bucket would not fit beside the aggregates.
        shaped(e, "smooth", new ItemStack(ConcreteSet.CONCRETE_SMOOTH, 8),
                "GSG", "SCS", "GSG",
                'G', Blocks.GRAVEL, 'S', Blocks.SAND, 'C', Items.CLAY_BALL);

        // CE chemical plant: 4 cement + 4 asbestos + 8 sand + water -> 16 asbestos concrete.
        // Half scale, with clay for cement and string for asbestos.
        shaped(e, "asbestos", new ItemStack(ConcreteSet.CONCRETE_ASBESTOS, 8),
                "SAS", " C ", "SAS",
                'S', Blocks.SAND, 'A', Items.STRING, 'C', Items.CLAY_BALL);

        // CE crystallizer: 1 rebar + concrete -> 1 rebar concrete. Iron bars stand in for rebar.
        shapeless(e, "rebar", new ItemStack(ConcreteSet.CONCRETE_REBAR),
                Blocks.IRON_BARS, ConcreteSet.CONCRETE_SMOOTH);
    }

    private static void blockRecipes(RegistryEvent.Register<IRecipe> e) {
        shaped(e, "tile", new ItemStack(ConcreteSet.CONCRETE, 4),
                "CC", "CC", 'C', ConcreteSet.CONCRETE_SMOOTH);
        shaped(e, "pillar", new ItemStack(ConcreteSet.CONCRETE_PILLAR, 6),
                "CBC", "CBC", "CBC", 'C', ConcreteSet.CONCRETE_SMOOTH, 'B', Blocks.IRON_BARS);
        shaped(e, "brick", new ItemStack(ConcreteSet.BRICK_CONCRETE, 4),
                " C ", "CBC", " C ", 'C', ConcreteSet.CONCRETE_SMOOTH, 'B', Items.CLAY_BALL);
        shaped(e, "brick_from_tile", new ItemStack(ConcreteSet.BRICK_CONCRETE, 4),
                " C ", "CBC", " C ", 'C', ConcreteSet.CONCRETE, 'B', Items.CLAY_BALL);
        shaped(e, "brick_mossy", new ItemStack(ConcreteSet.BRICK_CONCRETE_MOSSY, 8),
                "CCC", "CVC", "CCC", 'C', ConcreteSet.BRICK_CONCRETE, 'V', Blocks.VINE);
        shaped(e, "brick_cracked", new ItemStack(ConcreteSet.BRICK_CONCRETE_CRACKED, 6),
                " C ", "C C", " C ", 'C', ConcreteSet.BRICK_CONCRETE);
        shaped(e, "brick_broken", new ItemStack(ConcreteSet.BRICK_CONCRETE_BROKEN, 6),
                " C ", "C C", " C ", 'C', ConcreteSet.BRICK_CONCRETE_CRACKED);
    }

    /** Blocks to slabs and stairs only; no stair/slab conversion recipes, matching vanilla. */
    private static void slabStairRecipes(RegistryEvent.Register<IRecipe> e) {
        family(e, "brick_concrete", ConcreteSet.BRICK_CONCRETE);
        family(e, "brick_concrete_mossy", ConcreteSet.BRICK_CONCRETE_MOSSY);
        family(e, "brick_concrete_cracked", ConcreteSet.BRICK_CONCRETE_CRACKED);
        family(e, "brick_concrete_broken", ConcreteSet.BRICK_CONCRETE_BROKEN);
        family(e, "concrete", ConcreteSet.CONCRETE);
        family(e, "concrete_smooth", ConcreteSet.CONCRETE_SMOOTH);
        family(e, "concrete_asbestos", ConcreteSet.CONCRETE_ASBESTOS);
    }

    private static void family(RegistryEvent.Register<IRecipe> e, String key, Block block) {
        Block slab = ConcreteSet.slab(key + "_slab");
        Block stairs = ConcreteSet.stairs(key + "_stairs");
        if (slab == null || stairs == null) {
            throw new IllegalStateException("Missing slab/stairs for " + key);
        }
        shaped(e, "slab_" + key, new ItemStack(slab, 6), "###", '#', block);
        shaped(e, "stairs_" + key, new ItemStack(stairs, 8), "#  ", "## ", "###", '#', block);
    }

    /** Coloured concrete, its slabs and its stairs; only block-to-slab/stair recipes. */
    private static void coloredRecipes(RegistryEvent.Register<IRecipe> e) {
        for (int meta = 0; meta < ConcreteSet.DYES.length; meta++) {
            String dye = ConcreteSet.DYES[meta];
            Block slab = ConcreteSet.slab("concrete_" + dye + "_slab");
            Block stairs = ConcreteSet.stairs("concrete_colored_stairs_" + dye);
            if (slab == null || stairs == null) {
                throw new IllegalStateException("Missing coloured slab/stairs for " + dye);
            }

            shaped(e, "colored_" + dye, new ItemStack(ConcreteSet.CONCRETE_COLORED, 8, meta),
                    "CCC", "CDC", "CCC",
                    'C', ConcreteSet.CONCRETE_SMOOTH, 'D', DYE_ORE[meta]);

            ItemStack block = new ItemStack(ConcreteSet.CONCRETE_COLORED, 1, meta);
            shaped(e, "colored_slab_" + dye, new ItemStack(slab, 6), "###", '#', block);
            shaped(e, "colored_stairs_" + dye, new ItemStack(stairs, 8),
                    "#  ", "## ", "###", '#', block);
        }
    }

    /** Builder's Choice has stairs in CE but no slabs; only the block-to-stair recipe. */
    private static void extRecipes(RegistryEvent.Register<IRecipe> e) {
        for (int meta = 0; meta < ConcreteSet.EXT_TYPES.length; meta++) {
            String type = ConcreteSet.EXT_TYPES[meta];
            Block stairs = ConcreteSet.stairs("concrete_colored_ext_stairs_" + type);
            if (stairs == null) {
                throw new IllegalStateException("Missing Builder's Choice stairs for " + type);
            }

            shaped(e, "colored_ext_" + type,
                    new ItemStack(ConcreteSet.CONCRETE_COLORED_EXT, 6, meta),
                    "CCC", "1 2", "CCC",
                    'C', ConcreteSet.CONCRETE_SMOOTH,
                    '1', EXT_DYES[meta][0], '2', EXT_DYES[meta][1]);

            ItemStack block = new ItemStack(ConcreteSet.CONCRETE_COLORED_EXT, 1, meta);
            shaped(e, "ext_stairs_" + type, new ItemStack(stairs, 8),
                    "#  ", "## ", "###", '#', block);
        }
    }

    private static void shaped(RegistryEvent.Register<IRecipe> e, String name, ItemStack output,
                               Object... recipe) {
        ResourceLocation id = new ResourceLocation(NTMDoors.MODID, "concrete/" + name);
        ShapedOreRecipe shaped = new ShapedOreRecipe(id, output, recipe);
        shaped.setRegistryName(id);
        e.getRegistry().register(shaped);
    }

    private static void shapeless(RegistryEvent.Register<IRecipe> e, String name, ItemStack output,
                                  Object... recipe) {
        ResourceLocation id = new ResourceLocation(NTMDoors.MODID, "concrete/" + name);
        ShapelessOreRecipe shapeless = new ShapelessOreRecipe(id, output, recipe);
        shapeless.setRegistryName(id);
        e.getRegistry().register(shapeless);
    }
}
