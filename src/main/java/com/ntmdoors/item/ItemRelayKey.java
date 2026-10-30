package com.ntmdoors.item;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.util.InteractionGuard;
import com.ntmdoors.util.ModSounds;
import com.ntmdoors.util.Names;
import com.ntmdoors.util.PrivateKeys;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.client.util.ITooltipFlag;
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
 * Relay Key. A blank key creates one private key when the player sneak right-clicks, which opens a
 * naming screen; the server then stores the chosen name and a fresh ten digit serial. The key can
 * never be reset, but the private key can be copied onto other keys and cards.
 */
public class ItemRelayKey extends Item {

    private static final float FEEDBACK_VOLUME = 0.8F;

    public ItemRelayKey() {
        setRegistryName(NTMDoors.MODID, "relay_key");
        setTranslationKey(NTMDoors.MODID + ".relay_key");
        setCreativeTab(NTMDoors.TAB);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!player.isSneaking() || InteractionGuard.targetsInteractiveBlock(player)) {
            return new ActionResult<>(EnumActionResult.PASS, stack);
        }

        if (world.isRemote) {
            if (PrivateKeys.isBlank(stack)) NTMDoors.proxy.openRelayKeyNameGui(hand);
            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        }

        // The server owns the one-key-only rule, so it answers every attempt on a used key.
        if (!PrivateKeys.isBlank(stack)) {
            world.playSound(null, player.posX, player.posY, player.posZ,
                    ModSounds.SCANNER_BUTTON8, SoundCategory.PLAYERS, FEEDBACK_VOLUME, 1F);
            player.sendStatusMessage(new TextComponentTranslation("ntmdoors.msg.key_exists"), true);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return TextFormatting.YELLOW + super.getItemStackDisplayName(stack);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        Names.addDescription(tooltip,
                "Used to generate private keys via",
                "Sneak + Right Click");
        for (PrivateKeys.Key key : PrivateKeys.get(stack)) {
            tooltip.add(TextFormatting.WHITE + key.name);
            tooltip.add(TextFormatting.WHITE + key.serial);
        }
    }
}
