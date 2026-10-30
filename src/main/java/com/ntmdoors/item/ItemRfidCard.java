package com.ntmdoors.item;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.util.InteractionGuard;
import com.ntmdoors.util.PrivateKeys;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.util.text.TextFormatting;

import javax.annotation.Nullable;
import java.util.List;

/**
 * RFID card item. Every colour variant is identical apart from its registry name, which selects
 * the matching texture and model. Cards carry the private keys copied onto them by the programmer;
 * sneak right-clicking one opens the read-only viewer, except on the blank card. The Operator
 * Keycard inherits this item and gets its own key display later.
 */
public class ItemRfidCard extends Item {

    private final boolean keyViewer;

    public ItemRfidCard(String name) {
        this(name, true);
    }

    public ItemRfidCard(String name, boolean keyViewer) {
        this.keyViewer = keyViewer;
        setRegistryName(NTMDoors.MODID, name);
        setTranslationKey(NTMDoors.MODID + "." + name);
        setCreativeTab(NTMDoors.TAB);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!keyViewer || !player.isSneaking() || InteractionGuard.targetsInteractiveBlock(player)) {
            return new ActionResult<>(EnumActionResult.PASS, stack);
        }

        if (world.isRemote) NTMDoors.proxy.openKeycardViewerGui(stack);
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        if (this instanceof ItemOperatorKeycard) return;
        for (PrivateKeys.Key key : PrivateKeys.get(stack)) {
            tooltip.add(TextFormatting.WHITE + key.name);
            tooltip.add(TextFormatting.WHITE + key.serial);
        }
    }
}
