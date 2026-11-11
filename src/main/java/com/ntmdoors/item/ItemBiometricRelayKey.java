package com.ntmdoors.item;

import com.ntmdoors.sound.ModSounds;
import com.ntmdoors.util.Biometrics;
import com.ntmdoors.util.HeldItems;
import com.ntmdoors.util.InteractionGuard;
import com.ntmdoors.util.Names;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

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
    protected Formatting nameColour() {
        return Formatting.AQUA;
    }

    /**
     * Overrides the Relay Key's naming flow completely. There is no private key to name, and no
     * screen to open: the whole interaction is recording who is holding the key.
     */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (!player.isSneaking() || InteractionGuard.targetsInteractiveBlock(player)) {
            return TypedActionResult.pass(stack);
        }
        if (world.isClient) return TypedActionResult.success(stack, true);

        if (Biometrics.bindIfNeeded(stack, player)) {
            // The identity is written straight into the held stack
            HeldItems.sync(player);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    ModSounds.SCANNER_BUTTON3, SoundCategory.PLAYERS, FEEDBACK_VOLUME, 1.0F);
            player.sendMessage(Text.translatable("ntmdoors.msg.biometric_collected"), true);
        } else {
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    ModSounds.SCANNER_BUTTON8, SoundCategory.PLAYERS, FEEDBACK_VOLUME, 1.0F);
            player.sendMessage(Text.translatable("ntmdoors.msg.biometric_already",
                    Biometrics.ownerName(stack)), true);
        }
        return TypedActionResult.success(stack, false);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        Names.addDescription(tooltip,
                "Collects biometric data from the player via",
                "Sneak + Right Click.");
        Biometrics.addOwnerLine(tooltip, stack);
    }
}
