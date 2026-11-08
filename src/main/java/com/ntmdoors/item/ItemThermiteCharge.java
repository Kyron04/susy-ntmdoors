package com.ntmdoors.item;

import com.ntmdoors.block.BlockDoorGeneric;
import com.ntmdoors.tile.TileEntityDoorGeneric;
import com.ntmdoors.tile.TileEntityThermiteCharge;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

/**
 * Item form of the Thermite Charge. Placement runs before the clicked block's own interaction, so
 * a charge can be attached directly to doors, safes and other machines without opening them.
 *
 * <p>When the clicked block is a supported door, the hit point is converted into a 0..15 pixel
 * inset and stored on the charge's tile entity. The large/complex doors in
 * {@link #isExcludedDoor} refuse placement instead.</p>
 */
public class ItemThermiteCharge extends ItemBlock {

    public ItemThermiteCharge(Block block) {
        super(block);
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side,
                                           float hitX, float hitY, float hitZ, EnumHand hand) {
        IBlockState clicked = world.getBlockState(pos);
        BlockDoorGeneric door = clicked.getBlock() instanceof BlockDoorGeneric
                ? (BlockDoorGeneric) clicked.getBlock() : null;
        if (door != null && isExcludedDoor(door)) {
            if (!world.isRemote) {
                player.sendStatusMessage(new TextComponentTranslation(
                        "ntmdoors.msg.thermite_door_unsupported"), true);
            }
            // Consume the click so the door itself does not open.
            return EnumActionResult.SUCCESS;
        }

        ItemStack stack = player.getHeldItem(hand);
        int count = stack.getCount();
        BlockPos placedPos = clicked.getBlock().isReplaceable(world, pos) ? pos : pos.offset(side);
        EnumActionResult result = onItemUse(player, world, pos, hand, side, hitX, hitY, hitZ);

        // ItemBlock.onItemUse always shrinks; the normal creative path restores the count itself,
        // but onItemUseFirst bypasses that path.
        if (player.capabilities.isCreativeMode) {
            stack.setCount(count);
        }

        if (result == EnumActionResult.SUCCESS && door != null) {
            int inset = insetFromHit(side, hitX, hitY, hitZ);
            TileEntity te = world.getTileEntity(placedPos);
            if (te instanceof TileEntityThermiteCharge) {
                TileEntityDoorGeneric core = door.getCore(world, pos, clicked);
                BlockPos corePos = core == null ? door.getCorePos(world, pos, clicked) : core.getPos();
                float fraction = core == null ? 0F : core.getOpenFraction(1F);
                ((TileEntityThermiteCharge) te).setMount(inset, corePos, fraction);
            }
        }

        // Only consume the click when the charge actually placed; otherwise let the block react.
        return result == EnumActionResult.SUCCESS ? EnumActionResult.SUCCESS : EnumActionResult.PASS;
    }

    private static int insetFromHit(EnumFacing side, float hitX, float hitY, float hitZ) {
        double surface;
        switch (side) {
            case SOUTH: surface = 1.0D - hitZ; break;
            case NORTH: surface = hitZ; break;
            case EAST: surface = 1.0D - hitX; break;
            case WEST: surface = hitX; break;
            case UP: surface = 1.0D - hitY; break;
            case DOWN: surface = hitY; break;
            default: surface = 0.0D; break;
        }
        return Math.max(0, Math.min(15, (int) Math.round(surface * 16.0D)));
    }

    /** The doors that must not receive a charge. */
    private static boolean isExcludedDoor(BlockDoorGeneric door) {
        String name = door.decl.name;
        return "silo_hatch".equals(name)
                || "silo_hatch_large".equals(name)
                || "transition_seal".equals(name)
                || "blast_door".equals(name)
                || "vault_door".equals(name);
    }
}
