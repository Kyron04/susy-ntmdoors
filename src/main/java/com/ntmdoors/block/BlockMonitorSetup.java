package com.ntmdoors.block;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.tile.TileEntityMonitorSetup;
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
import net.minecraft.init.Blocks;
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
 * A 3x3x1 six-monitor desk made of nine cells. Each cell renders its own slice of the supplied
 * 48x41x16 model, so no single JSON element has to exceed the vanilla model limits. The bottom
 * centre cell is the CORE; the others remember where it is so breaking any cell removes the desk.
 *
 * <p>The model is authored facing north, so each part is shifted by its cell origin and the
 * blockstate rotates it for the other facings. The same {@link Facings} helpers used by the doors
 * keep the cells and their models aligned.</p>
 */
public class BlockMonitorSetup extends Block {

    public static final PropertyDirection FACING =
            PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);
    public static final PropertyEnum<Part> PART = PropertyEnum.create("part", Part.class);

    /**
     * The nine cells, ordered left-to-right and bottom-to-top. The enum name is also the blockstate
     * value; the matching model is {@code ntmdoors:monitor_setup_<name>}.
     */
    public enum Part implements IStringSerializable {
        BOTTOM_LEFT,
        BOTTOM_CENTER,
        BOTTOM_RIGHT,
        MIDDLE_LEFT,
        MIDDLE_CENTER,
        MIDDLE_RIGHT,
        TOP_LEFT,
        TOP_CENTER,
        TOP_RIGHT;

        public int column() {
            return ordinal() % 3;
        }

        public int layer() {
            return ordinal() / 3;
        }

        public boolean isBottom() {
            return layer() == 0;
        }

        public boolean isCore() {
            return this == BOTTOM_CENTER;
        }

        @Override
        public String getName() {
            return name().toLowerCase();
        }
    }

    public BlockMonitorSetup() {
        super(Material.WOOD);
        setRegistryName(NTMDoors.MODID, "monitor_setup");
        setTranslationKey(NTMDoors.MODID + ".monitor_setup");
        setCreativeTab(NTMDoors.TAB);
        setHardness(2.5F);
        setResistance(6F);
        setSoundType(SoundType.WOOD);
        // The upper cells are mostly open space; they must not darken the room or cull neighbours.
        setLightOpacity(0);
        setDefaultState(blockState.getBaseState()
                .withProperty(FACING, EnumFacing.NORTH)
                .withProperty(PART, Part.BOTTOM_CENTER));
    }

    /** World offset of one part relative to the bottom-centre core, rotated for the facing. */
    public static BlockPos offset(Part part, EnumFacing facing) {
        int dx = part.column() - 1;
        return new BlockPos(
                (int) Math.round(Facings.worldX(dx, 0, facing)),
                part.layer(),
                (int) Math.round(Facings.worldZ(dx, 0, facing)));
    }

    /** Every cell of the desk, relative to the bottom-centre core, rotated for the facing. */
    public static BlockPos[] cells(EnumFacing facing) {
        Part[] parts = Part.values();
        BlockPos[] out = new BlockPos[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = offset(parts[i], facing);
        return out;
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, PART);
    }

    /**
     * The part is kept in the tile entity because nine parts times four facings do not fit in the
     * four metadata bits. Metadata stores only the facing; {@link #getActualState} restores the
     * model part from the tile once the chunk has loaded.
     */
    @Override
    public IBlockState getStateFromMeta(int meta) {
        EnumFacing facing = EnumFacing.byHorizontalIndex(meta & 3);
        if (facing == null) facing = EnumFacing.NORTH;
        return getDefaultState().withProperty(FACING, facing).withProperty(PART, Part.BOTTOM_CENTER);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex();
    }

    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileEntityMonitorSetup) {
            Part part = ((TileEntityMonitorSetup) te).getPart();
            if (part != null) return state.withProperty(PART, part);
        }
        return state;
    }

    /** The desk faces the player, matching the safes, speaker and sensor. */
    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer, EnumHand hand) {
        EnumFacing face = placer == null ? EnumFacing.NORTH : placer.getHorizontalFacing().getOpposite();
        return getDefaultState().withProperty(FACING, face).withProperty(PART, Part.BOTTOM_CENTER);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        TileEntityMonitorSetup tile = new TileEntityMonitorSetup();
        tile.setPart(state.getValue(PART));
        return tile;
    }

    /** Adds the tile to a part placed before the tile entity existed (for example by /setblock). */
    public void ensureTileEntity(World world, BlockPos pos, IBlockState state) {
        if (world.isRemote || world.getTileEntity(pos) instanceof TileEntityMonitorSetup) return;
        TileEntity tile = createTileEntity(world, state);
        if (tile != null) world.setTileEntity(pos, tile);
    }

    /** Tile-backed part, falling back to the blockstate when the tile is not available yet. */
    public Part partAt(IBlockAccess world, BlockPos pos, IBlockState state) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileEntityMonitorSetup) {
            Part part = ((TileEntityMonitorSetup) te).getPart();
            if (part != null) return part;
        }
        return state.getValue(PART);
    }

    @Nullable
    public BlockPos getCorePos(IBlockAccess world, BlockPos pos, IBlockState state) {
        if (partAt(world, pos, state).isCore()) return pos;
        TileEntity te = world.getTileEntity(pos);
        return te instanceof TileEntityMonitorSetup
                ? ((TileEntityMonitorSetup) te).getCorePos() : null;
    }

    /** Removing any cell removes the whole desk; only the block actually broken drops the item. */
    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote) {
            BlockPos corePos = getCorePos(world, pos, state);
            if (corePos != null) {
                TileEntity te = world.getTileEntity(corePos);
                if (te instanceof TileEntityMonitorSetup
                        && ((TileEntityMonitorSetup) te).beginDismantle()) {
                    IBlockState coreState = world.getBlockState(corePos);
                    if (coreState.getBlock() instanceof BlockMonitorSetup) {
                        EnumFacing facing = coreState.getValue(FACING);
                        for (BlockPos off : cells(facing)) {
                            BlockPos p = corePos.add(off);
                            if (!p.equals(pos) && world.getBlockState(p).getBlock() == this) {
                                world.setBlockState(p, Blocks.AIR.getDefaultState(), 2);
                            }
                        }
                    }
                }
            }
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos fromPos) {
        if (!world.isRemote) ensureTileEntity(world, pos, state);
    }

    /** The bottom row is the desk/legs; the upper cells are mostly empty monitor space. */
    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return partAt(world, pos, state).isBottom() ? FULL_BLOCK_AABB : NULL_AABB;
    }

    @Nullable
    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return partAt(world, pos, state).isBottom() ? FULL_BLOCK_AABB : null;
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
        return state.getValue(PART).isBottom();
    }

    @Override
    public boolean isSideSolid(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return partAt(world, pos, state).isBottom();
    }

    @Override
    public EnumPushReaction getPushReaction(IBlockState state) {
        return EnumPushReaction.BLOCK;
    }
}
