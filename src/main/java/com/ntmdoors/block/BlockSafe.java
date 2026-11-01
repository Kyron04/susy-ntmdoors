package com.ntmdoors.block;

import com.ntmdoors.NTMDoors;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 1x1x1 steel safe. This first pass is the block shell only: it places, rotates and renders like a
 * normal full block. The code lock, inventory, GUI and biometric variant come later.
 */
public class BlockSafe extends Block {

    public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);

    public BlockSafe() {
        super(Material.IRON);
        setRegistryName(NTMDoors.MODID, "safe");
        setTranslationKey(NTMDoors.MODID + ".safe");
        setCreativeTab(NTMDoors.TAB);
        setHardness(5F);
        setResistance(30F);
        setSoundType(SoundType.METAL);
        setHarvestLevel("pickaxe", 2);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta & 3));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex();
    }

    /**
     * The door faces the player, whatever face of the neighbouring block was clicked. The block is
     * a full cube, so it can be placed on the floor, a wall or a ceiling like stone.
     */
    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer, EnumHand hand) {
        EnumFacing face = placer == null ? EnumFacing.NORTH : placer.getHorizontalFacing().getOpposite();
        return getDefaultState().withProperty(FACING, face);
    }
}
