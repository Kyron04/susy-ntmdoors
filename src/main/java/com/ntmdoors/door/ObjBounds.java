package com.ntmdoors.door;

import com.ntmdoors.NTMDoors;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the per-group bounds of a door OBJ on the logical server.
 *
 * <p>The renderer already has this information in {@code ObjModel}, but that class is client-only.
 * Collision needs the same model-space extents, so this parser reads only the vertex, group and
 * face lines (no UVs or normals) and caches one map per model. It is only used by doors that bind
 * a collision box to an OBJ group with {@code movingGroup(...)}.</p>
 */
public final class ObjBounds {

    private static final Map<ResourceLocation, Map<String, AxisAlignedBB>> CACHE = new HashMap<>();

    private ObjBounds() {
    }

    public static synchronized Map<String, AxisAlignedBB> get(ResourceLocation model) {
        if (model == null) return Collections.emptyMap();
        Map<String, AxisAlignedBB> cached = CACHE.get(model);
        if (cached != null) return cached;
        Map<String, AxisAlignedBB> loaded = load(model);
        CACHE.put(model, loaded);
        return loaded;
    }

    private static Map<String, AxisAlignedBB> load(ResourceLocation model) {
        String path = "/assets/" + model.getNamespace() + "/" + model.getPath();
        try (InputStream in = ObjBounds.class.getResourceAsStream(path)) {
            if (in == null) {
                NTMDoors.LOGGER.warn("Door collision: missing model {}", path);
                return Collections.emptyMap();
            }
            return parse(in);
        } catch (Exception ex) {
            NTMDoors.LOGGER.warn("Door collision: could not read {} ({})", path, ex.toString());
            return Collections.emptyMap();
        }
    }

    private static Map<String, AxisAlignedBB> parse(InputStream in) throws Exception {
        List<double[]> vertices = new ArrayList<>();
        Map<String, double[]> bounds = new LinkedHashMap<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String group = "default";
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.charAt(0) == '#') continue;
            String[] parts = line.split("\\s+");
            switch (parts[0]) {
                case "v":
                    if (parts.length >= 4) {
                        vertices.add(new double[]{
                                Double.parseDouble(parts[1]),
                                Double.parseDouble(parts[2]),
                                Double.parseDouble(parts[3])});
                    }
                    break;
                case "o":
                case "g":
                    group = parts.length > 1 ? parts[1] : "default";
                    break;
                case "f":
                    double[] b = bounds.computeIfAbsent(group, k -> new double[]{
                            Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                            Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY});
                    for (int i = 1; i < parts.length; i++) {
                        int index = vertexIndex(parts[i], vertices.size());
                        if (index < 0) continue;
                        double[] v = vertices.get(index);
                        if (v[0] < b[0]) b[0] = v[0];
                        if (v[1] < b[1]) b[1] = v[1];
                        if (v[2] < b[2]) b[2] = v[2];
                        if (v[0] > b[3]) b[3] = v[0];
                        if (v[1] > b[4]) b[4] = v[1];
                        if (v[2] > b[5]) b[5] = v[2];
                    }
                    break;
                default:
                    break;
            }
        }
        Map<String, AxisAlignedBB> out = new LinkedHashMap<>();
        for (Map.Entry<String, double[]> e : bounds.entrySet()) {
            double[] b = e.getValue();
            if (b[0] <= b[3] && b[1] <= b[4] && b[2] <= b[5]) {
                out.put(e.getKey(), new AxisAlignedBB(b[0], b[1], b[2], b[3], b[4], b[5]));
            }
        }
        return out;
    }

    private static int vertexIndex(String token, int size) {
        int slash = token.indexOf('/');
        if (slash >= 0) token = token.substring(0, slash);
        if (token.isEmpty()) return -1;
        try {
            int index = Integer.parseInt(token);
            return index < 0 ? size + index : index - 1;
        } catch (NumberFormatException ex) {
            return -1;
        }
    }
}
