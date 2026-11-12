package com.ntmdoors.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pure Fabric Wavefront OBJ model parser and geometry holder.
 * Parses v, vt, vn, o/g, f, automatically synthesizes missing geometric face normals,
 * triangulates quads and n-gons, merges disjoint groups, computes group and model AABBs,
 * and stores vertices in flat primitive float arrays (stride 8: x, y, z, u, v, nx, ny, nz).
 */
@Environment(EnvType.CLIENT)
public final class ObjModel {

    private static final Logger LOGGER = LoggerFactory.getLogger("ntmdoors-obj");
    private static final Map<Identifier, ObjModel> CACHE = new ConcurrentHashMap<>();
    private static final Set<Identifier> WARNED = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final Map<String, MeshGroup> groups;
    private final float minX, minY, minZ;
    private final float maxX, maxY, maxZ;

    public ObjModel(Map<String, MeshGroup> groups) {
        this.groups = Collections.unmodifiableMap(new LinkedHashMap<>(groups));
        float mX = Float.POSITIVE_INFINITY, mY = Float.POSITIVE_INFINITY, mZ = Float.POSITIVE_INFINITY;
        float xX = Float.NEGATIVE_INFINITY, xY = Float.NEGATIVE_INFINITY, xZ = Float.NEGATIVE_INFINITY;

        for (MeshGroup group : groups.values()) {
            if (group.vertexCount() > 0) {
                mX = Math.min(mX, group.minX());
                mY = Math.min(mY, group.minY());
                mZ = Math.min(mZ, group.minZ());
                xX = Math.max(xX, group.maxX());
                xY = Math.max(xY, group.maxY());
                xZ = Math.max(xZ, group.maxZ());
            }
        }

        if (Float.isInfinite(mX)) {
            this.minX = this.minY = this.minZ = 0.0f;
            this.maxX = this.maxY = this.maxZ = 0.0f;
        } else {
            this.minX = mX; this.minY = mY; this.minZ = mZ;
            this.maxX = xX; this.maxY = xY; this.maxZ = xZ;
        }
    }

    public static ObjModel get(Identifier id) {
        if (id == null) return null;
        ObjModel cached = CACHE.get(id);
        if (cached != null) return cached;

        try {
            Optional<Resource> res = MinecraftClient.getInstance().getResourceManager().getResource(id);
            if (res.isPresent()) {
                try (InputStream in = res.get().getInputStream()) {
                    ObjModel parsed = parse(in);
                    CACHE.put(id, parsed);
                    return parsed;
                }
            } else {
                if (WARNED.add(id)) {
                    LOGGER.warn("Could not find door OBJ model: {}", id);
                }
            }
        } catch (Exception e) {
            if (WARNED.add(id)) {
                LOGGER.warn("Failed to load door OBJ model {}: {}", id, e.getMessage());
            }
        }
        return null;
    }

    public static void clearCache() {
        CACHE.clear();
        WARNED.clear();
    }

    public static void clear() {
        clearCache();
    }

    public float minX() { return minX; }
    public float minY() { return minY; }
    public float minZ() { return minZ; }
    public float maxX() { return maxX; }
    public float maxY() { return maxY; }
    public float maxZ() { return maxZ; }

