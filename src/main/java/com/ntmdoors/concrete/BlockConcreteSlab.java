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
 * A metadata slab family. The variant enum selects the texture and the vanilla half property
 * selects bottom or top. The double slab is a separate technical block because {@link BlockSlab}
 * calls {@code isDouble()} while it is still constructing the block.
 */
public class BlockConcreteSlab extends BlockSlab {

    public static final PropertyEnum<Variant> VARIANT =
            PropertyEnum.create("variant", Variant.class);

    private final BlockSlab singleBlock;

    /** Single slab. */
    public BlockConcreteSlab(String name, float hardness, float resistance) {
        super(Material.ROCK);
        this.singleBlock = null;
        applyCommon(name, hardness, resistance);
        setCreativeTab(NTMDoors.TAB);
        setDefaultState(blockState.getBaseState()
                .withProperty(VARIANT, Variant.V0)
                .withProperty(HALF, EnumBlockHalf.BOTTOM));
    }

    /** Double slab. */
    protected BlockConcreteSlab(String name, Block single, float hardness, float resistance) {
        super(Material.ROCK);
        this.singleBlock = (BlockSlab) single;
        applyCommon(name, hardness, resistance);
        setDefaultState(blockState.getBaseState().withProperty(VARIANT, Variant.V0));
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
        return Variant.byMeta(stack.getMetadata());
    }

    @Override
    public String getTranslationKey(int meta) {
        return getTranslationKey();
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        int meta = state.getValue(VARIANT).ordinal();
        if (!isDouble() && state.getValue(HALF) == EnumBlockHalf.TOP) meta |= 8;
        return meta;
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        IBlockState state = getDefaultState().withProperty(VARIANT, Variant.byMeta(meta));
        if (!isDouble()) {
            state = state.withProperty(HALF,
                    (meta & 8) == 0 ? EnumBlockHalf.BOTTOM : EnumBlockHalf.TOP);
        }
        return state;
    }

    @Override
    public int damageDropped(IBlockState state) {
        return getMetaFromState(state) & 7;
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return Item.getItemFromBlock(isDouble() ? singleBlock : this);
    }

    @Override
    public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world,
                                  BlockPos pos, EntityPlayer player) {
        return new ItemStack(isDouble() ? singleBlock : this, 1, getMetaFromState(state) & 7);
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, HALF, VARIANT);
    }

    /** The eight slab textures a family can hold. */
    public enum Variant implements IStringSerializable {
        V0, V1, V2, V3, V4, V5, V6, V7;

        @Override
        public String getName() {
            return Integer.toString(ordinal());
        }

        public static Variant byMeta(int meta) {
            return values()[Math.floorMod(meta, values().length)];
        }
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
            return new BlockStateContainer(this, VARIANT);
        }
    }
}
