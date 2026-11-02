package com.ntmdoors.hlblocks;

import com.ntmdoors.NTMDoors;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.event.RegistryEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The included HL Blocks set, registered under the ntmdoors namespace with the {@code hlblocks_}
 * prefix so its origin stays visible: {@code hlblocks:fifties} becomes
 * {@code ntmdoors:hlblocks_fifties}.
 *
 * <p>The blast door and the Lab 1 Door 4D variant are deliberately not included.</p>
 */
public final class HLBlocksContent {

    /** Standalone blocks with a single texture. */
    public static final List<Block> SIMPLE_BLOCKS = new ArrayList<>();

    /** Blocks whose original textures are cycling block states. */
    public static final List<BlockGroup> GROUP_BLOCKS = new ArrayList<>();

    private static final List<Block> BLOCKS = new ArrayList<>();
    private static final List<Item> ITEMS = new ArrayList<>();

    static {
        addSimple(new HLSoundBlock(Material.ROCK, SoundType.STONE), "ca1x_con2");
        addSimple(new HLSoundBlock(Material.IRON, SoundType.METAL), "drkmtlt_flr01");
        addSimple(new BlockCrate(), "crate09");

        addGroup(new BlockGroup.Freezer(), "freezer");
        addGroup(new BlockGroup.Fifties(), "fifties");
        addGroup(new BlockGroup.Crete(), "crete");
        addGroup(new BlockGroup.Duct(), "duct");
        addGroup(new BlockGroup.Lab(), "lab");
        addGroup(new BlockGroup.Tunnel(), "tunnel");
        addGroup(new BlockGroup.Generic(), "generic");
    }

    private HLBlocksContent() {
    }

    /** Called after the concrete blocks, so saved numeric block ids stay put. */
    public static void registerBlocks(RegistryEvent.Register<Block> e) {
        for (Block block : BLOCKS) e.getRegistry().register(block);
    }

    /** Called after the concrete items, so saved numeric item ids stay put. */
    public static void registerItems(RegistryEvent.Register<Item> e) {
        for (Item item : ITEMS) e.getRegistry().register(item);
    }

    /** Adds every simple and metadata HL Block stack to the main creative tab. */
    public static void addCreativeStacks(NonNullList<ItemStack> items) {
        for (Block block : SIMPLE_BLOCKS) items.add(new ItemStack(block));
        for (BlockGroup block : GROUP_BLOCKS) {
            for (int meta = 0; meta < block.getVariants().size(); meta++) {
                items.add(new ItemStack(block, 1, meta));
            }
        }
    }

    private static void addSimple(Block block, String name) {
        applyStats(block);
        block.setRegistryName(NTMDoors.MODID, "hlblocks_" + name);
        block.setTranslationKey(NTMDoors.MODID + ".hlblocks_" + name);
        block.setCreativeTab(NTMDoors.TAB);

        ItemBlock item = new ItemBlock(block);
        item.setRegistryName(block.getRegistryName());

        BLOCKS.add(block);
        ITEMS.add(item);
        SIMPLE_BLOCKS.add(block);
    }

    private static void addGroup(BlockGroup block, String name) {
        applyStats(block);
        block.setRegistryName(NTMDoors.MODID, "hlblocks_" + name);
        block.setTranslationKey(NTMDoors.MODID + ".hlblocks_" + name);
        block.setCreativeTab(NTMDoors.TAB);

        ItemBlockGroup item = new ItemBlockGroup(block);
        item.setRegistryName(block.getRegistryName());

        BLOCKS.add(block);
        ITEMS.add(item);
        GROUP_BLOCKS.add(block);
    }

    private static void applyStats(Block block) {
        boolean metal = block.getDefaultState().getMaterial() == Material.IRON;
        block.setHardness(metal ? 4.0F : 2.0F);
        block.setResistance(metal ? 12.0F : 6.0F);
    }
}
