package com.ntmdoors.test;

import com.ntmdoors.client.render.ObjModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class ObjModelTest {

    private static final Path MODELS_DIR = Path.of("src", "main", "resources", "assets", "ntmdoors", "models", "doors");

    private static final String[] ALL_23_MODELS = {
        "black_mesa_door.obj",
        "blast_door.obj",
        "blast_shutter.obj",
        "cargo_door.obj",
        "fire_door.obj",
        "keycard_programmer.obj",
        "keycard_scanner.obj",
        "keypad.obj",
        "large_vehicle_door.obj",
        "office_door_1_double.obj",
        "office_door_2.obj",
        "office_door_2_double.obj",
        "qe_containment.obj",
        "qe_sliding_door.obj",
        "round_airlock_door.obj",
        "secure_access_door.obj",
        "silo_hatch.obj",
        "silo_hatch_large.obj",
        "sliding_blast_door.obj",
        "sliding_seal_door.obj",
        "transition_seal.obj",
        "vault_door.obj",
        "water_door.obj"
    };

    @Test
    @DisplayName("Parse All 23 OBJ Models: All models must load with non-empty groups, positive vertices, and valid bounds")
    public void testParseAll23Models() throws Exception {
        assertTrue(Files.isDirectory(MODELS_DIR), "Models directory must exist: " + MODELS_DIR);

        List<Path> objFiles = Files.list(MODELS_DIR)
                .filter(p -> p.toString().endsWith(".obj"))
                .collect(Collectors.toList());

        assertEquals(23, objFiles.size(), "Must discover exactly 23 OBJ door models");

        for (String modelName : ALL_23_MODELS) {
            Path modelPath = MODELS_DIR.resolve(modelName);
            assertTrue(Files.exists(modelPath), "Model file must exist: " + modelName);

            try (InputStream in = new FileInputStream(modelPath.toFile())) {
                ObjModel model = ObjModel.parse(in);
                assertNotNull(model, "Model should parse: " + modelName);
                assertFalse(model.groups().isEmpty(), "Model must have at least one group: " + modelName);

                int totalVerts = 0;
                for (Map.Entry<String, ObjModel.MeshGroup> entry : model.getGroups().entrySet()) {
                    ObjModel.MeshGroup group = entry.getValue();
                    assertTrue(group.vertexCount() > 0, "Group " + entry.getKey() + " in " + modelName + " must have > 0 vertices");
                    assertEquals(group.vertexCount() * 8, group.vertices().length, "Vertex array stride must be 8");
                    totalVerts += group.vertexCount();

                    // Assert all attributes are finite and normals are unit length
                    float[] verts = group.vertices();
                    for (int i = 0; i < verts.length; i += 8) {
                        float x = verts[i], y = verts[i + 1], z = verts[i + 2];
                        float u = verts[i + 3], v = verts[i + 4];
                        float nx = verts[i + 5], ny = verts[i + 6], nz = verts[i + 7];

                        assertFalse(Float.isNaN(x) || Float.isInfinite(x), "X is NaN/Inf in " + modelName);
                        assertFalse(Float.isNaN(y) || Float.isInfinite(y), "Y is NaN/Inf in " + modelName);
                        assertFalse(Float.isNaN(z) || Float.isInfinite(z), "Z is NaN/Inf in " + modelName);

                        assertFalse(Float.isNaN(u) || Float.isInfinite(u), "U is NaN/Inf in " + modelName);
                        assertFalse(Float.isNaN(v) || Float.isInfinite(v), "V is NaN/Inf in " + modelName);

                        assertFalse(Float.isNaN(nx) || Float.isInfinite(nx), "NX is NaN/Inf in " + modelName);
                        assertFalse(Float.isNaN(ny) || Float.isInfinite(ny), "NY is NaN/Inf in " + modelName);
                        assertFalse(Float.isNaN(nz) || Float.isInfinite(nz), "NZ is NaN/Inf in " + modelName);

                        float normLenSq = nx * nx + ny * ny + nz * nz;
                        assertTrue(normLenSq > 0.95f && normLenSq < 1.05f,
                                "Normal must be approximately unit length in " + modelName + " (got lenSq=" + normLenSq + ")");
                    }
                }
                assertTrue(totalVerts > 0, "Total vertices must be > 0 for " + modelName);
            }
        }
    }

    @Test
    @DisplayName("Automatic Normal Synthesis: Models with vn=0 must have valid synthesized face normals")
    public void testMissingNormalSynthesis() throws Exception {
        String[] modelsWithoutNormals = {
            "black_mesa_door.obj",
            "keycard_programmer.obj",
            "office_door_1_double.obj",
            "office_door_2.obj",
            "office_door_2_double.obj"
        };

        for (String modelName : modelsWithoutNormals) {
            Path modelPath = MODELS_DIR.resolve(modelName);
            try (InputStream in = new FileInputStream(modelPath.toFile())) {
                ObjModel model = ObjModel.parse(in);
                for (ObjModel.MeshGroup group : model.getGroups().values()) {
                    float[] verts = group.vertices();
                    for (int i = 0; i < verts.length; i += 8) {
                        float nx = verts[i + 5];
                        float ny = verts[i + 6];
                        float nz = verts[i + 7];
                        float lenSq = nx * nx + ny * ny + nz * nz;
                        assertTrue(lenSq > 0.95f && lenSq < 1.05f,
                                "Synthesized normal must be normalized in " + modelName + ": lenSq=" + lenSq);
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("Disjoint Group Merging: keycard_programmer.obj merges two disjoint 'Trim' blocks")
    public void testDisjointGroupMerging() throws Exception {
        Path modelPath = MODELS_DIR.resolve("keycard_programmer.obj");
        try (InputStream in = new FileInputStream(modelPath.toFile())) {
            ObjModel model = ObjModel.parse(in);
            ObjModel.MeshGroup trimGroup = model.getGroup("Trim");
            assertNotNull(trimGroup, "Trim group must exist");

            // keycard_programmer.obj has two separate 'o Trim' blocks with 1 quad each = 2 quads = 4 triangles = 12 vertices
            assertEquals(12, trimGroup.vertexCount(), "Merged Trim group should have exactly 12 vertices (4 triangles)");
        }
    }

    @Test
    @DisplayName("Model Bounds Accuracy: sliding_blast_door.obj matches known dimensions")
    public void testModelBounds() throws Exception {
        Path modelPath = MODELS_DIR.resolve("sliding_blast_door.obj");
        try (InputStream in = new FileInputStream(modelPath.toFile())) {
            ObjModel model = ObjModel.parse(in);
            assertEquals(-0.50f, model.minX(), 1e-2f);
            assertEquals(0.50f, model.maxX(), 1e-2f);
            assertEquals(0.00f, model.minY(), 1e-2f);
            assertEquals(4.00f, model.maxY(), 1e-2f);
            assertEquals(-3.50f, model.minZ(), 1e-2f);
            assertEquals(3.50f, model.maxZ(), 1e-2f);
        }
    }
}
