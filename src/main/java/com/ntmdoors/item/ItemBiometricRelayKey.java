package com.ntmdoors.item;

import com.ntmdoors.util.Biometrics;
import com.ntmdoors.util.InteractionGuard;
import com.ntmdoors.util.ModSounds;
import com.ntmdoors.util.Names;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Biometric Relay Key. It does not generate private keys: sneak right-clicking collects the
 * player's biometric data instead, and the key becomes a carrier for that identity.
 *
 * <p>The programmer can then bind the identity onto a plain RFID card or Operator Keycard, which
 * locks those cards to the player. Backup can duplicate the carrier onto an empty Relay Key.</p>
 */
public class ItemBiometricRelayKey extends ItemRelayKey {

    public ItemBiometricRelayKey() {
        super("biometric_relay_key");
    }

    @Override
    protected TextFormatting nameColour() {
        return TextFormatting.AQUA;
    }

    /**
     * Overrides the Relay Key's naming flow completely. There is no private key to name, and no
     * screen to open: the whole interaction is recording who is holding the key.
     */
    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!player.isSneaking() || InteractionGuard.targetsInteractiveBlock(player)) {
            return new ActionResult<>(EnumActionResult.PASS, stack);
        }
        if (world.isRemote) return new ActionResult<>(EnumActionResult.SUCCESS, stack);

        if (Biometrics.bindIfNeeded(stack, player)) {
            world.playSound(null, player.posX, player.posY, player.posZ,
                    ModSounds.SCANNER_BUTTON3, SoundCategory.PLAYERS, FEEDBACK_VOLUME, 1F);
            player.sendStatusMessage(new TextComponentTranslation("ntmdoors.msg.biometric_collected"), true);
        } else {
            world.playSound(null, player.posX, player.posY, player.posZ,
                    ModSounds.SCANNER_BUTTON8, SoundCategory.PLAYERS, FEEDBACK_VOLUME, 1F);
            player.sendStatusMessage(new TextComponentTranslation("ntmdoors.msg.biometric_already",
                    Biometrics.ownerName(stack)), true);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        Names.addDescription(tooltip,
                "Collects biometric data from the player.",
                "Sneak + Right Click");
        Biometrics.addOwnerLine(tooltip, stack);
    }
}
