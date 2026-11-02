package com.ntmdoors.item;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.tile.RedstoneBinding;
import com.ntmdoors.tile.TileEntityKeycardScanner;
import com.ntmdoors.tile.TileEntityRedstoneGate;
import com.ntmdoors.tile.TileEntitySafe;
import com.ntmdoors.util.Names;
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
 * Redstone Relay Key. Sneak right-click a safe or scanner to start or finish a wireless binding
 * session, then sneak right-click any Redstone Gate to link or unlink it.
 */
public class ItemRedstoneRelayKey extends Item {

    public ItemRedstoneRelayKey() {
        setRegistryName(NTMDoors.MODID, "redstone_relay_key");
        setTranslationKey(NTMDoors.MODID + ".redstone_relay_key");
        setCreativeTab(NTMDoors.TAB);
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return TextFormatting.RED + super.getItemStackDisplayName(stack);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                      EnumFacing facing, float hitX, float hitY, float hitZ) {
        // Vanilla skips Block.onBlockActivated for a sneaking player, so the item owns this action.
        if (!player.isSneaking()) return EnumActionResult.PASS;

        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileEntitySafe) {
                ((TileEntitySafe) te).getRedstoneBinding().toggle(player);
            } else if (te instanceof TileEntityKeycardScanner) {
                ((TileEntityKeycardScanner) te).getRedstoneBinding().toggle(player);
            } else if (te instanceof TileEntityRedstoneGate) {
                RedstoneBinding binding = RedstoneBinding.findSession(world, player);
                if (binding == null) {
                    player.sendStatusMessage(new TextComponentTranslation(
                            "ntmdoors.msg.redstone_binding_required"), true);
                } else {
                    binding.link(player, (TileEntityRedstoneGate) te);
                }
            }
        }
        return EnumActionResult.SUCCESS;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        Names.addDescription(tooltip,
                "Allows Keypads, Keycard Scanners, and Safes",
                "to be bound to the Redstone Gate");
    }
}
