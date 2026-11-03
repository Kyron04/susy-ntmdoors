package com.ntmdoors.item;

import com.ntmdoors.block.BlockMonitorSetup;
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

import java.util.List;

/** Places the whole 3x3x1 desk in one go; the core sits at the bottom centre of the footprint. */
public class ItemMonitorSetup extends ItemBlock {

    private final BlockMonitorSetup core;
    private final List<BlockMonitorSetup> layers;

    public ItemMonitorSetup(BlockMonitorSetup core, List<BlockMonitorSetup> layers) {
        super(core);
        this.core = core;
        this.layers = layers;
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                      EnumFacing side, float hitX, float hitY, float hitZ) {
        ItemStack stack = player.getHeldItem(hand);
        if (stack.isEmpty()) return EnumActionResult.FAIL;

        IBlockState clicked = world.getBlockState(pos);
        BlockPos corePos = clicked.getBlock().isReplaceable(world, pos) ? pos : pos.offset(side);
        EnumFacing facing = player.getHorizontalFacing().getOpposite();

        for (BlockMonitorSetup block : layers) {
            for (BlockMonitorSetup.Column column : BlockMonitorSetup.Column.values()) {
                BlockPos p = corePos.add(block.offset(column, facing));
                if (world.isOutsideBuildHeight(p) || !world.isBlockLoaded(p)) return EnumActionResult.FAIL;
                if (!player.canPlayerEdit(p, side, stack)) return EnumActionResult.FAIL;
                if (!world.getBlockState(p).getBlock().isReplaceable(world, p)) return EnumActionResult.FAIL;
            }
        }

        if (!world.isRemote) {
            for (BlockMonitorSetup block : layers) {
                for (BlockMonitorSetup.Column column : BlockMonitorSetup.Column.values()) {
                    BlockPos p = corePos.add(block.offset(column, facing));
                    IBlockState state = block.getDefaultState()
                            .withProperty(BlockMonitorSetup.FACING, facing)
                            .withProperty(BlockMonitorSetup.COLUMN, column);
                    world.setBlockState(p, state, 2);
                }
            }
            for (BlockMonitorSetup block : layers) {
                for (BlockMonitorSetup.Column column : BlockMonitorSetup.Column.values()) {
                    world.notifyNeighborsOfStateChange(corePos.add(block.offset(column, facing)), block, true);
                }
            }
        }

        SoundType sound = core.getSoundType(core.getDefaultState(), world, corePos, player);
        world.playSound(player, corePos, sound.getPlaceSound(), SoundCategory.BLOCKS,
                (sound.getVolume() + 1F) / 2F, sound.getPitch() * 0.8F);
        if (!player.capabilities.isCreativeMode) stack.shrink(1);
        return EnumActionResult.SUCCESS;
    }
}
