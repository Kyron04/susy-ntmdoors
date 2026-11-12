package com.ntmdoors.item;

import com.ntmdoors.block.BlockMonitorDesk;
import com.ntmdoors.block.BlockMonitorSetup;
import com.ntmdoors.block.MonitorDesk;
import com.ntmdoors.door.DoorPlacementGuard;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/** Places one of the small multi-block monitor desks in a single use. */
public class ItemMonitorDesk extends BlockItem {

    private final MonitorDesk desk;

    public ItemMonitorDesk(BlockMonitorDesk core, MonitorDesk desk, Settings settings) {
        super(core, settings);
        this.desk = desk;
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        PlayerEntity player = context.getPlayer();
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        Direction side = context.getSide();
        ItemStack stack = context.getStack();
        if (stack.isEmpty()) return ActionResult.FAIL;

        BlockState clicked = world.getBlockState(pos);
        ItemPlacementContext placementContext = new ItemPlacementContext(context);
        BlockPos corePos = clicked.canReplace(placementContext) ? pos : pos.offset(side);
        Direction facing = player == null ? Direction.NORTH : player.getHorizontalFacing().getOpposite();

        for (BlockMonitorDesk block : desk.blocks()) {
            for (BlockMonitorSetup.Column column : block.columns()) {
                BlockPos p = corePos.add(desk.offset(block, column, facing));
                if (world.isOutOfHeightLimit(p) || !world.isChunkLoaded(p)) return ActionResult.FAIL;
                if (player != null && !player.canModifyAt(world, p)) return ActionResult.FAIL;
                if (DoorPlacementGuard.blocksPlacement(world, p)) return ActionResult.FAIL;
                ItemPlacementContext cellContext = new ItemPlacementContext(
                        new ItemUsageContext(player, context.getHand(), new BlockHitResult(context.getHitPos(), side, p, false)));
                if (!world.getBlockState(p).canReplace(cellContext)) return ActionResult.FAIL;
            }
        }

        if (!world.isClient()) {
            for (BlockMonitorDesk block : desk.blocks()) {
                for (BlockMonitorSetup.Column column : block.columns()) {
                    BlockPos p = corePos.add(desk.offset(block, column, facing));
                    BlockState state = block.getDefaultState()
                            .with(BlockMonitorDesk.FACING, facing)
                            .with(block.columnProperty(), column);
                    world.setBlockState(p, state, Block.NOTIFY_ALL);
                }
            }
        }

        BlockSoundGroup sound = desk.core().getDefaultState().getSoundGroup();
        world.playSound(player, corePos, sound.getPlaceSound(), SoundCategory.BLOCKS,
                (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
        if (player != null && !player.isCreative()) {
            stack.decrement(1);
        }
        return ActionResult.SUCCESS;
    }
}
