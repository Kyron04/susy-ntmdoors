package com.ntmdoors.test.security;

import com.ntmdoors.block.KeypadBlock;
import com.ntmdoors.block.entity.KeypadBlockEntity;
import com.ntmdoors.util.Facings;
import net.minecraft.Bootstrap;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class KeypadLogicTest {

    @BeforeAll
    public static void setup() {
        net.minecraft.SharedConstants.createGameVersion();
        try {
            java.lang.reflect.Field field = Bootstrap.class.getDeclaredField("initialized");
            field.setAccessible(true);
            field.setBoolean(null, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        net.minecraft.registry.Registries.REGISTRIES.getIds();
    }

    private KeypadBlockEntity createKeypad(Direction facing) {
        KeypadBlock block = new KeypadBlock();
        BlockState state = block.getDefaultState().with(HorizontalFacingBlock.FACING, facing);
        return new KeypadBlockEntity(null, BlockPos.ORIGIN, state);
    }

    @Test
    @DisplayName("Verify 3D button raycast hit math across horizontal facings")
    public void testButtonHitMath() {
        for (Direction facing : Direction.Type.HORIZONTAL) {
            KeypadBlockEntity keypad = createKeypad(facing);

            // Test each button in BUTTON_LAYOUT
            for (int id = 0; id < KeypadBlockEntity.BUTTON_LAYOUT.length; id++) {
                KeypadBlockEntity.Button btn = KeypadBlockEntity.BUTTON_LAYOUT[id];
                double hitY = btn.y * KeypadBlock.MODEL_SCALE_Y + KeypadBlock.MODEL_OFFSET_Y;

                // Reconstruct world cx and cz based on facing so that localX matches btn.x * MODEL_SCALE_X
                double localX = btn.x * KeypadBlock.MODEL_SCALE_X;
                double cx = 0;
                double cz = 0;
                switch (facing) {
                    case NORTH -> { cx = localX; cz = 0; }
                    case SOUTH -> { cx = -localX; cz = 0; }
                    case WEST -> { cx = 0; cz = -localX; }
                    case EAST -> { cx = 0; cz = localX; }
                }

                double hitX = cx + 0.5;
                double hitZ = cz + 0.5;

                int detected = keypad.buttonAt(hitX, hitY, hitZ);
                assertEquals(id, detected, "Button ID mismatch for button " + id + " on facing " + facing);
            }

            // Test out-of-bounds hits
            assertEquals(-1, keypad.buttonAt(0.0, 0.0, 0.0));
            assertEquals(-1, keypad.buttonAt(1.0, 1.0, 1.0));
            assertEquals(-1, keypad.buttonAt(0.5, 0.95, 0.5));
        }
    }

    @Test
    @DisplayName("Verify Keypad digit entry and 6-digit maximum length constraint")
    public void testDigitEntryAndLimit() {
        KeypadBlockEntity keypad = createKeypad(Direction.NORTH);
        assertEquals("", keypad.getEntry());

        // Press digits 1, 2, 3, 4, 5, 6
        // Button 8 is digit 1, 7 is 2, 6 is 3, 5 is 4, 4 is 5, 3 is 6
        keypad.pressButton(8, null); // 1
        keypad.pressButton(7, null); // 2
        keypad.pressButton(6, null); // 3
        assertEquals("123", keypad.getEntry());

        keypad.pressButton(5, null); // 4
        keypad.pressButton(4, null); // 5
        keypad.pressButton(3, null); // 6
        assertEquals("123456", keypad.getEntry());

        // 7th digit attempt must be rejected (max length 6)
        keypad.pressButton(10, null); // 0
        assertEquals("123456", keypad.getEntry(), "Maximum 6 digits must be enforced");
    }

    @Test
    @DisplayName("Verify Keypad state machine: SET_CODE mode transitions to ENTER_CODE")
    public void testSetCodeFlow() {
        KeypadBlockEntity keypad = createKeypad(Direction.NORTH);
        assertEquals(KeypadBlockEntity.Mode.SET_CODE, keypad.getMode());

        // Enter code "789"
        // Button 2 is 7, 1 is 8, 0 is 9
        keypad.pressButton(2, null);
        keypad.pressButton(1, null);
        keypad.pressButton(0, null);
        assertEquals("789", keypad.getEntry());

        // Press Enter (Button 11)
        keypad.pressButton(KeypadBlockEntity.BUTTON_ENTER, null);

        // Should transition to ENTER_CODE and show CODE_SET feedback
        assertEquals(KeypadBlockEntity.Mode.ENTER_CODE, keypad.getMode());
        assertEquals("", keypad.getEntry(), "Entry must be cleared after setting code");
        assertEquals(KeypadBlockEntity.Feedback.CODE_SET, keypad.getFeedback());
    }

    @Test
    @DisplayName("Verify Keypad code verification: SUCCESS on correct code, DENIED on wrong code")
    public void testCodeVerification() {
        KeypadBlockEntity keypad = createKeypad(Direction.NORTH);

        // 1. Set code to "456"
        keypad.pressButton(5, null); // 4
        keypad.pressButton(4, null); // 5
        keypad.pressButton(3, null); // 6
        keypad.pressButton(KeypadBlockEntity.BUTTON_ENTER, null);
        assertEquals(KeypadBlockEntity.Mode.ENTER_CODE, keypad.getMode());

        // 2. Enter wrong code "111"
        keypad.pressButton(8, null); // 1
        keypad.pressButton(8, null); // 1
        keypad.pressButton(8, null); // 1
        keypad.pressButton(KeypadBlockEntity.BUTTON_ENTER, null);

        assertEquals(KeypadBlockEntity.Feedback.DENIED, keypad.getFeedback(), "Wrong code should trigger DENIED");
        assertEquals("", keypad.getEntry(), "Entry should be cleared on wrong code");

        // 3. Enter correct code "456"
        keypad.pressButton(5, null); // 4
        keypad.pressButton(4, null); // 5
        keypad.pressButton(3, null); // 6
        keypad.pressButton(KeypadBlockEntity.BUTTON_ENTER, null);

        assertEquals(KeypadBlockEntity.Feedback.SUCCESS, keypad.getFeedback(), "Correct code should trigger SUCCESS");
        assertEquals("", keypad.getEntry());
    }

    @Test
    @DisplayName("Verify Reset key clears currently typed digits")
    public void testResetKeyClearsEntry() {
        KeypadBlockEntity keypad = createKeypad(Direction.NORTH);
        keypad.pressButton(8, null); // 1
        keypad.pressButton(7, null); // 2
        assertEquals("12", keypad.getEntry());

        keypad.pressButton(KeypadBlockEntity.BUTTON_RESET, null);
        assertEquals("", keypad.getEntry(), "Reset key must clear current entry");
    }

    @Test
    @DisplayName("Verify strict security isolation: storedCode and securityCode never synced to client")
    public void testSecurityIsolationInNbt() {
        KeypadBlockEntity keypad = createKeypad(Direction.NORTH);

        // Set code
        keypad.pressButton(8, null);
        keypad.pressButton(7, null);
        keypad.pressButton(KeypadBlockEntity.BUTTON_ENTER, null);
        keypad.setSecurityCode("999888");

        // Check full disk writeNbt
        NbtCompound diskTag = new NbtCompound();
        keypad.writeNbt(diskTag);
        assertTrue(diskTag.contains("storedCode"), "Disk NBT must contain storedCode");
        assertTrue(diskTag.contains("securityCode"), "Disk NBT must contain securityCode");
        assertEquals("12", diskTag.getString("storedCode"));
        assertEquals("999888", diskTag.getString("securityCode"));

        // Check client sync NBT
        NbtCompound clientTag = keypad.toInitialChunkDataNbt();
        assertFalse(clientTag.contains("storedCode"), "Client sync NBT must NEVER contain storedCode");
        assertFalse(clientTag.contains("securityCode"), "Client sync NBT must NEVER contain securityCode");
    }

    @Test
    @DisplayName("Verify BindMode cycling: NONE -> DISTANCE -> MULTI -> NONE")
    public void testBindModeCycling() {
        KeypadBlockEntity keypad = createKeypad(Direction.NORTH);
        assertEquals(KeypadBlockEntity.BindMode.NONE, keypad.getBindMode());

        keypad.cycleBindMode(null);
        assertEquals(KeypadBlockEntity.BindMode.DISTANCE, keypad.getBindMode());

        keypad.cycleBindMode(null);
        assertEquals(KeypadBlockEntity.BindMode.MULTI, keypad.getBindMode());

        keypad.cycleBindMode(null);
        assertEquals(KeypadBlockEntity.BindMode.NONE, keypad.getBindMode());
    }
}
