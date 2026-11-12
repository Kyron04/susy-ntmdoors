package com.ntmdoors.block;

import com.ntmdoors.block.entity.MonitorDeskBlockEntity;
import com.ntmdoors.item.ModItems;
import com.ntmdoors.monitor.SecurityEvents;
import com.ntmdoors.network.ModMessages;
import com.ntmdoors.util.Facings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * A 3x3x1 six-monitor desk. Three block types (bottom, middle, top) each carry three columns.
 * A single block entity on the bottom-centre core stores the terminal state and password.
 */
public class BlockMonitorSetup extends BlockWithEntity {

    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final EnumProperty<Column> COLUMN = EnumProperty.of("column", Column.class);

    public enum Layer {
        BOTTOM,
        MIDDLE,
        TOP
    }

    public enum Column implements StringIdentifiable {
        LEFT,
        CENTER,
        RIGHT;

        @Override
        public String asString() {
            return name().toLowerCase();
        }
    }

    public static final VoxelShape SHAPE_BOTTOM = VoxelShapes.fullCube();
    public static final VoxelShape SHAPE_MONITOR_NORTH = VoxelShapes.cuboid(0.0, 0.0, 0.5, 1.0, 1.0, 1.0);
    public static final VoxelShape SHAPE_MONITOR_SOUTH = VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 1.0, 0.5);
    public static final VoxelShape SHAPE_MONITOR_EAST  = VoxelShapes.cuboid(0.0, 0.0, 0.0, 0.5, 1.0, 1.0);
    public static final VoxelShape SHAPE_MONITOR_WEST  = VoxelShapes.cuboid(0.5, 0.0, 0.0, 1.0, 1.0, 1.0);

    private final Layer layer;

    public BlockMonitorSetup(String name, Layer layer) {
        super(Settings.create()
                .strength(2.5F, 6.0F)
                .sounds(BlockSoundGroup.WOOD)
                .nonOpaque());
        this.layer = layer;
        setDefaultState(getStateManager().getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(COLUMN, Column.CENTER));
    }

    public Layer layer() {
        return layer;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, COLUMN);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    public static BlockPos offset(Layer layer, Column column, Direction facing) {
        int dx = column.ordinal() - 1;
        return new BlockPos(
                (int) Math.round(Facings.worldX(dx, 0, facing)),
                layer.ordinal(),
                (int) Math.round(Facings.worldZ(dx, 0, facing)));
    }

    public BlockPos offset(Column column, Direction facing) {
        return offset(layer, column, facing);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction face = ctx.getPlayer() == null ? Direction.NORTH : ctx.getPlayer().getHorizontalFacing().getOpposite();
        return getDefaultState().with(FACING, face).with(COLUMN, Column.CENTER);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return (layer == Layer.BOTTOM && state.get(COLUMN) == Column.CENTER)
                ? new MonitorDeskBlockEntity(pos, state)
                : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (layer != Layer.BOTTOM || state.get(COLUMN) != Column.CENTER) return null;
        return (w, p, s, be) -> {
            if (be instanceof MonitorDeskBlockEntity deskBe) {
                MonitorDeskBlockEntity.tick(w, p, s, deskBe);
            }
        };
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (layer == Layer.BOTTOM || player.isSneaking()) return ActionResult.PASS;
        if (!world.isClient() && player instanceof ServerPlayerEntity serverPlayer) {
            BlockPos core = pos.subtract(offset(state.get(COLUMN), state.get(FACING)));
            BlockEntity be = world.getBlockEntity(core);
            if (be instanceof MonitorDeskBlockEntity monitor) {
                int screenIndex = (layer == Layer.TOP ? 3 : 0) + state.get(COLUMN).ordinal();
                ModMessages.sendMonitorState(serverPlayer, core, (byte) 2, monitor.hasPassword(), monitor.isPasswordSkipped(), screenIndex);
            }
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient() && player.isCreative()) {
            BlockPos corePos = pos.subtract(offset(state.get(COLUMN), state.get(FACING)));
            BlockEntity be = world.getBlockEntity(corePos);
            if (be instanceof MonitorDeskBlockEntity core) {
                core.setCreativeBroken(true);
            }
        }
        super.onBreak(world, pos, state, player);
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && !world.isClient()) {
            Direction facing = state.get(FACING);
            BlockPos corePos = pos.subtract(offset(state.get(COLUMN), facing));
            BlockEntity be = world.getBlockEntity(corePos);
            if (be instanceof MonitorDeskBlockEntity core && core.beginDismantle()) {
                core.dropBackupContents(world, corePos);
                SecurityEvents.removeDesk(world, corePos);
                if (!core.isCreativeBroken()) {
                    dropStack(world, pos, new ItemStack(ModItems.MONITOR_SETUP_ITEM));
                }
                for (Layer l : Layer.values()) {
                    for (Column c : Column.values()) {
                        BlockPos p = corePos.add(offset(l, c, facing));
                        if (!p.equals(pos) && world.getBlockState(p).getBlock() instanceof BlockMonitorSetup) {
                            world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                        }
                    }
                }
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return layer == Layer.BOTTOM ? SHAPE_BOTTOM : getMonitorShape(state.get(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return layer == Layer.BOTTOM ? SHAPE_BOTTOM : getMonitorShape(state.get(FACING));
    }

    public static VoxelShape getMonitorShape(Direction facing) {
        switch (facing) {
            case SOUTH: return SHAPE_MONITOR_SOUTH;
            case EAST:  return SHAPE_MONITOR_EAST;
            case WEST:  return SHAPE_MONITOR_WEST;
            case NORTH:
            default:    return SHAPE_MONITOR_NORTH;
        }
    }
}
