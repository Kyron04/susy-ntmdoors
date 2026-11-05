package com.ntmdoors.monitor;

import net.minecraft.nbt.NBTTagCompound;

/**
 * One shared note stored on a monitor desk. Documents live in the desk's saved NBT and are
 * edited through the Notepad task in the terminal.
 */
public final class NotepadDocument {

    public static final int MAX_DOCUMENTS = 16;
    public static final int MAX_NAME_LENGTH = 48;
    public static final int MAX_TEXT_LENGTH = 8192;

    public final int id;
    private String name;
    private String text;

    public NotepadDocument(int id, String name, String text) {
        this.id = id;
        this.name = cleanName(name);
        this.text = cleanText(text);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = cleanName(name);
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = cleanText(text);
    }

    public static String cleanName(String value) {
        if (value == null) return "Untitled";
        StringBuilder out = new StringBuilder(Math.min(value.length(), MAX_NAME_LENGTH));
        for (int i = 0; i < value.length() && out.length() < MAX_NAME_LENGTH; i++) {
            char c = value.charAt(i);
            if (c >= 32 && c != 127) out.append(c);
        }
        String cleaned = out.toString().trim();
        return cleaned.isEmpty() ? "Untitled" : cleaned;
    }

    public static String cleanText(String value) {
        if (value == null) return "";
        if (value.length() > MAX_TEXT_LENGTH) return value.substring(0, MAX_TEXT_LENGTH);
        return value;
    }

    public NBTTagCompound toNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("Id", id);
        tag.setString("Name", name);
        tag.setString("Text", text);
        return tag;
    }

    public static NotepadDocument fromNBT(NBTTagCompound tag) {
        return new NotepadDocument(tag.getInteger("Id"), tag.getString("Name"),
                tag.getString("Text"));
    }
}
