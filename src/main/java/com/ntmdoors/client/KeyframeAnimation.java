package com.ntmdoors.client;

import com.ntmdoors.NTMDoors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Client-side keyframed model animation. The transition seal is a Collada scene with 20 animated
 * nodes and non-trivial rotations; per-group rigid matrices preserve the mechanical motion much
 * better than hand-written linear translations.
 *
 * File format (one sample per line):
 *   group time m00 m01 m02 m03 m10 m11 m12 m13 m20 m21 m22 m23
 * Time is the door open fraction. Matrices are row-major affine transforms in model space.
 */
@SideOnly(Side.CLIENT)
public final class KeyframeAnimation {

    private static final Map<ResourceLocation, Map<String, KeyframeAnimation>> CACHE = new HashMap<>();
    private static final Set<ResourceLocation> WARNED = new HashSet<>();
    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);

    private final Sample[] samples;

    private KeyframeAnimation(Sample[] samples) {
        this.samples = samples;
    }

    public static Map<String, KeyframeAnimation> get(ResourceLocation location) {
        if (location == null) return Collections.emptyMap();
        if (CACHE.containsKey(location)) {
            // A null entry marks a failed load, cached so the file is not re-parsed every frame.
            Map<String, KeyframeAnimation> cached = CACHE.get(location);
            return cached != null ? cached : Collections.emptyMap();
        }

        Map<String, KeyframeAnimation> loaded = load(location);
        CACHE.put(location, loaded);
        return loaded != null ? loaded : Collections.emptyMap();
    }

    public static void clear() {
        CACHE.clear();
        WARNED.clear();
    }

    private static Map<String, KeyframeAnimation> load(ResourceLocation location) {
        Map<String, List<Sample>> lists = new LinkedHashMap<>();
        try (IResource res = Minecraft.getMinecraft().getResourceManager().getResource(location);
             BufferedReader reader = new BufferedReader(new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.charAt(0) == '#') continue;
                String[] parts = line.split("\\s+");
                if (parts.length < 14) continue;
                float time = Float.parseFloat(parts[1]);
                float[] matrix = new float[12];
                for (int i = 0; i < 12; i++) matrix[i] = Float.parseFloat(parts[i + 2]);
                lists.computeIfAbsent(parts[0], k -> new ArrayList<>()).add(new Sample(time, matrix));
            }
        } catch (Exception ex) {
            if (WARNED.add(location)) {
                NTMDoors.LOGGER.warn("Could not load door keyframe animation {} ({})", location, ex.toString());
            }
            return null;
        }

        if (lists.isEmpty()) return Collections.emptyMap();
        Map<String, KeyframeAnimation> out = new HashMap<>();
        for (Map.Entry<String, List<Sample>> entry : lists.entrySet()) {
            List<Sample> sorted = entry.getValue();
            sorted.sort((a, b) -> Float.compare(a.time, b.time));
            out.put(entry.getKey(), new KeyframeAnimation(sorted.toArray(new Sample[0])));
        }
        return out;
    }

    /** Applies this group's interpolated transform to the current model-view matrix. */
    public void apply(float open) {
        if (samples.length == 0) return;

        float t = Math.max(0F, Math.min(1F, open));
        Sample a = samples[0];
        Sample b = samples[0];
        if (t <= samples[0].time) {
            a = b = samples[0];
        } else if (t >= samples[samples.length - 1].time) {
            a = b = samples[samples.length - 1];
        } else {
            // Samples are sorted and the renderer runs at most two adjacent DAE frames apart.
            // A binary search keeps this cheap for the 577-frame seal action.
            int lo = 0, hi = samples.length - 1;
            while (lo + 1 < hi) {
                int mid = (lo + hi) >>> 1;
                if (samples[mid].time <= t) lo = mid;
                else hi = mid;
            }
            a = samples[lo];
            b = samples[hi];
        }

        float blend = b.time > a.time ? (t - a.time) / (b.time - a.time) : 0F;
        float tx = a.tx + (b.tx - a.tx) * blend;
        float ty = a.ty + (b.ty - a.ty) * blend;
        float tz = a.tz + (b.tz - a.tz) * blend;

        // HBM's AnimatedModel slerps rotations and lerps translations. Interpolating the raw
        // matrix cells instead would visibly shrink fast-moving parts.
        float qx = a.qx, qy = a.qy, qz = a.qz, qw = a.qw;
        float bx = b.qx, by = b.qy, bz = b.qz, bw = b.qw;
        float dot = qx * bx + qy * by + qz * bz + qw * bw;
        if (dot < 0F) {
            dot = -dot;
            bx = -bx; by = -by; bz = -bz; bw = -bw;
        }
        if (dot > 0.9995F) {
            qx += (bx - qx) * blend;
            qy += (by - qy) * blend;
            qz += (bz - qz) * blend;
            qw += (bw - qw) * blend;
            float inv = 1F / (float) Math.sqrt(qx * qx + qy * qy + qz * qz + qw * qw);
            qx *= inv; qy *= inv; qz *= inv; qw *= inv;
        } else {
            float theta0 = (float) Math.acos(dot);
            float sin0 = (float) Math.sin(theta0);
            float s0 = (float) Math.sin((1F - blend) * theta0) / sin0;
            float s1 = (float) Math.sin(blend * theta0) / sin0;
            float x = qx * s0 + bx * s1;
            float y = qy * s0 + by * s1;
            float z = qz * s0 + bz * s1;
            float w = qw * s0 + bw * s1;
            qx = x; qy = y; qz = z; qw = w;
        }

        // OpenGL matrices are column-major. This is the same quaternion-to-matrix conversion
        // used by HBM's Transform.quatToGlMatrix.
        float xx = qx * qx, xy = qx * qy, xz = qx * qz, xw = qx * qw;
        float yy = qy * qy, yz = qy * qz, yw = qy * qw;
        float zz = qz * qz, zw = qz * qw;
        MATRIX.clear();
        MATRIX.put(1F - 2F * (yy + zz)).put(2F * (xy + zw)).put(2F * (xz - yw)).put(0F);
        MATRIX.put(2F * (xy - zw)).put(1F - 2F * (xx + zz)).put(2F * (yz + xw)).put(0F);
        MATRIX.put(2F * (xz + yw)).put(2F * (yz - xw)).put(1F - 2F * (xx + yy)).put(0F);
        MATRIX.put(tx).put(ty).put(tz).put(1F);
        MATRIX.flip();
        GL11.glMultMatrix(MATRIX);
    }

    private static final class Sample {
        final float time;
        final float tx, ty, tz;
        final float qx, qy, qz, qw;

        Sample(float time, float[] m) {
            this.time = time;
            this.tx = m[3];
            this.ty = m[7];
            this.tz = m[11];

            float trace = m[0] + m[5] + m[10];
            float x, y, z, w;
            if (trace > 0F) {
                float s = (float) Math.sqrt(trace + 1F) * 2F;
                w = 0.25F * s;
                x = (m[9] - m[6]) / s;
                y = (m[2] - m[8]) / s;
                z = (m[4] - m[1]) / s;
            } else if (m[0] > m[5] && m[0] > m[10]) {
                float s = (float) Math.sqrt(1F + m[0] - m[5] - m[10]) * 2F;
                w = (m[9] - m[6]) / s;
                x = 0.25F * s;
                y = (m[1] + m[4]) / s;
                z = (m[2] + m[8]) / s;
            } else if (m[5] > m[10]) {
                float s = (float) Math.sqrt(1F + m[5] - m[0] - m[10]) * 2F;
                w = (m[2] - m[8]) / s;
                x = (m[1] + m[4]) / s;
                y = 0.25F * s;
                z = (m[6] + m[9]) / s;
            } else {
                float s = (float) Math.sqrt(1F + m[10] - m[0] - m[5]) * 2F;
                w = (m[4] - m[1]) / s;
                x = (m[2] + m[8]) / s;
                y = (m[6] + m[9]) / s;
                z = 0.25F * s;
            }
            float inv = 1F / (float) Math.sqrt(x * x + y * y + z * z + w * w);
            this.qx = x * inv;
            this.qy = y * inv;
            this.qz = z * inv;
            this.qw = w * inv;
        }
    }
}
