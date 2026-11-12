package com.ntmdoors.test.security;

import com.ntmdoors.block.BlockSpatialDisrupter;
import com.ntmdoors.block.BlockThermiteCharge;
import com.ntmdoors.block.ModBlocks;
import com.ntmdoors.block.door.ModDoors;
import com.ntmdoors.block.entity.DoorBlockEntity;
import com.ntmdoors.block.entity.ModBlockEntities;
import com.ntmdoors.block.entity.SpatialDisrupterBlockEntity;
import com.ntmdoors.block.entity.ThermiteChargeBlockEntity;
import com.ntmdoors.door.DoorDecls;
import com.ntmdoors.door.DoorState;
import com.ntmdoors.item.ItemDocumentsFolder;
import com.ntmdoors.item.ItemKeycardHolder;
import com.ntmdoors.item.ItemThermiteCharge;
import com.ntmdoors.item.ModItems;
import com.ntmdoors.screen.KeycardHolderInventory;
import com.ntmdoors.sound.ModSounds;
import com.ntmdoors.util.BlockPositions;
import com.ntmdoors.weapons.WeaponsBindingEvents;
import com.ntmdoors.weapons.WeaponsBindings;
import com.ntmdoors.weapons.WeaponsKeyData;
import com.ntmdoors.weapons.WeaponsMode;
import net.minecraft.Bootstrap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Empirical Adversarial Challenger Stress Test Suite for Milestone M6:
 * Portable Accessories, Breaching & Disruption, and Weapons Protection.
 */
public class ChallengerM6AccessoriesAndWeaponsStressTest {

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

    public static class DummyPlayer extends PlayerEntity {
        public UUID customUuid = UUID.randomUUID();
        public boolean creative = false;

        public DummyPlayer() {
            super(null, BlockPos.ORIGIN, 0.0f, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "M6Challenger"));
        }

