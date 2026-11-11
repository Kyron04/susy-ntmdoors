package com.ntmdoors.test;

import com.ntmdoors.client.render.KeyframeAnimation;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class KeyframeAnimationTest {

    private static final Path ANIM_FILE = Path.of("src", "main", "resources", "assets", "ntmdoors", "models", "doors", "transition_seal.anim");

    private static final String[] EXPECTED_20_GROUPS = {
        "Cube_006", "Cylinder_001", "Cylinder_003", "Cylinder_005", "Cylinder_007",
        "Cylinder_008", "Cylinder_009", "Cylinder_010", "Cylinder_011", "door",
        "door_001", "door_002", "door_003", "door_004", "door_005", "door_006",
        "door_008", "ring", "ring_001", "ring_002"
    };

    @Test
    @DisplayName("Parse transition_seal.anim: Parses all 20 animated groups with 577 samples each")
    public void testParseTransitionSealAnim() throws Exception {
        assertTrue(Files.exists(ANIM_FILE), "transition_seal.anim must exist at " + ANIM_FILE);

        try (InputStream in = new FileInputStream(ANIM_FILE.toFile())) {
            Map<String, KeyframeAnimation> animations = KeyframeAnimation.parse(in);
            assertNotNull(animations, "Parsed animations should not be null");
            assertEquals(20, animations.size(), "Must parse exactly 20 distinct animated groups");

            for (String expectedGroup : EXPECTED_20_GROUPS) {
                KeyframeAnimation anim = animations.get(expectedGroup);
                assertNotNull(anim, "Animation for group " + expectedGroup + " must be present");
                assertEquals(577, anim.sampleCount(), "Group " + expectedGroup + " must have exactly 577 samples");
            }
        }
    }

    @Test
    @DisplayName("Affine Matrix to Quaternion Extraction: Rotation quaternions must be unit length")
    public void testQuaternionNormalization() {
        // Test Shepperd's algorithm with identity matrix
        float[] identity = new float[]{
            1, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0
        };
        KeyframeAnimation.Sample s1 = new KeyframeAnimation.Sample(0.0f, identity);
        float lenSq1 = s1.rotation.x * s1.rotation.x + s1.rotation.y * s1.rotation.y +
                       s1.rotation.z * s1.rotation.z + s1.rotation.w * s1.rotation.w;
        assertEquals(1.0f, lenSq1, 1e-4f, "Identity matrix must produce unit quaternion");

        // 90 degree rotation around Z
        float[] rotZ90 = new float[]{
            0, -1, 0, 2.5f,
            1,  0, 0, 3.5f,
            0,  0, 1, 4.5f
        };
        KeyframeAnimation.Sample s2 = new KeyframeAnimation.Sample(0.5f, rotZ90);
        float lenSq2 = s2.rotation.x * s2.rotation.x + s2.rotation.y * s2.rotation.y +
                       s2.rotation.z * s2.rotation.z + s2.rotation.w * s2.rotation.w;
        assertEquals(1.0f, lenSq2, 1e-4f, "Rotated matrix must produce unit quaternion");
        assertEquals(2.5f, s2.tx, 1e-5f);
        assertEquals(3.5f, s2.ty, 1e-5f);
        assertEquals(4.5f, s2.tz, 1e-5f);
    }

    @Test
    @DisplayName("Interpolation: Smoothly interpolates translation and rotation between keyframes")
    public void testInterpolation() {
        float[] m0 = new float[]{
            1, 0, 0, 0.0f,
            0, 1, 0, 0.0f,
            0, 0, 1, 0.0f
        };
        float[] m1 = new float[]{
            1, 0, 0, 10.0f,
            0, 1, 0, 20.0f,
            0, 0, 1, 30.0f
        };

        KeyframeAnimation.Sample[] samples = new KeyframeAnimation.Sample[]{
            new KeyframeAnimation.Sample(0.0f, m0),
            new KeyframeAnimation.Sample(1.0f, m1)
        };

        KeyframeAnimation anim = new KeyframeAnimation(samples);

        // At openFraction = 0.0
        Matrix4f mat0 = anim.getInterpolatedMatrix(0.0f, new Matrix4f());
        Vector3f trans0 = mat0.getTranslation(new Vector3f());
        assertEquals(0.0f, trans0.x, 1e-4f);
        assertEquals(0.0f, trans0.y, 1e-4f);
        assertEquals(0.0f, trans0.z, 1e-4f);

        // At openFraction = 0.5
        Matrix4f matMid = anim.getInterpolatedMatrix(0.5f, new Matrix4f());
        Vector3f transMid = matMid.getTranslation(new Vector3f());
        assertEquals(5.0f, transMid.x, 1e-4f);
        assertEquals(10.0f, transMid.y, 1e-4f);
        assertEquals(15.0f, transMid.z, 1e-4f);

        // At openFraction = 1.0
        Matrix4f mat1 = anim.getInterpolatedMatrix(1.0f, new Matrix4f());
        Vector3f trans1 = mat1.getTranslation(new Vector3f());
        assertEquals(10.0f, trans1.x, 1e-4f);
        assertEquals(20.0f, trans1.y, 1e-4f);
        assertEquals(30.0f, trans1.z, 1e-4f);
    }

    @Test
    @DisplayName("MatrixStack Multiplication: KeyframeAnimation correctly multiplies into MatrixStack")
    public void testMatrixStackMultiplication() {
        float[] m = new float[]{
            1, 0, 0, 1.5f,
            0, 1, 0, 2.5f,
            0, 0, 1, 3.5f
        };
        KeyframeAnimation.Sample[] samples = new KeyframeAnimation.Sample[]{
            new KeyframeAnimation.Sample(0.0f, m),
            new KeyframeAnimation.Sample(1.0f, m)
        };
        KeyframeAnimation anim = new KeyframeAnimation(samples);

        MatrixStack stack = new MatrixStack();
        stack.push();
        anim.apply(stack, 0.5f);

        Matrix4f posMat = stack.peek().getPositionMatrix();
        Vector3f trans = posMat.getTranslation(new Vector3f());
        assertEquals(1.5f, trans.x, 1e-4f);
        assertEquals(2.5f, trans.y, 1e-4f);
        assertEquals(3.5f, trans.z, 1e-4f);
        stack.pop();
    }
}
