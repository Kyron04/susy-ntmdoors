package com.ntmdoors.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

/**
 * Developer keycard for pack makers integrating security systems into structures.
 *
 * <p>It behaves exactly like a normal RFID card: it can hold private keys and biometric data, be
 * programmed, and be used by scanners. When its stored private keys or biometric IDs would be
 * shown, they are reported as {@code Unknown} instead.</p>
 */
public class ItemDeveloperKeycard extends ItemNamedRfidCard {

    public ItemDeveloperKeycard() {
        super(Formatting.GOLD, "developer_keycard",
                "For pack developers who wish to intergrade security systems into structures");
    }

    /** True when {@code stack} is a developer keycard whose key/ID details should be redacted. */
    public static boolean isRedacted(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && isRedacted(stack.getItem());
    }

    /** True when {@code item} is a developer keycard whose key/ID details should be redacted. */
    public static boolean isRedacted(@Nullable Item item) {
        return item instanceof ItemDeveloperKeycard;
    }
}
