package com.ntmdoors.item;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.block.BlockDoorGeneric;
import com.ntmdoors.tile.TileEntityDoorGeneric;
import com.ntmdoors.tile.TileEntitySafe;
import com.ntmdoors.util.Names;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Safe Binding Key. A creative-only USB key that links a safe to doors. Sneak right-click a safe to
 * start or finish the session, then sneak right-click closed doors to link or unlink them. A linked
 * door closes once when the safe enters a lockout, provided Binding Activities is on.
 */
public class ItemSafeBindingKey extends Item {

    public ItemSafeBindingKey() {
        setRegistryName(NTMDoors.MODID, "safe_binding_key");
        setTranslationKey(NTMDoors.MODID + ".safe_binding_key");
        setCreativeTab(NTMDoors.TAB);
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return TextFormatting.GREEN + super.getItemStackDisplayName(stack);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                      EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!player.isSneaking()) return EnumActionResult.PASS;

        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileEntitySafe) {
            if (!world.isRemote) ((TileEntitySafe) te).toggleBinding(player);
            return EnumActionResult.SUCCESS;
        }

        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockDoorGeneric)) return EnumActionResult.PASS;

        if (!world.isRemote) {
            TileEntityDoorGeneric door = ((BlockDoorGeneric) state.getBlock()).getCore(world, pos, state);
            if (door == null) return EnumActionResult.SUCCESS;

            TileEntitySafe safe = TileEntitySafe.findBindingSafe(world, player);
            if (safe == null) {
                player.sendStatusMessage(new TextComponentTranslation(
                        "ntmdoors.msg.safe_binding_required"), true);
            } else {
                safe.bindDoor(player, door);
            }
        }
        return EnumActionResult.SUCCESS;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        Names.addDescription(tooltip,
                "Binds safes to the security infrastructure",
                "to protect your stuff.",
                "Sneak + Right Click a safe to start, then",
                "Sneak + Right Click doors to link them.");
    }
}
