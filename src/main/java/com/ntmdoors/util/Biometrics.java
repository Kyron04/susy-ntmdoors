package com.ntmdoors.util;

import com.ntmdoors.item.ItemDeveloperKeycard;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

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
        NbtCompound root = root(stack);
        return root == null ? "" : root.getString(OWNER);
    }

    /** The cached owner name, or an empty string when unbound. */
    public static String ownerName(ItemStack stack) {
        NbtCompound root = root(stack);
        return root == null ? "" : root.getString(OWNER_NAME);
    }

    /** True when the stack is bound to this exact player. */
    public static boolean isOwnedBy(ItemStack stack, PlayerEntity player) {
        String raw = ownerUuid(stack);
        return !raw.isEmpty() && raw.equals(player.getUuidAsString());
    }

    /** "Biometric ID (Steve)", or "Biometric ID (unbound)" for a loose item. */
    public static String label(ItemStack stack) {
        return label(ownerName(stack));
    }

    /** The same label from a stored owner name, for scanners holding a copy of it. */
    public static String label(String name) {
        return "Biometric ID (" + (name == null || name.isEmpty() ? "unbound" : name) + ")";
    }

    public static boolean hasOwner(ItemStack stack) {
        return isBound(stack);
    }

    @Nullable
    public static java.util.UUID getOwner(ItemStack stack) {
        String raw = ownerUuid(stack);
        return raw.isEmpty() ? null : java.util.UUID.fromString(raw);
    }

    public static String getOwnerName(ItemStack stack) {
        return ownerName(stack);
    }

    public static boolean matches(ItemStack stack, java.util.UUID uuid) {
        return uuid != null && isOwnedBy(stack, uuid);
    }

    public static boolean isOwnedBy(ItemStack stack, java.util.UUID uuid) {
        String raw = ownerUuid(stack);
        return !raw.isEmpty() && raw.equals(uuid.toString());
    }

    /**
     * Binds the stack to {@code player} if it has no identity yet. Returns true when this call was
     * the one that bound it, so callers can play a sound or send a message once.
     */
    public static boolean bindIfNeeded(ItemStack stack, java.util.UUID ownerId, String ownerName) {
        if (stack.isEmpty() || isBound(stack)) return false;

        NbtCompound root = rootForWrite(stack);
        root.putString(OWNER, ownerId.toString());
        root.putString(OWNER_NAME, ownerName == null ? "" : ownerName);
        return true;
    }

    public static boolean bindIfNeeded(ItemStack stack, PlayerEntity player) {
        if (stack.isEmpty() || isBound(stack) || player == null) return false;
        return bindIfNeeded(stack, player.getUuid(), player.getName().getString());
    }

    /**
     * Copies the identity from one stack to another. The target must be unbound: an identity is
     * never replaced, because it is what makes the item a lock rather than a label. Returns true
     * when the copy happened.
     */
    public static boolean copyOwner(ItemStack source, ItemStack target) {
        if (source.isEmpty() || target.isEmpty() || isBound(target)) return false;
        NbtCompound owner = root(source);
        if (owner == null) return false;

        rootForWrite(target).copyFrom(owner);
        return true;
    }

    /** The "Biometric ID (name)" tooltip line, or "(unbound)" while it is still loose. */
    public static void addOwnerLine(List<Text> tooltip, ItemStack stack) {
        Formatting colour = isBound(stack) ? Formatting.WHITE : Formatting.DARK_GRAY;
        tooltip.add(Text.literal(label(stack)).formatted(colour));
    }

    /** The identity line for any item that happens to carry one. */
    public static void addOwnerIfBound(List<Text> tooltip, ItemStack stack) {
        if (ItemDeveloperKeycard.isRedacted(stack)) {
            if (isBound(stack)) tooltip.add(Text.literal("Biometric ID (Unknown)").formatted(Formatting.WHITE));
            return;
        }
        if (isBound(stack)) addOwnerLine(tooltip, stack);
    }

    /** The compound that stores the identity, created on the stack if needed. */
    private static NbtCompound rootForWrite(ItemStack stack) {
        NbtCompound tag = stack.getOrCreateNbt();
        if (!tag.contains(ROOT, NbtElement.COMPOUND_TYPE)) {
            tag.put(ROOT, new NbtCompound());
        }
        return tag.getCompound(ROOT);
    }

    @Nullable
    private static NbtCompound root(ItemStack stack) {
        if (stack.isEmpty() || !stack.hasNbt()) return null;
        NbtCompound tag = stack.getNbt();
        return (tag != null && tag.contains(ROOT, NbtElement.COMPOUND_TYPE)) ? tag.getCompound(ROOT) : null;
    }

    private Biometrics() {
    }
}
