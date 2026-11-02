package com.ntmdoors.item;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.tile.TileEntityKeycardScanner;
import com.ntmdoors.tile.TileEntityRedstoneGate;
import com.ntmdoors.util.HeldItems;
import com.ntmdoors.util.Names;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Debug keycard. It opens every door regardless of locks, security, keypad bindings or redstone,
 * and its name is shown in purple like other mods' creative-only items.
 */
public class ItemCreativeKeycard extends ItemRfidCard {
    public ItemCreativeKeycard() {
        super("creative_keycard");
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return TextFormatting.LIGHT_PURPLE + super.getItemStackDisplayName(stack);
    }

    /**
     * The Creative Keycard is the debug key, so it opens the scanner console without a security
     * code or bound doors. Vanilla skips block activation while sneaking, which is why the click is
     * handled by the item.
     */
    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                      EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!player.isSneaking()) return EnumActionResult.PASS;

        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileEntityKeycardScanner) {
            if (world.isRemote) NTMDoors.proxy.openScannerKeysGui(pos);
            return EnumActionResult.SUCCESS;
        }
        if (te instanceof TileEntityRedstoneGate) {
            if (world.isRemote) NTMDoors.proxy.openRedstoneGateGui(pos);
            return EnumActionResult.SUCCESS;
        }
        return EnumActionResult.PASS;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        Names.addDescription(tooltip,
                "Overrides every door, scanner and safe",
                "regardless of type or binding state.",
                "Sneak + Right Click a scanner for its console,",
                "or a Redstone Gate for its screen.");
    }

    /** True when either hand holds a Creative Keycard. */
    public static boolean isHeldBy(EntityPlayer player) {
        return HeldItems.holds(player, ItemCreativeKeycard.class);
    }
}
