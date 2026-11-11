package com.ntmdoors.item;

import com.ntmdoors.block.door.BlockDoorGeneric;
import com.ntmdoors.block.door.DoorPart;
import com.ntmdoors.block.entity.DoorBlockEntity;
import com.ntmdoors.block.entity.DoorPartBlockEntity;
import com.ntmdoors.door.DoorPlacementGuard;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * Places the complete multiblock door footprint in a single placement action.
 * Verifies clearance across all cells and swept-volume safety via DoorPlacementGuard.
 */
public class ItemDoorBlock extends BlockItem {

    private final BlockDoorGeneric door;

    public ItemDoorBlock(BlockDoorGeneric block, Settings settings) {
        super(block, settings);
        this.door = block;
    }

    public BlockDoorGeneric getDoorBlock() {
        return door;
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        PlayerEntity player = context.getPlayer();
        Direction side = context.getSide();
        ItemStack stack = context.getStack();

        if (stack.isEmpty()) return ActionResult.FAIL;

        BlockState clicked = world.getBlockState(pos);
        BlockPos origin = clicked.isReplaceable() ? pos : pos.offset(side);
        Direction facing = player != null ? player.getHorizontalFacing().getOpposite() : Direction.NORTH;
        BlockPos[] cells = door.decl.getCells(facing);

        // Placement guard: check moving panels sweep
        if (DoorPlacementGuard.blocksPlacement(world, origin)) {
            return ActionResult.FAIL;
        }

        // Clearance check for all cells
        for (BlockPos off : cells) {
            BlockPos p = origin.add(off);
            if (!world.isInBuildLimit(p) || !world.isChunkLoaded(p)) return ActionResult.FAIL;
            if (player != null && !world.canPlayerModifyAt(player, p)) return ActionResult.FAIL;
            if (!world.getBlockState(p).isReplaceable()) return ActionResult.FAIL;
        }

        if (world.isClient) return ActionResult.SUCCESS;

        BlockState dummyState = door.getDefaultState()
                .with(BlockDoorGeneric.FACING, facing)
                .with(BlockDoorGeneric.PART, DoorPart.DUMMY);
        BlockState coreState = door.getDefaultState()
                .with(BlockDoorGeneric.FACING, facing)
                .with(BlockDoorGeneric.PART, DoorPart.CORE);

        // Spawn dummies
        for (BlockPos off : cells) {
            if (BlockPos.ORIGIN.equals(off)) continue;
            BlockPos p = origin.add(off);
            world.setBlockState(p, dummyState, Block.NOTIFY_ALL);
            BlockEntity te = world.getBlockEntity(p);
            if (te instanceof DoorPartBlockEntity part) {
                part.setCorePos(origin);
            }
        }

        // Spawn core
        world.setBlockState(origin, coreState, Block.NOTIFY_ALL);

        // Notify neighbors
        for (BlockPos off : cells) {
            world.updateNeighbors(origin.add(off), door);
        }

        BlockEntity te = world.getBlockEntity(origin);
        if (te instanceof DoorBlockEntity core) {
            if (door.isSecurity() && player != null) {
                core.setOwner(player);
            }
            core.checkRedstone();
        }

        BlockSoundGroup soundGroup = door.getDefaultState().getSoundGroup();
        world.playSound(null, origin, soundGroup.getPlaceSound(), SoundCategory.BLOCKS,
                (soundGroup.getVolume() + 1.0F) / 2.0F, soundGroup.getPitch() * 0.8F);

        if (player != null && !player.isCreative()) {
            stack.decrement(1);
        }
        return ActionResult.SUCCESS;
    }
}
