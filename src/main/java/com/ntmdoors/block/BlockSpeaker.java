package com.ntmdoors.block;

import com.ntmdoors.NTMDoors;
import net.minecraft.block.Block;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * Decorative 1x1x1 speaker cabinet. It has no tile entity or behaviour; the only state is the
 * horizontal facing used by the supplied model, which is chosen to face the player on placement.
 */
public class BlockSpeaker extends Block {

    public static final PropertyDirection FACING = BlockHorizontal.FACING;

    public BlockSpeaker() {
        super(Material.WOOD);
        setRegistryName(NTMDoors.MODID, "speaker");
        setTranslationKey(NTMDoors.MODID + ".speaker");
        setCreativeTab(NTMDoors.TAB);
        setHardness(2.5F);
        setResistance(6F);
        setSoundType(SoundType.WOOD);
        // Match the safe: the cabinet model has a recessed front and shallow front details, so it
        // must not occlude neighbours or be treated as a full cube by the model/lighting culler.
        setLightOpacity(255);
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
     * The front is recessed and the driver/trim details sit just proud of it, so the block is not
     * opaque or a full cube for rendering. Collision remains the default full block.
     */
    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    /** Keep the full-block support behaviour despite the non-opaque render shape. */
    @Override
    public boolean isTopSolid(IBlockState state) {
        return true;
    }

    @Override
    public boolean isSideSolid(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return true;
    }

    @Override
    public EnumPushReaction getPushReaction(IBlockState state) {
        return EnumPushReaction.BLOCK;
    }

    /** Face the player, matching the safes and the supplied north-facing model. */
    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer, EnumHand hand) {
        EnumFacing face = placer == null ? EnumFacing.NORTH : placer.getHorizontalFacing().getOpposite();
        return getDefaultState().withProperty(FACING, face);
    }
}
