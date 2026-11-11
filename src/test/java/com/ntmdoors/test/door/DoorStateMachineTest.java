package com.ntmdoors.test.door;

import com.ntmdoors.block.entity.DoorBlockEntity;
import com.ntmdoors.door.DoorDecl;
import com.ntmdoors.door.DoorDecls;
import com.ntmdoors.door.DoorPlacementGuard;
import com.ntmdoors.door.DoorState;
import net.minecraft.Bootstrap;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DoorStateMachineTest {

    @BeforeAll
    public static void setup() {
        try {
            java.lang.reflect.Field field = Bootstrap.class.getDeclaredField("initialized");
            field.setAccessible(true);
            field.setBoolean(null, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        net.minecraft.registry.Registries.REGISTRIES.getIds();
    }

    private DoorBlockEntity createTestDoor(DoorDecl decl) {
        DoorBlockEntity be = new DoorBlockEntity(null, BlockPos.ORIGIN, null);
        be.setDecl(decl);
        return be;
    }

    @Test
    @DisplayName("Verify Initial State is CLOSED with Zero Timer and Open Fraction")
    public void testInitialStateIsClosed() {
        DoorBlockEntity be = createTestDoor(DoorDecls.FIRE_DOOR);
        assertEquals(DoorState.CLOSED, be.getState());
        assertEquals(0, be.getTimer());
        assertFalse(be.isBreached());
        assertFalse(be.isThermiteFused());
        assertEquals(0.0f, be.getOpenFraction(0.0f));
    }

    @Test
    @DisplayName("Verify Full State Cycle: CLOSED -> OPENING -> OPEN -> CLOSING -> CLOSED")
    public void testFullStateCycle() {
        DoorDecl d = DoorDecls.FIRE_DOOR;
        DoorBlockEntity be = createTestDoor(d);

        // 1. Request open
        be.request(true);
        assertEquals(DoorState.OPENING, be.getState(), "Should transition to OPENING on request(true)");
        assertEquals(0, be.getTimer());
        assertTrue(DoorPlacementGuard.isRegistered(be), "Moving door must register with DoorPlacementGuard");

        // 2. Advance ticks through opening duration
        for (int i = 0; i < d.openTicks - 1; i++) {
            be.tickServer();
            assertEquals(DoorState.OPENING, be.getState(), "Should remain OPENING until openTicks reached");
            assertEquals(i + 1, be.getTimer());
        }

        // Final opening tick
        be.tickServer();
        assertEquals(DoorState.OPEN, be.getState(), "Should transition to OPEN exactly at openTicks");
        assertEquals(0, be.getTimer());
        assertEquals(1.0f, be.getOpenFraction(0.0f));

        // 3. Request close
        be.request(false);
        assertEquals(DoorState.CLOSING, be.getState(), "Should transition to CLOSING on request(false)");
        assertEquals(0, be.getTimer());

        // 4. Advance ticks through closing duration
        for (int i = 0; i < d.closeTicks - 1; i++) {
            be.tickServer();
            assertEquals(DoorState.CLOSING, be.getState(), "Should remain CLOSING until closeTicks reached");
            assertEquals(i + 1, be.getTimer());
        }

        // Final closing tick
        be.tickServer();
        assertEquals(DoorState.CLOSED, be.getState(), "Should transition to CLOSED exactly at closeTicks");
        assertEquals(0, be.getTimer());
        assertEquals(0.0f, be.getOpenFraction(0.0f));
        assertFalse(DoorPlacementGuard.isRegistered(be), "Closed door must unregister from DoorPlacementGuard");
    }

    @Test
    @DisplayName("Verify Analytical Interpolation Monotonicity in Range [0.0, 1.0]")
    public void testAnalyticalInterpolationMonotonicity() {
        DoorDecl d = DoorDecls.VAULT_DOOR;
        DoorBlockEntity be = createTestDoor(d);

        be.request(true);
        float prevFraction = -1.0f;
        while (be.getState() == DoorState.OPENING) {
            float frac0 = be.getOpenFraction(0.0f);
            float fracHalf = be.getOpenFraction(0.5f);
            float frac1 = be.getOpenFraction(1.0f);

            assertTrue(frac0 >= 0.0f && frac0 <= 1.0f, "Fraction must be in [0, 1]");
            assertTrue(fracHalf >= frac0 && fracHalf <= frac1, "Sub-tick interpolation must be monotonic");
            assertTrue(frac0 >= prevFraction, "Opening stroke must be non-decreasing over ticks");

            prevFraction = frac0;
            be.tickServer();
        }

        assertEquals(1.0f, be.getOpenFraction(0.0f));

        be.request(false);
        prevFraction = 2.0f;
        while (be.getState() == DoorState.CLOSING) {
            float frac0 = be.getOpenFraction(0.0f);
            float fracHalf = be.getOpenFraction(0.5f);
            float frac1 = be.getOpenFraction(1.0f);

            assertTrue(frac0 >= 0.0f && frac0 <= 1.0f, "Fraction must be in [0, 1]");
            assertTrue(fracHalf <= frac0 && fracHalf >= frac1, "Sub-tick closing interpolation must be monotonic");
            assertTrue(frac0 <= prevFraction, "Closing stroke must be non-increasing over ticks");

            prevFraction = frac0;
            be.tickServer();
        }

        assertEquals(0.0f, be.getOpenFraction(0.0f));
    }

    @Test
    @DisplayName("Verify Mid-Stroke Reversal Queuing and Execution at Stroke Completion")
    public void testMidStrokeReversal() {
        DoorDecl d = DoorDecls.SLIDING_BLAST_DOOR;
        DoorBlockEntity be = createTestDoor(d);

        be.request(true);
        assertEquals(DoorState.OPENING, be.getState());

        // Halfway through opening, request close
        be.tickServer();
        be.request(false);
        assertEquals(DoorState.OPENING, be.getState(), "Should NOT instantly reverse mid-stroke; must finish stroke");
        assertEquals(Boolean.FALSE, be.getQueuedOpen(), "Queued open should be false");

        // Complete the opening stroke
        while (be.getState() == DoorState.OPENING) {
            be.tickServer();
        }

        // Upon completing opening stroke, queued reversal must immediately start CLOSING
        assertEquals(DoorState.CLOSING, be.getState(), "Should automatically reverse to CLOSING once opening completes");
        assertNull(be.getQueuedOpen(), "Queued open should be cleared");
    }

    @Test
    @DisplayName("Verify Emergency Override Aborts Stroke and Reverses Immediately")
    public void testEmergencyOverride() {
        DoorDecl d = DoorDecls.SLIDING_BLAST_DOOR;
        DoorBlockEntity be = createTestDoor(d);

        be.request(true);
        be.tickServer();
        assertEquals(DoorState.OPENING, be.getState());

        // Emergency close
        be.closeNow();
        assertEquals(DoorState.CLOSING, be.getState(), "closeNow must immediately abort opening stroke and start CLOSING");
        assertEquals(0, be.getTimer(), "Timer must reset to 0");
        assertNull(be.getQueuedOpen());

        // Emergency open while closing
        be.tickServer();
        be.openNow();
        assertEquals(DoorState.OPENING, be.getState(), "openNow must immediately abort closing stroke and start OPENING");
        assertEquals(0, be.getTimer(), "Timer must reset to 0");
    }

    @Test
    @DisplayName("Verify Auto-Close Timer Decrement and Automatic Close Trigger")
    public void testAutoCloseTimer() {
        DoorDecl d = DoorDecls.FIRE_DOOR;
        DoorBlockEntity be = createTestDoor(d);

        be.request(true);
        while (be.getState() == DoorState.OPENING) {
            be.tickServer();
        }
        assertEquals(DoorState.OPEN, be.getState());

        be.closeAfter(5);
        assertEquals(5, be.getAutoCloseTicks());

        for (int i = 0; i < 4; i++) {
            be.tickServer();
            assertEquals(DoorState.OPEN, be.getState(), "Door should remain OPEN before auto-close timer expires");
            assertEquals(4 - i, be.getAutoCloseTicks());
        }

        // 5th tick: autoClose reaches 0, triggers request(false)
        be.tickServer();
        assertEquals(DoorState.CLOSING, be.getState(), "Door must start CLOSING when auto-close timer elapses");
        assertEquals(0, be.getAutoCloseTicks());
    }

    @Test
    @DisplayName("Verify Breach State Resets Door, Clears Guard, and Blocks Interactions")
    public void testBreachBehavior() {
        DoorDecl d = DoorDecls.FIRE_DOOR;
        DoorBlockEntity be = createTestDoor(d);

        be.request(true);
        assertTrue(DoorPlacementGuard.isRegistered(be));

        be.breach();
        assertTrue(be.isBreached());
        assertEquals(DoorState.CLOSED, be.getState());
        assertEquals(0, be.getTimer());
        assertEquals(0.0f, be.getOpenFraction(0.0f));
        assertFalse(DoorPlacementGuard.isRegistered(be), "Breached door must unregister from DoorPlacementGuard");

        // Subsequent requests and toggles must be blocked
        be.request(true);
        assertEquals(DoorState.CLOSED, be.getState(), "Breached door must not respond to request(true)");

        be.toggle();
        assertEquals(DoorState.CLOSED, be.getState(), "Breached door must not respond to toggle()");

        assertFalse(be.cycleSkin(), "Breached door must not cycle skin");
    }

    @Test
    @DisplayName("Verify Thermite Charge Fusion Blocks Requests and Unfuses on Removal")
    public void testThermiteFusion() {
        DoorDecl d = DoorDecls.FIRE_DOOR;
        DoorBlockEntity be = createTestDoor(d);
        BlockPos chargePos = new BlockPos(1, 2, 3);

        be.addThermiteCharge(chargePos);
        assertTrue(be.isThermiteFused(), "Door must report thermite fused when charge is attached");

        be.request(true);
        assertEquals(DoorState.CLOSED, be.getState(), "Thermite-fused door must refuse request(true)");

        assertFalse(be.cycleSkin(), "Thermite-fused door must refuse cycleSkin()");

        // Remove charge
        be.removeThermiteCharge(chargePos);
        assertFalse(be.isThermiteFused(), "Door must no longer be thermite fused after charge removal");

        be.request(true);
        assertEquals(DoorState.OPENING, be.getState(), "Door must open normally once thermite charge is removed");
    }

    @Test
    @DisplayName("Verify Skin Variant Cycling and Skin Count Boundaries")
    public void testSkinCycling() {
        DoorDecl withSkins = null;
        DoorDecl noSkins = null;

        for (DoorDecl d : DoorDecls.ALL) {
            if (d.hasSkinVariants() && withSkins == null) withSkins = d;
            if (!d.hasSkinVariants() && noSkins == null) noSkins = d;
        }

        if (withSkins != null) {
            DoorBlockEntity be = createTestDoor(withSkins);
            int count = withSkins.skinCount();
            assertTrue(count > 1);
            assertEquals(0, be.getSkinIndex());

            for (int i = 1; i < count; i++) {
                assertTrue(be.cycleSkin());
                assertEquals(i, be.getSkinIndex());
            }

            // Wrap around
            assertTrue(be.cycleSkin());
            assertEquals(0, be.getSkinIndex());
        }

        if (noSkins != null) {
            DoorBlockEntity be = createTestDoor(noSkins);
            assertFalse(be.cycleSkin());
            assertEquals(0, be.getSkinIndex());
        }
    }
}
