package com.ntmdoors.test.security;

import com.ntmdoors.block.BlockThermiteCharge;
import com.ntmdoors.block.ModBlocks;
import com.ntmdoors.block.door.BlockDoorGeneric;
import com.ntmdoors.block.door.ModDoors;
import com.ntmdoors.block.entity.DoorBlockEntity;
import com.ntmdoors.block.entity.ModBlockEntities;
import com.ntmdoors.block.entity.ThermiteChargeBlockEntity;
import com.ntmdoors.door.DoorDecl;
import com.ntmdoors.door.DoorDecls;
import com.ntmdoors.door.DoorState;
import com.ntmdoors.item.ItemThermiteCharge;
import com.ntmdoors.item.ModItems;
import com.ntmdoors.sound.ModSounds;
import net.minecraft.Bootstrap;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ThermiteChargeTest {

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
        ModSounds.registerModSounds();
        ModBlocks.registerModBlocks();
        ModBlockEntities.registerModBlockEntities();
        ModItems.registerModItems();
        ModDoors.registerModDoors();
    }

    @Test
    @DisplayName("Verify BlockThermiteCharge properties, default state, and 96 precomputed VoxelShapes")
    public void testBlockPropertiesAndShapes() {
        BlockThermiteCharge block = ModBlocks.THERMITE_CHARGE;
        BlockState defaultState = block.getDefaultState();

        assertEquals(Direction.NORTH, defaultState.get(BlockThermiteCharge.FACING));
        assertEquals(0, defaultState.get(BlockThermiteCharge.INSET));

        // Verify shape for every direction and inset 0..15 is valid and non-empty
        for (Direction dir : Direction.values()) {
            for (int inset = 0; inset <= 15; inset++) {
                BlockState state = defaultState
                        .with(BlockThermiteCharge.FACING, dir)
                        .with(BlockThermiteCharge.INSET, inset);
                VoxelShape shape = block.getOutlineShape(state, null, BlockPos.ORIGIN, ShapeContext.absent());
                assertNotNull(shape, "Shape for dir " + dir + " inset " + inset + " must not be null");
                assertFalse(shape.isEmpty(), "Shape for dir " + dir + " inset " + inset + " must not be empty");
            }
        }
    }

    @Test
    @DisplayName("Verify insetFromHit calculations across all 6 hit normal directions")
    public void testInsetFromHitCalculations() {
        // SOUTH: 1.0 - hitZ
        assertEquals(0, ItemThermiteCharge.insetFromHit(Direction.SOUTH, 0.5, 0.5, 1.0));
        assertEquals(8, ItemThermiteCharge.insetFromHit(Direction.SOUTH, 0.5, 0.5, 0.5));
        assertEquals(15, ItemThermiteCharge.insetFromHit(Direction.SOUTH, 0.5, 0.5, 0.0));

        // NORTH: hitZ
        assertEquals(0, ItemThermiteCharge.insetFromHit(Direction.NORTH, 0.5, 0.5, 0.0));
        assertEquals(4, ItemThermiteCharge.insetFromHit(Direction.NORTH, 0.5, 0.5, 0.25));
        assertEquals(15, ItemThermiteCharge.insetFromHit(Direction.NORTH, 0.5, 0.5, 1.0));

        // EAST: 1.0 - hitX
        assertEquals(0, ItemThermiteCharge.insetFromHit(Direction.EAST, 1.0, 0.5, 0.5));
        assertEquals(12, ItemThermiteCharge.insetFromHit(Direction.EAST, 0.25, 0.5, 0.5));

        // WEST: hitX
        assertEquals(0, ItemThermiteCharge.insetFromHit(Direction.WEST, 0.0, 0.5, 0.5));
        assertEquals(2, ItemThermiteCharge.insetFromHit(Direction.WEST, 0.125, 0.5, 0.5));

        // UP: 1.0 - hitY
        assertEquals(0, ItemThermiteCharge.insetFromHit(Direction.UP, 0.5, 1.0, 0.5));
        assertEquals(8, ItemThermiteCharge.insetFromHit(Direction.UP, 0.5, 0.5, 0.5));

        // DOWN: hitY
        assertEquals(0, ItemThermiteCharge.insetFromHit(Direction.DOWN, 0.5, 0.0, 0.5));
        assertEquals(15, ItemThermiteCharge.insetFromHit(Direction.DOWN, 0.5, 1.0, 0.5));
    }

    @Test
    @DisplayName("Verify excluded doors check: silo hatches, transition seal, blast door, vault door")
    public void testExcludedDoorsCheck() {
        assertTrue(ItemThermiteCharge.isExcludedDoor(ModDoors.SILO_HATCH));
        assertTrue(ItemThermiteCharge.isExcludedDoor(ModDoors.SILO_HATCH_LARGE));
        assertTrue(ItemThermiteCharge.isExcludedDoor(ModDoors.TRANSITION_SEAL));
        assertTrue(ItemThermiteCharge.isExcludedDoor(ModDoors.BLAST_DOOR));
        assertTrue(ItemThermiteCharge.isExcludedDoor(ModDoors.VAULT_DOOR));

        assertFalse(ItemThermiteCharge.isExcludedDoor(ModDoors.FIRE_DOOR));
        assertFalse(ItemThermiteCharge.isExcludedDoor(ModDoors.QE_CONTAINMENT));
        assertFalse(ItemThermiteCharge.isExcludedDoor(ModDoors.ROUND_AIRLOCK_DOOR));
        assertFalse(ItemThermiteCharge.isExcludedDoor(ModDoors.CARGO_DOOR));
        assertFalse(ItemThermiteCharge.isExcludedDoor(null));
    }

    @Test
    @DisplayName("Verify fuse countdown constants: 100 ticks total, 70 ticks warning beep")
    public void testFuseConstants() {
        assertEquals(100, ThermiteChargeBlockEntity.FUSE_TICKS);
        assertEquals(70, ThermiteChargeBlockEntity.BEEP_START_TICKS);
    }

    @Test
    @DisplayName("Verify ThermiteChargeBlockEntity mount state and NBT serialization roundtrip")
    public void testBlockEntityNbtRoundtrip() {
        BlockPos pos = new BlockPos(50, 64, -100);
        BlockState state = ModBlocks.THERMITE_CHARGE.getDefaultState();
        ThermiteChargeBlockEntity be = new ThermiteChargeBlockEntity(pos, state);

        assertEquals(0, be.getInset());
        assertFalse(be.isDoorTracked());
        assertFalse(be.isLit());

        BlockPos doorPos = new BlockPos(50, 64, -101);
        be.setMount(9, doorPos, 0.42f);

        assertEquals(9, be.getInset());
        assertTrue(be.isDoorTracked());

        NbtCompound tag = new NbtCompound();
        be.writeNbt(tag);

        ThermiteChargeBlockEntity readBe = new ThermiteChargeBlockEntity(pos, state);
        readBe.readNbt(tag);

        assertEquals(9, readBe.getInset());
        assertTrue(readBe.isDoorTracked());
        assertFalse(readBe.isLit());
    }

    @Test
    @DisplayName("Verify DoorBlockEntity breach and thermite fuse integration")
    public void testDoorBreachAndFuseIntegration() {
        DoorBlockEntity door = new DoorBlockEntity(null, BlockPos.ORIGIN, null);
        door.setDecl(DoorDecls.FIRE_DOOR);

        assertFalse(door.isBreached());
        assertFalse(door.isThermiteFused());

        BlockPos charge1 = new BlockPos(0, 1, 0);
        BlockPos charge2 = new BlockPos(0, 2, 0);

        door.addThermiteCharge(charge1);
        assertTrue(door.isThermiteFused());

        door.addThermiteCharge(charge2);
        assertTrue(door.isThermiteFused());

        // Skin change is prohibited while thermite fused
        assertFalse(door.cycleSkin());

        door.removeThermiteCharge(charge1);
        assertTrue(door.isThermiteFused());

        door.removeThermiteCharge(charge2);
        assertFalse(door.isThermiteFused());

        // Breach door
        door.addThermiteCharge(charge1);
        door.breach();

        assertTrue(door.isBreached());
        assertFalse(door.isThermiteFused(), "Breaching must clear thermite charges");
        assertEquals(DoorState.CLOSED, door.getState());

        // Breached door permanently rejects further thermite charges and skin cycles
        door.addThermiteCharge(charge1);
        assertFalse(door.isThermiteFused(), "Breached door must reject further charges");
        assertFalse(door.cycleSkin(), "Breached door cannot cycle skins");
    }
}
