package com.ntmdoors.block;

import com.ntmdoors.NTMDoors;
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
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;

/**
 * Decorative desk or monitor column block. It reuses the desk/monitor halves of the monitor desk
 * models but has no tile entity, no terminal and no monitor rendering. One block type covers every
 * column of a 1x1, 1x2 or 1x3 arrangement; the item form places all columns at once.
 */
public class BlockDesk extends Block {

    public static final PropertyDirection FACING =
            PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);
    public static final PropertyEnum<BlockMonitorSetup.Column> COLUMN =
            PropertyEnum.create("column", BlockMonitorSetup.Column.class);

    private static final ThreadLocal<Boolean> DISMANTLING = ThreadLocal.withInitial(() -> false);
    private static final BlockMonitorSetup.Column[] ONE_COLUMN = {
            BlockMonitorSetup.Column.CENTER
    };
    private static final BlockMonitorSetup.Column[] TWO_COLUMNS = {
            BlockMonitorSetup.Column.LEFT, BlockMonitorSetup.Column.RIGHT
    };
    private static final BlockMonitorSetup.Column[] THREE_COLUMNS = {
            BlockMonitorSetup.Column.LEFT, BlockMonitorSetup.Column.CENTER, BlockMonitorSetup.Column.RIGHT
    };

    private final int columns;
    private final BlockMonitorSetup.Column coreColumn;

    public BlockDesk(String name, int columns) {
        this(name, columns, Material.WOOD, SoundType.WOOD, 2.5F, 6F);
    }

    protected BlockDesk(String name, int columns, Material material, SoundType sound,
                        float hardness, float resistance) {
        super(material);
        if (columns < 1 || columns > 3) {
            throw new IllegalArgumentException("columns must be 1, 2 or 3");
        }
        this.columns = columns;
        this.coreColumn = columns == 3 ? BlockMonitorSetup.Column.CENTER
                : columns == 2 ? BlockMonitorSetup.Column.LEFT
                : BlockMonitorSetup.Column.CENTER;

        setRegistryName(NTMDoors.MODID, name);
        setTranslationKey(NTMDoors.MODID + "." + name);
        setCreativeTab(NTMDoors.TAB);
        setHardness(hardness);
        setResistance(resistance);
        setSoundType(sound);
        setLightOpacity(0);
        setDefaultState(blockState.getBaseState()
                .withProperty(FACING, EnumFacing.NORTH)
                .withProperty(COLUMN, coreColumn));
    }

    public int columns() {
        return columns;
    }

    public BlockMonitorSetup.Column coreColumn() {
        return coreColumn;
    }

    /** Columns this desk actually places; the shared column property still exposes all three. */
    public BlockMonitorSetup.Column[] columnValues() {
        return columns == 3 ? THREE_COLUMNS : columns == 2 ? TWO_COLUMNS : ONE_COLUMN;
    }

    public PropertyEnum<BlockMonitorSetup.Column> columnProperty() {
        return COLUMN;
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, COLUMN);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        EnumFacing facing = EnumFacing.byHorizontalIndex(meta & 3);
        if (facing == null) facing = EnumFacing.NORTH;
        int ordinal = (meta >> 2) & 3;
        BlockMonitorSetup.Column column = coreColumn;
        for (BlockMonitorSetup.Column candidate : columnValues()) {
            if (candidate.ordinal() == ordinal) {
                column = candidate;
                break;
            }
        }
        return getDefaultState().withProperty(FACING, facing).withProperty(COLUMN, column);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex()
                | (state.getValue(COLUMN).ordinal() << 2);
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer, EnumHand hand) {
        EnumFacing face = placer == null ? EnumFacing.NORTH : placer.getHorizontalFacing().getOpposite();
        return getDefaultState().withProperty(FACING, face).withProperty(COLUMN, coreColumn);
    }

    /** World offset of one column relative to the core block, rotated for the facing. */
    public BlockPos offset(BlockMonitorSetup.Column column, EnumFacing facing) {
        int dx = columns == 3
                ? column.ordinal() - 1
                : columns == 2
                ? (column == BlockMonitorSetup.Column.LEFT ? 0 : 1)
                : 0;
        return new BlockPos(
                (int) Math.round(Facings.worldX(dx, 0, facing)),
                0,
                (int) Math.round(Facings.worldZ(dx, 0, facing)));
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote && !DISMANTLING.get()) {
            EnumFacing facing = state.getValue(FACING);
            BlockPos corePos = pos.subtract(offset(state.getValue(COLUMN), facing));
            DISMANTLING.set(true);
            try {
                for (BlockMonitorSetup.Column column : columnValues()) {
                    BlockPos p = corePos.add(offset(column, facing));
                    if (!p.equals(pos) && world.getBlockState(p).getBlock() == this) {
                        world.setBlockState(p, Blocks.AIR.getDefaultState(), 2);
                    }
                }
            } finally {
                DISMANTLING.set(false);
            }
        }
        super.breakBlock(world, pos, state);
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
    public EnumPushReaction getPushReaction(IBlockState state) {
        return EnumPushReaction.BLOCK;
    }

    @Override
    public Item getItemDropped(IBlockState state, java.util.Random rand, int fortune) {
        return Item.getItemFromBlock(this);
    }

    @Override
    public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world,
                                  BlockPos pos, EntityPlayer player) {
        return new ItemStack(this);
    }
}
