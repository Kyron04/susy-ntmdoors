package com.ntmdoors.block;

import com.ntmdoors.block.entity.KeypadBlockEntity;
import com.ntmdoors.block.entity.ModBlockEntities;
import com.ntmdoors.item.ItemRedstoneRelayKey;
import com.ntmdoors.item.ItemScrewdriver;
import com.ntmdoors.item.ItemTerminalRelayKey;
import com.ntmdoors.util.Facings;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.enums.Instrument;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
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
 * Wall-mounted keypad block with interactive 3D buttons and world LCD screen.
 */
public class KeypadBlock extends BlockWithEntity {

    public static final DirectionProperty FACING = HorizontalFacingBlock.FACING;

    public static final double MODEL_OFFSET_Y = 0.29375;
    public static final double MODEL_OFFSET_Z = -0.47;
    public static final double MODEL_SCALE_X = 0.35;
    public static final double MODEL_SCALE_Y = 0.4125;
    public static final double MODEL_SCALE_Z = 0.25;
    private static final double MODEL_HALF_WIDTH = 0.375;
    private static final double MODEL_HALF_DEPTH = 0.0625;

    private static final VoxelShape[] SHAPES = new VoxelShape[4];

    static {
        double halfWidth = MODEL_HALF_WIDTH * MODEL_SCALE_X;  // 0.13125
        double halfDepth = MODEL_HALF_DEPTH * MODEL_SCALE_Z;  // 0.015625
        Box local = new Box(
                -halfWidth, MODEL_OFFSET_Y, MODEL_OFFSET_Z - halfDepth,
                halfWidth, MODEL_OFFSET_Y + MODEL_SCALE_Y, MODEL_OFFSET_Z + halfDepth);
        for (Direction dir : Direction.Type.HORIZONTAL) {
            Box rotated = Facings.rotate(local, dir).offset(0.5, 0, 0.5);
            SHAPES[dir.getHorizontal()] = VoxelShapes.cuboid(rotated);
        }
    }

    public KeypadBlock() {
        super(FabricBlockSettings.create()
                .mapColor(MapColor.IRON_GRAY)
                .instrument(Instrument.IRON_XYLOPHONE)
                .strength(3.0F, 6.0F)
                .sounds(BlockSoundGroup.METAL)
                .nonOpaque());
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, net.minecraft.block.ShapeContext context) {
        Direction dir = state.get(FACING);
        return SHAPES[dir.getHorizontal()];
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction face = ctx.getSide();
        if (face.getAxis().isHorizontal()) {
            face = face.getOpposite();
        } else {
            face = ctx.getPlayer() != null ? ctx.getPlayer().getHorizontalFacing().getOpposite() : Direction.NORTH;
        }
        return getDefaultState().with(FACING, face);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (!world.isClient) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof KeypadBlockEntity keypad) {
                keypad.autoSyncWithDoor();
            }
        }
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        ItemStack held = player.getStackInHand(hand);
        if (held.getItem() instanceof ItemScrewdriver || held.getItem() instanceof ItemRedstoneRelayKey
                || held.getItem() instanceof ItemTerminalRelayKey) {
            return ActionResult.PASS;
        }

        if (world.isClient) return ActionResult.SUCCESS;

        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof KeypadBlockEntity keypad)) return ActionResult.PASS;

        double hitX = hit.getPos().x - pos.getX();
        double hitY = hit.getPos().y - pos.getY();
        double hitZ = hit.getPos().z - pos.getZ();

        int button = keypad.buttonAt(hitX, hitY, hitZ);
        if (button >= 0) {
            keypad.pressButton(button, player);
            return ActionResult.SUCCESS;
        }

        return ActionResult.SUCCESS;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new KeypadBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return checkType(type, ModBlockEntities.KEYPAD, KeypadBlockEntity::tick);
    }
}
