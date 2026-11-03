package com.ntmdoors.block;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.tile.TileEntityMotionSensor;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * Surface-mounted PIR housing. The sensor can sit on a floor, a wall or a ceiling: its front is the
 * face that was clicked during placement, and the back plate stays against that surface.
 *
 * <p>The block state serialises only the six-way facing. The four status colours are stored on
 * {@link TileEntityMotionSensor} and fed into the model through {@link #getActualState}, because
 * six facings times four statuses is more than the four metadata bits available per block id.</p>
 */
public class BlockMotionSensor extends Block {

    public static final PropertyDirection FACING = PropertyDirection.create("facing");
    public static final PropertyEnum<Status> STATUS = PropertyEnum.create("status", Status.class);

    /** One texture pixel. The model occupies an 8x8 footprint and is 2.25 pixels deep. */
    private static final double P = 1.0 / 16.0;
    private static final AxisAlignedBB BOX_NORTH =
            new AxisAlignedBB(4 * P, 4 * P, 13.75 * P, 12 * P, 12 * P, 16 * P);
    private static final AxisAlignedBB BOX_SOUTH =
            new AxisAlignedBB(4 * P, 4 * P, 0 * P, 12 * P, 12 * P, 2.25 * P);
    private static final AxisAlignedBB BOX_EAST =
            new AxisAlignedBB(0 * P, 4 * P, 4 * P, 2.25 * P, 12 * P, 12 * P);
    private static final AxisAlignedBB BOX_WEST =
            new AxisAlignedBB(13.75 * P, 4 * P, 4 * P, 16 * P, 12 * P, 12 * P);
    private static final AxisAlignedBB BOX_UP =
            new AxisAlignedBB(4 * P, 0 * P, 4 * P, 12 * P, 2.25 * P, 12 * P);
    private static final AxisAlignedBB BOX_DOWN =
            new AxisAlignedBB(4 * P, 13.75 * P, 4 * P, 12 * P, 16 * P, 12 * P);

    /** Light colours: idle green, authorised blue, maintenance yellow, motion red. */
    public enum Status implements IStringSerializable {
        IDLE("idle"),
        AUTHORIZED("authorized"),
        MAINTENANCE("maintenance"),
        MOTION("motion");

        private final String name;

        Status(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    public BlockMotionSensor() {
        super(Material.IRON);
        setRegistryName(NTMDoors.MODID, "motion_sensor");
        setTranslationKey(NTMDoors.MODID + ".motion_sensor");
        setCreativeTab(NTMDoors.TAB);
        setHardness(1.5F);
        setResistance(6F);
        setSoundType(SoundType.METAL);
        // A small surface-mounted unit must not block a full cube of light.
        setLightOpacity(0);
        setDefaultState(blockState.getBaseState()
                .withProperty(FACING, EnumFacing.NORTH)
                .withProperty(STATUS, Status.IDLE));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, STATUS);
    }

    /**
     * Only the facing is persisted in the chunk metadata. The status is restored from the tile
     * entity and applied by {@link #getActualState}.
     */
    @Override
    public IBlockState getStateFromMeta(int meta) {
        EnumFacing facing = EnumFacing.byIndex(meta & 7);
        if (facing == null) facing = EnumFacing.NORTH;
        return getDefaultState().withProperty(FACING, facing).withProperty(STATUS, Status.IDLE);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getIndex();
    }

    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileEntityMotionSensor) {
            return state.withProperty(STATUS, ((TileEntityMotionSensor) te).getStatus());
        }
        return state;
    }

    /** The clicked face becomes the front, so floors, walls and ceilings all work. */
    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer, EnumHand hand) {
        EnumFacing front = facing == null ? EnumFacing.NORTH : facing;
        return getDefaultState().withProperty(FACING, front);
    }

    /** Adds the tile to a sensor placed before the tile entity existed. */
    public void ensureTileEntity(World world, BlockPos pos, IBlockState state) {
        if (world.isRemote || world.getTileEntity(pos) instanceof TileEntityMotionSensor) return;
        TileEntity tile = createTileEntity(world, state);
        if (tile != null) world.setTileEntity(pos, tile);
    }

    /** Changes the status light without disturbing the facing. Used by the future sensor logic. */
    public static void setStatus(World world, BlockPos pos, Status status) {
        if (world.isRemote) return;
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockMotionSensor)) return;
        BlockMotionSensor block = (BlockMotionSensor) state.getBlock();
        block.ensureTileEntity(world, pos, state);
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileEntityMotionSensor) {
            ((TileEntityMotionSensor) te).setStatus(status);
        }
    }

    /** Creative-only preview until the motion logic lands; survival right-click is left untouched. */
    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        if (!player.isCreative()) return false;
        if (world.isRemote) return true;
        ensureTileEntity(world, pos, state);
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileEntityMotionSensor) {
            ((TileEntityMotionSensor) te).cycleStatus();
            return true;
        }
        return false;
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos fromPos) {
        if (!world.isRemote) ensureTileEntity(world, pos, state);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileEntityMotionSensor();
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        switch (state.getValue(FACING)) {
            case SOUTH:
                return BOX_SOUTH;
            case EAST:
                return BOX_EAST;
            case WEST:
                return BOX_WEST;
            case UP:
                return BOX_UP;
            case DOWN:
                return BOX_DOWN;
            case NORTH:
            default:
                return BOX_NORTH;
        }
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return getBoundingBox(state, world, pos);
    }

    /** The housing is smaller than a full block, so do not occlude neighbours or block light. */
    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public EnumPushReaction getPushReaction(IBlockState state) {
        return EnumPushReaction.BLOCK;
    }
}
