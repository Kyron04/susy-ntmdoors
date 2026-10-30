package com.ntmdoors.util;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Private-key data shared by Relay Keys, Operator Keycards and RFID cards.
 *
 * <p>A private key is a ten digit serial number plus the name the player gave it. Every card type
 * stores the same list, so the NBT stays readable when a key is copied from a Relay Key onto a
 * card:</p>
 *
 * <pre>
 * PrivateKeys: [
 *     { Name: "Sector 1", Serial: "2345356753" }
 * ]
 * </pre>
 *
 * <p>A Relay Key holds exactly one entry and can never be reset. Cards may hold any number.</p>
 */
public final class PrivateKeys {

    public static final String ROOT = "PrivateKeys";
    public static final int SERIAL_LENGTH = 10;
    public static final int NAME_LENGTH = 32;

    private static final String NAME = "Name";
    private static final String SERIAL = "Serial";
    private static final SecureRandom RANDOM = new SecureRandom();

    /** One private key: the player's chosen name and the serial it stands for. */
    public static final class Key {

        public final String name;
        public final String serial;

        public Key(String name, String serial) {
            this.name = name;
            this.serial = serial;
        }
    }

    /** Every well-formed private key on the stack, in the order they were added. */
    public static List<Key> get(ItemStack stack) {
        if (stack.isEmpty() || !stack.hasTagCompound()) return Collections.emptyList();
        NBTTagCompound tag = stack.getTagCompound();
        if (!tag.hasKey(ROOT, Constants.NBT.TAG_LIST)) return Collections.emptyList();

        NBTTagList list = tag.getTagList(ROOT, Constants.NBT.TAG_COMPOUND);
        List<Key> keys = new ArrayList<>(list.tagCount());
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            String serial = entry.getString(SERIAL);
            if (serial.isEmpty()) continue;
            keys.add(new Key(entry.getString(NAME), serial));
        }
        return keys;
    }

    public static int count(ItemStack stack) {
        return get(stack).size();
    }

    /** True for a Relay Key or card that has never held a private key. */
    public static boolean isBlank(ItemStack stack) {
        return count(stack) == 0;
    }

    public static boolean hasSerial(ItemStack stack, String serial) {
        for (Key key : get(stack)) {
            if (key.serial.equals(serial)) return true;
        }
        return false;
    }

    /** Adds one private key; returns false when the stack already carries that serial. */
    public static boolean add(ItemStack stack, String name, String serial) {
        if (stack.isEmpty() || hasSerial(stack, serial)) return false;

        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        NBTTagList list = tag.getTagList(ROOT, Constants.NBT.TAG_COMPOUND);
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString(NAME, name);
        entry.setString(SERIAL, serial);
        list.appendTag(entry);
        tag.setTag(ROOT, list);
        return true;
    }

    /** Copies every key that the target does not already have; returns how many were added. */
    public static int addAll(ItemStack source, ItemStack target) {
        int added = 0;
        for (Key key : get(source)) {
            if (add(target, key.name, key.serial)) added++;
        }
        return added;
    }

    /**
     * A ten digit serial. The leading digit is never zero, so the number always reads as ten
     * digits, and every digit comes from {@link SecureRandom}.
     */
    public static String createSerial() {
        StringBuilder serial = new StringBuilder(SERIAL_LENGTH);
        serial.append(1 + RANDOM.nextInt(9));
        for (int i = 1; i < SERIAL_LENGTH; i++) {
            serial.append(RANDOM.nextInt(10));
        }
        return serial.toString();
    }

    /**
     * Trims the entered name, drops control characters and section signs so the name cannot break
     * tooltips or colour text, and cuts it to {@link #NAME_LENGTH} characters.
     */
    public static String saniseName(String name) {
        if (name == null) return "";
        StringBuilder clean = new StringBuilder(name.length());
        for (int i = 0; i < name.length() && clean.length() < NAME_LENGTH; i++) {
            char c = name.charAt(i);
            if (c == '\u00a7' || c < ' ') continue;
            clean.append(c);
        }
        return clean.toString().trim();
    }

    private PrivateKeys() {
    }
}
