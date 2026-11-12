package com.ntmdoors.block;

import com.ntmdoors.block.entity.KeycardScannerBlockEntity;
import com.ntmdoors.block.entity.ModBlockEntities;
import com.ntmdoors.item.ItemCreativeKeycard;
import com.ntmdoors.item.ItemKeycardHolder;
import com.ntmdoors.item.ItemOperatorKeycard;
import com.ntmdoors.item.ItemRedstoneRelayKey;
import com.ntmdoors.item.ItemRfidCard;
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
 * Keycard Scanner block that evaluates card snapshots, controls doors, and displays 3-color LED states.
 */
public class KeycardScannerBlock extends BlockWithEntity {

    public static final DirectionProperty FACING = HorizontalFacingBlock.FACING;

    public static final double MODEL_OFFSET_Y = 0.22;
    public static final double MODEL_OFFSET_Z = -0.50;
    public static final double MODEL_SCALE_X = 0.36;
    public static final double MODEL_SCALE_Y = 0.55;
    public static final double MODEL_SCALE_Z = 0.30;
    public static final double MODEL_HALF_WIDTH = 0.375;

    private static final VoxelShape[] SHAPES = new VoxelShape[4];

    static {
        double halfWidth = MODEL_HALF_WIDTH * MODEL_SCALE_X;  // 0.135
        double depth = MODEL_SCALE_Z * MODEL_SCALE_Z;        // 0.09
        Box local = new Box(
                -halfWidth, MODEL_OFFSET_Y, MODEL_OFFSET_Z,
                halfWidth, MODEL_OFFSET_Y + MODEL_SCALE_Y, MODEL_OFFSET_Z + depth);
        for (Direction dir : Direction.Type.HORIZONTAL) {
            Box rotated = Facings.rotate(local, dir).offset(0.5, 0, 0.5);
            SHAPES[dir.getHorizontal()] = VoxelShapes.cuboid(rotated);
        }
    }

    public KeycardScannerBlock() {
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
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        ItemStack held = player.getStackInHand(hand);

        // Tool and configuration items must be allowed to trigger their useOnBlock on both client & server
        if (held.getItem() instanceof ItemScrewdriver || held.getItem() instanceof ItemRedstoneRelayKey
                || held.getItem() instanceof ItemTerminalRelayKey) {
            return ActionResult.PASS;
        }

        if (player.isSneaking() && (held.getItem() instanceof ItemOperatorKeycard
                || held.getItem() instanceof ItemCreativeKeycard
                || held.getItem() instanceof ItemKeycardHolder)) {
            return ActionResult.PASS;
        }

        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof KeycardScannerBlockEntity scanner)) return ActionResult.PASS;

        if (held.getItem() instanceof ItemKeycardHolder) {
            if (!world.isClient) {
                scanner.useHolder(player, held);
            }
            return ActionResult.success(world.isClient);
        }

        if (held.getItem() instanceof ItemRfidCard || player.getOffHandStack().getItem() instanceof ItemRfidCard) {
            if (!world.isClient) {
                scanner.useCard(player);
            }
            return ActionResult.success(world.isClient);
        }

        return ActionResult.PASS;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new KeycardScannerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return checkType(type, ModBlockEntities.KEYCARD_SCANNER, KeycardScannerBlockEntity::tick);
    }
}
