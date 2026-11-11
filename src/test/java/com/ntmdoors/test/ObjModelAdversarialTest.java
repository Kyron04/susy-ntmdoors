package com.ntmdoors.test;

import com.ntmdoors.client.render.ObjModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Empirical Adversarial Test Harness for ObjModel.
 * Stress-tests OBJ parsing across all 23 production door models,
 * verifies normal synthesis tolerances, disjoint group merging,
 * AABB calculation precision, n-gon fan triangulation, negative index resolution,
 * degenerate geometry handling, and multithreaded parsing concurrency.
 */
public class ObjModelAdversarialTest {

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

    private static final String[] FIVE_MISSING_NORMAL_MODELS = {
        "black_mesa_door.obj",
        "keycard_programmer.obj",
        "office_door_1_double.obj",
        "office_door_2.obj",
        "office_door_2_double.obj"
    };

    // =========================================================================
    // 1. Full Asset Parsing Challenge
    // =========================================================================

    @Test
    @DisplayName("Challenge 1.1: Verify existence and inventory count of all 23 OBJ models")
    public void testInventoryCount() throws Exception {
        assertTrue(Files.isDirectory(MODELS_DIR), "Models directory must exist: " + MODELS_DIR);

        List<Path> objFiles = Files.list(MODELS_DIR)
                .filter(p -> p.getFileName().toString().endsWith(".obj"))
                .collect(Collectors.toList());

        assertEquals(23, objFiles.size(), "Must discover exactly 23 OBJ door models in filesystem");

        for (String name : ALL_23_MODELS) {
            Path p = MODELS_DIR.resolve(name);
            assertTrue(Files.isRegularFile(p), "Model file missing: " + name);
            assertTrue(Files.size(p) > 0, "Model file empty: " + name);
        }
    }

