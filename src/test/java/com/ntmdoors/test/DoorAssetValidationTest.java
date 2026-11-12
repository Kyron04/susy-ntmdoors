package com.ntmdoors.test;

import com.ntmdoors.client.render.DoorItemRenderer;
import com.ntmdoors.client.render.ObjModel;
import com.ntmdoors.door.DoorDecl;
import com.ntmdoors.door.DoorDecls;
import net.minecraft.Bootstrap;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class DoorAssetValidationTest {

    private static final Path ASSETS_DIR = Path.of("src", "main", "resources", "assets");

    @BeforeAll
    public static void setup() {
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
    @DisplayName("Validate All 22 Door Declarations: Model and texture files must exist on disk")
    public void testDoorModelsAndTexturesExist() {
        assertFalse(DoorDecls.ALL.isEmpty(), "DoorDecls.ALL must not be empty");
        assertEquals(22, DoorDecls.ALL.size(), "DoorDecls.ALL must contain exactly 22 door types");

        for (DoorDecl decl : DoorDecls.ALL) {
            String doorId = decl.name;
            assertNotNull(decl.model, "Model ID missing for " + doorId);
            assertNotNull(decl.texture, "Texture ID missing for " + doorId);

            Path modelPath = ASSETS_DIR.resolve(decl.model.getNamespace()).resolve(decl.model.getPath());
            assertTrue(Files.exists(modelPath), "Model file missing for " + doorId + ": " + modelPath);

            Path texPath = ASSETS_DIR.resolve(decl.texture.getNamespace()).resolve(decl.texture.getPath());
            assertTrue(Files.exists(texPath), "Texture file missing for " + doorId + ": " + texPath);

            // Validate skins if present
            for (Identifier skin : decl.skins) {
                Path skinPath = ASSETS_DIR.resolve(skin.getNamespace()).resolve(skin.getPath());
                assertTrue(Files.exists(skinPath), "Skin texture missing for " + doorId + ": " + skinPath);
            }

            // Validate part skins if present
            for (Map.Entry<String, List<Identifier>> partEntry : decl.partSkins.entrySet()) {
                for (Identifier partSkin : partEntry.getValue()) {
                    Path partSkinPath = ASSETS_DIR.resolve(partSkin.getNamespace()).resolve(partSkin.getPath());
                    assertTrue(Files.exists(partSkinPath), "Part skin texture missing for " + doorId + " part " + partEntry.getKey() + ": " + partSkinPath);
                }
            }
        }
    }

    @Test
    @DisplayName("Validate DoorItemRenderer Normalization: computeBounds produces finite positive scale and centered offset")
    public void testDoorItemRendererBounds() throws Exception {
        for (DoorDecl decl : DoorDecls.ALL) {
            Path modelPath = ASSETS_DIR.resolve(decl.model.getNamespace()).resolve(decl.model.getPath());
            try (InputStream in = Files.newInputStream(modelPath)) {
                ObjModel model = ObjModel.parse(in);
                Collection<String> targetGroups = decl.itemGroups.isEmpty() ? model.groups() : decl.itemGroups;
                DoorItemRenderer.ItemBounds bounds = DoorItemRenderer.computeBounds(model, targetGroups);
                assertNotNull(bounds, "ItemBounds should not be null for " + decl.name);
                assertTrue(bounds.scale() > 0.0f && Float.isFinite(bounds.scale()), "Scale must be positive and finite for " + decl.name);
                assertTrue(Float.isFinite(bounds.cx()) && Float.isFinite(bounds.cy()) && Float.isFinite(bounds.cz()),
                        "Center offsets must be finite for " + decl.name);
            }
        }
    }

    @Test
    @DisplayName("Validate Asset Directories: Ensure singular block/ and item/ texture paths are clean of plural variants")
    public void testTexturePathsAreSingular() throws Exception {
        Path jsonDir = ASSETS_DIR.resolve("ntmdoors");
        try (var stream = Files.walk(jsonDir)) {
            List<Path> offending = stream
                    .filter(p -> p.toString().endsWith(".json"))
                    .filter(p -> {
                        try {
                            String content = Files.readString(p);
                            return content.contains("ntmdoors:blocks/") || content.contains("ntmdoors:items/")
                                    || content.contains("minecraft:blocks/") || content.contains("minecraft:items/");
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .toList();
            assertTrue(offending.isEmpty(), "Found JSON files with plural texture paths: " + offending);
        }
    }
}
