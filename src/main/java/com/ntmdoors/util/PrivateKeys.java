package com.ntmdoors.util;

import com.ntmdoors.item.ItemDeveloperKeycard;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key other)) return false;
            return serial.equals(other.serial);
        }

        @Override
        public int hashCode() {
            return serial.hashCode();
        }
    }

    /** Every well-formed private key on the stack, in the order they were added. */
    public static List<Key> get(ItemStack stack) {
        if (stack.isEmpty() || !stack.hasNbt()) return Collections.emptyList();
        NbtCompound tag = stack.getNbt();
        if (tag == null || !tag.contains(ROOT, NbtElement.LIST_TYPE)) return Collections.emptyList();
        return fromList(tag.getList(ROOT, NbtElement.COMPOUND_TYPE));
    }

    /** Parses the standard {@code { Name, Serial }} list shape, skipping entries without a serial. */
    public static List<Key> fromList(NbtList list) {
        List<Key> keys = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            NbtCompound entry = list.getCompound(i);
            String serial = entry.getString(SERIAL);
            if (serial.isEmpty()) continue;
            keys.add(new Key(entry.getString(NAME), serial));
        }
        return keys;
    }

    /** Writes keys in the standard {@code { Name, Serial }} list shape. */
    public static NbtList toList(List<Key> keys) {
        NbtList list = new NbtList();
        for (Key key : keys) list.add(entry(key.name, key.serial));
        return list;
    }

    private static NbtCompound entry(String name, String serial) {
        NbtCompound entry = new NbtCompound();
        entry.putString(NAME, name);
        entry.putString(SERIAL, serial);
        return entry;
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

    public static List<Key> getKeys(ItemStack stack) {
        return get(stack);
    }

    public static boolean hasKey(ItemStack stack, Key key) {
        return key != null && hasSerial(stack, key.serial);
    }

    /** Adds one private key; returns false when the stack already carries that serial. */
    public static boolean add(ItemStack stack, Key key) {
        return key != null && add(stack, key.name, key.serial);
    }

    /** Adds one private key; returns false when the stack already carries that serial. */
    public static boolean add(ItemStack stack, String name, String serial) {
        if (stack.isEmpty() || hasSerial(stack, serial)) return false;

        NbtCompound tag = stack.getOrCreateNbt();
        NbtList list = tag.getList(ROOT, NbtElement.COMPOUND_TYPE);
        list.add(entry(name, serial));
        tag.put(ROOT, list);
        return true;
    }

    /** Adds the standard white name/serial pairs to an item tooltip. */
    public static void addTooltip(List<Text> tooltip, ItemStack stack) {
        if (ItemDeveloperKeycard.isRedacted(stack)) {
            if (!isBlank(stack)) tooltip.add(Text.literal("Unknown").formatted(Formatting.WHITE));
            return;
        }
        for (Key key : get(stack)) {
            tooltip.add(Text.literal(key.name).formatted(Formatting.WHITE));
            tooltip.add(Text.literal(key.serial).formatted(Formatting.WHITE));
        }
    }

    /** Copies every key that the target does not already have; returns how many were added. */
    public static int addAll(ItemStack source, ItemStack target) {
        int added = 0;
        for (Key key : get(source)) {
            if (add(target, key.name, key.serial)) added++;
        }
        return added;
    }

    public static int addAll(ItemStack target, List<Key> keys) {
        if (keys == null || keys.isEmpty()) return 0;
        int added = 0;
        for (Key key : keys) {
            if (add(target, key)) added++;
        }
        return added;
    }

    /**
     * A ten digit serial. The leading digit is never zero, so the number always reads as ten
     * digits, and every digit comes from {@link SecureRandom}.
     */
    public static String createSerial() {
        return createSerial(RANDOM);
    }

    public static String createSerial(java.util.Random rand) {
        StringBuilder serial = new StringBuilder(SERIAL_LENGTH);
        serial.append(1 + rand.nextInt(9));
        for (int i = 1; i < SERIAL_LENGTH; i++) {
            serial.append(rand.nextInt(10));
        }
        return serial.toString();
    }

    public static String sanitizeName(String name) {
        return sanitiseName(name);
    }

    /**
     * Trims the entered name, drops control characters and section signs so the name cannot break
     * tooltips or colour text, and cuts it to {@link #NAME_LENGTH} characters.
     */
    public static String sanitiseName(String name) {
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
