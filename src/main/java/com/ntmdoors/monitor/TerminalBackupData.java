package com.ntmdoors.monitor;

import com.ntmdoors.item.ItemTerminalBackupKey;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nullable;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Persistent payload of a Terminal Backup Key.
 *
 * <p>The desk snapshot is compressed into one byte array, so a key carrying every security net,
 * device binding, notepad document, screen wallpaper and the terminal password hash stays small
 * enough to travel through an item stack. Only the snapshot is stored.</p>
 */
public final class TerminalBackupData {

    public static final int SCHEMA = 1;
    /** Enough for a full terminal; one key must stay well below Minecraft's packet limit. */
    public static final int MAX_COMPRESSED_BYTES = 1024 * 1024;
    public static final int MAX_UNCOMPRESSED_BYTES = 4 * 1024 * 1024;

    private static final String FORMAT = "Format";
    private static final String CREATED = "Created";
    private static final String SOURCE_DIM = "SourceDim";
    private static final String SOURCE_POS = "SourcePos";
    private static final String NET_COUNT = "Nets";
    private static final String DOCUMENT_COUNT = "Documents";
    private static final String DATA = "Data";

    private TerminalBackupData() {
    }

    public static boolean isKey(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemTerminalBackupKey;
    }

    /** True when the key already carries a readable terminal backup. */
    public static boolean hasBackup(ItemStack stack) {
        return root(stack) != null;
    }

    /**
     * Server side: replaces the key's payload with a compressed copy of {@code snapshot}.
     * Returns false when the snapshot cannot be compressed or would be too large for a key.
     */
    public static boolean write(ItemStack stack, NBTTagCompound snapshot, int dimension,
                                @Nullable BlockPos sourcePos) {
        if (!isKey(stack) || snapshot == null) return false;

        byte[] compressed;
        try {
            compressed = compress(snapshot);
        } catch (IOException | RuntimeException ex) {
            return false;
        }
        if (compressed.length == 0 || compressed.length > MAX_COMPRESSED_BYTES) return false;

        NBTTagCompound root = new NBTTagCompound();
        root.setInteger(FORMAT, SCHEMA);
        root.setLong(CREATED, System.currentTimeMillis());
        root.setInteger(SOURCE_DIM, dimension);
        root.setLong(SOURCE_POS, sourcePos == null ? 0L : sourcePos.toLong());
        root.setInteger(NET_COUNT, snapshot.getTagList("SecurityNets",
                Constants.NBT.TAG_COMPOUND).tagCount());
        root.setInteger(DOCUMENT_COUNT, snapshot.getTagList("Notepad",
                Constants.NBT.TAG_COMPOUND).tagCount());
        root.setByteArray(DATA, compressed);
        stack.setTagCompound(root);
        return true;
    }

    /** Reads and decompresses a key's snapshot, or null when the key is blank or damaged. */
    @Nullable
    public static NBTTagCompound read(ItemStack stack) {
        NBTTagCompound root = root(stack);
        if (root == null) return null;
        byte[] compressed = root.getByteArray(DATA);
        if (compressed.length == 0 || compressed.length > MAX_COMPRESSED_BYTES) return null;

        try {
            NBTTagCompound snapshot = decompress(compressed);
            if (snapshot.getInteger("Schema") != SCHEMA) return null;
            return snapshot;
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    public static int getNetCount(ItemStack stack) {
        return metaInt(stack, NET_COUNT);
    }

    public static int getDocumentCount(ItemStack stack) {
        return metaInt(stack, DOCUMENT_COUNT);
    }

    @Nullable
    private static NBTTagCompound root(ItemStack stack) {
        if (!isKey(stack)) return null;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || tag.getInteger(FORMAT) != SCHEMA || !tag.hasKey(DATA)) {
            return null;
        }
        int length = tag.getByteArray(DATA).length;
        return length > 0 && length <= MAX_COMPRESSED_BYTES ? tag : null;
    }

    private static int metaInt(ItemStack stack, String key) {
        NBTTagCompound root = root(stack);
        return root == null ? 0 : root.getInteger(key);
    }

    private static byte[] compress(NBTTagCompound tag) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            CompressedStreamTools.write(tag, out);
        }
        return bytes.toByteArray();
    }

    private static NBTTagCompound decompress(byte[] bytes) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                new GZIPInputStream(new ByteArrayInputStream(bytes))))) {
            return CompressedStreamTools.read(in, new NBTSizeTracker(MAX_UNCOMPRESSED_BYTES));
        }
    }
}
