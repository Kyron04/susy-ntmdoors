package com.ntmdoors.test.security;

import com.ntmdoors.item.ItemDocumentsFolder;
import com.ntmdoors.item.ModItems;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.Bootstrap;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Hand;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DocumentsFolderTest {

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
        com.ntmdoors.sound.ModSounds.registerModSounds();
        com.ntmdoors.block.ModBlocks.registerModBlocks();
        com.ntmdoors.block.entity.ModBlockEntities.registerModBlockEntities();
        com.ntmdoors.item.ModItems.registerModItems();
    }

    @Test
    @DisplayName("Verify document folder capacity and default initial state")
    public void testFolderCapacityAndInitialState() {
        assertEquals(20, ItemDocumentsFolder.PAGE_COUNT);
        assertEquals(1024, ItemDocumentsFolder.MAX_PAGE_LENGTH);
        assertEquals(24, ItemDocumentsFolder.MAX_NAME_LENGTH);

        ItemStack folder = new ItemStack(ModItems.DOCUMENTS_FOLDER);
        assertEquals(0, ItemDocumentsFolder.writtenPageCount(folder));
        assertEquals(0, ItemDocumentsFolder.getLastPage(folder));
        assertEquals("", ItemDocumentsFolder.getCustomName(folder));

        List<String> pages = ItemDocumentsFolder.getPages(folder);
        assertNotNull(pages);
        assertEquals(20, pages.size());
        for (int i = 0; i < 20; i++) {
            assertEquals("", pages.get(i), "Initial page " + i + " must be empty");
        }
    }

    @Test
    @DisplayName("Verify page reading, writing, and NBT roundtrip serialization")
    public void testPageSerializationRoundtrip() {
        ItemStack folder = new ItemStack(ModItems.DOCUMENTS_FOLDER);

        List<String> testPages = new ArrayList<>(Collections.nCopies(20, ""));
        testPages.set(0, "Incident Report: Reactor Containment Alpha");
        testPages.set(1, "Status: Temperature nominal at 350 C. Pressure 2.1 MPa.");
        testPages.set(4, "Warning: Security bypass detected on Sub-Level 3.");

        ItemDocumentsFolder.setPages(folder, testPages);
        assertEquals(3, ItemDocumentsFolder.writtenPageCount(folder));

        List<String> readPages = ItemDocumentsFolder.getPages(folder);
        assertEquals(20, readPages.size());
        assertEquals("Incident Report: Reactor Containment Alpha", readPages.get(0));
        assertEquals("Status: Temperature nominal at 350 C. Pressure 2.1 MPa.", readPages.get(1));
        assertEquals("", readPages.get(2));
        assertEquals("", readPages.get(3));
        assertEquals("Warning: Security bypass detected on Sub-Level 3.", readPages.get(4));
        assertEquals("", readPages.get(5));
        assertEquals("", readPages.get(19));
    }

    @Test
    @DisplayName("Verify trailing empty pages are cleaned and empty folder purges NBT tag")
    public void testCleanTrailingAndNbtPurge() {
        ItemStack folder = new ItemStack(ModItems.DOCUMENTS_FOLDER);

        List<String> input = Arrays.asList("Page 1", "Page 2", "", "", "");
        List<String> cleaned = ItemDocumentsFolder.clean(input);
        assertEquals(2, cleaned.size());
        assertEquals("Page 1", cleaned.get(0));
        assertEquals("Page 2", cleaned.get(1));

        ItemDocumentsFolder.setPages(folder, input);
        assertNotNull(folder.getNbt());
        assertTrue(folder.getNbt().contains(ItemDocumentsFolder.TAG_PAGES));

        // Setting all empty pages must purge the tag completely
        List<String> allEmpty = Collections.nCopies(20, "");
        ItemDocumentsFolder.setPages(folder, allEmpty);
        if (folder.hasNbt()) {
            assertFalse(folder.getNbt().contains(ItemDocumentsFolder.TAG_PAGES));
        }
        assertEquals(0, ItemDocumentsFolder.writtenPageCount(folder));
    }

    @Test
    @DisplayName("Verify custom name cleaning: stripping formatting codes, control characters, and length limit")
    public void testCustomNameCleaningAndLimits() {
        assertEquals("", ItemDocumentsFolder.cleanName(null));
        assertEquals("", ItemDocumentsFolder.cleanName("   "));

        // Strip formatting §r, §c, etc.
        String formatted = "§cTop§r §4Secret§r Dossier";
        assertEquals("Top Secret Dossier", ItemDocumentsFolder.cleanName(formatted));

        // Truncate to MAX_NAME_LENGTH (24 chars)
        String veryLong = "Supercalifragilisticexpialidocious Operation Document";
        String cleaned = ItemDocumentsFolder.cleanName(veryLong);
        assertTrue(cleaned.length() <= ItemDocumentsFolder.MAX_NAME_LENGTH, "Cleaned name must not exceed 24 chars");
        assertEquals("Supercalifragilisticexpi", cleaned);

        // Control characters filtered out
        String withCtrl = "Black\u0001\u0002Mesa\tProject\n";
        String cleanCtrl = ItemDocumentsFolder.cleanName(withCtrl);
        assertEquals("BlackMesaProject", cleanCtrl);

        // Set and get custom name on ItemStack
        ItemStack folder = new ItemStack(ModItems.DOCUMENTS_FOLDER);
        ItemDocumentsFolder.setCustomName(folder, "Classified File 7");
        assertEquals("Classified File 7", ItemDocumentsFolder.getCustomName(folder));

        // Empty string clears custom name
        ItemDocumentsFolder.setCustomName(folder, "");
        assertEquals("", ItemDocumentsFolder.getCustomName(folder));
        assertFalse(folder.hasCustomName());
    }

    @Test
    @DisplayName("Verify last page persistence and clamping between 0 and 19")
    public void testLastPageClampingAndPersistence() {
        ItemStack folder = new ItemStack(ModItems.DOCUMENTS_FOLDER);
        assertEquals(0, ItemDocumentsFolder.getLastPage(folder));

        // Set to page 7
        ItemDocumentsFolder.setLastPage(folder, 7);
        assertEquals(7, ItemDocumentsFolder.getLastPage(folder));

        // Clamping negative to 0
        ItemDocumentsFolder.setLastPage(folder, -5);
        assertEquals(0, ItemDocumentsFolder.getLastPage(folder));

        // Page 0 purges TAG_PAGE
        if (folder.hasNbt()) {
            assertFalse(folder.getNbt().contains(ItemDocumentsFolder.TAG_PAGE));
        }

        // Clamping overflow (> 19) to 19
        ItemDocumentsFolder.setLastPage(folder, 42);
        assertEquals(19, ItemDocumentsFolder.getLastPage(folder));
    }

    @Test
    @DisplayName("Verify page length enforcement clamps individual page to 1024 characters")
    public void testPageLengthClamping() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1500; i++) {
            sb.append((char) ('A' + (i % 26)));
        }
        String giantPage = sb.toString();

        List<String> input = Collections.singletonList(giantPage);
        List<String> cleaned = ItemDocumentsFolder.clean(input);
        assertEquals(1, cleaned.size());
        assertEquals(1024, cleaned.get(0).length());
        assertEquals(giantPage.substring(0, 1024), cleaned.get(0));
    }

    @Test
    @DisplayName("Verify DocumentsFolder network packet payload roundtrip encoding/decoding")
    public void testDocumentsFolderPacketCodec() {
        PacketByteBuf buf = PacketByteBufs.create();
        Hand hand = Hand.MAIN_HAND;
        int lastPage = 5;
        String customName = "Site 19 Manifest";
        List<String> pages = Arrays.asList(
                "Entry 1: Containment active.",
                "Entry 2: Backup power grid operational."
        );

        // Encoding matching sendDocumentsFolderPages
        buf.writeByte(hand == Hand.OFF_HAND ? 1 : 0);
        buf.writeByte(Math.max(0, Math.min(ItemDocumentsFolder.PAGE_COUNT - 1, lastPage)));
        buf.writeString(customName, ItemDocumentsFolder.MAX_NAME_LENGTH);
        int count = Math.min(pages.size(), ItemDocumentsFolder.PAGE_COUNT);
        buf.writeByte(count);
        for (int i = 0; i < count; i++) {
            buf.writeString(pages.get(i), ItemDocumentsFolder.MAX_PAGE_LENGTH);
        }

        // Decoding matching receiver
        int readHandOrdinal = buf.readByte();
        int readLastPage = buf.readByte();
        String readName = buf.readString(ItemDocumentsFolder.MAX_NAME_LENGTH);
        int readCount = buf.readByte();
        List<String> readPages = new ArrayList<>(readCount);
        for (int i = 0; i < readCount; i++) {
            readPages.add(buf.readString(ItemDocumentsFolder.MAX_PAGE_LENGTH));
        }

        assertEquals(0, readHandOrdinal);
        assertEquals(5, readLastPage);
        assertEquals("Site 19 Manifest", readName);
        assertEquals(2, readCount);
        assertEquals("Entry 1: Containment active.", readPages.get(0));
        assertEquals("Entry 2: Backup power grid operational.", readPages.get(1));
    }
}