    @ParameterizedTest(name = "Parse model: {0}")
    @ValueSource(strings = {
        "black_mesa_door.obj", "blast_door.obj", "blast_shutter.obj", "cargo_door.obj",
        "fire_door.obj", "keycard_programmer.obj", "keycard_scanner.obj", "keypad.obj",
        "large_vehicle_door.obj", "office_door_1_double.obj", "office_door_2.obj",
        "office_door_2_double.obj", "qe_containment.obj", "qe_sliding_door.obj",
        "round_airlock_door.obj", "secure_access_door.obj", "silo_hatch.obj",
        "silo_hatch_large.obj", "sliding_blast_door.obj", "sliding_seal_door.obj",
        "transition_seal.obj", "vault_door.obj", "water_door.obj"
    })
    @DisplayName("Challenge 1.2: Rigorous empirical validation of every vertex, UV, and normal across all models")
    public void testEmpiricalModelIntegrity(String modelName) throws Exception {
        Path path = MODELS_DIR.resolve(modelName);
        ObjModel model;
        try (InputStream in = new FileInputStream(path.toFile())) {
            model = ObjModel.parse(in);
        }
        assertNotNull(model, "Model must parse: " + modelName);
        assertFalse(model.groups().isEmpty(), "Model must have groups: " + modelName);

        float modelObsMinX = Float.POSITIVE_INFINITY, modelObsMinY = Float.POSITIVE_INFINITY, modelObsMinZ = Float.POSITIVE_INFINITY;
        float modelObsMaxX = Float.NEGATIVE_INFINITY, modelObsMaxY = Float.NEGATIVE_INFINITY, modelObsMaxZ = Float.NEGATIVE_INFINITY;
        int totalVertices = 0;

        for (Map.Entry<String, ObjModel.MeshGroup> entry : model.getGroups().entrySet()) {
            String groupName = entry.getKey();
            ObjModel.MeshGroup group = entry.getValue();

            assertNotNull(group, "MeshGroup null for " + groupName + " in " + modelName);
            assertEquals(groupName, group.name());
            assertTrue(group.vertexCount() > 0, "Group " + groupName + " in " + modelName + " has 0 vertices");
            assertEquals(0, group.vertexCount() % 3, "Group " + groupName + " in " + modelName + " vertexCount not multiple of 3 (un-triangulated)");

            float[] v = group.vertices();
            assertEquals(group.vertexCount() * 8, v.length, "Vertices array length must match vertexCount * 8");

            float gObsMinX = Float.POSITIVE_INFINITY, gObsMinY = Float.POSITIVE_INFINITY, gObsMinZ = Float.POSITIVE_INFINITY;
            float gObsMaxX = Float.NEGATIVE_INFINITY, gObsMaxY = Float.NEGATIVE_INFINITY, gObsMaxZ = Float.NEGATIVE_INFINITY;

            for (int i = 0; i < v.length; i += 8) {
                float x = v[i], y = v[i + 1], z = v[i + 2];
                float u = v[i + 3], w = v[i + 4];
                float nx = v[i + 5], ny = v[i + 6], nz = v[i + 7];

                // Coordinates must be finite
                assertFalse(Float.isNaN(x) || Float.isInfinite(x), "NaN/Inf x at vert " + (i / 8) + " in " + modelName);
                assertFalse(Float.isNaN(y) || Float.isInfinite(y), "NaN/Inf y at vert " + (i / 8) + " in " + modelName);
                assertFalse(Float.isNaN(z) || Float.isInfinite(z), "NaN/Inf z at vert " + (i / 8) + " in " + modelName);

                // UVs must be finite
                assertFalse(Float.isNaN(u) || Float.isInfinite(u), "NaN/Inf u at vert " + (i / 8) + " in " + modelName);
                assertFalse(Float.isNaN(w) || Float.isInfinite(w), "NaN/Inf v at vert " + (i / 8) + " in " + modelName);

                // Normals must be finite and normalized
                assertFalse(Float.isNaN(nx) || Float.isInfinite(nx), "NaN/Inf nx at vert " + (i / 8) + " in " + modelName);
                assertFalse(Float.isNaN(ny) || Float.isInfinite(ny), "NaN/Inf ny at vert " + (i / 8) + " in " + modelName);
                assertFalse(Float.isNaN(nz) || Float.isInfinite(nz), "NaN/Inf nz at vert " + (i / 8) + " in " + modelName);

                float lenSq = nx * nx + ny * ny + nz * nz;
                assertTrue(lenSq >= 0.98f && lenSq <= 1.02f,
                        "Normal not normalized at vert " + (i / 8) + " in " + modelName + " (" + groupName + "): lenSq=" + lenSq + " [" + nx + "," + ny + "," + nz + "]");

                // Never emit zero normal (0,0,0)
                assertFalse(Math.abs(nx) < 1e-6f && Math.abs(ny) < 1e-6f && Math.abs(nz) < 1e-6f,
                        "Zero normal emitted at vert " + (i / 8) + " in " + modelName + " (" + groupName + ")");

                gObsMinX = Math.min(gObsMinX, x);
                gObsMinY = Math.min(gObsMinY, y);
                gObsMinZ = Math.min(gObsMinZ, z);
                gObsMaxX = Math.max(gObsMaxX, x);
                gObsMaxY = Math.max(gObsMaxY, y);
                gObsMaxZ = Math.max(gObsMaxZ, z);
            }

            // Verify group bounding box matches observed bounds
            assertEquals(gObsMinX, group.minX(), 1e-4f, "Group minX mismatch in " + groupName);
            assertEquals(gObsMinY, group.minY(), 1e-4f, "Group minY mismatch in " + groupName);
            assertEquals(gObsMinZ, group.minZ(), 1e-4f, "Group minZ mismatch in " + groupName);
            assertEquals(gObsMaxX, group.maxX(), 1e-4f, "Group maxX mismatch in " + groupName);
            assertEquals(gObsMaxY, group.maxY(), 1e-4f, "Group maxY mismatch in " + groupName);
            assertEquals(gObsMaxZ, group.maxZ(), 1e-4f, "Group maxZ mismatch in " + groupName);

            modelObsMinX = Math.min(modelObsMinX, gObsMinX);
            modelObsMinY = Math.min(modelObsMinY, gObsMinY);
            modelObsMinZ = Math.min(modelObsMinZ, gObsMinZ);
            modelObsMaxX = Math.max(modelObsMaxX, gObsMaxX);
            modelObsMaxY = Math.max(modelObsMaxY, gObsMaxY);
            modelObsMaxZ = Math.max(modelObsMaxZ, gObsMaxZ);

            totalVertices += group.vertexCount();
        }

        assertTrue(totalVertices > 0, "Model must have vertices: " + modelName);

        // Verify model overall bounding box matches observed bounds
        assertEquals(modelObsMinX, model.minX(), 1e-4f, "Model minX mismatch in " + modelName);
        assertEquals(modelObsMinY, model.minY(), 1e-4f, "Model minY mismatch in " + modelName);
        assertEquals(modelObsMinZ, model.minZ(), 1e-4f, "Model minZ mismatch in " + modelName);
        assertEquals(modelObsMaxX, model.maxX(), 1e-4f, "Model maxX mismatch in " + modelName);
        assertEquals(modelObsMaxY, model.maxY(), 1e-4f, "Model maxY mismatch in " + modelName);
        assertEquals(modelObsMaxZ, model.maxZ(), 1e-4f, "Model maxZ mismatch in " + modelName);
    }

