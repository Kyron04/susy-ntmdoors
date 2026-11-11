package com.ntmdoors.block;

import com.ntmdoors.block.entity.ModBlockEntities;
import com.ntmdoors.block.entity.MotionSensorBlockEntity;
import com.ntmdoors.client.MotionSensorOverlay;
import com.ntmdoors.client.screen.MotionSensorScreen;
import com.ntmdoors.item.ItemMotionRelayKey;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.*;
import net.minecraft.block.enums.Instrument;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
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
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class MotionSensorBlock extends BlockWithEntity {

    public static final DirectionProperty FACING = Properties.FACING;
    public static final EnumProperty<MotionSensorStatus> STATUS = EnumProperty.of("status", MotionSensorStatus.class);

    private static final double DEPTH = 0.0859375; // 1.375 pixels
    private static final VoxelShape SHAPE_NORTH = VoxelShapes.cuboid(0.25, 0.25, 1.0 - DEPTH, 0.75, 0.75, 1.0);
    private static final VoxelShape SHAPE_SOUTH = VoxelShapes.cuboid(0.25, 0.25, 0.0, 0.75, 0.75, DEPTH);
    private static final VoxelShape SHAPE_EAST = VoxelShapes.cuboid(0.0, 0.25, 0.25, DEPTH, 0.75, 0.75);
    private static final VoxelShape SHAPE_WEST = VoxelShapes.cuboid(1.0 - DEPTH, 0.25, 0.25, 1.0, 0.75, 0.75);
    private static final VoxelShape SHAPE_UP = VoxelShapes.cuboid(0.25, 0.0, 0.25, 0.75, DEPTH, 0.75);
    private static final VoxelShape SHAPE_DOWN = VoxelShapes.cuboid(0.25, 1.0 - DEPTH, 0.25, 0.75, 1.0, 0.75);

    public MotionSensorBlock() {
        super(FabricBlockSettings.create()
                .mapColor(MapColor.IRON_GRAY)
                .instrument(Instrument.IRON_XYLOPHONE)
                .strength(1.5F, 5.0F)
                .sounds(BlockSoundGroup.METAL)
                .nonOpaque());
        setDefaultState(getStateManager().getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(STATUS, MotionSensorStatus.IDLE));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, STATUS);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getSide()).with(STATUS, MotionSensorStatus.IDLE);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        Direction dir = state.get(FACING);
        return switch (dir) {
            case NORTH -> SHAPE_NORTH;
            case SOUTH -> SHAPE_SOUTH;
            case EAST -> SHAPE_EAST;
            case WEST -> SHAPE_WEST;
            case UP -> SHAPE_UP;
            case DOWN -> SHAPE_DOWN;
        };
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new MotionSensorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return checkType(type, ModBlockEntities.MOTION_SENSOR, (w, p, s, be) -> be.tick(w, p, s));
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        ItemStack held = player.getStackInHand(hand);

        if (held.getItem() instanceof ItemMotionRelayKey) {
            if (player.isSneaking()) {
                if (world.isClient) {
                    MotionSensorOverlay.toggle(pos);
                }
                return ActionResult.success(world.isClient);
            } else {
                if (world.isClient) {
                    MinecraftClient.getInstance().setScreen(new MotionSensorScreen(pos));
                }
                return ActionResult.success(world.isClient);
            }
        }

        if (player.isCreative() && held.isEmpty()) {
            if (!world.isClient) {
                BlockEntity be = world.getBlockEntity(pos);
                if (be instanceof MotionSensorBlockEntity sensor) {
                    sensor.cycleStatus();
                }
            }
            return ActionResult.success(world.isClient);
        }

        return ActionResult.PASS;
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof MotionSensorBlockEntity sensor) {
                sensor.getRedstoneBinding().clearGates();
            }
            super.onStateReplaced(state, world, pos, newState, moved);
        }
    }
}
