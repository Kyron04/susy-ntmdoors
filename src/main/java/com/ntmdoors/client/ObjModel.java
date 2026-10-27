package com.ntmdoors.client;

import com.ntmdoors.NTMDoors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Minimal Wavefront OBJ loader. Each "o"/"g" group becomes a separate GL display list so parts can be
 * transformed individually. Faces are triangulated; V is flipped to Minecraft's texture orientation.
 */
@SideOnly(Side.CLIENT)
public class ObjModel {

    private static final Map<ResourceLocation, ObjModel> CACHE = new HashMap<>();
    private static final Set<ResourceLocation> WARNED = new HashSet<>();

    private final Map<String, float[]> data = new LinkedHashMap<>();   // 8 floats per vertex: x y z u v nx ny nz
    private final Map<String, float[]> bounds = new HashMap<>();       // per group: minX minY minZ maxX maxY maxZ
    private final Map<String, Integer> lists = new HashMap<>();

    private float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
    private float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;

    /** Returns null (and logs once) if the model cannot be loaded. Missing models are retried. */
    public static ObjModel get(ResourceLocation loc) {
        ObjModel cached = CACHE.get(loc);
        if (cached != null) return cached;
        // Do not cache a negative result: the first item/TESR draw can happen before the reload
        // listener has finished, and that would otherwise hide the model until F3+T.
        ObjModel m = null;
        try (IResource res = Minecraft.getMinecraft().getResourceManager().getResource(loc)) {
            m = parse(res.getInputStream());
        } catch (Exception ex) {
            if (WARNED.add(loc)) {
                NTMDoors.LOGGER.warn("Could not load door model {} ({})", loc, ex.toString());
            }
        }
        if (m != null) CACHE.put(loc, m);
        return m;
    }

    public static void clear() {
        for (ObjModel m : CACHE.values()) {
            for (int id : m.lists.values()) GLAllocation.deleteDisplayLists(id);
            m.lists.clear();
        }
        CACHE.clear();
        WARNED.clear();
    }

    public Set<String> groups() {
        return data.keySet();
    }

    public float minX() { return minX; }
    public float minY() { return minY; }
    public float minZ() { return minZ; }
    public float maxX() { return maxX; }
    public float maxY() { return maxY; }
    public float maxZ() { return maxZ; }

    /** Combined bounding box of the requested groups (minX, minY, minZ, maxX, maxY, maxZ), or null if none match. */
    public float[] bounds(Collection<String> groups) {
        float x1 = Float.POSITIVE_INFINITY, y1 = Float.POSITIVE_INFINITY, z1 = Float.POSITIVE_INFINITY;
        float x2 = Float.NEGATIVE_INFINITY, y2 = Float.NEGATIVE_INFINITY, z2 = Float.NEGATIVE_INFINITY;
        boolean any = false;
        for (String group : groups) {
            float[] b = bounds.get(group);
            if (b == null) continue;
            any = true;
            x1 = Math.min(x1, b[0]); y1 = Math.min(y1, b[1]); z1 = Math.min(z1, b[2]);
            x2 = Math.max(x2, b[3]); y2 = Math.max(y2, b[4]); z2 = Math.max(z2, b[5]);
        }
        return any ? new float[]{x1, y1, z1, x2, y2, z2} : null;
    }

    public void draw(String group) {
        float[] d = data.get(group);
        if (d == null) return;
        Integer id = lists.get(group);
        if (id == null) {
            id = compile(d);
            lists.put(group, id);
        }
        GL11.glCallList(id);
    }

    private static int compile(float[] d) {
        int id = GLAllocation.generateDisplayLists(1);
        GL11.glNewList(id, GL11.GL_COMPILE);
        GL11.glBegin(GL11.GL_TRIANGLES);
        for (int i = 0; i < d.length; i += 8) {
            GL11.glNormal3f(d[i + 5], d[i + 6], d[i + 7]);
            GL11.glTexCoord2f(d[i + 3], d[i + 4]);
            GL11.glVertex3f(d[i], d[i + 1], d[i + 2]);
        }
        GL11.glEnd();
        GL11.glEndList();
        return id;
    }

    // ------------------------------------------------------------ parsing