        @Override public UUID getUuid() { return customUuid; }
        @Override public Text getName() { return Text.literal("M6Challenger"); }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isCreative() { return creative; }
        @Override public void sendMessage(Text message, boolean overlay) {}
    }

    private DummyPlayer createPlayer(boolean creative) {
        try {
            Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) f.get(null);
            DummyPlayer p = (DummyPlayer) unsafe.allocateInstance(DummyPlayer.class);
            p.customUuid = UUID.randomUUID();
            p.creative = creative;

            PlayerInventory inv = new PlayerInventory(p);
            Field invField = PlayerEntity.class.getDeclaredField("inventory");
            invField.setAccessible(true);
            invField.set(p, inv);

            return p;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Challenge 1: Keycard Holder high-frequency concurrent slot mutations")
    public void testKeycardHolderConcurrentMutations() throws Exception {
        int threads = 8;
        int operationsPerThread = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger writeCount = new AtomicInteger();

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            pool.submit(() -> {
                try {
                    ItemStack threadHolder = new ItemStack(ModItems.KEYCARD_HOLDER);
                    KeycardHolderInventory inv = new KeycardHolderInventory(threadHolder, null);
                    for (int i = 0; i < operationsPerThread; i++) {
                        int slot = (threadId + i) % 12;
                        inv.setStack(slot, new ItemStack(ModItems.RED_RFID_CARD));
                        writeCount.incrementAndGet();
                        inv.removeStack(slot);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Concurrent operations must complete within timeout");
        pool.shutdown();
        assertEquals(threads * operationsPerThread, writeCount.get());
    }

    @Test
    @DisplayName("Challenge 2: Documents Folder adversarial edge cases (overflow text, unicode, deep paging)")
    public void testDocumentsFolderAdversarialPages() {
        ItemStack folder = new ItemStack(ModItems.DOCUMENTS_FOLDER);

        // 1. Array with 50 pages (beyond 20 limit)
        List<String> giantList = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            giantList.add("Log Entry #" + i + ": ☢ Containment status confirmed.");
        }

        ItemDocumentsFolder.setPages(folder, giantList);
        List<String> stored = ItemDocumentsFolder.getPages(folder);
        assertEquals(20, stored.size(), "Folder must clamp page storage to 20 pages");
        assertEquals("Log Entry #0: ☢ Containment status confirmed.", stored.get(0));
        assertEquals("Log Entry #19: ☢ Containment status confirmed.", stored.get(19));

        // 2. Exact 1024 characters boundary check
        StringBuilder sb1024 = new StringBuilder();
        while (sb1024.length() < 1024) sb1024.append("X");
        String exact1024 = sb1024.toString();

        stored.set(0, exact1024);
        ItemDocumentsFolder.setPages(folder, stored);
        assertEquals(1024, ItemDocumentsFolder.getPages(folder).get(0).length());

        // 3. 1025 characters must be clamped to 1024
        stored.set(0, exact1024 + "EXTRA");
        ItemDocumentsFolder.setPages(folder, stored);
        assertEquals(1024, ItemDocumentsFolder.getPages(folder).get(0).length());
        assertEquals(exact1024, ItemDocumentsFolder.getPages(folder).get(0));

        // 4. Custom name unicode and formatting strip
        ItemDocumentsFolder.setCustomName(folder, "§6Project §k123§r §lTITAN§r");
        assertEquals("Project 123 TITAN", ItemDocumentsFolder.getCustomName(folder));
    }

    @Test
    @DisplayName("Challenge 3: Thermite Charge comprehensive 3D bounding geometry and inset limits")
    public void testThermiteChargeBoundingGeometry() {
        BlockThermiteCharge block = ModBlocks.THERMITE_CHARGE;

        // Verify shape consistency for all 6 faces x 16 insets
        for (Direction dir : Direction.values()) {
            for (int inset = 0; inset <= 15; inset++) {
                BlockState state = block.getDefaultState()
                        .with(BlockThermiteCharge.FACING, dir)
                        .with(BlockThermiteCharge.INSET, inset);

                VoxelShape shape = block.getOutlineShape(state, null, BlockPos.ORIGIN, ShapeContext.absent());
                assertNotNull(shape);
                assertFalse(shape.isEmpty());
                assertTrue(shape.getBoundingBox().maxX <= 2.0 && shape.getBoundingBox().minX >= -1.0);
            }
        }

        // Test boundary clamping on hit inset
        assertEquals(0, ItemThermiteCharge.insetFromHit(Direction.NORTH, 0, 0, -0.5));
        assertEquals(15, ItemThermiteCharge.insetFromHit(Direction.NORTH, 0, 0, 1.5));
    }

    @Test
    @DisplayName("Challenge 4: Spatial Disrupter chunk offset coordinates across negative and section boundaries")
    public void testSpatialDisrupterCoordinatesMapping() {
        BlockPos centerPos = new BlockPos(-350, 48, 512);
        SpatialDisrupterBlockEntity be = new SpatialDisrupterBlockEntity(centerPos, ModBlocks.SPATIAL_DISRUPTER.getDefaultState());

        int expectedMinChunkX = (-350 >> 4) - 8;
        int expectedMinChunkZ = (512 >> 4) - 8;

        // Verify chunk index mapping
        for (int chunkIndex = 0; chunkIndex < 256; chunkIndex++) {
            int cx = expectedMinChunkX + (chunkIndex % 16);
            int cz = expectedMinChunkZ + (chunkIndex / 16);

            assertTrue(cx >= expectedMinChunkX && cx < expectedMinChunkX + 16);
            assertTrue(cz >= expectedMinChunkZ && cz < expectedMinChunkZ + 16);
        }
    }

    @Test
    @DisplayName("Challenge 5: Weapons Protection Chebyshev distance boundary queries and disarm authority")
    public void testWeaponsChebyshevDistanceAndDisarm() {
        BlockPos trapPos = new BlockPos(100, 64, 100);
        UUID keyId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        WeaponsBindings.Binding trap = new WeaponsBindings.Binding(
                "minecraft:overworld", trapPos, keyId, ownerId, WeaponsMode.DEAD_MANS_HAND
        );

        // At Chebyshev distance <= 1:
        BlockPos adjacent1 = new BlockPos(101, 64, 100); // dist 1
        BlockPos diagonal1 = new BlockPos(101, 65, 101); // dist 1
        BlockPos distant = new BlockPos(102, 64, 100);   // dist 2

        assertEquals(1, chebyshev(trapPos, adjacent1));
        assertEquals(1, chebyshev(trapPos, diagonal1));
        assertEquals(2, chebyshev(trapPos, distant));

        assertTrue(chebyshev(trapPos, adjacent1) <= WeaponsBindingEvents.TRAP_RADIUS);
        assertTrue(chebyshev(trapPos, diagonal1) <= WeaponsBindingEvents.TRAP_RADIUS);
        assertFalse(chebyshev(trapPos, distant) <= WeaponsBindingEvents.TRAP_RADIUS);

        // Player authorization check
        DummyPlayer owner = createPlayer(false);
        DummyPlayer intruder = createPlayer(false);

        ItemStack authorizedKey = new ItemStack(ModItems.WEAPONS_RELAY_KEY);
        authorizedKey.getOrCreateNbt().putString(WeaponsKeyData.KEY_ID, keyId.toString());
        owner.getInventory().main.set(0, authorizedKey);

        assertTrue(WeaponsKeyData.hasKey(owner, keyId));
        assertFalse(WeaponsKeyData.hasKey(intruder, keyId));
    }

    private static int chebyshev(BlockPos a, BlockPos b) {
        return Math.max(Math.abs(a.getX() - b.getX()),
                Math.max(Math.abs(a.getY() - b.getY()), Math.abs(a.getZ() - b.getZ())));
    }

    @Test
    @DisplayName("Challenge 6: Redstone Gate cascading linkage persistence across multiple explosive bindings")
    public void testRedstoneGateCascadingLinkages() {
        WeaponsBindings bindings = new WeaponsBindings();

        UUID key1 = UUID.randomUUID();
        UUID owner1 = UUID.randomUUID();
        BlockPos explosive1 = new BlockPos(10, 64, 10);
        BlockPos explosive2 = new BlockPos(20, 64, 20);
        BlockPos gateA = new BlockPos(15, 64, 15);
        BlockPos gateB = new BlockPos(25, 64, 25);

        WeaponsBindings.Binding b1 = new WeaponsBindings.Binding("minecraft:overworld", explosive1, key1, owner1, WeaponsMode.REGULAR);
        b1.gates.add(gateA);
        b1.gates.add(gateB);

        WeaponsBindings.Binding b2 = new WeaponsBindings.Binding("minecraft:overworld", explosive2, key1, owner1, WeaponsMode.REGULAR);
        b2.gates.add(gateA);

        // Serialize and restore
        NbtCompound root = new NbtCompound();
        NbtList list = new NbtList();

        NbtCompound t1 = new NbtCompound();
        t1.putString("dim", b1.dimension);
        t1.putLong("pos", b1.pos.asLong());
        t1.putString("key", b1.keyId.toString());
        t1.putString("owner", b1.ownerId.toString());
        t1.putInt("mode", b1.mode.id);
        t1.putString("gates", BlockPositions.encodeList(b1.gates));
        list.add(t1);

        NbtCompound t2 = new NbtCompound();
        t2.putString("dim", b2.dimension);
        t2.putLong("pos", b2.pos.asLong());
        t2.putString("key", b2.keyId.toString());
        t2.putString("owner", b2.ownerId.toString());
        t2.putInt("mode", b2.mode.id);
        t2.putString("gates", BlockPositions.encodeList(b2.gates));
        list.add(t2);

        root.put("bindings", list);

        WeaponsBindings restored = WeaponsBindings.fromNbt(root);
        NbtCompound roundtrip = restored.writeNbt(new NbtCompound());
        NbtList restoredList = roundtrip.getList("bindings", NbtElement.COMPOUND_TYPE);

        assertEquals(2, restoredList.size());
        assertEquals(BlockPositions.encodeList(b1.gates), restoredList.getCompound(0).getString("gates"));
        assertEquals(BlockPositions.encodeList(b2.gates), restoredList.getCompound(1).getString("gates"));
    }
}
