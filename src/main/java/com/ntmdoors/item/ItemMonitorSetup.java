package com.ntmdoors.item;

import com.ntmdoors.block.BlockMonitorSetup;
import com.ntmdoors.tile.TileEntityMonitorSetup;
import net.minecraft.block.SoundType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Places the whole 3x3x1 desk in one go; the core sits at the bottom centre of the footprint. */
public class ItemMonitorSetup extends ItemBlock {

    private final BlockMonitorSetup monitor;

    public ItemMonitorSetup(BlockMonitorSetup block) {
        super(block);
        this.monitor = block;
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                      EnumFacing side, float hitX, float hitY, float hitZ) {
        ItemStack stack = player.getHeldItem(hand);
        if (stack.isEmpty()) return EnumActionResult.FAIL;

        IBlockState clicked = world.getBlockState(pos);
        BlockPos corePos = clicked.getBlock().isReplaceable(world, pos) ? pos : pos.offset(side);
        EnumFacing facing = player.getHorizontalFacing().getOpposite();
        BlockMonitorSetup.Part[] parts = BlockMonitorSetup.Part.values();

        for (BlockMonitorSetup.Part part : parts) {
            BlockPos p = corePos.add(BlockMonitorSetup.offset(part, facing));
            if (world.isOutsideBuildHeight(p) || !world.isBlockLoaded(p)) return EnumActionResult.FAIL;
            if (!player.canPlayerEdit(p, side, stack)) return EnumActionResult.FAIL;
            if (!world.getBlockState(p).getBlock().isReplaceable(world, p)) return EnumActionResult.FAIL;
        }

        if (!world.isRemote) {
            for (BlockMonitorSetup.Part part : parts) {
                if (part.isCore()) continue;
                placePart(world, corePos, facing, part);
            }
            placePart(world, corePos, facing, BlockMonitorSetup.Part.BOTTOM_CENTER);

            for (BlockMonitorSetup.Part part : parts) {
                world.notifyNeighborsOfStateChange(corePos.add(BlockMonitorSetup.offset(part, facing)),
                        monitor, true);
            }
        }

        SoundType sound = monitor.getSoundType(monitor.getDefaultState(), world, corePos, player);
        world.playSound(player, corePos, sound.getPlaceSound(), SoundCategory.BLOCKS,
                (sound.getVolume() + 1F) / 2F, sound.getPitch() * 0.8F);
        if (!player.capabilities.isCreativeMode) stack.shrink(1);
        return EnumActionResult.SUCCESS;
    }

    private void placePart(World world, BlockPos corePos, EnumFacing facing,
                           BlockMonitorSetup.Part part) {
        BlockPos p = corePos.add(BlockMonitorSetup.offset(part, facing));
        IBlockState state = monitor.getDefaultState()
                .withProperty(BlockMonitorSetup.FACING, facing)
                .withProperty(BlockMonitorSetup.PART, part);
        world.setBlockState(p, state, 2);

        TileEntity te = world.getTileEntity(p);
        if (te instanceof TileEntityMonitorSetup) {
            TileEntityMonitorSetup monitorTile = (TileEntityMonitorSetup) te;
            monitorTile.setPart(part);
            monitorTile.setCorePos(corePos);
        }
    }
}
