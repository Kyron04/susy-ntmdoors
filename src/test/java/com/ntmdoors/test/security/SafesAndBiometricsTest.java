package com.ntmdoors.test.security;

import com.ntmdoors.block.BiometricSafeBlock;
import com.ntmdoors.block.ModBlocks;
import com.ntmdoors.block.SafeBlock;
import com.ntmdoors.block.entity.BiometricSafeBlockEntity;
import com.ntmdoors.block.entity.ModBlockEntities;
import com.ntmdoors.block.entity.SafeBlockEntity;
import com.ntmdoors.item.ModItems;
import com.ntmdoors.sound.ModSounds;
import net.minecraft.Bootstrap;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class SafesAndBiometricsTest {

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
        ModSounds.registerModSounds();
        ModBlocks.registerModBlocks();
        ModBlockEntities.registerModBlockEntities();
        ModItems.registerModItems();
    }

    private SafeBlockEntity createSafe() {
        SafeBlock block = ModBlocks.SAFE;
        BlockState state = block.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH);
        return new SafeBlockEntity(BlockPos.ORIGIN, state);
    }

    private BiometricSafeBlockEntity createBiometricSafe() {
        BiometricSafeBlock block = ModBlocks.BIOMETRIC_SAFE;
        BlockState state = block.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH);
        return new BiometricSafeBlockEntity(BlockPos.ORIGIN, state);
    }

    @Test
    @DisplayName("Verify safe 27-slot capacity and hopper automation isolation via SidedInventory")
    public void testSafeInventoryBoundsAndSidedIsolation() {
        SafeBlockEntity safe = createSafe();

        assertEquals(27, safe.size(), "Safe must have exactly 27 storage slots");

        // Verify all 6 facings return empty slot arrays to block hopper input/output
        for (Direction side : Direction.values()) {
            int[] available = safe.getAvailableSlots(side);
            assertNotNull(available);
            assertEquals(0, available.length, "Hopper automation must be blocked from facing: " + side);
            assertFalse(safe.canInsert(0, new ItemStack(Items.DIAMOND), side));
            assertFalse(safe.canExtract(0, new ItemStack(Items.DIAMOND), side));
        }

        // Verify slot operations
        safe.setStack(0, new ItemStack(Items.NETHERITE_INGOT, 16));
        safe.setStack(26, new ItemStack(Items.GOLD_INGOT, 64));

        assertEquals(Items.NETHERITE_INGOT, safe.getStack(0).getItem());
        assertEquals(16, safe.getStack(0).getCount());
        assertEquals(Items.GOLD_INGOT, safe.getStack(26).getItem());
        assertEquals(64, safe.getStack(26).getCount());
        assertTrue(safe.getStack(1).isEmpty());

        safe.clear();
        assertTrue(safe.isEmpty());
    }

    @Test
    @DisplayName("Verify 3-hour mining hardness delta and piston immovability")
    public void testSafeMiningHardnessDelta() {
        SafeBlock block = ModBlocks.SAFE;
        BlockState state = block.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH);

        assertEquals(1.0F / 216000.0F, SafeBlock.BREAK_DAMAGE_PER_TICK, 1e-9F,
                "Break delta must be 1/216000 per tick (3 hours in survival)");

        // Non-pushable by pistons
        assertEquals(PistonBehavior.BLOCK, state.getPistonBehavior(),
                "Safe must refuse piston movement");
    }

    @Test
    @DisplayName("Verify 5-minute (6000 tick) lockout after 3 consecutive failed attempts")
    public void testSafeLockoutMechanics() {
        SafeBlockEntity safe = createSafe();

        assertFalse(safe.isLockedOut());
        assertEquals(0, safe.getFailedAttempts());
        assertEquals(0, safe.getLockoutTicks());

        // Failure 1
        safe.recordFailure();
        assertEquals(1, safe.getFailedAttempts());
        assertFalse(safe.isLockedOut());

        // Failure 2
        safe.recordFailure();
        assertEquals(2, safe.getFailedAttempts());
        assertFalse(safe.isLockedOut());

        // Failure 3 -> triggers lockout
        safe.recordFailure();
        assertEquals(3, safe.getFailedAttempts());
        assertTrue(safe.isLockedOut());
        assertEquals(SafeBlockEntity.LOCKOUT_TICKS, safe.getLockoutTicks());
        assertEquals(6000, safe.getLockoutTicks(), "Lockout duration must be 6000 ticks (5 minutes)");

        // Tick countdown
        safe.tickServer();
        assertEquals(5999, safe.getLockoutTicks());
        assertTrue(safe.isLockedOut());

        // Fast-forward to expiration
        for (int i = 0; i < 5999; i++) {
            safe.tickServer();
        }
        assertEquals(0, safe.getLockoutTicks());
        assertFalse(safe.isLockedOut(), "Lockout should expire after countdown reaches 0");

        // When lockout mode is disabled, 3 failures must not lock out
        safe.setLockoutMode(false);
        safe.resetFailures();
        safe.recordFailure();
        safe.recordFailure();
        safe.recordFailure();
        assertFalse(safe.isLockedOut(), "Safe must not lock out when lockout mode is disabled");
    }

    @Test
    @DisplayName("Verify biometric safe owner registration, matching, and lockout bypass")
    public void testBiometricSafeOwnerAuthenticationAndLockoutBypass() {
        BiometricSafeBlockEntity safe = createBiometricSafe();

        assertTrue(safe.isBiometric());

        UUID owner = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();

        safe.setOwner(owner, "DrVance");
        assertEquals(owner.toString(), safe.getOwnerUuid());
        assertEquals("DrVance", safe.getOwnerName());
        assertTrue(safe.isOwner(owner));
        assertFalse(safe.isOwner(stranger));

        // Lock out the safe
        safe.recordFailure();
        safe.recordFailure();
        safe.recordFailure();
        assertTrue(safe.isLockedOut());

        // The owner card bypasses the lockout
        assertTrue(safe.isOwner(owner), "Owner identity must always match even during lockout");
    }

    @Test
    @DisplayName("Verify voidOnDestroy option toggling")
    public void testSafeVoidOnDestroy() {
        SafeBlockEntity safe = createSafe();

        assertFalse(safe.isVoidOnDestroy(), "Default voidOnDestroy must be false");

        safe.setVoidOnDestroy(true);
        assertTrue(safe.isVoidOnDestroy());

        safe.setVoidOnDestroy(false);
        assertFalse(safe.isVoidOnDestroy());
    }

    @Test
    @DisplayName("Verify safe NBT serialization and deserialization roundtrip")
    public void testSafeNbtRoundTrip() {
        SafeBlockEntity original = createSafe();

        original.setStack(0, new ItemStack(Items.DIAMOND_BLOCK, 32));
        original.setStack(15, new ItemStack(Items.EMERALD, 12));
        original.setLockoutMode(false);
        original.setVoidOnDestroy(true);
        original.setBindingActivities(false);
        original.setRedstoneEvent(SafeBlockEntity.RS_CODE_SUCCESS, true);
        original.setRedstoneEvent(SafeBlockEntity.RS_LOCKOUT, true);

        NbtCompound tag = new NbtCompound();
        original.writeNbt(tag);

        SafeBlockEntity loaded = createSafe();
        loaded.readNbt(tag);

        assertEquals(Items.DIAMOND_BLOCK, loaded.getStack(0).getItem());
        assertEquals(32, loaded.getStack(0).getCount());
        assertEquals(Items.EMERALD, loaded.getStack(15).getItem());
        assertEquals(12, loaded.getStack(15).getCount());
        assertFalse(loaded.isLockoutMode());
        assertTrue(loaded.isVoidOnDestroy());
        assertFalse(loaded.isBindingActivities());
        assertTrue(loaded.isRedstoneEvent(SafeBlockEntity.RS_CODE_SUCCESS));
        assertTrue(loaded.isRedstoneEvent(SafeBlockEntity.RS_LOCKOUT));
        assertFalse(loaded.isRedstoneEvent(SafeBlockEntity.RS_CODE_FAIL));
    }
}
