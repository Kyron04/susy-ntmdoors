package com.ntmdoors.weapons;

import com.ntmdoors.item.ItemWeaponsRelayKey;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/** NBT helpers for a Weapons Relay Key stack. */
public final class WeaponsKeyData {

    public static final String KEY_ID = "ntmWeaponsKeyId";
    public static final String MODE = "ntmWeaponsMode";
    public static final String SESSION_DIM = "ntmWeaponsSessionDim";
    public static final String SESSION_POS = "ntmWeaponsSessionPos";

    private static NbtCompound tag(ItemStack stack) {
        return stack.getOrCreateNbt();
    }

    /** Creates the key's unique id on first use. */
    public static UUID ensureKeyId(ItemStack stack) {
        NbtCompound tag = tag(stack);
        String id = tag.getString(KEY_ID);
        if (id.isEmpty()) {
            id = UUID.randomUUID().toString();
            tag.putString(KEY_ID, id);
        }
        return UUID.fromString(id);
    }

    public static UUID keyId(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasNbt()) return null;
        String id = stack.getNbt().getString(KEY_ID);
        if (id.isEmpty()) return null;
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static WeaponsMode mode(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasNbt()) return WeaponsMode.REGULAR;
        return WeaponsMode.byId(stack.getNbt().getInt(MODE));
    }

    public static void setMode(ItemStack stack, WeaponsMode mode) {
        tag(stack).putInt(MODE, mode.id);
    }

    public static BlockPos sessionPos(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasNbt() || !stack.getNbt().contains(SESSION_POS, NbtElement.LONG_TYPE)) {
            return null;
        }
        return BlockPos.fromLong(stack.getNbt().getLong(SESSION_POS));
    }

    public static String sessionDim(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasNbt()) return "";
        NbtCompound tag = stack.getNbt();
        if (tag.contains(SESSION_DIM, NbtElement.STRING_TYPE)) {
            return tag.getString(SESSION_DIM);
        } else if (tag.contains(SESSION_DIM, NbtElement.INT_TYPE)) {
            return String.valueOf(tag.getInt(SESSION_DIM));
        }
        return "";
    }

    public static void startSession(ItemStack stack, String dimension, BlockPos pos) {
        NbtCompound tag = tag(stack);
        tag.putString(SESSION_DIM, dimension != null ? dimension : "");
        tag.putLong(SESSION_POS, pos.asLong());
    }

    public static void clearSession(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasNbt()) return;
        stack.getNbt().remove(SESSION_DIM);
        stack.getNbt().remove(SESSION_POS);
    }

    /** True when the player carries a key with this id anywhere in their inventory. */
    public static boolean hasKey(PlayerEntity player, UUID keyId) {
        if (player == null || keyId == null) return false;
        for (ItemStack stack : player.getInventory().main) {
            if (isKey(stack, keyId)) return true;
        }
        for (ItemStack stack : player.getInventory().armor) {
            if (isKey(stack, keyId)) return true;
        }
        for (ItemStack stack : player.getInventory().offHand) {
            if (isKey(stack, keyId)) return true;
        }
        return false;
    }

    private static boolean isKey(ItemStack stack, UUID keyId) {
        return stack != null && !stack.isEmpty()
                && stack.getItem() instanceof ItemWeaponsRelayKey
                && keyId.equals(keyId(stack));
    }

    private WeaponsKeyData() {}
}
