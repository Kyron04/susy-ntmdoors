import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

/**
 * Generates the Emancipation Grill field textures. Standalone tool; run with JDK 8.
 *
 * <p>The field texture is a seamless, pale white-cyan wisp pattern. It is intentionally
 * low-contrast so the in-game renderer can layer it three times at different scales and scroll
 * speeds without turning into a bright fog bank.</p>
 */
public final class FieldTextureGenerator {

    private static final int FIELD_SIZE = 256;
    private static final int EDGE_WIDTH = 64;
    private static final int EDGE_HEIGHT = 16;
    private static final int SPARK_SIZE = 16;

    /**
     * Four cohesive variants of the same pale glass look. Different seeds and small parameter
     * changes give each pair a different splash without turning them into different materials.
     */
    private static final FieldVariant[] FIELD_VARIANTS = {
            new FieldVariant("emancipation_grill_field.png", 1337L, 0.43F, 0.17F, 0.33F, 1.28F),
            new FieldVariant("emancipation_grill_field_2.png", 2718L, 0.47F, 0.16F, 0.26F, 1.00F),
            new FieldVariant("emancipation_grill_field_3.png", 3141L, 0.40F, 0.19F, 0.32F, 1.26F),
            new FieldVariant("emancipation_grill_field_4.png", 4242L, 0.45F, 0.18F, 0.21F, 0.80F)
    };

    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : ".");
        if (!out.exists() && !out.mkdirs()) {
            throw new IllegalStateException("Cannot create " + out);
        }

        for (FieldVariant variant : FIELD_VARIANTS) {
            writeField(new File(out, variant.fileName), variant);
        }
        writeEdge(new File(out, "emancipation_grill_field_edge.png"));
        writeSpark(new File(out, "emancipation_grill_field_spark.png"));
        System.out.println("Wrote " + FIELD_VARIANTS.length
                + " field variants, edge and spark textures to " + out.getAbsolutePath());
    }

    private static void writeField(File file, FieldVariant variant) throws Exception {
        BufferedImage image = new BufferedImage(FIELD_SIZE, FIELD_SIZE, BufferedImage.TYPE_INT_ARGB);

        Fbm cloud = new Fbm(variant.seed, 4, variant.stretch);
        Fbm warpX = new Fbm(variant.seed + 101L, 2, 0.85F);
        Fbm warpY = new Fbm(variant.seed + 202L, 2, 0.85F);
        Fbm ridgeNoise = new Fbm(variant.seed + 303L, 3, variant.stretch * 0.72F);

        float warp = variant.warp;
        float threshold = variant.threshold;
        float gamma = variant.gamma;

        for (int y = 0; y < FIELD_SIZE; y++) {
            float v = y / (float) FIELD_SIZE;
            for (int x = 0; x < FIELD_SIZE; x++) {
                float u = x / (float) FIELD_SIZE;

                float wx = warpX.sample(u, v) - 0.5F;
                float wy = warpY.sample(u, v) - 0.5F;

                float n = cloud.sample(u + warp * wx, v + warp * wy);
                float rn = ridgeNoise.sample(u + warp * 0.55F * wx, v + warp * 0.55F * wy);
                float ridge = 1.0F - Math.abs(rn * 2.0F - 1.0F);
                float ridgeShape = ridge * ridge * (0.55F + 0.45F * ridge);

                // Mostly soft cloud, with just enough ridge to keep a little filament structure.
                float mix = n * 0.78F + ridgeShape * 0.22F;
                float alpha = clamp01((mix - threshold) / (1.0F - threshold));
                alpha = (float) Math.pow(alpha, gamma);

                float light = 0.35F + 0.65F * n;
                int r = Math.round(126.0F + 120.0F * light);
                int g = Math.round(204.0F + 48.0F * light);
                int b = 255;
                int a = Math.round(alpha * 255.0F);
                image.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }

        ImageIO.write(image, "png", file);
    }

    private static void writeEdge(File file) throws Exception {
        BufferedImage image = new BufferedImage(EDGE_WIDTH, EDGE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < EDGE_WIDTH; x++) {
            float t = x / (float) (EDGE_WIDTH - 1);
            float alpha = (float) Math.pow(1.0F - t, 2.2F);
            if (x < 2) {
                alpha = 1.0F;
            }
            int a = Math.round(alpha * 255.0F);
            int rgb = (a << 24) | (235 << 16) | (252 << 8) | 255;
            for (int y = 0; y < EDGE_HEIGHT; y++) {
                image.setRGB(x, y, rgb);
            }
        }
        ImageIO.write(image, "png", file);
    }

    private static void writeSpark(File file) throws Exception {
        BufferedImage image = new BufferedImage(SPARK_SIZE, SPARK_SIZE, BufferedImage.TYPE_INT_ARGB);
        float cx = (SPARK_SIZE - 1) * 0.5F;
        float cy = (SPARK_SIZE - 1) * 0.5F;
        float max = (float) Math.sqrt(cx * cx + cy * cy);

        for (int y = 0; y < SPARK_SIZE; y++) {
            for (int x = 0; x < SPARK_SIZE; x++) {
                float dx = x - cx;
                float dy = y - cy;
                float d = (float) Math.sqrt(dx * dx + dy * dy) / max;
                float alpha = Math.max(0.0F, 1.0F - d);
                alpha = alpha * alpha;
                int a = Math.round(alpha * 255.0F);
                image.setRGB(x, y, (a << 24) | (220 << 16) | (250 << 8) | 255);
            }
        }

        ImageIO.write(image, "png", file);
    }

    private static float clamp01(float value) {
        return value < 0.0F ? 0.0F : (value > 1.0F ? 1.0F : value);
    }

    private static final class Grid {
        private final int px;
        private final int py;
        private final float[] values;

        Grid(int px, int py, long seed) {
            this.px = px;
            this.py = py;
            this.values = new float[px * py];
            Random random = new Random(seed);
            for (int i = 0; i < values.length; i++) {
                values[i] = random.nextFloat();
            }
        }

        float sample(float x, float y) {
            int xi = (int) Math.floor(x);
            int yi = (int) Math.floor(y);
            float fx = x - xi;
            float fy = y - yi;
            fx = fx * fx * (3.0F - 2.0F * fx);
            fy = fy * fy * (3.0F - 2.0F * fy);

            int x0 = mod(xi, px);
            int x1 = (x0 + 1) % px;
            int y0 = mod(yi, py);
            int y1 = (y0 + 1) % py;

            float v00 = values[y0 * px + x0];
            float v10 = values[y0 * px + x1];
            float v01 = values[y1 * px + x0];
            float v11 = values[y1 * px + x1];

            float a = v00 + (v10 - v00) * fx;
            float b = v01 + (v11 - v01) * fx;
            return a + (b - a) * fy;
        }

        private static int mod(int value, int modulus) {
            int result = value % modulus;
            return result < 0 ? result + modulus : result;
        }
    }

    private static final class Fbm {
        private final Grid[] grids;
        private final int[] px;
        private final int[] py;
        private final float[] amplitudes;
        private final float normalisation;

        Fbm(long seed, int octaves, float stretch) {
            grids = new Grid[octaves];
            px = new int[octaves];
            py = new int[octaves];
            amplitudes = new float[octaves];

            float total = 0.0F;
            int periodX = 4;
            for (int i = 0; i < octaves; i++) {
                int periodY = Math.max(2, Math.round(periodX * stretch));
                px[i] = periodX;
                py[i] = periodY;
                amplitudes[i] = (float) Math.pow(0.5, i);
                total += amplitudes[i];
                grids[i] = new Grid(periodX, periodY, seed + i * 1013L);
                periodX *= 2;
            }
            normalisation = total;
        }

        float sample(float u, float v) {
            float sum = 0.0F;
            for (int i = 0; i < grids.length; i++) {
                sum += amplitudes[i] * grids[i].sample(u * px[i], v * py[i]);
            }
            return sum / normalisation;
        }
    }

    private static final class FieldVariant {
        private final String fileName;
        private final long seed;
        private final float stretch;
        private final float warp;
        private final float threshold;
        private final float gamma;

        private FieldVariant(String fileName, long seed, float stretch, float warp,
                             float threshold, float gamma) {
            this.fileName = fileName;
            this.seed = seed;
            this.stretch = stretch;
            this.warp = warp;
            this.threshold = threshold;
            this.gamma = gamma;
        }
    }

    private FieldTextureGenerator() {
    }
}
