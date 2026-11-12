package com.ntmdoors.block.door;

import com.ntmdoors.block.entity.DoorBlockEntity;
import com.ntmdoors.block.entity.DoorPartBlockEntity;
import com.ntmdoors.door.DoorDecl;
import com.ntmdoors.door.DoorState;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * Modern Fabric 1.20.1 implementation of multiblock doors.
 * One CORE cell hosts DoorBlockEntity, all other footprint cells host DoorPartBlockEntity.
 * Rendering is handled by DoorBlockEntityRenderer; collision is dynamically clipped per-cell.
 */
public class BlockDoorGeneric extends BlockWithEntity {

    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final EnumProperty<DoorPart> PART = EnumProperty.of("part", DoorPart.class);
    public static final BooleanProperty PASS = BooleanProperty.of("pass");
    public static final BooleanProperty OPEN = BooleanProperty.of("open");
    public static final BooleanProperty LOCKED = BooleanProperty.of("locked");
    public static final BooleanProperty SECURITY = BooleanProperty.of("security");

    public enum Variant {
        NORMAL("", ""),
        LOCKED("_locked", " (Locked)"),
        SECURITY("_security", " (Security)");

        public final String suffix;
        public final String displaySuffix;

        Variant(String suffix, String displaySuffix) {
            this.suffix = suffix;
            this.displaySuffix = displaySuffix;
        }
    }

    public final DoorDecl decl;
    public final Variant variant;
    public final boolean locked;
    public final boolean security;

    public BlockDoorGeneric(DoorDecl decl, Variant variant, Settings settings) {
        super(settings);
        this.decl = decl;
        this.variant = variant;
        this.locked = (variant == Variant.LOCKED);
        this.security = (variant == Variant.SECURITY);
        setDefaultState(getStateManager().getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(PART, DoorPart.CORE)
                .with(PASS, false)
                .with(OPEN, false)
                .with(LOCKED, this.locked)
                .with(SECURITY, this.security));
    }

    public boolean isSecurity() {
        return security;
    }

    public boolean isLocked() {
        return locked;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, PASS, OPEN, LOCKED, SECURITY);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.INVISIBLE;
    }

    @Override
    public net.minecraft.fluid.FluidState getFluidState(BlockState state) {
        try {
            return super.getFluidState(state);
        } catch (Throwable t) {
            return null;
        }
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return state.get(PART) == DoorPart.CORE
                ? new DoorBlockEntity(pos, state)
                : new DoorPartBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (state.get(PART) != DoorPart.CORE) return null;
        return checkType(type, ModDoors.DOOR_BLOCK_ENTITY, DoorBlockEntity::tick);
    }

    @Nullable
    public static DoorBlockEntity getCore(BlockView world, BlockPos pos, BlockState state) {
        if (state.get(PART) == DoorPart.CORE) {
            BlockEntity be = world.getBlockEntity(pos);
            return be instanceof DoorBlockEntity ? (DoorBlockEntity) be : null;
        }
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof DoorPartBlockEntity part && part.getCorePos() != null) {
            BlockEntity coreBe = world.getBlockEntity(part.getCorePos());
            return coreBe instanceof DoorBlockEntity ? (DoorBlockEntity) coreBe : null;
        }
        return null;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (player.isSneaking()) return ActionResult.PASS;
        if (world.isClient) return ActionResult.SUCCESS;
        DoorBlockEntity core = getCore(world, pos, state);
        if (core != null) {
            core.onPlayerUse(player);
        }
        return ActionResult.CONSUME;
    }

    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        super.neighborUpdate(state, world, pos, sourceBlock, sourcePos, notify);
        if (!world.isClient) {
            DoorBlockEntity core = getCore(world, pos, state);
            if (core != null) {
                core.checkRedstone();
            }
        }
    }

    @Override
    public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient && player.isCreative()) {
            DoorBlockEntity core = getCore(world, pos, state);
            if (core != null) core.setCreativeBroken(true);
        }
        super.onBreak(world, pos, state, player);
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            if (!world.isClient) {
                DoorBlockEntity core = getCore(world, pos, state);
                if (core != null && core.beginDismantle()) {
                    core.unbindFromControllers();
                    if (!core.isCreativeBroken()) {
                        dropStack(world, pos, new ItemStack(this.asItem()));
                    }
                    BlockPos corePos = core.getPos();
                    Direction facing = state.get(FACING);
                    for (BlockPos off : decl.getCells(facing)) {
                        BlockPos p = corePos.add(off);
                        if (!p.equals(pos) && world.getBlockState(p).isOf(this)) {
                            world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                        }
                    }
                }
            }
            super.onStateReplaced(state, world, pos, newState, moved);
        }
    }

    @Override
    public float calcBlockBreakingDelta(BlockState state, PlayerEntity player, BlockView world, BlockPos pos) {
        if (player.isCreative()) return 1.0F;
        if (security) {
            return 1.0F / 216000.0F;
        }
        return super.calcBlockBreakingDelta(state, player, world, pos);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        if (decl.passFallingWhenOpen && state.get(PASS)) {
            return VoxelShapes.empty();
        }
        DoorBlockEntity core = getCore(world, pos, state);
        if (core == null) {
            return VoxelShapes.fullCube();
        }

        BlockPos corePos = core.getPos();
        BlockPos offset = pos.subtract(corePos);
        Direction facing = state.get(FACING);

        if (core.isBreached()) {
            return DoorVoxelShapes.getStaticShape(decl, facing, offset, DoorVoxelShapes.ShapeState.BREACHED);
        }
        if (core.getState() == DoorState.CLOSED) {
            return DoorVoxelShapes.getStaticShape(decl, facing, offset, DoorVoxelShapes.ShapeState.CLOSED);
        }
        if (core.getState() == DoorState.OPEN) {
            return DoorVoxelShapes.getStaticShape(decl, facing, offset, DoorVoxelShapes.ShapeState.OPEN);
        }

        Box[] boxes = core.getCollisionBoxes(facing);
        double ox = corePos.getX() + 0.5 - pos.getX();
        double oy = corePos.getY() - pos.getY();
        double oz = corePos.getZ() + 0.5 - pos.getZ();
        return DoorVoxelShapes.clipBoxesToCell(boxes, ox, oy, oz);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        if (decl.passFallingWhenOpen && state.get(PASS)) {
            return VoxelShapes.empty();
        }
        DoorBlockEntity core = getCore(world, pos, state);
        if (core == null) {
            return VoxelShapes.fullCube();
        }

        BlockPos corePos = core.getPos();
        Direction facing = state.get(FACING);
        float openFraction = core.getOpenFraction(1.0f);
        boolean breached = core.isBreached();

        Box[] boxes = decl.getBoxes(facing, openFraction, false, !breached);
        double ox = corePos.getX() + 0.5 - pos.getX();
        double oy = corePos.getY() - pos.getY();
        double oz = corePos.getZ() + 0.5 - pos.getZ();
        VoxelShape shape = DoorVoxelShapes.clipBoxesToCell(boxes, ox, oy, oz);

        if (shape.isEmpty() && pos.equals(corePos)) {
            return VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 0.1, 1.0);
        }
        return shape;
    }

    @Override
    public VoxelShape getCullingShape(BlockState state, BlockView world, BlockPos pos) {
        return VoxelShapes.empty();
    }

    @Override
    public VoxelShape getCameraCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return getCollisionShape(state, world, pos, context);
    }

    @Override
    public List<ItemStack> getDroppedStacks(BlockState state, LootContextParameterSet.Builder builder) {
        return Collections.emptyList();
    }
}
