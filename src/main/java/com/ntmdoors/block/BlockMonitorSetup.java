package com.ntmdoors.block;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.registry.ModContent;
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
import net.minecraft.item.Item;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * A 3x3x1 six-monitor desk. Three block types (bottom, middle, top) each carry three columns, so
 * the part and facing fit in the four block-metadata bits and no tile entity is needed. Each part
 * renders one slice of the supplied 48x41x16 model.
 *
 * <p>The model is authored facing north; the blockstate rotates each part for the other facings.
 * The bottom row is solid; the two upper rows are pass-through and only reserve the desk volume.
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

    /** Middle and top cells are invisible to collision but still reserve their block space. */
    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return layer == Layer.BOTTOM ? FULL_BLOCK_AABB : NULL_AABB;
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return layer == Layer.BOTTOM ? FULL_BLOCK_AABB : NULL_AABB;
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
