package com.ntmdoors.util;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextFormatting;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Biometric identity carried by the three biometric items.
 *
 * <p>The owner is stored as a UUID plus a cached name, so a label can be built without a player
 * lookup: the owner may be offline, and the item may be sitting in a chest.</p>
 *
 * <pre>
 * Biometrics: {
 *     Owner: "4f2d...",
 *     OwnerName: "Steve"
 * }
 * </pre>
 *
 * <p>Nothing binds automatically. The relay key collects the player's data on sneak right-click,
 * and the programmer copies that identity onto a card. Once bound it never changes: that is the
 * point of a biometric item, and it is why the check is "has no owner" rather than "owner is not
 * this player".</p>
 *
 * <p>An identity can be copied onto another item by the programmer. The target must be unbound, so
 * an identity can spread to spare carriers but can never be overwritten.</p>
 */
public final class Biometrics {

    public static final String ROOT = "Biometrics";
    private static final String OWNER = "Owner";
    private static final String OWNER_NAME = "OwnerName";

    /** True when the stack already carries an identity. */
    public static boolean isBound(ItemStack stack) {
        return !ownerUuid(stack).isEmpty();
    }

    /** The owner UUID as a string, or an empty string when unbound. */
    public static String ownerUuid(ItemStack stack) {
        NBTTagCompound root = root(stack);
        return root == null ? "" : root.getString(OWNER);
    }

    /** The cached owner name, or an empty string when unbound. */
    public static String ownerName(ItemStack stack) {
        NBTTagCompound root = root(stack);
        return root == null ? "" : root.getString(OWNER_NAME);
    }

    /** True when the stack is bound to this exact player. */
    public static boolean isOwnedBy(ItemStack stack, EntityPlayer player) {
        String raw = ownerUuid(stack);
        return !raw.isEmpty() && raw.equals(player.getUniqueID().toString());
    }

    /** "Biometric ID (Steve)", or "Biometric ID (unbound)" for a loose item. */
    public static String label(ItemStack stack) {
        return label(ownerName(stack));
    }

    /** The same label from a stored owner name, for scanners holding a copy of it. */
    public static String label(String name) {
        return "Biometric ID (" + (name == null || name.isEmpty() ? "unbound" : name) + ")";
    }

    /**
     * Binds the stack to {@code player} if it has no identity yet. Returns true when this call was
     * the one that bound it, so callers can play a sound or send a message once.
     */
    public static boolean bindIfNeeded(ItemStack stack, EntityPlayer player) {
        if (stack.isEmpty() || isBound(stack)) return false;

        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        NBTTagCompound root = tag.getCompoundTag(ROOT);
        root.setString(OWNER, player.getUniqueID().toString());
        root.setString(OWNER_NAME, player.getName());
        tag.setTag(ROOT, root);
        return true;
    }

    /**
     * Copies the identity from one stack to another. The target must be unbound: an identity is
     * never replaced, because it is what makes the item a lock rather than a label. Returns true
     * when the copy happened.
     */
    public static boolean copyOwner(ItemStack source, ItemStack target) {
        if (source.isEmpty() || target.isEmpty() || isBound(target)) return false;
        NBTTagCompound owner = root(source);
        if (owner == null) return false;

        NBTTagCompound tag = target.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            target.setTagCompound(tag);
        }
        tag.setTag(ROOT, owner.copy());
        return true;
    }

    /** The "Biometric ID (name)" tooltip line, or "(unbound)" while it is still loose. */
    public static void addOwnerLine(List<String> tooltip, ItemStack stack) {
        TextFormatting colour = isBound(stack) ? TextFormatting.WHITE : TextFormatting.DARK_GRAY;
        tooltip.add(colour + label(stack));
    }

    /** The identity line for any item that happens to carry one. */
    public static void addOwnerIfBound(List<String> tooltip, ItemStack stack) {
        if (isBound(stack)) addOwnerLine(tooltip, stack);
    }

    @Nullable
    private static NBTTagCompound root(ItemStack stack) {
        if (stack.isEmpty() || !stack.hasTagCompound()) return null;
        NBTTagCompound tag = stack.getTagCompound();
        return tag.hasKey(ROOT, 10) ? tag.getCompoundTag(ROOT) : null;
    }

    private Biometrics() {
    }
}
