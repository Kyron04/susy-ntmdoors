package com.ntmdoors.block;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.tile.TileEntitySpeaker;
import net.minecraft.block.Block;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * 1x1x1 speaker cabinet. Shift-right-click opens its two-row control screen; redstone power while
 * enabled drives the alarm loop in {@link TileEntitySpeaker}.
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

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileEntitySpeaker();
    }

    /** Adds the speaker tile to a block placed before the tile entity existed. */
    public void ensureTileEntity(World world, BlockPos pos, IBlockState state) {
        if (world.isRemote || world.getTileEntity(pos) instanceof TileEntitySpeaker) return;
        TileEntity tile = createTileEntity(world, state);
        if (tile != null) world.setTileEntity(pos, tile);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        if (!player.isSneaking()) return false;
        if (world.isRemote) {
            NTMDoors.proxy.openSpeakerGui(pos);
        } else {
            ensureTileEntity(world, pos, state);
        }
        return true;
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos fromPos) {
        if (world.isRemote) return;
        ensureTileEntity(world, pos, state);
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileEntitySpeaker) ((TileEntitySpeaker) te).onNeighborChanged();
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

    /** Let redstone dust visually connect to the cabinet instead of stopping at its face. */
    @Override
    public boolean canConnectRedstone(IBlockState state, IBlockAccess world, BlockPos pos,
                                      @Nullable EnumFacing side) {
        return side != null;
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
