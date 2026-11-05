package com.ntmdoors.item;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.monitor.TerminalBackupData;
import com.ntmdoors.util.Names;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Terminal Backup Key. A creative-only USB key used by the monitor terminal's backup screen.
 *
 * <p>One key receives the terminal snapshot with Upload; the Backup button copies that key onto a
 * blank key in the second slot, exactly like the keycard programmer's legacy backup.</p>
 */
public class ItemTerminalBackupKey extends Item {

    public ItemTerminalBackupKey() {
        setRegistryName(NTMDoors.MODID, "terminal_backup_key");
        setTranslationKey(NTMDoors.MODID + ".terminal_backup_key");
        setCreativeTab(NTMDoors.TAB);
        setMaxStackSize(1);
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return TextFormatting.DARK_RED + super.getItemStackDisplayName(stack);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip,
                               ITooltipFlag flag) {
        if (TerminalBackupData.hasBackup(stack)) {
            tooltip.add(TextFormatting.GREEN + "Terminal backup data");
            Names.addDescription(tooltip,
                    TerminalBackupData.getNetCount(stack) + " security nets, "
                            + TerminalBackupData.getDocumentCount(stack) + " documents");
        } else {
            Names.addDescription(tooltip,
                    "Blank key for the Terminal Backup system.",
                    "Upload terminal data from the backup screen.");
        }
    }
}
