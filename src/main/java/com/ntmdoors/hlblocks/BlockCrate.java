package com.ntmdoors.hlblocks;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * HL crate 09. One crate is one block tall; a crate with another crate directly above it becomes
 * the lower half and switches its sides to the bottom texture.
 *
 * <p>The bottom texture now has an opaque palette backing, so the crate can render in the solid
 * pass and no longer shows the world through its lower half.</p>
 */
public class BlockCrate extends HLSoundBlock {

    public static final PropertyBool BOTTOM = PropertyBool.create("bottom");

    public BlockCrate() {
        super(Material.WOOD, SoundType.WOOD);
        setDefaultState(this.blockState.getBaseState().withProperty(BOTTOM, Boolean.FALSE));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, BOTTOM);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(BOTTOM, meta == 1);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(BOTTOM) ? 1 : 0;
    }

    @Override
    public int damageDropped(IBlockState state) {
        return 0;
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer,
                                ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        updateBottom(world, pos);
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos fromPos) {
        if (!world.isRemote) {
            updateBottom(world, pos);
        }
    }

    /** A crate is the bottom half of a stack whenever another crate sits directly above it. */
    private void updateBottom(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() != this) {
            return;
        }
        boolean bottom = world.getBlockState(pos.up()).getBlock() == this;
        if (state.getValue(BOTTOM) != bottom) {
            world.setBlockState(pos, state.withProperty(BOTTOM, bottom), 2);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.SOLID;
    }
}