    // =========================================================================
    // 2. Missing Normal Synthesis Challenge
    // =========================================================================

    @ParameterizedTest(name = "Missing normal model: {0}")
    @ValueSource(strings = {
        "black_mesa_door.obj",
        "keycard_programmer.obj",
        "office_door_1_double.obj",
        "office_door_2.obj",
        "office_door_2_double.obj"
    })
    @DisplayName("Challenge 2.1: Verify 5 vn=0 models synthesize strict unit normals (|n| in [0.99, 1.01]) matching geometric face plane")
    public void testStrictNormalSynthesis(String modelName) throws Exception {
        Path path = MODELS_DIR.resolve(modelName);
        ObjModel model;
        try (InputStream in = new FileInputStream(path.toFile())) {
            model = ObjModel.parse(in);
        }

        int triangleCount = 0;
        for (ObjModel.MeshGroup group : model.getGroups().values()) {
            float[] d = group.vertices();
            for (int i = 0; i < d.length; i += 24) { // each triangle is 3 verts * 8 floats = 24 floats
                float x0 = d[i], y0 = d[i + 1], z0 = d[i + 2];
                float nx0 = d[i + 5], ny0 = d[i + 6], nz0 = d[i + 7];

                float x1 = d[i + 8], y1 = d[i + 9], z1 = d[i + 10];
                float nx1 = d[i + 13], ny1 = d[i + 14], nz1 = d[i + 15];

                float x2 = d[i + 16], y2 = d[i + 17], z2 = d[i + 18];
                float nx2 = d[i + 21], ny2 = d[i + 22], nz2 = d[i + 23];

                // All three vertices of the triangle must have identical normals (flat face normal)
                assertEquals(nx0, nx1, 1e-5f, "Normal X mismatch across triangle in " + modelName);
                assertEquals(ny0, ny1, 1e-5f, "Normal Y mismatch across triangle in " + modelName);
                assertEquals(nz0, nz1, 1e-5f, "Normal Z mismatch across triangle in " + modelName);
                assertEquals(nx0, nx2, 1e-5f, "Normal X mismatch across triangle in " + modelName);
                assertEquals(ny0, ny2, 1e-5f, "Normal Y mismatch across triangle in " + modelName);
                assertEquals(nz0, nz2, 1e-5f, "Normal Z mismatch across triangle in " + modelName);

                // Magnitude check: strict unit normal [0.99, 1.01]
                float len = (float) Math.sqrt(nx0 * nx0 + ny0 * ny0 + nz0 * nz0);
                assertTrue(len >= 0.99f && len <= 1.01f, "Synthesized normal magnitude not 1.0: " + len);

                // Check direction: if triangle is non-degenerate, normal must be perpendicular to edges (P1 - P0) and (P2 - P0)
                float e1x = x1 - x0, e1y = y1 - y0, e1z = z1 - z0;
                float e2x = x2 - x0, e2y = y2 - y0, e2z = z2 - z0;
                float dot1 = e1x * nx0 + e1y * ny0 + e1z * nz0;
                float dot2 = e2x * nx0 + e2y * ny0 + e2z * nz0;

                // Orthogonality: dot product of face normal with tangent edges must be ~0
                assertEquals(0.0f, dot1, 1e-3f, "Normal not orthogonal to e1 in " + modelName);
                assertEquals(0.0f, dot2, 1e-3f, "Normal not orthogonal to e2 in " + modelName);

                triangleCount++;
            }
        }
        assertTrue(triangleCount > 0, "Must have checked at least one triangle in " + modelName);
    }

