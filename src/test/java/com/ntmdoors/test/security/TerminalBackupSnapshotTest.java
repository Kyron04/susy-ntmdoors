package com.ntmdoors.test.security;

import com.ntmdoors.item.ItemTerminalBackupKey;
import com.ntmdoors.item.ModItems;
import com.ntmdoors.monitor.TerminalBackupData;
import net.minecraft.Bootstrap;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class TerminalBackupSnapshotTest {

    @BeforeAll
    public static void setup() {
        net.minecraft.SharedConstants.createGameVersion();
        try {
            java.lang.reflect.Field field = Bootstrap.class.getDeclaredField("initialized");
            field.setAccessible(true);
            field.setBoolean(null, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        net.minecraft.registry.Registries.REGISTRIES.getIds();
    }

    @Test
    @DisplayName("Verify GZIP compression and decompression roundtrip with NbtTagSizeTracker")
    public void testGzipCompressionRoundtrip() throws IOException {
        NbtCompound original = new NbtCompound();
        original.putInt("Schema", 1);
        original.putString("PasswordHash", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");

        NbtList nets = new NbtList();
        NbtCompound net1 = new NbtCompound();
        net1.putInt("Id", 0);
        net1.putString("Name", "Sector 7");
        nets.add(net1);
        original.put("SecurityNets", nets);

        NbtList docs = new NbtList();
        NbtCompound doc1 = new NbtCompound();
        doc1.putInt("Id", 1);
        doc1.putString("Name", "Emergency Checklist");
        doc1.putString("Text", "1. Check pressure\n2. Lock outer airlock\n3. Engage blast shields.");
        docs.add(doc1);
        original.put("Notepad", docs);

        // Compress
        byte[] compressed = TerminalBackupData.compress(original);
        assertNotNull(compressed);
        assertTrue(compressed.length > 0, "Compressed payload must not be empty");

        // Decompress
        NbtCompound decompressed = TerminalBackupData.decompress(compressed);
        assertNotNull(decompressed);
        assertEquals(1, decompressed.getInt("Schema"));
        assertEquals("Sector 7", decompressed.getList("SecurityNets", 10).getCompound(0).getString("Name"));
        assertEquals("Emergency Checklist", decompressed.getList("Notepad", 10).getCompound(0).getString("Name"));
    }

    @Test
    @DisplayName("Verify TerminalBackupData write, read, and metadata querying on ItemTerminalBackupKey")
    public void testBackupKeyWriteAndRead() {
        ItemStack key = new ItemStack(ModItems.TERMINAL_BACKUP_KEY);
        assertFalse(TerminalBackupData.hasBackup(key), "Fresh key must not have backup");
        assertEquals(0, TerminalBackupData.getNetCount(key));
        assertEquals(0, TerminalBackupData.getDocumentCount(key));

        NbtCompound snapshot = new NbtCompound();
        snapshot.putInt("Schema", 1);

        NbtList nets = new NbtList();
        NbtCompound n1 = new NbtCompound();
        n1.putInt("Id", 0);
        nets.add(n1);
        NbtCompound n2 = new NbtCompound();
        n2.putInt("Id", 1);
        nets.add(n2);
        snapshot.put("SecurityNets", nets);

        NbtList docs = new NbtList();
        NbtCompound d1 = new NbtCompound();
        d1.putInt("Id", 1);
        docs.add(d1);
        snapshot.put("Notepad", docs);

        BlockPos sourcePos = new BlockPos(120, 65, -340);
        boolean written = TerminalBackupData.write(key, snapshot, "minecraft:overworld", sourcePos);
        assertTrue(written, "Writing backup snapshot to key should succeed");

        assertTrue(TerminalBackupData.hasBackup(key));
        assertEquals(2, TerminalBackupData.getNetCount(key));
        assertEquals(1, TerminalBackupData.getDocumentCount(key));

        // Read snapshot back
        NbtCompound readSnapshot = TerminalBackupData.read(key);
        assertNotNull(readSnapshot, "Read snapshot must not be null");
        assertEquals(1, readSnapshot.getInt("Schema"));
        assertEquals(2, readSnapshot.getList("SecurityNets", 10).size());
        assertEquals(1, readSnapshot.getList("Notepad", 10).size());
    }

    @Test
    @DisplayName("Verify corrupted or invalid schema backup snapshot rejection")
    public void testCorruptedOrWrongSchemaRejection() {
        ItemStack key = new ItemStack(ModItems.TERMINAL_BACKUP_KEY);

        // Wrong schema
        NbtCompound invalidSchema = new NbtCompound();
        invalidSchema.putInt("Schema", 999);
        invalidSchema.put("SecurityNets", new NbtList());
        invalidSchema.put("Notepad", new NbtList());

        assertTrue(TerminalBackupData.write(key, invalidSchema, "minecraft:overworld", BlockPos.ORIGIN));
        assertNull(TerminalBackupData.read(key), "Reading snapshot with invalid schema must return null");

        // Corrupted byte array
        NbtCompound root = key.getNbt();
        assertNotNull(root);
        root.putByteArray("Data", new byte[]{0x00, 0x11, 0x22, 0x33}); // Garbage non-GZIP bytes
        key.setNbt(root);

        assertNull(TerminalBackupData.read(key), "Decompressing garbage bytes must catch exception and return null");
    }

    @Test
    @DisplayName("Verify rejection of non-backup-key items")
    public void testNonKeyItemRejection() {
        ItemStack invalidItem = new ItemStack(net.minecraft.item.Items.STICK);
        NbtCompound snapshot = new NbtCompound();
        snapshot.putInt("Schema", 1);

        assertFalse(TerminalBackupData.isKey(invalidItem));
        assertFalse(TerminalBackupData.write(invalidItem, snapshot, "overworld", BlockPos.ORIGIN));
        assertNull(TerminalBackupData.read(invalidItem));
        assertFalse(TerminalBackupData.hasBackup(invalidItem));
    }
}
