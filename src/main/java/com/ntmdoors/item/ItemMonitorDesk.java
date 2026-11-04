package com.ntmdoors.item;

import com.ntmdoors.block.BlockMonitorDesk;
import com.ntmdoors.block.BlockMonitorSetup;
import com.ntmdoors.block.MonitorDesk;
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

/** Places one of the small multi-block monitor desks in a single use. */
public class ItemMonitorDesk extends ItemBlock {

    private final MonitorDesk desk;

    public ItemMonitorDesk(BlockMonitorDesk core, MonitorDesk desk) {
        super(core);
        this.desk = desk;
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                      EnumFacing side, float hitX, float hitY, float hitZ) {
        ItemStack stack = player.getHeldItem(hand);
        if (stack.isEmpty()) return EnumActionResult.FAIL;

        IBlockState clicked = world.getBlockState(pos);
        BlockPos corePos = clicked.getBlock().isReplaceable(world, pos) ? pos : pos.offset(side);
        EnumFacing facing = player.getHorizontalFacing().getOpposite();

        for (BlockMonitorDesk block : desk.blocks()) {
            for (BlockMonitorSetup.Column column : block.columns()) {
                BlockPos p = corePos.add(block.offset(column, facing));
                if (world.isOutsideBuildHeight(p) || !world.isBlockLoaded(p)) return EnumActionResult.FAIL;
                if (!player.canPlayerEdit(p, side, stack)) return EnumActionResult.FAIL;
                if (!world.getBlockState(p).getBlock().isReplaceable(world, p)) return EnumActionResult.FAIL;
            }
        }

        if (!world.isRemote) {
            for (BlockMonitorDesk block : desk.blocks()) {
                for (BlockMonitorSetup.Column column : block.columns()) {
                    BlockPos p = corePos.add(block.offset(column, facing));
                    IBlockState state = block.getDefaultState()
                            .withProperty(BlockMonitorDesk.FACING, facing)
                            .withProperty(block.columnProperty(), column);
                    world.setBlockState(p, state, 2);
                }
            }
            for (BlockMonitorDesk block : desk.blocks()) {
                for (BlockMonitorSetup.Column column : block.columns()) {
                    world.notifyNeighborsOfStateChange(corePos.add(block.offset(column, facing)), block, true);
                }
            }
        }

        SoundType sound = desk.core().getSoundType(desk.core().getDefaultState(), world, corePos, player);
        world.playSound(player, corePos, sound.getPlaceSound(), SoundCategory.BLOCKS,
                (sound.getVolume() + 1F) / 2F, sound.getPitch() * 0.8F);
        if (!player.capabilities.isCreativeMode) stack.shrink(1);
        return EnumActionResult.SUCCESS;
    }
}
