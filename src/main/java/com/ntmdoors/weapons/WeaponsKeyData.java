package com.ntmdoors.weapons;

import com.ntmdoors.item.ItemWeaponsRelayKey;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/** NBT helpers for a Weapons Relay Key stack. */
public final class WeaponsKeyData {

    private static final String KEY_ID = "ntmWeaponsKeyId";
    private static final String MODE = "ntmWeaponsMode";
    private static final String SESSION_DIM = "ntmWeaponsSessionDim";
    private static final String SESSION_POS = "ntmWeaponsSessionPos";

    private static NBTTagCompound tag(ItemStack stack) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        return stack.getTagCompound();
    }

    /** Creates the key's unique id on first use. */
    public static UUID ensureKeyId(ItemStack stack) {
        NBTTagCompound tag = tag(stack);
        String id = tag.getString(KEY_ID);
        if (id.isEmpty()) {
            id = UUID.randomUUID().toString();
            tag.setString(KEY_ID, id);
        }
        return UUID.fromString(id);
    }

    public static UUID keyId(ItemStack stack) {
        String id = stack.hasTagCompound() ? stack.getTagCompound().getString(KEY_ID) : "";
        if (id.isEmpty()) return null;
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static WeaponsMode mode(ItemStack stack) {
        return WeaponsMode.byId(stack.hasTagCompound()
                ? stack.getTagCompound().getInteger(MODE) : 0);
    }

    public static void setMode(ItemStack stack, WeaponsMode mode) {
        tag(stack).setInteger(MODE, mode.id);
    }

    public static BlockPos sessionPos(ItemStack stack) {
        if (!stack.hasTagCompound() || !stack.getTagCompound().hasKey(SESSION_POS)) return null;
        return BlockPos.fromLong(stack.getTagCompound().getLong(SESSION_POS));
    }

    public static int sessionDim(ItemStack stack) {
        return stack.hasTagCompound() ? stack.getTagCompound().getInteger(SESSION_DIM) : 0;
    }

    public static void startSession(ItemStack stack, int dimension, BlockPos pos) {
        NBTTagCompound tag = tag(stack);
        tag.setInteger(SESSION_DIM, dimension);
        tag.setLong(SESSION_POS, pos.toLong());
    }

    public static void clearSession(ItemStack stack) {
        if (!stack.hasTagCompound()) return;
        stack.getTagCompound().removeTag(SESSION_DIM);
        stack.getTagCompound().removeTag(SESSION_POS);
    }

    /** True when the player carries a key with this id anywhere in their inventory. */
    public static boolean hasKey(EntityPlayer player, UUID keyId) {
        if (player == null || keyId == null) return false;
        for (ItemStack stack : player.inventory.mainInventory) {
            if (isKey(stack, keyId)) return true;
        }
        for (ItemStack stack : player.inventory.armorInventory) {
            if (isKey(stack, keyId)) return true;
        }
        for (ItemStack stack : player.inventory.offHandInventory) {
            if (isKey(stack, keyId)) return true;
        }
        return false;
    }

    private static boolean isKey(ItemStack stack, UUID keyId) {
        return stack != null && !stack.isEmpty()
                && stack.getItem() instanceof ItemWeaponsRelayKey
                && keyId.equals(keyId(stack));
    }

    private WeaponsKeyData() {
    }
}
