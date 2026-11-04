package com.ntmdoors.block;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.network.MessageMonitorState;
import com.ntmdoors.network.ModNetwork;
import com.ntmdoors.registry.ModContent;
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
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * A 3x3x1 six-monitor desk. Three block types (bottom, middle, top) each carry three columns, so
 * the part and facing fit in the four block-metadata bits. A single tickless tile entity on the
 * bottom-centre core stores the terminal password. Each part renders one slice of the supplied
 * 48x41x16 model.
 *
 * <p>The model is authored facing north; the blockstate rotates each part for the other facings.
 * The bottom row is solid; the two upper rows add a half-depth collision wall on the monitor side
 * so the player can stand on the front half of the desk but cannot walk through the monitors.
 * The core is the bottom-centre block, named {@code ntmdoors:monitor_setup}.</p>
 */
public class BlockMonitorSetup extends Block {

    public static final PropertyDirection FACING =
            PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);
    public static final PropertyEnum<Column> COLUMN = PropertyEnum.create("column", Column.class);

    /** One block type per horizontal row of the desk. */
    public enum Layer {
        BOTTOM,
        MIDDLE,
        TOP
    }

    /** Left, centre and right cell within a row. */
    public enum Column implements IStringSerializable {
        LEFT,
        CENTER,
        RIGHT;

        @Override
        public String getName() {
            return name().toLowerCase();
        }
    }

    private static final ThreadLocal<Boolean> DISMANTLING = ThreadLocal.withInitial(() -> false);
    /** Authored monitor wall: the south half of a block, centred so it can be rotated. */
    private static final AxisAlignedBB MONITOR_LOCAL = new AxisAlignedBB(-0.5, 0, 0, 0.5, 1, 0.5);
    private static final AxisAlignedBB MONITOR_NORTH = rotatedMonitorBox(EnumFacing.NORTH);
    private static final AxisAlignedBB MONITOR_SOUTH = rotatedMonitorBox(EnumFacing.SOUTH);
    private static final AxisAlignedBB MONITOR_EAST = rotatedMonitorBox(EnumFacing.EAST);
    private static final AxisAlignedBB MONITOR_WEST = rotatedMonitorBox(EnumFacing.WEST);

    private final Layer layer;

    public BlockMonitorSetup(String name, Layer layer) {
        super(Material.WOOD);
        this.layer = layer;
        setRegistryName(NTMDoors.MODID, name);
        setTranslationKey(NTMDoors.MODID + "." + name);
        setCreativeTab(NTMDoors.TAB);
        setHardness(2.5F);
        setResistance(6F);
        setSoundType(SoundType.WOOD);
        // The upper cells are mostly open space; they must not darken the room or cull neighbours.
        setLightOpacity(0);
        setDefaultState(blockState.getBaseState()
                .withProperty(FACING, EnumFacing.NORTH)
                .withProperty(COLUMN, Column.CENTER));
    }

    public Layer layer() {
        return layer;
    }

    /** World offset of one cell relative to the bottom-centre core, rotated for the facing. */
    public static BlockPos offset(Layer layer, Column column, EnumFacing facing) {
        int dx = column.ordinal() - 1;
        return new BlockPos(
                (int) Math.round(Facings.worldX(dx, 0, facing)),
                layer.ordinal(),
                (int) Math.round(Facings.worldZ(dx, 0, facing)));
    }

    /** Offset of this block's own layer. */
    public BlockPos offset(Column column, EnumFacing facing) {
        return offset(layer, column, facing);
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, COLUMN);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        EnumFacing facing = EnumFacing.byHorizontalIndex(meta & 3);
        if (facing == null) facing = EnumFacing.NORTH;
        int column = (meta >> 2) & 3;
        Column[] columns = Column.values();
        return getDefaultState().withProperty(FACING, facing)
                .withProperty(COLUMN, column < columns.length ? columns[column] : Column.CENTER);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex() | (state.getValue(COLUMN).ordinal() << 2);
    }

    /** The desk faces the player, matching the safes, speaker and sensor. */
    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer, EnumHand hand) {
        EnumFacing face = placer == null ? EnumFacing.NORTH : placer.getHorizontalFacing().getOpposite();
        return getDefaultState().withProperty(FACING, face).withProperty(COLUMN, Column.CENTER);
    }

    /** Only the bottom-centre core owns the desk's password tile entity. */
    @Override
    public boolean hasTileEntity(IBlockState state) {
        return layer == Layer.BOTTOM && state.getValue(COLUMN) == Column.CENTER;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return hasTileEntity(state) ? new TileEntityMonitorDesk() : null;
    }

    /**
     * Right-clicking a monitor cell asks the server to open the terminal. The solid bottom row
     * stays inert so the tabletop keeps its normal block/item interactions, and sneaking bypasses
     * the screen so blocks can still be placed against the monitors.
     */
    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (layer == Layer.BOTTOM || player.isSneaking()) return false;
        if (!world.isRemote && player instanceof EntityPlayerMP) {
            BlockPos core = pos.subtract(offset(state.getValue(COLUMN), state.getValue(FACING)));
            TileEntityMonitorDesk monitor = TileEntityMonitorDesk.at(world, core);
            if (monitor != null) {
                ModNetwork.INSTANCE.sendTo(new MessageMonitorState(core,
                        MessageMonitorState.OPEN, monitor.hasPassword(),
                        monitor.isPasswordSkipped()), (EntityPlayerMP) player);
            }
        }
        return true;
    }

    /**
     * Removing any cell removes the whole desk. The core is derived from the broken cell's layer,
     * column and facing, so this works without a tile entity. The thread-local guard stops the
     * removed cells from recursively dismantling the rest.
     */
    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote && !DISMANTLING.get()) {
            EnumFacing facing = state.getValue(FACING);
            BlockPos corePos = pos.subtract(offset(state.getValue(COLUMN), facing));
            DISMANTLING.set(true);
            try {
                for (Layer l : Layer.values()) {
                    for (Column c : Column.values()) {
                        BlockPos p = corePos.add(offset(l, c, facing));
                        if (!p.equals(pos) && world.getBlockState(p).getBlock() instanceof BlockMonitorSetup) {
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
     * The bottom row keeps the full cube. The middle and top rows use the back half only, forming
     * one continuous half-depth wall over the monitors while leaving the front half of the desk
     * walkable and jumpable.
     */
    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return layer == Layer.BOTTOM ? FULL_BLOCK_AABB : monitorBox(state.getValue(FACING));
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return layer == Layer.BOTTOM ? FULL_BLOCK_AABB : monitorBox(state.getValue(FACING));
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
        return layer == Layer.BOTTOM;
    }

    @Override
    public boolean isSideSolid(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return layer == Layer.BOTTOM;
    }

    @Override
    public EnumPushReaction getPushReaction(IBlockState state) {
        return EnumPushReaction.BLOCK;
    }

    /** Middle/top blocks have no item of their own, so all cells drop the desk item. */
    @Override
    public Item getItemDropped(IBlockState state, java.util.Random rand, int fortune) {
        return Item.getItemFromBlock(ModContent.MONITOR_SETUP);
    }
}
