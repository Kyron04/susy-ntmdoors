package com.ntmdoors.item;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.block.BlockDesk;
import com.ntmdoors.block.BlockMonitorSetup;
import com.ntmdoors.door.DoorPlacementGuard;
import net.minecraft.block.SoundType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Places a decorative 1x1, 1x2 or 1x3 desk as one item, with no terminal or tile entity. */
public class ItemDesk extends ItemBlock {

    private final BlockDesk desk;

    public ItemDesk(BlockDesk desk) {
        super(desk);
        this.desk = desk;
        setCreativeTab(NTMDoors.TAB);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                      EnumFacing side, float hitX, float hitY, float hitZ) {
        ItemStack stack = player.getHeldItem(hand);
        if (stack.isEmpty()) return EnumActionResult.FAIL;

        IBlockState clicked = world.getBlockState(pos);
        BlockPos corePos = clicked.getBlock().isReplaceable(world, pos) ? pos : pos.offset(side);
        EnumFacing facing = player.getHorizontalFacing().getOpposite();

        for (BlockMonitorSetup.Column column : desk.columnValues()) {
            BlockPos p = corePos.add(desk.offset(column, facing));
            if (world.isOutsideBuildHeight(p) || !world.isBlockLoaded(p)) return EnumActionResult.FAIL;
            if (!player.canPlayerEdit(p, side, stack)) return EnumActionResult.FAIL;
            if (DoorPlacementGuard.blocksPlacement(world, p)) return EnumActionResult.FAIL;
            if (!world.getBlockState(p).getBlock().isReplaceable(world, p)) return EnumActionResult.FAIL;
        }

        if (!world.isRemote) {
            for (BlockMonitorSetup.Column column : desk.columnValues()) {
                BlockPos p = corePos.add(desk.offset(column, facing));
                IBlockState state = desk.getDefaultState()
                        .withProperty(BlockDesk.FACING, facing)
                        .withProperty(desk.columnProperty(), column);
                world.setBlockState(p, state, 2);
            }
            for (BlockMonitorSetup.Column column : desk.columnValues()) {
                world.notifyNeighborsOfStateChange(corePos.add(desk.offset(column, facing)), desk, true);
            }
        }

        SoundType sound = desk.getSoundType(desk.getDefaultState(), world, corePos, player);
        world.playSound(player, corePos, sound.getPlaceSound(), SoundCategory.BLOCKS,
                (sound.getVolume() + 1F) / 2F, sound.getPitch() * 0.8F);
        if (!player.capabilities.isCreativeMode) stack.shrink(1);
        return EnumActionResult.SUCCESS;
    }
}
