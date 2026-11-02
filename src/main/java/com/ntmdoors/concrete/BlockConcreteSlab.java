package com.ntmdoors.concrete;

import com.ntmdoors.NTMDoors;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;

import java.util.Random;

/**
 * Single concrete slab. Vanilla has no data-driven slab, so each family gets a pair of block ids:
 * the single slab carries the item, {@link Double} is only ever placed by
 * {@link net.minecraft.item.ItemSlab} and drops the single slab again.
 *
 * <p>The double slab is a subclass rather than a flag because {@link BlockSlab} calls
 * {@code isDouble()} while it is still constructing the block, before any instance field could be
 * assigned. A constant override keeps the blockstate shape correct from the first call.</p>
 */
public class BlockConcreteSlab extends BlockSlab {

    public static final PropertyEnum<Variant> VARIANT = PropertyEnum.create("variant", Variant.class);

    private final BlockSlab singleBlock;

    /** Single slab. */
    public BlockConcreteSlab(String name, float hardness, float resistance) {
        super(Material.ROCK);
        this.singleBlock = null;
        applyCommon(name, hardness, resistance);
        setCreativeTab(ConcreteSet.TAB);
        setDefaultState(blockState.getBaseState()
                .withProperty(VARIANT, Variant.DEFAULT)
                .withProperty(HALF, EnumBlockHalf.BOTTOM));
    }

    /** Double slab. The single slab is the block the player gets back. */
    protected BlockConcreteSlab(String name, Block single, float hardness, float resistance) {
        super(Material.ROCK);
        this.singleBlock = (BlockSlab) single;
        applyCommon(name, hardness, resistance);
        setDefaultState(blockState.getBaseState().withProperty(VARIANT, Variant.DEFAULT));
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
        return VARIANT;
    }

    @Override
    public Comparable<?> getTypeForItem(ItemStack stack) {
        return Variant.DEFAULT;
    }

    @Override
    public String getTranslationKey(int meta) {
        return getTranslationKey();
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        int meta = 0;
        if (!isDouble() && state.getValue(HALF) == EnumBlockHalf.TOP) meta |= 8;
        return meta;
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        IBlockState state = getDefaultState().withProperty(VARIANT, Variant.DEFAULT);
        if (!isDouble()) {
            state = state.withProperty(HALF,
                    (meta & 8) == 0 ? EnumBlockHalf.BOTTOM : EnumBlockHalf.TOP);
        }
        return state;
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return Item.getItemFromBlock(isDouble() ? singleBlock : this);
    }

    @Override
    public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world,
                                  BlockPos pos, EntityPlayer player) {
        return new ItemStack(isDouble() ? singleBlock : this);
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, HALF, VARIANT);
    }

    public enum Variant implements IStringSerializable {
        DEFAULT;

        @Override
        public String getName() {
            return "default";
        }
    }

    /** The double slab half of a family; see the class comment for why this is a subclass. */
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
            return new BlockStateContainer(this, VARIANT);
        }
    }
}
