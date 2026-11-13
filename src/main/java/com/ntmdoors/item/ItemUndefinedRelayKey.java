package com.ntmdoors.item;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.registry.ModContent;
import com.ntmdoors.util.InteractionGuard;
import com.ntmdoors.util.Names;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Undefined Relay Key. A programming item: sneak right-click opens the list of relay and utility
 * keys, and choosing one replaces this stack with a fresh blank key of that type. The target keys
 * stay recipe-free; only this item would need a recipe if one is added later.
 */
public class ItemUndefinedRelayKey extends Item {

    public ItemUndefinedRelayKey() {
        setRegistryName(NTMDoors.MODID, "undefined_relay_key");
        setTranslationKey(NTMDoors.MODID + ".undefined_relay_key");
        setCreativeTab(NTMDoors.TAB);
        // The picker replaces the held stack, so one item per programming action is enough.
        setMaxStackSize(1);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!player.isSneaking() || InteractionGuard.targetsInteractiveBlock(player)) {
            return new ActionResult<>(EnumActionResult.PASS, stack);
        }

        if (world.isRemote) {
            NTMDoors.proxy.openUndefinedRelayKeyGui(hand);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return TextFormatting.GRAY + super.getItemStackDisplayName(stack);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip,
                               ITooltipFlag flag) {
        Names.addDescription(tooltip,
                "Used to program relay key functions.",
                "Sneak + Right Click to choose a key type.");
    }

    /** One-line description shown under the picker list. */
    public static String descriptionFor(Item item) {
        if (item == ModContent.RELAY_KEY) return "Creates and stores private keys.";
        if (item == ModContent.BIOMETRIC_RELAY_KEY) return "Records the holder's biometric identity.";
        if (item == ModContent.REDSTONE_RELAY_KEY) return "Links security devices to a Redstone Gate.";
        if (item == ModContent.MOTION_RELAY_KEY) return "Opens the Motion Sensor console.";
        if (item == ModContent.TERMINAL_RELAY_KEY) return "Binds devices to a Security Terminal net.";
        if (item == ModContent.WEAPONS_RELAY_KEY) return "Binds explosives and Redstone Gates.";
        if (item == ModContent.SAFE_BINDING_KEY) return "Links a safe to doors.";
        if (item == ModContent.TERMINAL_BACKUP_KEY) return "Uploads and restores terminal backup data.";
        return "";
    }
}
