package com.ntmdoors.block;

import com.ntmdoors.block.entity.MonitorDeskBlockEntity;
import com.ntmdoors.monitor.SecurityEvents;
import com.ntmdoors.network.ModMessages;
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
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * One cell of a small monitor desk. The bottom layer is the solid desk; the middle layer carries
 * the monitors. A single block entity on the bottom core cell stores the terminal state.
 */
public class BlockMonitorDesk extends BlockWithEntity {

    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final EnumProperty<BlockMonitorSetup.Column> COLUMN =
            EnumProperty.of("column", BlockMonitorSetup.Column.class);

    private final MonitorDesk desk;
    private final BlockMonitorSetup.Layer layer;
    private final Set<BlockMonitorSetup.Column> columns;

    public BlockMonitorDesk(MonitorDesk desk, String name, BlockMonitorSetup.Layer layer,
                            BlockMonitorSetup.Column... columns) {
        super(Settings.create()
                .strength(2.5F, 6.0F)
                .sounds(BlockSoundGroup.WOOD)
                .nonOpaque());
        this.desk = desk;
        this.layer = layer;
        this.columns = Collections.unmodifiableSet(EnumSet.copyOf(Arrays.asList(columns)));
        setDefaultState(getStateManager().getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(columnProperty(), desk.coreColumn()));
        desk.add(this);
    }

    public MonitorDesk desk() {
        return desk;
    }

    public BlockMonitorSetup.Layer layer() {
        return layer;
    }

    public Set<BlockMonitorSetup.Column> columns() {
        return columns;
    }

    public EnumProperty<BlockMonitorSetup.Column> columnProperty() {
        return COLUMN;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, columnProperty());
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction face = ctx.getPlayer() == null ? Direction.NORTH : ctx.getPlayer().getHorizontalFacing().getOpposite();
        return getDefaultState().with(FACING, face).with(columnProperty(), desk.coreColumn());
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return (layer == BlockMonitorSetup.Layer.BOTTOM && state.get(columnProperty()) == desk.coreColumn())
                ? new MonitorDeskBlockEntity(pos, state)
                : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (layer != BlockMonitorSetup.Layer.BOTTOM || state.get(columnProperty()) != desk.coreColumn()) return null;
        return (w, p, s, be) -> {
            if (be instanceof MonitorDeskBlockEntity deskBe) {
                MonitorDeskBlockEntity.tick(w, p, s, deskBe);
            }
        };
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (layer == BlockMonitorSetup.Layer.BOTTOM || player.isSneaking()) return ActionResult.PASS;
        if (this == ModBlocks.SINGLE_MONITOR_SETUP_MIDDLE && state.get(columnProperty()) != BlockMonitorSetup.Column.RIGHT) {
            return ActionResult.PASS;
        }
        if (!world.isClient() && player instanceof ServerPlayerEntity serverPlayer) {
            BlockPos core = pos.subtract(desk.offset(this, state.get(columnProperty()), state.get(FACING)));
            BlockEntity be = world.getBlockEntity(core);
            if (be instanceof MonitorDeskBlockEntity monitor) {
                int screenIndex = state.get(columnProperty()).ordinal();
                ModMessages.sendMonitorState(serverPlayer, core, (byte) 2, monitor.hasPassword(), monitor.isPasswordSkipped(), screenIndex);
            }
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient() && player.isCreative()) {
            BlockPos corePos = pos.subtract(desk.offset(this, state.get(columnProperty()), state.get(FACING)));
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
            BlockPos corePos = pos.subtract(desk.offset(this, state.get(columnProperty()), facing));
            BlockEntity be = world.getBlockEntity(corePos);
            if (be instanceof MonitorDeskBlockEntity core && core.beginDismantle()) {
                core.dropBackupContents(world, corePos);
                SecurityEvents.removeDesk(world, corePos);
                if (!core.isCreativeBroken()) {
                    dropStack(world, pos, new ItemStack(desk.core().asItem()));
                }
                for (BlockMonitorDesk b : desk.blocks()) {
                    for (BlockMonitorSetup.Column c : b.columns()) {
                        BlockPos p = corePos.add(desk.offset(b, c, facing));
                        if (!p.equals(pos) && world.getBlockState(p).isOf(b)) {
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
        return layer == BlockMonitorSetup.Layer.BOTTOM ? BlockMonitorSetup.SHAPE_BOTTOM : BlockMonitorSetup.getMonitorShape(state.get(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return layer == BlockMonitorSetup.Layer.BOTTOM ? BlockMonitorSetup.SHAPE_BOTTOM : BlockMonitorSetup.getMonitorShape(state.get(FACING));
    }
}
