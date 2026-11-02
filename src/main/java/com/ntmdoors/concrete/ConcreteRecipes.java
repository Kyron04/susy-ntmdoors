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
 * The concrete recipes. Cubes and slabs are metadata variants now, so every recipe names the cube
 * meta it consumes and the slab meta it produces. Only blocks make slabs and stairs; the reverse
 * conversions are deliberately absent, matching vanilla.
 */
public final class ConcreteRecipes {

    private static final String[] DYE_ORE = {
            "dyeWhite", "dyeOrange", "dyeMagenta", "dyeLightBlue", "dyeYellow", "dyeLime",
            "dyePink", "dyeGray", "dyeLightGray", "dyeCyan", "dyePurple", "dyeBlue",
            "dyeBrown", "dyeGreen", "dyeRed", "dyeBlack"
    };

    private static final String[][] EXT_DYES = {
            {"dyeBrown", "dyeGray"},     // machine
            {"dyeBrown", "dyeBlack"},    // machine_stripe
            {"dyeBlue", "dyePurple"},    // indigo
            {"dyePurple", "dyePurple"},  // purple
            {"dyePink", "dyeRed"},       // pink
            {"dyeYellow", "dyeBlack"},   // hazard
            {"dyeYellow", "dyeGray"},    // sand
            {"dyeOrange", "dyeBrown"},   // bronze
            {"dyeRed", "dyeBlack"},      // hazard_red
            {"dyeGreen", "dyeBlack"},    // hazard_green
            {"dyeBlue", "dyeBlack"},     // hazard_blue
            {"dyeWhite", "dyeBlack"},    // hazard_white
            {"dyePurple", "dyeBlack"}    // hazard_purple
    };

    private ConcreteRecipes() {
    }

    public static void register(RegistryEvent.Register<IRecipe> e) {
        baseRecipes(e);
        blockRecipes(e);
        coloredRecipes(e);
        extRecipes(e);
        plainSlabStairRecipes(e);
        brickSlabStairRecipes(e);
        coloredSlabStairRecipes(e);
        extSlabStairRecipes(e);
    }

    private static void baseRecipes(RegistryEvent.Register<IRecipe> e) {
        // Smooth concrete: half-scale chemical plant, clay for cement.
        shaped(e, "smooth", new ItemStack(ConcreteSet.CONCRETE, 8, 1),
                "GSG", "SCS", "GSG",
                'G', Blocks.GRAVEL, 'S', Blocks.SAND, 'C', Items.CLAY_BALL);
        // Asbestos concrete: clay for cement, string for asbestos.
        shaped(e, "asbestos", new ItemStack(ConcreteSet.CONCRETE_ASBESTOS, 8),
                "SAS", " C ", "SAS",
                'S', Blocks.SAND, 'A', Items.STRING, 'C', Items.CLAY_BALL);
        // Rebar concrete: iron bars for rebar.
        shapeless(e, "rebar", new ItemStack(ConcreteSet.CONCRETE_REBAR),
                Blocks.IRON_BARS, new ItemStack(ConcreteSet.CONCRETE, 1, 1));
    }

    private static void blockRecipes(RegistryEvent.Register<IRecipe> e) {
        shaped(e, "tile", new ItemStack(ConcreteSet.CONCRETE, 4, 0),
                "CC", "CC", 'C', new ItemStack(ConcreteSet.CONCRETE, 1, 1));
        shaped(e, "pillar", new ItemStack(ConcreteSet.CONCRETE_PILLAR, 6),
                "CBC", "CBC", "CBC",
                'C', new ItemStack(ConcreteSet.CONCRETE, 1, 1), 'B', Blocks.IRON_BARS);
        shaped(e, "brick", new ItemStack(ConcreteSet.CONCRETE_BRICKS, 4, 0),
                " C ", "CBC", " C ",
                'C', new ItemStack(ConcreteSet.CONCRETE, 1, 1), 'B', Items.CLAY_BALL);
        shaped(e, "brick_from_tile", new ItemStack(ConcreteSet.CONCRETE_BRICKS, 4, 0),
                " C ", "CBC", " C ",
                'C', new ItemStack(ConcreteSet.CONCRETE, 1, 0), 'B', Items.CLAY_BALL);
        shaped(e, "brick_mossy", new ItemStack(ConcreteSet.CONCRETE_BRICKS, 8, 1),
                "CCC", "CVC", "CCC",
                'C', new ItemStack(ConcreteSet.CONCRETE_BRICKS, 1, 0), 'V', Blocks.VINE);
        shaped(e, "brick_cracked", new ItemStack(ConcreteSet.CONCRETE_BRICKS, 6, 2),
                " C ", "C C", " C ",
                'C', new ItemStack(ConcreteSet.CONCRETE_BRICKS, 1, 0));
        shaped(e, "brick_broken", new ItemStack(ConcreteSet.CONCRETE_BRICKS, 6, 3),
                " C ", "C C", " C ",
                'C', new ItemStack(ConcreteSet.CONCRETE_BRICKS, 1, 2));
    }

