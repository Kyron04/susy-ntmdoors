package com.ntmdoors.item;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Random;

public final class LockUtil {

    private static final String KEY = "lockCode";
    private static final Random RAND = new Random();

    public static int getCode(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag == null ? 0 : tag.getInteger(KEY);
    }

    public static ItemStack withCode(ItemStack stack, int code) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setInteger(KEY, code);
        return stack;
    }

    public static int newCode() {
        int c;
        do {
            c = RAND.nextInt();
        } while (c == 0);
        return c;
    }

    public static void ensureCode(ItemStack stack) {
        if (getCode(stack) == 0) withCode(stack, newCode());
    }

    private LockUtil() {
    }
}
