package com.ntmdoors.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Biometric identity carried by the three biometric items.
 *
 * <p>The owner is stored as a UUID plus a cached name, so a tooltip can be built without a player
 * lookup: the owner may be offline, and the item may be sitting in a chest.</p>
 *
 * <pre>
 * Biometrics: {
 *     Owner: "4f2d...",
 *     OwnerName: "Steve"
 * }
 * </pre>
 *
 * <p>Binding happens the first time the stack ticks inside a player's inventory, so it covers
 * pickup, crafting output, a chest withdrawal and creative equally. Once bound it never changes:
 * that is the point of a biometric item, and it is why the check is "has no owner" rather than
 * "owner is not this player".</p>
 */
public final class Biometrics {

    public static final String ROOT = "Biometrics";
    private static final String OWNER = "Owner";
    private static final String OWNER_NAME = "OwnerName";

    /** True when the stack already carries an owner. */
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

    @Nullable
    public static UUID ownerId(ItemStack stack) {
        String raw = ownerUuid(stack);
        if (raw.isEmpty()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** True when the stack is bound to this exact player. */
    public static boolean isOwnedBy(ItemStack stack, EntityPlayer player) {
        String raw = ownerUuid(stack);
        return !raw.isEmpty() && raw.equals(player.getUniqueID().toString());
    }

    /**
     * Binds the stack to {@code player} if it has no owner yet. Returns true when this call was
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
     * Server-side inventory hook shared by the three biometric items. {@code Item#onUpdate} runs
     * for every stack in a player's inventory, so this is the one place that catches all the ways
     * an item can arrive.
     */
    public static void tick(ItemStack stack, World world, Entity holder) {
        if (world == null || world.isRemote) return;
        if (!(holder instanceof EntityPlayer)) return;
        bindIfNeeded(stack, (EntityPlayer) holder);
    }

    /** The "Owner (name)" tooltip line, or "Owner (unbound)" while it is still loose. */
    public static void addOwnerLine(List<String> tooltip, ItemStack stack) {
        String name = ownerName(stack);
        if (name.isEmpty()) {
            tooltip.add(TextFormatting.DARK_GRAY + "Owner (unbound)");
            return;
        }
        tooltip.add(TextFormatting.WHITE + "Owner (" + name + ")");
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