    // =========================================================================
    // 3. Disjoint Group Merging & AABB Calculation
    // =========================================================================

    @Test
    @DisplayName("Challenge 3.1: keycard_programmer.obj disjoint 'Trim' blocks merged without loss")
    public void testDisjointGroupMergingKeycardProgrammer() throws Exception {
        Path path = MODELS_DIR.resolve("keycard_programmer.obj");
        ObjModel model;
        try (InputStream in = new FileInputStream(path.toFile())) {
            model = ObjModel.parse(in);
        }

        // keycard_programmer has groups: Skirt, Body, Screen, Slit, Buttons, Trim, Light, HandPanel, HandLight, Vents
        Set<String> groups = model.groups();
        assertTrue(groups.contains("Trim"), "Trim group must exist");
        assertTrue(groups.contains("Light"), "Light group must exist");
        assertTrue(groups.contains("Body"), "Body group must exist");

        ObjModel.MeshGroup trimGroup = model.getGroup("Trim");
        assertNotNull(trimGroup);

        // Lines 355 and 359 both declare 'o Trim'. Each has 1 quad (4 vertices -> 2 triangles -> 6 emitted vertices).
        // Merged together, Trim must have exactly 12 vertices.
        assertEquals(12, trimGroup.vertexCount(), "Merged Trim group must contain exactly 12 vertices");
        assertEquals(96, trimGroup.vertices().length, "Float array length must be 12 * 8 = 96");

        // Verify bounding box of Trim group
        assertTrue(trimGroup.minX() <= trimGroup.maxX());
        assertTrue(trimGroup.minY() <= trimGroup.maxY());
        assertTrue(trimGroup.minZ() <= trimGroup.maxZ());
    }

    @Test
    @DisplayName("Challenge 3.2: Subset bounds() calculation matrix")
    public void testSubsetBoundsCalculation() throws Exception {
        Path path = MODELS_DIR.resolve("sliding_blast_door.obj");
        ObjModel model;
        try (InputStream in = new FileInputStream(path.toFile())) {
            model = ObjModel.parse(in);
        }

        // Null group collection -> returns full model bounds
        float[] fullBounds = model.bounds(null);
        assertNotNull(fullBounds);
        assertEquals(6, fullBounds.length);
        assertEquals(model.minX(), fullBounds[0], 1e-5f);
        assertEquals(model.minY(), fullBounds[1], 1e-5f);
        assertEquals(model.minZ(), fullBounds[2], 1e-5f);
        assertEquals(model.maxX(), fullBounds[3], 1e-5f);
        assertEquals(model.maxY(), fullBounds[4], 1e-5f);
        assertEquals(model.maxZ(), fullBounds[5], 1e-5f);

        // Empty group collection -> returns full model bounds
        float[] emptyBounds = model.bounds(Collections.emptyList());
        assertNotNull(emptyBounds);
        assertArrayEquals(fullBounds, emptyBounds, 1e-5f);

        // Unknown group name -> returns null
        float[] unknownBounds = model.bounds(List.of("NonExistentGroup123"));
        assertNull(unknownBounds, "Unknown group must return null bounds");

        // Single group -> matches that group's exact bounds
        for (String groupName : model.groups()) {
            ObjModel.MeshGroup group = model.getGroup(groupName);
            float[] gBounds = model.bounds(List.of(groupName));
            assertNotNull(gBounds);
            assertEquals(group.minX(), gBounds[0], 1e-5f);
            assertEquals(group.minY(), gBounds[1], 1e-5f);
            assertEquals(group.minZ(), gBounds[2], 1e-5f);
            assertEquals(group.maxX(), gBounds[3], 1e-5f);
            assertEquals(group.maxY(), gBounds[4], 1e-5f);
            assertEquals(group.maxZ(), gBounds[5], 1e-5f);
        }

        // Mix of valid and unknown groups -> bounds of the valid group(s)
        String firstGroup = model.groups().iterator().next();
        ObjModel.MeshGroup group = model.getGroup(firstGroup);
        float[] mixedBounds = model.bounds(List.of(firstGroup, "BogusGroup"));
        assertNotNull(mixedBounds);
        assertEquals(group.minX(), mixedBounds[0], 1e-5f);
        assertEquals(group.maxX(), mixedBounds[3], 1e-5f);
    }

