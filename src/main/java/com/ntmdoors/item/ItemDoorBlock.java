package com.ntmdoors.item;

import com.ntmdoors.block.BlockDoorGeneric;
import com.ntmdoors.tile.TileEntityDoorGeneric;
import com.ntmdoors.tile.TileEntityDoorPart;
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

/** Places the whole multiblock footprint in one go; the door's front faces the player. */
public class ItemDoorBlock extends ItemBlock {

    private final BlockDoorGeneric door;

    public ItemDoorBlock(BlockDoorGeneric block) {
        super(block);
        this.door = block;
    }

    /** Always use the door's resolved name so the creative menu and JEI never show a raw key. */
    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        if (stack.hasDisplayName()) return stack.getDisplayName();
        return door.getLocalizedName();
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing side,
                                      float hitX, float hitY, float hitZ) {
        ItemStack stack = player.getHeldItem(hand);
        if (stack.isEmpty()) return EnumActionResult.FAIL;

        IBlockState clicked = world.getBlockState(pos);
        BlockPos origin = clicked.getBlock().isReplaceable(world, pos) ? pos : pos.offset(side);
        EnumFacing facing = player.getHorizontalFacing().getOpposite();
        BlockPos[] cells = door.decl.getCells(facing);

        for (BlockPos off : cells) {
            BlockPos p = origin.add(off);
            if (world.isOutsideBuildHeight(p) || !world.isBlockLoaded(p)) return EnumActionResult.FAIL;
            if (!player.canPlayerEdit(p, side, stack)) return EnumActionResult.FAIL;
            if (!world.getBlockState(p).getBlock().isReplaceable(world, p)) return EnumActionResult.FAIL;
        }

        if (!world.isRemote) {
            IBlockState dummy = door.getDefaultState()
                    .withProperty(BlockDoorGeneric.FACING, facing)
                    .withProperty(BlockDoorGeneric.PART, BlockDoorGeneric.Part.DUMMY);
            IBlockState core = door.getDefaultState()
                    .withProperty(BlockDoorGeneric.FACING, facing)
                    .withProperty(BlockDoorGeneric.PART, BlockDoorGeneric.Part.CORE);

            for (BlockPos off : cells) {
                if (off.getX() == 0 && off.getY() == 0 && off.getZ() == 0) continue;
                BlockPos p = origin.add(off);
                world.setBlockState(p, dummy, 2);
                TileEntity te = world.getTileEntity(p);
                if (te instanceof TileEntityDoorPart) ((TileEntityDoorPart) te).setCorePos(origin);
            }
            world.setBlockState(origin, core, 2);

            for (BlockPos off : cells) world.notifyNeighborsOfStateChange(origin.add(off), door, true);

            TileEntity te = world.getTileEntity(origin);
            if (te instanceof TileEntityDoorGeneric) ((TileEntityDoorGeneric) te).checkRedstone();
        }

        SoundType st = SoundType.METAL;
        world.playSound(player, origin, st.getPlaceSound(), SoundCategory.BLOCKS, (st.getVolume() + 1F) / 2F, st.getPitch() * 0.8F);
        if (!player.capabilities.isCreativeMode) stack.shrink(1);
        return EnumActionResult.SUCCESS;
    }
}
