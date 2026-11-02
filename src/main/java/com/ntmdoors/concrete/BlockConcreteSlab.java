package com.ntmdoors.concrete;

import com.ntmdoors.NTMDoors;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;

import java.util.Random;

/**
 * A metadata slab family. Metadata bits 0-2 select the variant and bit 3 is the top half, so one
 * block id can hold up to eight variants. The double slab is a separate technical block because
 * {@link BlockSlab} calls {@code isDouble()} during construction.
 */
public class BlockConcreteSlab extends BlockSlab {

    public static final PropertyInteger META = PropertyInteger.create("meta", 0, 7);

    private final BlockSlab singleBlock;

    /** Single slab. */
    public BlockConcreteSlab(String name, float hardness, float resistance) {
        super(Material.ROCK);
        this.singleBlock = null;
        applyCommon(name, hardness, resistance);
        setCreativeTab(ConcreteSet.TAB);
        setDefaultState(blockState.getBaseState()
                .withProperty(META, 0)
                .withProperty(HALF, EnumBlockHalf.BOTTOM));
    }

    /** Double slab. */
    protected BlockConcreteSlab(String name, Block single, float hardness, float resistance) {
        super(Material.ROCK);
        this.singleBlock = (BlockSlab) single;
        applyCommon(name, hardness, resistance);
        setDefaultState(blockState.getBaseState().withProperty(META, 0));
    }

    private void applyCommon(String name, float hardness, float resistance) {
        setRegistryName(NTMDoors.MODID, name);
        setTranslationKey(NTMDoors.MODID + "." + name);
        setHardness(hardness);
        setResistance(resistance);
        setSoundType(SoundType.STONE);
        setHarvestLevel("pickaxe", 0);
    }

    @Override
    public boolean isDouble() {
        return false;
    }

    @Override
    public IProperty<?> getVariantProperty() {
        return META;
    }

    @Override
    public Comparable<?> getTypeForItem(ItemStack stack) {
        return stack.getMetadata() & 7;
    }

    @Override
    public String getTranslationKey(int meta) {
        return getTranslationKey();
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        int meta = state.getValue(META);
        if (!isDouble() && state.getValue(HALF) == EnumBlockHalf.TOP) meta |= 8;
        return meta;
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        IBlockState state = getDefaultState().withProperty(META, meta & 7);
        if (!isDouble()) {
            state = state.withProperty(HALF,
                    (meta & 8) == 0 ? EnumBlockHalf.BOTTOM : EnumBlockHalf.TOP);
        }
        return state;
    }

    @Override
    public int damageDropped(IBlockState state) {
        return state.getValue(META);
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return Item.getItemFromBlock(isDouble() ? singleBlock : this);
    }

    @Override
    public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world,
                                  BlockPos pos, EntityPlayer player) {
        return new ItemStack(isDouble() ? singleBlock : this, 1, state.getValue(META));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, HALF, META);
    }

    /** Double slab half of a family. */
    public static final class Double extends BlockConcreteSlab {

        public Double(String name, Block single, float hardness, float resistance) {
            super(name, single, hardness, resistance);
        }

        @Override
        public boolean isDouble() {
            return true;
        }

        @Override
        protected BlockStateContainer createBlockState() {
            return new BlockStateContainer(this, META);
        }
    }
}
