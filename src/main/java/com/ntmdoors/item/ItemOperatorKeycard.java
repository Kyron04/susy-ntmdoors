package com.ntmdoors.item;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.tile.TileEntityKeycardScanner;
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
 * Operator Keycard. A privileged card produced by the Keycard Programmer; its access rules will be
 * defined later. It shares the RFID card item model and hand display, so it is also recognised by
 * the scanner as a card. Its name is orange and its stored keys are listed in the card viewer.
 *
 * <p>Sneak right-clicking a scanner that is already bound to doors opens its security code screen.
 * The same action in the air still opens the key viewer.</p>
 */
public class ItemOperatorKeycard extends ItemRfidCard {

    public ItemOperatorKeycard() {
        super("operator_keycard");
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return TextFormatting.GOLD + super.getItemStackDisplayName(stack);
    }

    /**
     * Called when the card is used on a block. The scanner screen is the one machine interaction
     * the Operator Keycard owns, so it takes the click before anything else could see it.
     */
    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                      EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!player.isSneaking()) return EnumActionResult.PASS;
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileEntityKeycardScanner)) return EnumActionResult.PASS;

        TileEntityKeycardScanner scanner = (TileEntityKeycardScanner) te;
        if (!scanner.hasBoundDoors()) return EnumActionResult.PASS;

        if (world.isRemote) {
            NTMDoors.proxy.openScannerCodeGui(scanner.getPos(), scanner.hasSecurityCode());
        }
        return EnumActionResult.SUCCESS;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        Names.addDescription(tooltip,
                "The master keycard of your security network.",
                "Holds several private keys that can be assigned",
                "to scanners bound to doors",
                "Sneak + Right Click a bound Scanner to set",
                "its security code");
    }
}
