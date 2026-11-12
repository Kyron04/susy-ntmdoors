package com.ntmdoors.item;

import com.ntmdoors.monitor.TerminalBackupData;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Terminal Backup Key. A creative-only USB key used by the monitor terminal's backup screen.
 *
 * <p>One key receives the terminal snapshot with Upload; the Backup button copies that key onto a
 * blank key in the second slot, exactly like the keycard programmer's legacy backup.</p>
 */
public class ItemTerminalBackupKey extends Item {

    public ItemTerminalBackupKey(Settings settings) {
        super(settings.maxCount(1));
    }

    public ItemTerminalBackupKey() {
        this(new Settings());
    }

    @Override
    public Text getName(ItemStack stack) {
        return Text.translatable(this.getTranslationKey(stack)).formatted(Formatting.DARK_RED);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        if (TerminalBackupData.hasBackup(stack)) {
            tooltip.add(Text.literal("Terminal backup data").formatted(Formatting.GREEN));
            tooltip.add(Text.literal(TerminalBackupData.getNetCount(stack) + " security nets, "
                    + TerminalBackupData.getDocumentCount(stack) + " documents").formatted(Formatting.GRAY));
        } else {
            tooltip.add(Text.literal("Blank key for the Terminal Backup system.").formatted(Formatting.GRAY));
            tooltip.add(Text.literal("Upload terminal data from the backup screen.").formatted(Formatting.GRAY));
        }
    }
}
