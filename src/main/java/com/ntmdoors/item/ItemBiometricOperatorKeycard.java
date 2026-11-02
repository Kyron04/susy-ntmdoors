package com.ntmdoors.item;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.tile.TileEntityRedstoneGate;
import com.ntmdoors.util.Biometrics;
import com.ntmdoors.util.Names;
import com.ntmdoors.util.PrivateKeys;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Biometric Operator Keycard. Everything the Operator Keycard does - privileged card, sneak
 * right-click on a bound scanner to set its security code - plus a biometric identity and a blue
 * name. The identity is bound onto it by the programmer, never automatically.
 */
public class ItemBiometricOperatorKeycard extends ItemOperatorKeycard {

    public ItemBiometricOperatorKeycard() {
        super("biometric_operator_keycard");
    }

    @Override
    protected TextFormatting nameColour() {
        return TextFormatting.AQUA;
    }

    /**
     * Sneak + right-click a Redstone Gate to open its screen. The scanner console is still handled
     * by the parent class, so this card keeps both administration tools.
     */
    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                      EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (player.isSneaking() && world.getTileEntity(pos) instanceof TileEntityRedstoneGate) {
            if (world.isRemote) NTMDoors.proxy.openRedstoneGateGui(pos);
            return EnumActionResult.SUCCESS;
        }
        return super.onItemUse(player, world, pos, hand, facing, hitX, hitY, hitZ);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        Names.addDescription(tooltip,
                "Does exactly what the normal Operator Keycard does",
                "while storing your biometric data.",
                "Sneak + Right Click a Redstone Gate for its screen.");
        Biometrics.addOwnerLine(tooltip, stack);
        // The card can carry private keys just like an RFID card; show them instead of hiding
        // the administrator data behind the sneak-right-click viewer.
        PrivateKeys.addTooltip(tooltip, stack);
    }
}
