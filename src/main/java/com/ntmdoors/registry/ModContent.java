package com.ntmdoors.registry;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.block.BlockDoorGeneric;
import com.ntmdoors.block.BlockDoorKeypad;
import com.ntmdoors.door.DoorDecl;
import com.ntmdoors.door.DoorDecls;
import com.ntmdoors.item.ItemBlockKeypad;
import com.ntmdoors.item.ItemDoorBlock;
import com.ntmdoors.item.ItemDoorKey;
import com.ntmdoors.item.ItemPadlock;
import com.ntmdoors.item.ItemScrewdriver;
import com.ntmdoors.recipe.RecipeKeyCopy;
import net.minecraft.block.Block;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = NTMDoors.MODID)
public class ModContent {

    public static final List<BlockDoorGeneric> DOORS = new ArrayList<>();
    public static final BlockDoorKeypad KEYPAD = new BlockDoorKeypad();
    public static final ItemPadlock PADLOCK = new ItemPadlock();
    public static final ItemDoorKey KEY = new ItemDoorKey();
    public static final ItemScrewdriver SCREWDRIVER = new ItemScrewdriver();

    static {
        for (DoorDecl d : DoorDecls.ALL) DOORS.add(new BlockDoorGeneric(d, false));
        // The locked twins are appended after the normal doors so the creative tab stays grouped.
        for (DoorDecl d : DoorDecls.ALL) DOORS.add(new BlockDoorGeneric(d, true));
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> e) {
        for (BlockDoorGeneric b : DOORS) e.getRegistry().register(b);
        e.getRegistry().register(KEYPAD);
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> e) {
        for (BlockDoorGeneric b : DOORS) e.getRegistry().register(new ItemDoorBlock(b).setRegistryName(b.getRegistryName()));
        e.getRegistry().register(new ItemBlockKeypad(KEYPAD).setRegistryName(KEYPAD.getRegistryName()));
        e.getRegistry().register(PADLOCK);
        e.getRegistry().register(KEY);
        e.getRegistry().register(SCREWDRIVER);
    }

    @SubscribeEvent
    public static void registerSounds(RegistryEvent.Register<SoundEvent> e) {
        for (SoundEvent s : DoorDecl.ALL_SOUNDS) e.getRegistry().register(s);
    }

    @SubscribeEvent
    public static void registerRecipes(RegistryEvent.Register<IRecipe> e) {
        // padlock:  " I " / "I I" / "III"
        NonNullList<Ingredient> in = NonNullList.withSize(9, Ingredient.EMPTY);
        Ingredient iron = Ingredient.fromItems(Items.IRON_INGOT);
        for (int slot : new int[]{1, 3, 5, 6, 7, 8}) in.set(slot, iron);
        ShapedRecipes padlock = new ShapedRecipes(NTMDoors.MODID + ":padlock", 3, 3, in, new ItemStack(PADLOCK));
        padlock.setRegistryName(new ResourceLocation(NTMDoors.MODID, "padlock"));
        e.getRegistry().register(padlock);

        RecipeKeyCopy copy = new RecipeKeyCopy();
        copy.setRegistryName(new ResourceLocation(NTMDoors.MODID, "door_key"));
        e.getRegistry().register(copy);

        // screwdriver: "  I" / " I " / "S  "
        NonNullList<Ingredient> screw = NonNullList.withSize(9, Ingredient.EMPTY);
        screw.set(2, Ingredient.fromItems(Items.IRON_INGOT));
        screw.set(4, Ingredient.fromItems(Items.IRON_INGOT));
        screw.set(6, Ingredient.fromItems(Items.STICK));
        ShapedRecipes screwdriver = new ShapedRecipes(NTMDoors.MODID + ":screwdriver", 3, 3, screw,
                new ItemStack(SCREWDRIVER));
        screwdriver.setRegistryName(new ResourceLocation(NTMDoors.MODID, "screwdriver"));
        e.getRegistry().register(screwdriver);

        // Door crafting recipes are intentionally not included: the originals depend on NTM-only materials.
        // Add your own here (or via JSON in data/recipes), e.g. new ShapedRecipes(...) per door.
    }
}