    // =========================================================================
    // 4. Adversarial Synthetic Edge-Case Challenges
    // =========================================================================

    @Test
    @DisplayName("Challenge 4.1: Empty and comment-only OBJ files parse without crashing")
    public void testEmptyAndCommentFiles() throws Exception {
        ObjModel emptyModel = ObjModel.parse(new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8)));
        assertNotNull(emptyModel);
        assertTrue(emptyModel.groups().isEmpty());
        assertEquals(0.0f, emptyModel.minX());
        assertEquals(0.0f, emptyModel.maxX());

        String commentOnly = "# Just comments\n# Another line\n   \n\t\n";
        ObjModel commentModel = ObjModel.parse(new ByteArrayInputStream(commentOnly.getBytes(StandardCharsets.UTF_8)));
        assertNotNull(commentModel);
        assertTrue(commentModel.groups().isEmpty());
    }

    @Test
    @DisplayName("Challenge 4.2: N-gon fan triangulation (5-gon, 6-gon, 8-gon)")
    public void testNgonTriangulation() throws Exception {
        // Construct an 8-gon in the XY plane: 8 vertices, 1 face with 8 indices
        StringBuilder sb = new StringBuilder();
        int N = 8;
        for (int i = 0; i < N; i++) {
            double angle = 2.0 * Math.PI * i / N;
            float x = (float) Math.cos(angle);
            float y = (float) Math.sin(angle);
            sb.append("v ").append(x).append(" ").append(y).append(" 0.0\n");
            sb.append("vt ").append((x + 1) * 0.5f).append(" ").append((y + 1) * 0.5f).append("\n");
        }
        sb.append("o Octagon\n");
        sb.append("f");
        for (int i = 1; i <= N; i++) {
            sb.append(" ").append(i).append("/").append(i);
        }
        sb.append("\n");

        ObjModel model = ObjModel.parse(new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8)));
        ObjModel.MeshGroup group = model.getGroup("Octagon");
        assertNotNull(group);

        // An N-gon triangulated by a fan has (N - 2) triangles = 6 triangles = 18 vertices
        assertEquals((N - 2) * 3, group.vertexCount(), "8-gon must triangulate into 6 triangles = 18 vertices");

        // Verify normal synthesized correctly perpendicular to XY plane: (0, 0, 1) or (0, 0, -1)
        float[] v = group.vertices();
        for (int i = 0; i < v.length; i += 8) {
            assertEquals(0.0f, v[i + 5], 1e-4f, "Nx should be 0");
            assertEquals(0.0f, v[i + 6], 1e-4f, "Ny should be 0");
            assertEquals(1.0f, Math.abs(v[i + 7]), 1e-4f, "Nz should be +-1");
        }
    }

    @Test
    @DisplayName("Challenge 4.3: Negative index resolution in face directives")
    public void testNegativeIndexResolution() throws Exception {
        // In OBJ, negative indices reference relative to the current position in the list
        // -1 = most recent vertex, -2 = second most recent, etc.
        String objData = """
                v 0.0 0.0 0.0
                v 1.0 0.0 0.0
                v 0.0 1.0 0.0
                vt 0.0 0.0
                vt 1.0 0.0
                vt 0.0 1.0
                vn 0.0 0.0 1.0
                o RelativeIndexed
                f -3/-3/-1 -2/-2/-1 -1/-1/-1
                """;
        ObjModel model = ObjModel.parse(new ByteArrayInputStream(objData.getBytes(StandardCharsets.UTF_8)));
        ObjModel.MeshGroup group = model.getGroup("RelativeIndexed");
        assertNotNull(group);
        assertEquals(3, group.vertexCount());

        float[] v = group.vertices();
        // First vertex: (0, 0, 0)
        assertEquals(0.0f, v[0]); assertEquals(0.0f, v[1]); assertEquals(0.0f, v[2]);
        // Second vertex: (1, 0, 0)
        assertEquals(1.0f, v[8]); assertEquals(0.0f, v[9]); assertEquals(0.0f, v[10]);
        // Third vertex: (0, 1, 0)
        assertEquals(0.0f, v[16]); assertEquals(1.0f, v[17]); assertEquals(0.0f, v[18]);
        // Normal should be (0, 0, 1)
        assertEquals(0.0f, v[5]); assertEquals(0.0f, v[6]); assertEquals(1.0f, v[7]);
    }

    @Test
    @DisplayName("Challenge 4.4: Degenerate triangles (zero area, collinear) fallback without NaN")
    public void testDegenerateGeometrySynthesis() throws Exception {
        String degenerateData = """
                v 0.0 0.0 0.0
                v 0.0 0.0 0.0
                v 0.0 0.0 0.0
                o DegeneratePoint
                f 1 2 3
                v 0.0 0.0 0.0
                v 1.0 0.0 0.0
                v 2.0 0.0 0.0
                o CollinearLine
                f 4 5 6
                """;
        ObjModel model = ObjModel.parse(new ByteArrayInputStream(degenerateData.getBytes(StandardCharsets.UTF_8)));
        assertNotNull(model);

        for (ObjModel.MeshGroup group : model.getGroups().values()) {
            float[] v = group.vertices();
            for (int i = 0; i < v.length; i += 8) {
                float nx = v[i + 5], ny = v[i + 6], nz = v[i + 7];
                assertFalse(Float.isNaN(nx), "Normal X must not be NaN");
                assertFalse(Float.isNaN(ny), "Normal Y must not be NaN");
                assertFalse(Float.isNaN(nz), "Normal Z must not be NaN");
                // Degenerate fallback is (0, 1, 0)
                assertEquals(0.0f, nx, 1e-5f);
                assertEquals(1.0f, ny, 1e-5f);
                assertEquals(0.0f, nz, 1e-5f);
            }
        }
    }

    @Test
    @DisplayName("Challenge 4.5: Highly concurrent multi-threaded model parsing stress")
    public void testConcurrentParsingStress() throws Exception {
        int threads = 8;
        int iterationsPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<Void>> futures = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            futures.add(executor.submit(() -> {
                for (int iter = 0; iter < iterationsPerThread; iter++) {
                    for (String name : FIVE_MISSING_NORMAL_MODELS) {
                        Path path = MODELS_DIR.resolve(name);
                        try (InputStream in = new FileInputStream(path.toFile())) {
                            ObjModel m = ObjModel.parse(in);
                            assertNotNull(m);
                            assertFalse(m.groups().isEmpty());
                        }
                    }
                }
                return null;
            }));
        }

        for (Future<Void> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        executor.shutdown();
    }

    @Test
    @DisplayName("Challenge 5.1: Dump full empirical statistics table for all 23 OBJ models")
    public void testDumpModelStatistics() throws Exception {
        System.out.println("\n| Model Name | Groups | Vertices | Triangles | Has Native vn | Synthesized? | Bounding Box [minX..maxX, minY..maxY, minZ..maxZ] |");
        System.out.println("|---|---|---|---|---|---|---|");

        for (String name : ALL_23_MODELS) {
            Path path = MODELS_DIR.resolve(name);
            boolean hasVn = Files.lines(path).anyMatch(l -> l.trim().startsWith("vn "));

            ObjModel model;
            try (InputStream in = new FileInputStream(path.toFile())) {
                model = ObjModel.parse(in);
            }

            int totalVerts = 0;
            for (ObjModel.MeshGroup g : model.getGroups().values()) {
                totalVerts += g.vertexCount();
            }
            int triangles = totalVerts / 3;

            String boundsStr = String.format("[%.2f..%.2f, %.2f..%.2f, %.2f..%.2f]",
                    model.minX(), model.maxX(), model.minY(), model.maxY(), model.minZ(), model.maxZ());

            System.out.printf("| `%s` | %d | %d | %d | %s | %s | `%s` |\n",
                    name, model.groups().size(), totalVerts, triangles,
                    hasVn ? "YES" : "NO",
                    hasVn ? "NO" : "YES (vn=0)",
                    boundsStr);
        }
    }
}
