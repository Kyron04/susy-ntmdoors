package com.ntmdoors.test.security;

import com.ntmdoors.client.display.DisplayImages;
import com.ntmdoors.monitor.MonitorWallpaper;
import com.ntmdoors.monitor.NotepadDocument;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class WallpaperAndNotepadTest {

    @Test
    @DisplayName("Verify 5 built-in wallpapers and Identifier resolution")
    public void testBuiltinWallpapers() {
        List<MonitorWallpaper.Builtin> builtins = MonitorWallpaper.builtins();
        assertEquals(5, builtins.size(), "Must have exactly 5 built-in wallpapers");

        List<String> expectedIds = Arrays.asList("supersymmetry", "black_mesa", "goog", "world_map", "windows_error");
        for (MonitorWallpaper.Builtin b : builtins) {
            assertTrue(expectedIds.contains(b.id), "Built-in id must be expected: " + b.id);
            assertNotNull(b.texture, "Built-in texture must not be null");
            assertEquals("ntmdoors", b.texture.getNamespace());
            assertEquals("textures/display/" + b.id + ".png", b.texture.getPath());

            // Check MonitorWallpaper.texture() lookup
            Identifier found = MonitorWallpaper.texture(MonitorWallpaper.builtin(b.id));
            assertEquals(b.texture, found);
        }

        // Custom URL should return null from texture() (DisplayImages handles remote textures)
        assertNull(MonitorWallpaper.texture(MonitorWallpaper.url("https://example.com/art.png")));
        assertNull(MonitorWallpaper.texture(null));
        assertNull(MonitorWallpaper.texture(""));
    }

    @Test
    @DisplayName("Verify wallpaper URL syntax validation and normalization")
    public void testWallpaperUrlValidation() {
        // Valid http and https URLs
        assertTrue(MonitorWallpaper.isValidHttpUrl("http://example.com/art.png"));
        assertTrue(MonitorWallpaper.isValidHttpUrl("https://textures.example.org/display_1.jpg"));

        // Invalid protocols
        assertFalse(MonitorWallpaper.isValidHttpUrl("ftp://example.com/image.png"));
        assertFalse(MonitorWallpaper.isValidHttpUrl("file:///C:/secrets.png"));
        assertFalse(MonitorWallpaper.isValidHttpUrl("javascript:alert(1)"));

        // Length limit <= 512
        StringBuilder longUrl = new StringBuilder("https://example.com/");
        while (longUrl.length() < 512) {
            longUrl.append("a");
        }
        assertTrue(MonitorWallpaper.isValidHttpUrl(longUrl.toString()));
        longUrl.append("x"); // 513
        assertFalse(MonitorWallpaper.isValidHttpUrl(longUrl.toString()));

        // Control characters rejected
        assertFalse(MonitorWallpaper.isValidHttpUrl("https://example.com/foo\nbar"));
        assertFalse(MonitorWallpaper.isValidHttpUrl("https://example.com/foo\rbar"));
        assertFalse(MonitorWallpaper.isValidHttpUrl("https://example.com/foo\tbar"));

        // Built-ins and URLs validation through isValid()
        assertTrue(MonitorWallpaper.isValid(MonitorWallpaper.builtin("supersymmetry")));
        assertTrue(MonitorWallpaper.isValid(MonitorWallpaper.url("https://example.com/valid.png")));
        assertTrue(MonitorWallpaper.isValid("")); // empty means normal terminal
        assertFalse(MonitorWallpaper.isValid(MonitorWallpaper.builtin("nonexistent_id")));
        assertFalse(MonitorWallpaper.isValid(MonitorWallpaper.url("ftp://invalid.com")));

        // Normalization
        assertEquals(MonitorWallpaper.builtin("supersymmetry"), MonitorWallpaper.normalize("builtin:supersymmetry"));
        assertEquals(MonitorWallpaper.url("https://example.com/art.png"),
                MonitorWallpaper.normalize("url:https://example.com/art.png"));
        assertEquals("", MonitorWallpaper.normalize(null));
        assertEquals("", MonitorWallpaper.normalize("garbage_string"));
    }

    @Test
    @DisplayName("Verify NotepadDocument text and title limits with NBT serialization")
    public void testNotepadDocumentLimitsAndNbt() {
        // Enforce 48 chars title limit
        String longTitle = "A".repeat(100);
        NotepadDocument doc = new NotepadDocument(1, longTitle, "Short body");
        assertEquals(48, doc.getName().length(), "Document title must be clamped to 48 characters");

        // Enforce 8192 chars body limit
        String longBody = "B".repeat(10000);
        NotepadDocument doc2 = new NotepadDocument(2, "Notes", longBody);
        assertEquals(NotepadDocument.MAX_TEXT_LENGTH, doc2.getText().length(), "Body text must be clamped to 8192 chars");

        // NBT roundtrip
        NbtCompound tag = doc2.toNbt();
        assertNotNull(tag);
        assertEquals(2, tag.getInt("Id"));
        assertEquals("Notes", tag.getString("Name"));
        assertEquals(8192, tag.getString("Text").length());

        NotepadDocument restored = NotepadDocument.fromNbt(tag);
        assertNotNull(restored);
        assertEquals(2, restored.getId());
        assertEquals("Notes", restored.getName());
        assertEquals(doc2.getText(), restored.getText());

        // Null and default handling
        NotepadDocument defaultDoc = new NotepadDocument(5, null, null);
        assertEquals(5, defaultDoc.getId());
        assertEquals("Untitled", defaultDoc.getName());
        assertEquals("", defaultDoc.getText());
    }

    @Test
    @DisplayName("Verify DisplayImages bounded stream clamp (8MB safety limit)")
    public void testDisplayImagesBoundedStreamClamp() throws IOException {
        // Stream under limit (1024 bytes) -> success
        byte[] smallData = new byte[1024];
        Arrays.fill(smallData, (byte) 0x42);
        byte[] readSmall = DisplayImages.readLimited(new ByteArrayInputStream(smallData), DisplayImages.MAX_BYTES);
        assertArrayEquals(smallData, readSmall);

        // Stream exactly at limit -> success
        byte[] exactLimit = new byte[16384];
        byte[] readExact = DisplayImages.readLimited(new ByteArrayInputStream(exactLimit), 16384);
        assertEquals(16384, readExact.length);

        // Stream exceeding limit -> throws IOException
        byte[] oversized = new byte[16385];
        assertThrows(IOException.class, () -> {
            DisplayImages.readLimited(new ByteArrayInputStream(oversized), 16384);
        }, "Stream exceeding byte clamp must throw IOException to prevent memory exhaustion");
    }
}