    private static void coloredRecipes(RegistryEvent.Register<IRecipe> e) {
        for (int meta = 0; meta < ConcreteSet.DYES.length; meta++) {
            shaped(e, "colored_" + ConcreteSet.DYES[meta],
                    new ItemStack(ConcreteSet.CONCRETE_COLORED, 8, meta),
                    "CCC", "CDC", "CCC",
                    'C', new ItemStack(ConcreteSet.CONCRETE, 1, 1), 'D', DYE_ORE[meta]);
        }
    }

    private static void extRecipes(RegistryEvent.Register<IRecipe> e) {
        for (int meta = 0; meta < ConcreteSet.EXT_TYPES.length; meta++) {
            shaped(e, "colored_ext_" + ConcreteSet.EXT_TYPES[meta],
                    new ItemStack(ConcreteSet.CONCRETE_COLORED_EXT, 6, meta),
                    "CCC", "1 2", "CCC",
                    'C', new ItemStack(ConcreteSet.CONCRETE, 1, 1),
                    '1', EXT_DYES[meta][0], '2', EXT_DYES[meta][1]);
        }
    }

    private static void plainSlabStairRecipes(RegistryEvent.Register<IRecipe> e) {
        slab(e, "slab_concrete", ConcreteSet.CONCRETE_SLABS, 0, ConcreteSet.CONCRETE, 0);
        slab(e, "slab_concrete_smooth", ConcreteSet.CONCRETE_SLABS, 1, ConcreteSet.CONCRETE, 1);
        stairs(e, "stairs_concrete", ConcreteSet.stairs("concrete_stairs"), ConcreteSet.CONCRETE, 0);
        stairs(e, "stairs_concrete_smooth", ConcreteSet.stairs("concrete_smooth_stairs"),
                ConcreteSet.CONCRETE, 1);
        slab(e, "slab_concrete_asbestos", ConcreteSet.CONCRETE_ASBESTOS_SLABS, 0,
                ConcreteSet.CONCRETE_ASBESTOS, 0);
        stairs(e, "stairs_concrete_asbestos", ConcreteSet.stairs("concrete_asbestos_stairs"),
                ConcreteSet.CONCRETE_ASBESTOS, 0);
    }

    private static void brickSlabStairRecipes(RegistryEvent.Register<IRecipe> e) {
        String[] stairs = {
                "brick_concrete_stairs", "brick_concrete_mossy_stairs",
                "brick_concrete_cracked_stairs", "brick_concrete_broken_stairs"
        };
        for (int meta = 0; meta < ConcreteSet.BRICK_TYPES.length; meta++) {
            slab(e, "slab_" + ConcreteSet.BRICK_TYPES[meta], ConcreteSet.CONCRETE_BRICKS_SLABS,
                    meta, ConcreteSet.CONCRETE_BRICKS, meta);
            stairs(e, "stairs_" + ConcreteSet.BRICK_TYPES[meta],
                    ConcreteSet.stairs(stairs[meta]), ConcreteSet.CONCRETE_BRICKS, meta);
        }
    }

    private static void coloredSlabStairRecipes(RegistryEvent.Register<IRecipe> e) {
        for (int meta = 0; meta < ConcreteSet.DYES.length; meta++) {
            String dye = ConcreteSet.DYES[meta];
            ConcreteSet.SlabGroup group = meta < 8
                    ? ConcreteSet.CONCRETE_COLORED_SLABS
                    : ConcreteSet.CONCRETE_COLORED_SLABS_2;
            int slabMeta = meta & 7;
            slab(e, "slab_concrete_" + dye, group, slabMeta, ConcreteSet.CONCRETE_COLORED, meta);
            stairs(e, "stairs_concrete_" + dye,
                    ConcreteSet.stairs("concrete_colored_stairs_" + dye),
                    ConcreteSet.CONCRETE_COLORED, meta);
        }
    }

    private static void extSlabStairRecipes(RegistryEvent.Register<IRecipe> e) {
        for (int meta = 0; meta < ConcreteSet.EXT_TYPES.length; meta++) {
            String type = ConcreteSet.EXT_TYPES[meta];
            stairs(e, "stairs_ext_" + type,
                    ConcreteSet.stairs("concrete_colored_ext_stairs_" + type),
                    ConcreteSet.CONCRETE_COLORED_EXT, meta);
        }

        int slabMeta = 0;
        for (int meta = 0; meta < ConcreteSet.EXT_TYPES.length; meta++) {
            String type = ConcreteSet.EXT_TYPES[meta];
            if (!ConcreteSet.isHazard(type)) continue;
            slab(e, "slab_ext_" + type, ConcreteSet.CONCRETE_COLORED_EXT_SLABS, slabMeta,
                    ConcreteSet.CONCRETE_COLORED_EXT, meta);
            slabMeta++;
        }
    }

    private static void slab(RegistryEvent.Register<IRecipe> e, String name,
                             ConcreteSet.SlabGroup group, int slabMeta,
                             Block cube, int cubeMeta) {
        shaped(e, name, new ItemStack(group.single, 6, slabMeta), "###", '#',
                new ItemStack(cube, 1, cubeMeta));
    }

    private static void stairs(RegistryEvent.Register<IRecipe> e, String name, Block stairs,
                               Block cube, int cubeMeta) {
        if (stairs == null) throw new IllegalStateException("Missing stairs " + name);
        shaped(e, name, new ItemStack(stairs, 8), "#  ", "## ", "###", '#',
                new ItemStack(cube, 1, cubeMeta));
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
