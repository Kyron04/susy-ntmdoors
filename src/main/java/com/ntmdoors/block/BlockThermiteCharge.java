package com.ntmdoors.block;

import com.ntmdoors.block.entity.ModBlockEntities;
import com.ntmdoors.block.entity.ThermiteChargeBlockEntity;
import com.ntmdoors.util.Facings;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.MapColor;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.IntProperty;
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

/**
 * Framed thermite breaching charge for Fabric 1.20.1.
 * Mounts flush against the face it was clicked on and points away from that surface.
 * On doors it is inset by the hit point (0..15 sixteenths of a block) so it sits on the door's
 * hitbox rather than floating at the cell boundary.
 */
public class BlockThermiteCharge extends BlockWithEntity {

    public static final DirectionProperty FACING = Properties.FACING;
    public static final IntProperty INSET = IntProperty.of("inset", 0, 15);

    private static final double P = 1.0 / 16.0;
    private static final double DEPTH = 2.416;

    private static final VoxelShape[][] SHAPES = new VoxelShape[6][16];

    static {
        for (Direction dir : Direction.values()) {
            for (int inset = 0; inset < 16; inset++) {
                int pixels = inset;
                Box box;
                switch (dir) {
                    case UP -> box = new Box(1 * P, -pixels * P, 0, 15 * P, (DEPTH - pixels) * P, 1);
                    case DOWN -> box = new Box(1 * P, (16 - DEPTH + pixels) * P, 0, 15 * P, (16 + pixels) * P, 1);
                    default -> {
                        Box local = new Box(1 * P - 0.5, 0, (16 - DEPTH + pixels) * P - 0.5, 15 * P - 0.5, 1, (16 + pixels) * P - 0.5);
                        box = Facings.rotate(local, dir).offset(0.5, 0, 0.5);
                    }
                }
                SHAPES[dir.getId()][inset] = VoxelShapes.cuboid(box);
            }
        }
    }

    public BlockThermiteCharge() {
        super(FabricBlockSettings.create()
                .mapColor(MapColor.IRON_GRAY)
                .strength(1.5F, 10.0F)
                .sounds(BlockSoundGroup.METAL)
                .nonOpaque());
        setDefaultState(getStateManager().getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(INSET, 0));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, INSET);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPES[state.get(FACING).getId()][state.get(INSET)];
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return getOutlineShape(state, world, pos, context);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ThermiteChargeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return checkType(type, ModBlockEntities.THERMITE_CHARGE, ThermiteChargeBlockEntity::tick);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (player.getStackInHand(hand).isOf(Items.FLINT_AND_STEEL)) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof ThermiteChargeBlockEntity charge) {
                if (charge.isLit()) return ActionResult.SUCCESS;
                if (!world.isClient()) {
                    if (charge.light()) {
                        world.playSound(null, pos, SoundEvents.ITEM_FLINTANDSTEEL_USE, SoundCategory.BLOCKS, 1.0F, 0.8F);
                        if (!player.isCreative()) {
                            player.getStackInHand(hand).damage(1, player, p -> p.sendToolBreakStatus(hand));
                        }
                    }
                }
                return ActionResult.SUCCESS;
            }
        }
        return ActionResult.PASS;
    }

    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        super.neighborUpdate(state, world, pos, sourceBlock, sourcePos, notify);
        if (world.isClient()) return;

        Direction facing = state.get(FACING);
        BlockPos support = pos.offset(facing.getOpposite());
        BlockState supportState = world.getBlockState(support);

        if (supportState.isAir() || supportState.isReplaceable()) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof ThermiteChargeBlockEntity charge) {
                if (charge.isLit()) {
                    charge.scheduleExplosion();
                } else {
                    world.breakBlock(pos, true);
                }
            }
        }
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof ThermiteChargeBlockEntity charge) {
                charge.onRemoved();
            }
            super.onStateReplaced(state, world, pos, newState, moved);
        }
    }
}
