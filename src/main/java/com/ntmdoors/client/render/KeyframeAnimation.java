package com.ntmdoors.client.render;

import com.ntmdoors.NTMDoors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Parses and applies keyframed matrix animations (.anim) for animated door models.
 * Decomposes 4x3 row-major affine matrices into translation vectors and rotation quaternions
 * using Shepperd's algorithm, interpolating smoothly by open fraction.
 */
@Environment(EnvType.CLIENT)
public final class KeyframeAnimation {

    private static final Map<Identifier, Map<String, KeyframeAnimation>> CACHE = new ConcurrentHashMap<>();
    private static final Set<Identifier> WARNED = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final Sample[] samples;

    public KeyframeAnimation(Sample[] samples) {
        this.samples = samples;
    }

    public static Map<String, KeyframeAnimation> get(Identifier location) {
        if (location == null) return Collections.emptyMap();
        Map<String, KeyframeAnimation> cached = CACHE.get(location);
        if (cached != null) return cached;

        try {
            Optional<Resource> res = MinecraftClient.getInstance().getResourceManager().getResource(location);
            if (res.isPresent()) {
                try (InputStream in = res.get().getInputStream()) {
                    Map<String, KeyframeAnimation> loaded = parse(in);
                    CACHE.put(location, loaded);
                    return loaded;
                }
            } else {
                if (WARNED.add(location)) {
                    NTMDoors.LOGGER.warn("Could not find door keyframe animation {}", location);
                }
            }
        } catch (Exception ex) {
            if (WARNED.add(location)) {
                NTMDoors.LOGGER.warn("Failed to load door keyframe animation {}: {}", location, ex.getMessage());
            }
        }
        return Collections.emptyMap();
    }

    public static void clearCache() {
        CACHE.clear();
        WARNED.clear();
    }

    /**
     * Parses keyframe animation samples from an InputStream.
     */
    public static Map<String, KeyframeAnimation> parse(InputStream in) throws Exception {
        Map<String, List<Sample>> lists = new LinkedHashMap<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.charAt(0) == '#') continue;

                String[] parts = line.split("\\s+");
                if (parts.length < 14) continue;

                String group = parts[0];
                float time = Float.parseFloat(parts[1]);
                float[] m = new float[12];
                for (int i = 0; i < 12; i++) {
                    m[i] = Float.parseFloat(parts[i + 2]);
                }
                lists.computeIfAbsent(group, k -> new ArrayList<>()).add(new Sample(time, m));
            }
        }

        if (lists.isEmpty()) return Collections.emptyMap();

        Map<String, KeyframeAnimation> out = new HashMap<>();
        for (Map.Entry<String, List<Sample>> entry : lists.entrySet()) {
            List<Sample> sorted = entry.getValue();
            sorted.sort(Comparator.comparingDouble(a -> a.time));
            out.put(entry.getKey(), new KeyframeAnimation(sorted.toArray(new Sample[0])));
        }
        return out;
    }

    public int sampleCount() {
        return samples.length;
    }

    /**
     * Applies this group's interpolated rigid transform into the MatrixStack.
     */
    public void apply(MatrixStack matrices, float openFraction) {
        if (samples.length == 0) return;

        float t = MathHelper.clamp(openFraction, 0.0f, 1.0f);
        Sample a;
        Sample b;

        if (t <= samples[0].time) {
            a = b = samples[0];
        } else if (t >= samples[samples.length - 1].time) {
            a = b = samples[samples.length - 1];
        } else {
            int lo = 0, hi = samples.length - 1;
            while (lo + 1 < hi) {
                int mid = (lo + hi) >>> 1;
                if (samples[mid].time <= t) lo = mid;
                else hi = mid;
            }
            a = samples[lo];
            b = samples[hi];
        }

        float blend = b.time > a.time ? (t - a.time) / (b.time - a.time) : 0.0f;
        float tx = MathHelper.lerp(blend, a.tx, b.tx);
        float ty = MathHelper.lerp(blend, a.ty, b.ty);
        float tz = MathHelper.lerp(blend, a.tz, b.tz);

        Quaternionf rot = new Quaternionf(a.rotation).slerp(b.rotation, blend);

        matrices.translate(tx, ty, tz);
        matrices.multiply(rot);
    }

    /**
     * Computes the interpolated Matrix4f without modifying MatrixStack.
     */
    public Matrix4f getInterpolatedMatrix(float openFraction, Matrix4f dest) {
        if (samples.length == 0) return dest.identity();

        float t = MathHelper.clamp(openFraction, 0.0f, 1.0f);
        Sample a;
        Sample b;

        if (t <= samples[0].time) {
            a = b = samples[0];
        } else if (t >= samples[samples.length - 1].time) {
            a = b = samples[samples.length - 1];
        } else {
            int lo = 0, hi = samples.length - 1;
            while (lo + 1 < hi) {
                int mid = (lo + hi) >>> 1;
                if (samples[mid].time <= t) lo = mid;
                else hi = mid;
            }
            a = samples[lo];
            b = samples[hi];
        }

        float blend = b.time > a.time ? (t - a.time) / (b.time - a.time) : 0.0f;
        float tx = MathHelper.lerp(blend, a.tx, b.tx);
        float ty = MathHelper.lerp(blend, a.ty, b.ty);
        float tz = MathHelper.lerp(blend, a.tz, b.tz);

        Quaternionf rot = new Quaternionf(a.rotation).slerp(b.rotation, blend);
        return dest.translationRotate(tx, ty, tz, rot);
    }

    public static final class Sample {
        public final float time;
        public final float tx, ty, tz;
        public final Quaternionf rotation;

        public Sample(float time, float[] m) {
            this.time = time;
            this.tx = m[3];
            this.ty = m[7];
            this.tz = m[11];

            // Shepperd's algorithm: robust extraction of quaternion from 3x3 rotation matrix
            float trace = m[0] + m[5] + m[10];
            float x, y, z, w;
            if (trace > 0.0f) {
                float s = (float) Math.sqrt(trace + 1.0f) * 2.0f;
                w = 0.25f * s;
                x = (m[9] - m[6]) / s;
                y = (m[2] - m[8]) / s;
                z = (m[4] - m[1]) / s;
            } else if (m[0] > m[5] && m[0] > m[10]) {
                float s = (float) Math.sqrt(1.0f + m[0] - m[5] - m[10]) * 2.0f;
                w = (m[9] - m[6]) / s;
                x = 0.25f * s;
                y = (m[1] + m[4]) / s;
                z = (m[2] + m[8]) / s;
            } else if (m[5] > m[10]) {
                float s = (float) Math.sqrt(1.0f + m[5] - m[0] - m[10]) * 2.0f;
                w = (m[2] - m[8]) / s;
                x = (m[1] + m[4]) / s;
                y = 0.25f * s;
                z = (m[6] + m[9]) / s;
            } else {
                float s = (float) Math.sqrt(1.0f + m[10] - m[0] - m[5]) * 2.0f;
                w = (m[4] - m[1]) / s;
                x = (m[2] + m[8]) / s;
                y = (m[6] + m[9]) / s;
                z = 0.25f * s;
            }

            float inv = 1.0f / (float) Math.sqrt(x * x + y * y + z * z + w * w);
            this.rotation = new Quaternionf(x * inv, y * inv, z * inv, w * inv);
        }
    }
}
