package com.ntmdoors.block;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.monitor.SecurityEvents;
import com.ntmdoors.network.MessageMonitorState;
import com.ntmdoors.network.ModNetwork;
import com.ntmdoors.tile.TileEntityMonitorDesk;
import com.ntmdoors.util.Facings;
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
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * One cell of a small monitor desk. The bottom layer is the solid desk; the middle layer carries
 * the monitors and uses the same half-depth collision wall as the six-monitor desk. A single
 * tickless tile entity on the bottom core stores the terminal password; there is no per-tick logic.
 */
public class BlockMonitorDesk extends Block {

    public static final PropertyDirection FACING =
            PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);
    /** All columns; two-wide desks use the subclass property that only exposes left and right. */
    public static final PropertyEnum<BlockMonitorSetup.Column> COLUMN =
            PropertyEnum.create("column", BlockMonitorSetup.Column.class);

    private static final ThreadLocal<Boolean> DISMANTLING = ThreadLocal.withInitial(() -> false);
    /** Authored monitor wall: the south half of a block, centred so it can be rotated. */
    private static final AxisAlignedBB MONITOR_LOCAL = new AxisAlignedBB(-0.5, 0, 0, 0.5, 1, 0.5);
    private static final AxisAlignedBB MONITOR_NORTH = rotatedMonitorBox(EnumFacing.NORTH);
    private static final AxisAlignedBB MONITOR_SOUTH = rotatedMonitorBox(EnumFacing.SOUTH);
    private static final AxisAlignedBB MONITOR_EAST = rotatedMonitorBox(EnumFacing.EAST);
    private static final AxisAlignedBB MONITOR_WEST = rotatedMonitorBox(EnumFacing.WEST);

    private final MonitorDesk desk;
    private final BlockMonitorSetup.Layer layer;
    private final Set<BlockMonitorSetup.Column> columns;

    public BlockMonitorDesk(MonitorDesk desk, String name, BlockMonitorSetup.Layer layer,
                            BlockMonitorSetup.Column... columns) {
        super(Material.WOOD);
        this.desk = desk;
        this.layer = layer;
        this.columns = Collections.unmodifiableSet(EnumSet.copyOf(Arrays.asList(columns)));
        setRegistryName(NTMDoors.MODID, name);
        setTranslationKey(NTMDoors.MODID + "." + name);
        setCreativeTab(NTMDoors.TAB);
        setHardness(2.5F);
        setResistance(6F);
        setSoundType(SoundType.WOOD);
        setLightOpacity(0);
        setDefaultState(blockState.getBaseState()
                .withProperty(FACING, EnumFacing.NORTH)
                .withProperty(columnProperty(), desk.coreColumn()));
        desk.add(this);
    }

    public MonitorDesk desk() {
        return desk;
    }

    public BlockMonitorSetup.Layer layer() {
        return layer;
    }

    /** The columns this layer actually places; empty cells are left out entirely. */
    public Set<BlockMonitorSetup.Column> columns() {
        return columns;
    }

    public PropertyEnum<BlockMonitorSetup.Column> columnProperty() {
        return COLUMN;
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, columnProperty());
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        EnumFacing facing = EnumFacing.byHorizontalIndex(meta & 3);
        if (facing == null) facing = EnumFacing.NORTH;
        int ordinal = (meta >> 2) & 3;
        BlockMonitorSetup.Column column = desk.coreColumn();
        for (BlockMonitorSetup.Column candidate : columnProperty().getAllowedValues()) {
            if (candidate.ordinal() == ordinal) {
                column = candidate;
                break;
            }
        }
        return getDefaultState().withProperty(FACING, facing).withProperty(columnProperty(), column);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex()
                | (state.getValue(columnProperty()).ordinal() << 2);
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer, EnumHand hand) {
        EnumFacing face = placer == null ? EnumFacing.NORTH : placer.getHorizontalFacing().getOpposite();
        return getDefaultState()
                .withProperty(FACING, face)
                .withProperty(columnProperty(), desk.coreColumn());
    }

    public BlockPos offset(BlockMonitorSetup.Column column, EnumFacing facing) {
        return desk.offset(this, column, facing);
    }

    /** Only the bottom core cell owns the desk's password tile entity. */
    @Override
    public boolean hasTileEntity(IBlockState state) {
        return layer == BlockMonitorSetup.Layer.BOTTOM
                && state.getValue(columnProperty()) == desk.coreColumn();
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return hasTileEntity(state) ? new TileEntityMonitorDesk() : null;
    }

    /**
     * Right-clicking a monitor cell asks the server to open the terminal. The desk row stays inert
     * so the keyboard, mousepad and tabletop keep their normal block/item interactions, and
     * sneaking bypasses the screen so blocks can still be placed against the monitors.
     */
    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (layer == BlockMonitorSetup.Layer.BOTTOM || player.isSneaking()) return false;
        if (!world.isRemote && player instanceof EntityPlayerMP) {
            BlockPos core = pos.subtract(offset(state.getValue(columnProperty()),
                    state.getValue(FACING)));
            TileEntityMonitorDesk monitor = TileEntityMonitorDesk.at(world, core);
            if (monitor != null) {
                int screenIndex = state.getValue(columnProperty()).ordinal();
                ModNetwork.INSTANCE.sendTo(new MessageMonitorState(core,
                        MessageMonitorState.OPEN, monitor.hasPassword(),
                        monitor.isPasswordSkipped(), screenIndex), (EntityPlayerMP) player);
            }
        }
        return true;
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote) {
            TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof TileEntityMonitorDesk) {
                ((TileEntityMonitorDesk) tile).dropBackupContents(world, pos);
            }
        }
        if (!world.isRemote && !DISMANTLING.get()) {
            EnumFacing facing = state.getValue(FACING);
            BlockPos corePos = pos.subtract(offset(state.getValue(columnProperty()), facing));
            SecurityEvents.removeDesk(world, corePos);
            DISMANTLING.set(true);
            try {
                for (BlockMonitorDesk block : desk.blocks()) {
                    for (BlockMonitorSetup.Column column : block.columns()) {
                        BlockPos p = corePos.add(block.offset(column, facing));
                        if (!p.equals(pos) && world.getBlockState(p).getBlock() instanceof BlockMonitorDesk) {
                            world.setBlockState(p, Blocks.AIR.getDefaultState(), 2);
                        }
                    }
                }
            } finally {
                DISMANTLING.set(false);
            }
        }
        super.breakBlock(world, pos, state);
    }

    /**
     * The bottom row keeps the full cube. The middle row uses the back half only, forming the
     * same half-depth monitor wall as the larger desk while leaving the front half walkable.
     */
    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return layer == BlockMonitorSetup.Layer.BOTTOM ? FULL_BLOCK_AABB : monitorBox(state.getValue(FACING));
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return layer == BlockMonitorSetup.Layer.BOTTOM ? FULL_BLOCK_AABB : monitorBox(state.getValue(FACING));
    }

    /** Rotates the authored south-half monitor wall into the block's facing. */
    private static AxisAlignedBB monitorBox(EnumFacing facing) {
        switch (facing) {
            case SOUTH:
                return MONITOR_SOUTH;
            case EAST:
                return MONITOR_EAST;
            case WEST:
                return MONITOR_WEST;
            case NORTH:
            default:
                return MONITOR_NORTH;
        }
    }

    private static AxisAlignedBB rotatedMonitorBox(EnumFacing facing) {
        return Facings.rotate(MONITOR_LOCAL, facing).offset(0.5D, 0D, 0.5D);
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isTopSolid(IBlockState state) {
        return layer == BlockMonitorSetup.Layer.BOTTOM;
    }

    @Override
    public boolean isSideSolid(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return layer == BlockMonitorSetup.Layer.BOTTOM;
    }

    @Override
    public EnumPushReaction getPushReaction(IBlockState state) {
        return EnumPushReaction.BLOCK;
    }

    /** Middle cells have no item of their own, so every cell drops the desk item. */
    @Override
    public Item getItemDropped(IBlockState state, java.util.Random rand, int fortune) {
        return Item.getItemFromBlock(desk.core());
    }
}