    private static ObjModel parse(InputStream in) throws IOException {
        List<float[]> v = new ArrayList<>(), vt = new ArrayList<>(), vn = new ArrayList<>();
        Map<String, List<float[]>> groups = new LinkedHashMap<>();
        String cur = "default";

        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String line;
        while ((line = r.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.charAt(0) == '#') continue;
            String[] t = line.split("\\s+");
            switch (t[0]) {
                case "v":
                    v.add(new float[]{f(t[1]), f(t[2]), f(t[3])});
                    break;
                case "vt":
                    vt.add(new float[]{f(t[1]), t.length > 2 ? f(t[2]) : 0F});
                    break;
                case "vn":
                    vn.add(new float[]{f(t[1]), f(t[2]), f(t[3])});
                    break;
                case "o":
                case "g":
                    cur = t.length > 1 ? t[1] : "default";
                    break;
                case "f":
                    addFace(t, v, vt, vn, groups.computeIfAbsent(cur, k -> new ArrayList<>()));
                    break;
                default:
                    break;
            }
        }

        ObjModel m = new ObjModel();
        for (Map.Entry<String, List<float[]>> e : groups.entrySet()) {
            List<float[]> verts = e.getValue();
            float[] flat = new float[verts.size() * 8];
            float gx1 = Float.POSITIVE_INFINITY, gy1 = Float.POSITIVE_INFINITY, gz1 = Float.POSITIVE_INFINITY;
            float gx2 = Float.NEGATIVE_INFINITY, gy2 = Float.NEGATIVE_INFINITY, gz2 = Float.NEGATIVE_INFINITY;
            for (int i = 0; i < verts.size(); i++) {
                float[] vert = verts.get(i);
                System.arraycopy(vert, 0, flat, i * 8, 8);
                gx1 = Math.min(gx1, vert[0]); gy1 = Math.min(gy1, vert[1]); gz1 = Math.min(gz1, vert[2]);
                gx2 = Math.max(gx2, vert[0]); gy2 = Math.max(gy2, vert[1]); gz2 = Math.max(gz2, vert[2]);
                m.minX = Math.min(m.minX, vert[0]);
                m.minY = Math.min(m.minY, vert[1]);
                m.minZ = Math.min(m.minZ, vert[2]);
                m.maxX = Math.max(m.maxX, vert[0]);
                m.maxY = Math.max(m.maxY, vert[1]);
                m.maxZ = Math.max(m.maxZ, vert[2]);
            }
            m.bounds.put(e.getKey(), new float[]{gx1, gy1, gz1, gx2, gy2, gz2});
            m.data.put(e.getKey(), flat);
        }
        return m;
    }

    private static float f(String s) {
        return Float.parseFloat(s);
    }

    private static int idx(String s, int size) {
        int i = Integer.parseInt(s);
        return i < 0 ? size + i : i - 1;
    }

    private static void addFace(String[] t, List<float[]> v, List<float[]> vt, List<float[]> vn, List<float[]> out) {
        int n = t.length - 1;
        if (n < 3) return;
        float[][] fv = new float[n][8];
        boolean missingNormal = false;
        for (int i = 0; i < n; i++) {
            String[] p = t[i + 1].split("/", -1);
            float[] pos = v.get(idx(p[0], v.size()));
            fv[i][0] = pos[0]; fv[i][1] = pos[1]; fv[i][2] = pos[2];
            if (p.length > 1 && !p[1].isEmpty()) {
                float[] uv = vt.get(idx(p[1], vt.size()));
                fv[i][3] = uv[0];
                fv[i][4] = 1F - uv[1];
            }
            if (p.length > 2 && !p[2].isEmpty()) {
                float[] nr = vn.get(idx(p[2], vn.size()));
                fv[i][5] = nr[0]; fv[i][6] = nr[1]; fv[i][7] = nr[2];
            } else {
                missingNormal = true;
            }
        }
        if (missingNormal) {
            float ax = fv[1][0] - fv[0][0], ay = fv[1][1] - fv[0][1], az = fv[1][2] - fv[0][2];
            float bx = fv[2][0] - fv[0][0], by = fv[2][1] - fv[0][1], bz = fv[2][2] - fv[0][2];
            float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len > 0F) { nx /= len; ny /= len; nz /= len; }
            for (float[] vert : fv) { vert[5] = nx; vert[6] = ny; vert[7] = nz; }
        }
        for (int i = 1; i < n - 1; i++) {   // triangle fan
            out.add(fv[0]);
            out.add(fv[i]);
            out.add(fv[i + 1]);
        }
    }
}