    public Box getBoundingBox() {
        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public float[] bounds(Collection<String> groupNames) {
        if (groupNames == null || groupNames.isEmpty()) {
            return new float[]{minX, minY, minZ, maxX, maxY, maxZ};
        }
        float mX = Float.POSITIVE_INFINITY, mY = Float.POSITIVE_INFINITY, mZ = Float.POSITIVE_INFINITY;
        float xX = Float.NEGATIVE_INFINITY, xY = Float.NEGATIVE_INFINITY, xZ = Float.NEGATIVE_INFINITY;
        boolean found = false;

        for (String name : groupNames) {
            MeshGroup g = groups.get(name);
            if (g != null && g.vertexCount() > 0) {
                found = true;
                mX = Math.min(mX, g.minX());
                mY = Math.min(mY, g.minY());
                mZ = Math.min(mZ, g.minZ());
                xX = Math.max(xX, g.maxX());
                xY = Math.max(xY, g.maxY());
                xZ = Math.max(xZ, g.maxZ());
            }
        }

        if (!found) return null;
        return new float[]{mX, mY, mZ, xX, xY, xZ};
    }

    public Box getBoundingBox(Collection<String> groupNames) {
        float[] b = bounds(groupNames);
        if (b == null) return getBoundingBox();
        return new Box(b[0], b[1], b[2], b[3], b[4], b[5]);
    }

    public Set<String> groups() {
        return groups.keySet();
    }

    public Map<String, MeshGroup> getGroups() {
        return groups;
    }

    public MeshGroup getGroup(String name) {
        return groups.get(name);
    }

    public void render(MatrixStack matrices, VertexConsumer consumer, int light, int overlay) {
        render(matrices, consumer, light, overlay, 1.0f, 1.0f, 1.0f, 1.0f, null);
    }

    public void render(MatrixStack matrices, VertexConsumer consumer, int light, int overlay,
                       float red, float green, float blue, float alpha, ClipBounds clipBounds) {
        for (MeshGroup group : groups.values()) {
            group.render(matrices, consumer, light, overlay, red, green, blue, alpha, clipBounds);
        }
    }

    public void renderGroup(MatrixStack matrices, VertexConsumer consumer, String groupName,
                            int light, int overlay) {
        renderGroup(matrices, consumer, groupName, light, overlay, 1.0f, 1.0f, 1.0f, 1.0f, null);
    }

    public void renderGroup(MatrixStack matrices, VertexConsumer consumer, String groupName,
                            int light, int overlay, float red, float green, float blue, float alpha,
                            ClipBounds clipBounds) {
        MeshGroup group = groups.get(groupName);
        if (group != null) {
            group.render(matrices, consumer, light, overlay, red, green, blue, alpha, clipBounds);
        }
    }

    /**
     * Parses an OBJ file from an InputStream.
     */
    public static ObjModel parse(InputStream in) throws IOException {
        List<float[]> positions = new ArrayList<>(1024);
        List<float[]> uvs = new ArrayList<>(1024);
        List<float[]> normals = new ArrayList<>(1024);

        // Map from group name to list of vertices (each float[8])
        Map<String, List<float[]>> groupVertices = new LinkedHashMap<>();
        String currentGroup = "default";

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.charAt(0) == '#') continue;

                String[] tokens = line.split("\\s+");
                if (tokens.length == 0) continue;

                String type = tokens[0];
                switch (type) {
                    case "v" -> {
                        if (tokens.length >= 4) {
                            float x = Float.parseFloat(tokens[1]);
                            float y = Float.parseFloat(tokens[2]);
                            float z = Float.parseFloat(tokens[3]);
                            positions.add(new float[]{x, y, z});
                        }
                    }
                    case "vt" -> {
                        if (tokens.length >= 3) {
                            float u = Float.parseFloat(tokens[1]);
                            float v = 1.0f - Float.parseFloat(tokens[2]); // Minecraft UV flip
                            uvs.add(new float[]{u, v});
                        }
                    }
                    case "vn" -> {
                        if (tokens.length >= 4) {
                            float nx = Float.parseFloat(tokens[1]);
                            float ny = Float.parseFloat(tokens[2]);
                            float nz = Float.parseFloat(tokens[3]);
                            float lenSq = nx * nx + ny * ny + nz * nz;
                            if (lenSq > 1e-12f) {
                                float inv = (float) (1.0 / Math.sqrt(lenSq));
                                nx *= inv; ny *= inv; nz *= inv;
                            }
                            normals.add(new float[]{nx, ny, nz});
                        }
                    }
                    case "o", "g" -> {
                        if (tokens.length > 1) {
                            currentGroup = tokens[1];
                        }
                    }
                    case "f" -> {
                        if (tokens.length < 4) continue;
                        int faceVertCount = tokens.length - 1;
                        float[][] faceVerts = new float[faceVertCount][8];
                        boolean missingNormal = false;

                        for (int i = 0; i < faceVertCount; i++) {
                            String token = tokens[i + 1];
                            String[] indices = token.split("/", -1);

                            // Vertex position
                            int vIdx = Integer.parseInt(indices[0]);
                            float[] pos = resolveIndex(positions, vIdx);
                            faceVerts[i][0] = pos[0];
                            faceVerts[i][1] = pos[1];
                            faceVerts[i][2] = pos[2];

                            // Texture UV
                            if (indices.length > 1 && !indices[1].isEmpty()) {
                                int vtIdx = Integer.parseInt(indices[1]);
                                float[] uv = resolveIndex(uvs, vtIdx);
                                faceVerts[i][3] = uv[0];
                                faceVerts[i][4] = uv[1];
                            } else {
                                faceVerts[i][3] = 0.0f;
                                faceVerts[i][4] = 0.0f;
                            }

                            // Normal
                            if (indices.length > 2 && !indices[2].isEmpty()) {
                                int vnIdx = Integer.parseInt(indices[2]);
                                float[] norm = resolveIndex(normals, vnIdx);
                                faceVerts[i][5] = norm[0];
                                faceVerts[i][6] = norm[1];
                                faceVerts[i][7] = norm[2];
                            } else {
                                missingNormal = true;
                            }
                        }

                        // Synthesize missing geometric face normal from edges
                        if (missingNormal) {
                            float ax = faceVerts[1][0] - faceVerts[0][0];
                            float ay = faceVerts[1][1] - faceVerts[0][1];
                            float az = faceVerts[1][2] - faceVerts[0][2];
                            float bx = faceVerts[2][0] - faceVerts[0][0];
                            float by = faceVerts[2][1] - faceVerts[0][1];
                            float bz = faceVerts[2][2] - faceVerts[0][2];

                            float nx = ay * bz - az * by;
                            float ny = az * bx - ax * bz;
                            float nz = ax * by - ay * bx;
                            float lenSq = nx * nx + ny * ny + nz * nz;
                            if (lenSq > 1e-12f) {
                                float inv = (float) (1.0 / Math.sqrt(lenSq));
                                nx *= inv; ny *= inv; nz *= inv;
                            } else {
                                nx = 0.0f; ny = 1.0f; nz = 0.0f;
                            }

                            for (int i = 0; i < faceVertCount; i++) {
                                faceVerts[i][5] = nx;
                                faceVerts[i][6] = ny;
                                faceVerts[i][7] = nz;
                            }
                        }

                        // Triangulate n-gon via triangle fan (0, i, i + 1)
                        List<float[]> groupList = groupVertices.computeIfAbsent(currentGroup, k -> new ArrayList<>());
                        for (int i = 1; i < faceVertCount - 1; i++) {
                            groupList.add(faceVerts[0]);
                            groupList.add(faceVerts[i]);
                            groupList.add(faceVerts[i + 1]);
                        }
                    }
                }
            }
        }

        // Build flat primitive MeshGroup for each group (disjoint groups merged via map)
        Map<String, MeshGroup> resultGroups = new LinkedHashMap<>();
        for (Map.Entry<String, List<float[]>> entry : groupVertices.entrySet()) {
            List<float[]> vList = entry.getValue();
            int vCount = vList.size();
            float[] flat = new float[vCount * 8];
            float gMinX = Float.POSITIVE_INFINITY, gMinY = Float.POSITIVE_INFINITY, gMinZ = Float.POSITIVE_INFINITY;
            float gMaxX = Float.NEGATIVE_INFINITY, gMaxY = Float.NEGATIVE_INFINITY, gMaxZ = Float.NEGATIVE_INFINITY;

            for (int i = 0; i < vCount; i++) {
                float[] vert = vList.get(i);
                int off = i * 8;
                System.arraycopy(vert, 0, flat, off, 8);

                float x = vert[0], y = vert[1], z = vert[2];
                gMinX = Math.min(gMinX, x);
                gMinY = Math.min(gMinY, y);
                gMinZ = Math.min(gMinZ, z);
                gMaxX = Math.max(gMaxX, x);
                gMaxY = Math.max(gMaxY, y);
                gMaxZ = Math.max(gMaxZ, z);
            }

            if (Float.isInfinite(gMinX)) {
                gMinX = gMinY = gMinZ = 0.0f;
                gMaxX = gMaxY = gMaxZ = 0.0f;
            }

            resultGroups.put(entry.getKey(), new MeshGroup(entry.getKey(), flat, vCount,
                    gMinX, gMinY, gMinZ, gMaxX, gMaxY, gMaxZ));
        }

        return new ObjModel(resultGroups);
    }

    private static float[] resolveIndex(List<float[]> list, int index) {
        if (index > 0) {
            return list.get(index - 1);
        } else {
            return list.get(list.size() + index);
        }
    }

    public static final class MeshGroup {
        private final String name;
        private final float[] vertices; // 8 floats per vertex: x y z u v nx ny nz
        private final int vertexCount;
        private final float minX, minY, minZ;
        private final float maxX, maxY, maxZ;

        public MeshGroup(String name, float[] vertices, int vertexCount,
                         float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
            this.name = name;
            this.vertices = vertices;
            this.vertexCount = vertexCount;
            this.minX = minX; this.minY = minY; this.minZ = minZ;
            this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        }

        public String name() { return name; }
        public int vertexCount() { return vertexCount; }
        public float[] vertices() { return vertices; }
        public float[] data() { return vertices; }
        public float minX() { return minX; }
        public float minY() { return minY; }
        public float minZ() { return minZ; }
        public float maxX() { return maxX; }
        public float maxY() { return maxY; }
        public float maxZ() { return maxZ; }

        public Box getBoundingBox() {
            return new Box(minX, minY, minZ, maxX, maxY, maxZ);
        }

        public void render(MatrixStack matrices, VertexConsumer consumer, int light, int overlay,
                           float red, float green, float blue, float alpha, ClipBounds clipBounds) {
            VertexConsumer quadConsumer = QuadAdaptingVertexConsumer.wrap(consumer);
            MatrixStack.Entry entry = matrices.peek();
            Matrix4f posMatrix = entry.getPositionMatrix();
            Matrix3f normMatrix = entry.getNormalMatrix();

            if (clipBounds == null || clipBounds.isInfinite()) {
                // Zero-allocation unclipped stream
                float[] d = this.vertices;
                int len = d.length;
                for (int i = 0; i < len; i += 8) {
                    quadConsumer.vertex(posMatrix, d[i], d[i + 1], d[i + 2])
                            .color(red, green, blue, alpha)
                            .texture(d[i + 3], d[i + 4])
                            .overlay(overlay)
                            .light(light)
                            .normal(normMatrix, d[i + 5], d[i + 6], d[i + 7])
                            .next();
                }
            } else {
                // Clipped stream: matrices.peek() is in Model Space
                float[] d = this.vertices;
                int len = d.length;
                for (int i = 0; i < len; i += 24) {
                    ObjClipping.clipAndEmitTriangle(
                            posMatrix, normMatrix,
                            null, null,
                            quadConsumer, d, i, i + 8, i + 16,
                            clipBounds, light, overlay, red, green, blue, alpha
                    );
                }
            }
        }
    }
}
