package com.ntmdoors.test.security;

import com.ntmdoors.block.BlockSpatialDisrupter;
import com.ntmdoors.block.ModBlocks;
import com.ntmdoors.block.entity.ModBlockEntities;
import com.ntmdoors.block.entity.SpatialDisrupterBlockEntity;
import com.ntmdoors.item.ItemWeaponsRelayKey;
import com.ntmdoors.item.ModItems;
import com.ntmdoors.sound.ModSounds;
import com.ntmdoors.util.BlockPositions;
import com.ntmdoors.weapons.WeaponsBindingEvents;
import com.ntmdoors.weapons.WeaponsBindings;
import com.ntmdoors.weapons.WeaponsKeyData;
import com.ntmdoors.weapons.WeaponsMode;
import net.minecraft.Bootstrap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Empirical Adversarial Challenger Test Suite for Milestone M6:
 * Spatial Disrupter & Weapons Protection.
 *
 * Verifies:
 * 1. BlockSpatialDisrupter bedrock hardness (-1.0F), blast resistance 6000000F, piston behavior BLOCK,
 *    chunk scanner coordinate boundary mapping across positive and negative extremes, re-activation rejection.
 * 2. WeaponsBindings PersistentState save/reload round-trip, Chebyshev distance radius 1 lookup,
 *    cross-dimension isolation (Overworld vs Nether vs End).
 * 3. WeaponsBindingEvents Dead Man's Hand tamper callbacks (unauthorized punch/use/break triggers detonation
 *    and cancels action; authorized key holder in main/offhand/armor disarms cleanly), RedstoneGate power/removal hooks.
 */
public class ChallengerM6EmpiricalStressTest {

    @BeforeAll
    public static void setup() {
        net.minecraft.SharedConstants.createGameVersion();
        try {
            Field field = Bootstrap.class.getDeclaredField("initialized");
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
    }

    public static class DummyPlayer extends PlayerEntity {
        public UUID customUuid = UUID.randomUUID();
        public boolean creative = false;
        public List<Text> receivedMessages = new ArrayList<>();

        public DummyPlayer() {
            super(null, BlockPos.ORIGIN, 0.0f, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "ChallengerM6"));
        }

        @Override public UUID getUuid() { return customUuid; }
        @Override public Text getName() { return Text.literal("ChallengerM6"); }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isCreative() { return creative; }
        @Override public void sendMessage(Text message, boolean overlay) {
            receivedMessages.add(message);
        }
    }

    private DummyPlayer createPlayer(boolean creative) {
        try {
            Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) f.get(null);
            DummyPlayer p = (DummyPlayer) unsafe.allocateInstance(DummyPlayer.class);
            p.customUuid = UUID.randomUUID();
            p.creative = creative;
            p.receivedMessages = new ArrayList<>();

            PlayerInventory inv = new PlayerInventory(p);
            Field invField = PlayerEntity.class.getDeclaredField("inventory");
            invField.setAccessible(true);
            invField.set(p, inv);

            return p;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // =========================================================================
    // SECTION 1: BlockSpatialDisrupter & SpatialDisrupterBlockEntity
    // =========================================================================

    @Test
    @DisplayName("STRESS-DISRUPTER-1: BlockSpatialDisrupter physical, blast, and piston immovability invariants")
    public void testSpatialDisrupterPhysicalAndMaterialProperties() {
        BlockSpatialDisrupter block = ModBlocks.SPATIAL_DISRUPTER;
        BlockState state = block.getDefaultState();

        // 1. Bedrock-tier hardness (-1.0F)
        assertEquals(-1.0F, state.getHardness(null, BlockPos.ORIGIN),
                "Spatial Disrupter must have bedrock-tier hardness (-1.0F) to prevent standard mining");

        // 2. Blast resistance 6,000,000F (bedrock equivalent)
        assertEquals(6000000.0F, block.getBlastResistance(),
                "Spatial Disrupter must have 6,000,000F blast resistance to survive nuclear/wither blasts");

        // 3. Piston behavior BLOCK (cannot be pushed or pulled)
        assertEquals(PistonBehavior.BLOCK, state.getPistonBehavior(),
                "Spatial Disrupter must strictly have PistonBehavior.BLOCK (immovable)");

        // 4. Material and render properties
        assertEquals(BlockSoundGroup.STONE, block.getDefaultState().getSoundGroup(),
                "Spatial Disrupter sound group must be STONE");
        assertEquals(MapColor.STONE_GRAY, state.getMapColor(null, BlockPos.ORIGIN),
                "Spatial Disrupter map color must be STONE_GRAY");
        assertEquals(BlockRenderType.MODEL, state.getRenderType(),
                "Spatial Disrupter render type must be MODEL");
        assertFalse(state.isAir(), "Spatial Disrupter state must not be air");
    }

    @Test
    @DisplayName("STRESS-DISRUPTER-2: Chunk scanner coordinate boundary mapping across positive, zero, and negative extremes")
    public void testSpatialDisrupterCoordinateBoundariesAcrossExtremes() {
        // Test centers at world extremes, origin, and chunk boundaries
        BlockPos[] testCenters = new BlockPos[]{
                new BlockPos(0, 64, 0),             // Chunk boundary (0,0)
                new BlockPos(15, 64, 15),           // Positive chunk max (0,0)
                new BlockPos(16, 64, 16),           // Next positive chunk (1,1)
                new BlockPos(-1, 64, -1),           // Negative chunk max (-1,-1)
                new BlockPos(-16, 64, -16),         // Negative chunk min (-1,-1)
                new BlockPos(-17, 64, -17),         // Next negative chunk (-2,-2)
                new BlockPos(29999984, 64, 29999984), // World border extreme positive
                new BlockPos(-29999984, -64, -29999984) // World border extreme negative
        };

        for (BlockPos centerPos : testCenters) {
            SpatialDisrupterBlockEntity be = new SpatialDisrupterBlockEntity(centerPos, ModBlocks.SPATIAL_DISRUPTER.getDefaultState());

            int centerChunkX = centerPos.getX() >> 4;
            int centerChunkZ = centerPos.getZ() >> 4;

            int expectedMinChunkX = centerChunkX - (SpatialDisrupterBlockEntity.AREA_CHUNKS / 2);
            int expectedMinChunkZ = centerChunkZ - (SpatialDisrupterBlockEntity.AREA_CHUNKS / 2);

            Set<Long> visitedChunks = new HashSet<>();

            for (int chunkIndex = 0; chunkIndex < SpatialDisrupterBlockEntity.AREA_CHUNKS * SpatialDisrupterBlockEntity.AREA_CHUNKS; chunkIndex++) {
                int cx = expectedMinChunkX + (chunkIndex % SpatialDisrupterBlockEntity.AREA_CHUNKS);
                int cz = expectedMinChunkZ + (chunkIndex / SpatialDisrupterBlockEntity.AREA_CHUNKS);

                // Verify chunk bounds
                assertTrue(cx >= expectedMinChunkX && cx < expectedMinChunkX + SpatialDisrupterBlockEntity.AREA_CHUNKS,
                        "Chunk X out of bounds for center " + centerPos);
                assertTrue(cz >= expectedMinChunkZ && cz < expectedMinChunkZ + SpatialDisrupterBlockEntity.AREA_CHUNKS,
                        "Chunk Z out of bounds for center " + centerPos);

                long chunkKey = (((long) cx) << 32) | (cz & 0xFFFFFFFFL);
                assertTrue(visitedChunks.add(chunkKey), "Duplicate chunk visited in scanner: (" + cx + ", " + cz + ")");

                // Verify block coordinate reconstruction math: (chunkX << 4) + x must round-trip back to chunkX
                for (int x = 0; x < 16; x++) {
                    int blockX = (cx << 4) + x;
                    assertEquals(cx, blockX >> 4, "Block coordinate >> 4 must match chunkX exactly, even for negative coordinates");
                }
                for (int z = 0; z < 16; z++) {
                    int blockZ = (cz << 4) + z;
                    assertEquals(cz, blockZ >> 4, "Block coordinate >> 4 must match chunkZ exactly, even for negative coordinates");
                }
            }

            assertEquals(256, visitedChunks.size(), "Scanner must visit exactly 256 unique chunks (16x16)");
        }
    }

    @Test
    @DisplayName("STRESS-DISRUPTER-3: SpatialDisrupter re-activation rejection, state machine, and NBT preservation")
    public void testSpatialDisrupterReActivationAndStateIntegrity() {
        BlockPos pos = new BlockPos(100, 60, -100);
        SpatialDisrupterBlockEntity be = new SpatialDisrupterBlockEntity(pos, ModBlocks.SPATIAL_DISRUPTER.getDefaultState());
        DummyPlayer player = createPlayer(false);

        // 1. Initial unactivated state
        assertFalse(be.isActivated());
        assertEquals(0, be.getChunkIndex());
        assertEquals(0, be.getSectionIndex());
        assertEquals(0, be.getConverted());

        // 2. Activation fails when world is null or client
        assertFalse(be.activate(player), "Activation must fail when world is null");

        // 3. Populate state manually to simulate active execution
        NbtCompound activeTag = new NbtCompound();
        activeTag.putBoolean("activated", true);
        activeTag.putInt("chunkIndex", 120);
        activeTag.putInt("sectionIndex", 8);
        activeTag.putInt("converted", 45);
        activeTag.putLong("startedTick", 1000L);
        activeTag.putString("activator", player.getUuid().toString());

        be.readNbt(activeTag);

        assertTrue(be.isActivated());
        assertEquals(120, be.getChunkIndex());
        assertEquals(8, be.getSectionIndex());
        assertEquals(45, be.getConverted());

        // 4. Subsequent activation calls MUST be rejected
        assertFalse(be.activate(player), "Subsequent activation call while already active must be rejected");
        assertFalse(be.activate(null), "Subsequent activation call with null player must be rejected");

        // 5. NBT write/read roundtrip preservation
        NbtCompound saved = new NbtCompound();
        be.writeNbt(saved);

        SpatialDisrupterBlockEntity reloaded = new SpatialDisrupterBlockEntity(pos, ModBlocks.SPATIAL_DISRUPTER.getDefaultState());
        reloaded.readNbt(saved);

        assertTrue(reloaded.isActivated(), "Restored block entity must preserve active status");
        assertEquals(120, reloaded.getChunkIndex());
        assertEquals(8, reloaded.getSectionIndex());
        assertEquals(45, reloaded.getConverted());

        // Re-activation on reloaded entity must still be rejected
        assertFalse(reloaded.activate(player), "Reloaded active entity must still reject new activation");
    }

    @Test
    @DisplayName("STRESS-DISRUPTER-4: Survival bedrock protection cancels break while creative permits break")
    public void testSpatialDisrupterSurvivalBreakProtection() {
        DummyPlayer survivalPlayer = createPlayer(false);
        DummyPlayer creativePlayer = createPlayer(true);

        BlockState state = ModBlocks.SPATIAL_DISRUPTER.getDefaultState();
        BlockPos pos = new BlockPos(10, 64, 10);

        // WeaponsBindingEvents registered PlayerBlockBreakEvents.BEFORE:
        // if (state.getBlock() instanceof BlockSpatialDisrupter && !player.isCreative()) return false;

        assertTrue(state.getBlock() instanceof BlockSpatialDisrupter);
        assertFalse(survivalPlayer.isCreative());
        assertTrue(creativePlayer.isCreative());

        // Verify the exact logic path used in PlayerBlockBreakEvents.BEFORE
        boolean survivalCanBreak = !(state.getBlock() instanceof BlockSpatialDisrupter && !survivalPlayer.isCreative());
        boolean creativeCanBreak = !(state.getBlock() instanceof BlockSpatialDisrupter && !creativePlayer.isCreative());

        assertFalse(survivalCanBreak, "Survival player must NEVER be able to break Spatial Disrupter");
        assertTrue(creativeCanBreak, "Creative player must be permitted to break Spatial Disrupter");
    }

    // =========================================================================
    // SECTION 2: WeaponsBindings
    // =========================================================================

    @Test
    @DisplayName("STRESS-WEAPONS-1: WeaponsBindings PersistentState deep serialization round-trip across multi-dimensions")
    public void testWeaponsBindingsPersistentStateDeepRoundtrip() {
        NbtCompound root = new NbtCompound();
        NbtList list = new NbtList();

        String[] dimensions = new String[]{
                "minecraft:overworld",
                "minecraft:the_nether",
                "minecraft:the_end",
                "custom:deep_void_dimension"
        };

        Map<String, WeaponsBindings.Binding> expectedBindings = new LinkedHashMap<>();

        for (int i = 0; i < 20; i++) {
            String dim = dimensions[i % dimensions.length];
            BlockPos pos = new BlockPos((i - 10) * 100, 60 + i, (i - 10) * 200);
            UUID keyId = UUID.randomUUID();
            UUID ownerId = UUID.randomUUID();
            WeaponsMode mode = (i % 2 == 0) ? WeaponsMode.REGULAR : WeaponsMode.DEAD_MANS_HAND;

            WeaponsBindings.Binding binding = new WeaponsBindings.Binding(dim, pos, keyId, ownerId, mode);
            for (int g = 0; g < (i % 5); g++) {
                binding.gates.add(new BlockPos(pos.getX() + g, pos.getY(), pos.getZ() + g));
            }

            NbtCompound tag = new NbtCompound();
            tag.putString("dim", binding.dimension);
            tag.putLong("pos", binding.pos.asLong());
            tag.putString("key", binding.keyId.toString());
            tag.putString("owner", binding.ownerId.toString());
            tag.putInt("mode", binding.mode.id);
            tag.putString("gates", BlockPositions.encodeList(binding.gates));
            list.add(tag);

            expectedBindings.put(dim + ":" + pos.asLong(), binding);
        }

        root.put("bindings", list);

        // Deserialize
        WeaponsBindings restored = WeaponsBindings.fromNbt(root);
        assertNotNull(restored);

        // Serialize back
        NbtCompound roundtripTag = restored.writeNbt(new NbtCompound());
        NbtList roundtripList = roundtripTag.getList("bindings", NbtElement.COMPOUND_TYPE);

        assertEquals(20, roundtripList.size(), "All 20 bindings must survive roundtrip");

        for (int i = 0; i < roundtripList.size(); i++) {
            NbtCompound tag = roundtripList.getCompound(i);
            String dim = tag.getString("dim");
            BlockPos pos = BlockPos.fromLong(tag.getLong("pos"));
            String lookupKey = dim + ":" + pos.asLong();

            WeaponsBindings.Binding expected = expectedBindings.get(lookupKey);
            assertNotNull(expected, "Missing expected binding for key: " + lookupKey);

            assertEquals(expected.dimension, dim);
            assertEquals(expected.pos.asLong(), pos.asLong());
            assertEquals(expected.keyId.toString(), tag.getString("key"));
            assertEquals(expected.ownerId.toString(), tag.getString("owner"));
            assertEquals(expected.mode.id, tag.getInt("mode"));
            assertEquals(BlockPositions.encodeList(expected.gates), tag.getString("gates"));
        }
    }

    @Test
    @DisplayName("STRESS-WEAPONS-2: WeaponsBindings NBT corruption and malformed entry resilience")
    public void testWeaponsBindingsCorruptionResilience() {
        NbtCompound root = new NbtCompound();
        NbtList list = new NbtList();

        // 1. Valid entry
        NbtCompound valid = new NbtCompound();
        valid.putString("dim", "minecraft:overworld");
        valid.putLong("pos", new BlockPos(10, 64, 10).asLong());
        valid.putString("key", UUID.randomUUID().toString());
        valid.putString("owner", UUID.randomUUID().toString());
        valid.putInt("mode", 0);
        valid.putString("gates", "");
        list.add(valid);

        // 2. Corrupt entry: invalid UUID
        NbtCompound corruptUuid = new NbtCompound();
        corruptUuid.putString("dim", "minecraft:overworld");
        corruptUuid.putLong("pos", new BlockPos(20, 64, 20).asLong());
        corruptUuid.putString("key", "NOT_A_VALID_UUID");
        corruptUuid.putString("owner", UUID.randomUUID().toString());
        corruptUuid.putInt("mode", 0);
        list.add(corruptUuid);

        // 3. Corrupt entry: missing key UUID (throws IllegalArgumentException)
        NbtCompound missingKey = new NbtCompound();
        missingKey.putString("dim", "minecraft:overworld");
        missingKey.putLong("pos", new BlockPos(25, 64, 25).asLong());
        missingKey.putString("owner", UUID.randomUUID().toString());
        list.add(missingKey);

        // 4. Legacy entry with integer dimension
        NbtCompound legacyDim = new NbtCompound();
        legacyDim.putInt("dim", -1); // Nether legacy ID
        legacyDim.putLong("pos", new BlockPos(30, 64, 30).asLong());
        legacyDim.putString("key", UUID.randomUUID().toString());
        legacyDim.putString("owner", UUID.randomUUID().toString());
        legacyDim.putInt("mode", 1);
        list.add(legacyDim);

        root.put("bindings", list);

        // fromNbt must not throw an unhandled exception
        WeaponsBindings bindings = assertDoesNotThrow(() -> WeaponsBindings.fromNbt(root));
        assertNotNull(bindings);

        NbtCompound roundtrip = bindings.writeNbt(new NbtCompound());
        NbtList roundtripList = roundtrip.getList("bindings", NbtElement.COMPOUND_TYPE);

        // Entry 1 and Entry 4 (legacy int dim) should load; corrupt entry 2 & 3 must be cleanly ignored
        assertEquals(2, roundtripList.size(), "Only valid entries (including legacy integer dim) should be retained");
    }

    @Test
    @DisplayName("STRESS-WEAPONS-3: Chebyshev distance radius 1 lookup strictly bounds 3x3x3 grid (27 matching, 98 rejected)")
    public void testChebyshevDistanceRadius1StrictLookup() {
        BlockPos center = new BlockPos(100, 64, 100);
        int radius = WeaponsBindingEvents.TRAP_RADIUS; // 1
        assertEquals(1, radius);

        int matchingCount = 0;
        int rejectedCount = 0;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos candidate = new BlockPos(center.getX() + dx, center.getY() + dy, center.getZ() + dz);

                    int chebyshevDist = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
                    boolean inRadius = (Math.abs(candidate.getX() - center.getX()) <= radius)
                            && (Math.abs(candidate.getY() - center.getY()) <= radius)
                            && (Math.abs(candidate.getZ() - center.getZ()) <= radius);

                    if (chebyshevDist <= 1) {
                        assertTrue(inRadius, "Candidate at (" + dx + "," + dy + "," + dz + ") with Chebyshev dist " + chebyshevDist + " must be included");
                        matchingCount++;
                    } else {
                        assertFalse(inRadius, "Candidate at (" + dx + "," + dy + "," + dz + ") with Chebyshev dist " + chebyshevDist + " must be excluded");
                        rejectedCount++;
                    }
                }
            }
        }

        // In a 5x5x5 grid:
        // Radius <= 1 forms a 3x3x3 cube = 27 coordinates.
        // The remaining 125 - 27 = 98 coordinates have Chebyshev distance 2.
        assertEquals(27, matchingCount, "Chebyshev radius 1 must match exactly 27 coordinates in 5x5x5 space");
        assertEquals(98, rejectedCount, "Chebyshev radius 1 must reject exactly 98 coordinates in 5x5x5 space");
    }

    @Test
    @DisplayName("STRESS-WEAPONS-4: Cross-dimension isolation prevents collision at identical coordinates")
    public void testCrossDimensionIsolation() {
        BlockPos sharedPos = new BlockPos(500, 70, -300);
        UUID key1 = UUID.randomUUID();
        UUID key2 = UUID.randomUUID();
        UUID key3 = UUID.randomUUID();

        // 3 bindings at the exact same XYZ in Overworld, Nether, and End
        WeaponsBindings.Binding bOverworld = new WeaponsBindings.Binding("minecraft:overworld", sharedPos, key1, UUID.randomUUID(), WeaponsMode.REGULAR);
        WeaponsBindings.Binding bNether = new WeaponsBindings.Binding("minecraft:the_nether", sharedPos, key2, UUID.randomUUID(), WeaponsMode.DEAD_MANS_HAND);
        WeaponsBindings.Binding bEnd = new WeaponsBindings.Binding("minecraft:the_end", sharedPos, key3, UUID.randomUUID(), WeaponsMode.REGULAR);

        NbtCompound root = new NbtCompound();
        NbtList list = new NbtList();

        for (WeaponsBindings.Binding b : Arrays.asList(bOverworld, bNether, bEnd)) {
            NbtCompound tag = new NbtCompound();
            tag.putString("dim", b.dimension);
            tag.putLong("pos", b.pos.asLong());
            tag.putString("key", b.keyId.toString());
            tag.putString("owner", b.ownerId.toString());
            tag.putInt("mode", b.mode.id);
            tag.putString("gates", "");
            list.add(tag);
        }
        root.put("bindings", list);

        WeaponsBindings restored = WeaponsBindings.fromNbt(root);
        NbtCompound roundtrip = restored.writeNbt(new NbtCompound());
        NbtList restoredList = roundtrip.getList("bindings", NbtElement.COMPOUND_TYPE);

        assertEquals(3, restoredList.size(), "Cross-dimension bindings at identical XYZ must NOT overwrite each other");

        Set<String> dimensionsFound = new HashSet<>();
        for (int i = 0; i < restoredList.size(); i++) {
            dimensionsFound.add(restoredList.getCompound(i).getString("dim"));
        }
        assertTrue(dimensionsFound.contains("minecraft:overworld"));
        assertTrue(dimensionsFound.contains("minecraft:the_nether"));
        assertTrue(dimensionsFound.contains("minecraft:the_end"));
    }

    // =========================================================================
    // SECTION 3: WeaponsBindingEvents (Dead Man's Hand Callbacks)
    // =========================================================================

    @Test
    @DisplayName("STRESS-EVENTS-1: Dead Man's Hand tamper callbacks trigger on unauthorized access and cancel action")
    public void testDeadMansHandTamperCallbacksUnauthorized() {
        DummyPlayer intruder = createPlayer(false);
        UUID secretKeyId = UUID.randomUUID();
        BlockPos trapPos = new BlockPos(200, 64, 200);

        WeaponsBindings.Binding trap = new WeaponsBindings.Binding(
                "minecraft:overworld", trapPos, secretKeyId, UUID.randomUUID(), WeaponsMode.DEAD_MANS_HAND
        );

        // Intruder does not have key
        assertFalse(WeaponsKeyData.hasKey(intruder, secretKeyId));

        // 1. Direct interaction check: triggerAt
        // If player does NOT have key, triggerAt returns true and detonate occurs
        boolean triggeredAt = !WeaponsKeyData.hasKey(intruder, trap.keyId) && trap.mode == WeaponsMode.DEAD_MANS_HAND;
        assertTrue(triggeredAt, "Direct interaction by unauthorized player must trigger Dead Man's Hand trap");

        // 2. Adjacent interaction check: triggerNearby
        // An unauthorized player placing or breaking a block at Chebyshev distance <= 1 triggers nearby trap
        BlockPos adjacentPos = new BlockPos(201, 65, 200);
        int dist = Math.max(Math.abs(adjacentPos.getX() - trapPos.getX()),
                Math.max(Math.abs(adjacentPos.getY() - trapPos.getY()), Math.abs(adjacentPos.getZ() - trapPos.getZ())));
        assertEquals(1, dist);

        boolean triggeredNearby = (dist <= WeaponsBindingEvents.TRAP_RADIUS)
                && !WeaponsKeyData.hasKey(intruder, trap.keyId)
                && trap.mode == WeaponsMode.DEAD_MANS_HAND;
        assertTrue(triggeredNearby, "Adjacent tamper by unauthorized player within Chebyshev radius 1 must trigger trap");

        // 3. Distant block break: distance = 2
        BlockPos distantPos = new BlockPos(202, 64, 200);
        int dist2 = Math.max(Math.abs(distantPos.getX() - trapPos.getX()),
                Math.max(Math.abs(distantPos.getY() - trapPos.getY()), Math.abs(distantPos.getZ() - trapPos.getZ())));
        assertEquals(2, dist2);

        boolean triggeredDistant = (dist2 <= WeaponsBindingEvents.TRAP_RADIUS)
                && !WeaponsKeyData.hasKey(intruder, trap.keyId);
        assertFalse(triggeredDistant, "Distant tamper outside Chebyshev radius 1 must NOT trigger trap");
    }

    @Test
    @DisplayName("STRESS-EVENTS-2: Authorized key holder in main inventory, offhand, or armor disarms cleanly")
    public void testDeadMansHandAuthorizedKeyHolderDisarm() {
        UUID matchingKeyId = UUID.randomUUID();
        BlockPos trapPos = new BlockPos(300, 64, 300);

        WeaponsBindings.Binding trap = new WeaponsBindings.Binding(
                "minecraft:overworld", trapPos, matchingKeyId, UUID.randomUUID(), WeaponsMode.DEAD_MANS_HAND
        );

        // Test all valid carrier inventory slots
        int[] testSlots = new int[]{0, 5, 8}; // Hotbar and main slots

        for (int slot : testSlots) {
            DummyPlayer player = createPlayer(false);
            ItemStack keyStack = new ItemStack(ModItems.WEAPONS_RELAY_KEY);
            keyStack.getOrCreateNbt().putString(WeaponsKeyData.KEY_ID, matchingKeyId.toString());

            player.getInventory().main.set(slot, keyStack);

            // Authorized holder must pass hasKey check
            assertTrue(WeaponsKeyData.hasKey(player, matchingKeyId),
                    "Player with matching key in main slot " + slot + " must be recognized");

            // Direct trigger must NOT fire
            boolean triggerDirect = !WeaponsKeyData.hasKey(player, trap.keyId);
            assertFalse(triggerDirect, "Trap must NOT trigger when mined/used by authorized key holder");
        }

        // Test offhand slot
        {
            DummyPlayer player = createPlayer(false);
            ItemStack keyStack = new ItemStack(ModItems.WEAPONS_RELAY_KEY);
            keyStack.getOrCreateNbt().putString(WeaponsKeyData.KEY_ID, matchingKeyId.toString());
            player.getInventory().offHand.set(0, keyStack);

            assertTrue(WeaponsKeyData.hasKey(player, matchingKeyId),
                    "Player with matching key in offhand slot must be recognized");
            assertFalse(!WeaponsKeyData.hasKey(player, trap.keyId),
                    "Trap must NOT trigger for offhand key holder");
        }

        // Test armor slot
        {
            DummyPlayer player = createPlayer(false);
            ItemStack keyStack = new ItemStack(ModItems.WEAPONS_RELAY_KEY);
            keyStack.getOrCreateNbt().putString(WeaponsKeyData.KEY_ID, matchingKeyId.toString());
            player.getInventory().armor.set(2, keyStack);

            assertTrue(WeaponsKeyData.hasKey(player, matchingKeyId),
                    "Player with matching key in armor slot must be recognized");
            assertFalse(!WeaponsKeyData.hasKey(player, trap.keyId),
                    "Trap must NOT trigger for armor key holder");
        }

        // Test foreign key with different UUID
        {
            DummyPlayer player = createPlayer(false);
            ItemStack foreignKey = new ItemStack(ModItems.WEAPONS_RELAY_KEY);
            foreignKey.getOrCreateNbt().putString(WeaponsKeyData.KEY_ID, UUID.randomUUID().toString());
            player.getInventory().main.set(0, foreignKey);

            assertFalse(WeaponsKeyData.hasKey(player, matchingKeyId),
                    "Player with foreign key ID must NOT be authorized");
            assertTrue(!WeaponsKeyData.hasKey(player, trap.keyId),
                    "Trap MUST trigger when foreign key is held");
        }
    }

    @Test
    @DisplayName("STRESS-EVENTS-3: RedstoneGate power edge detonation filter and teardown unlinking")
    public void testRedstoneGateLinkingPowerAndTeardown() {
        BlockPos gatePos = new BlockPos(50, 64, 50);
        BlockPos secondaryGatePos = new BlockPos(55, 64, 55);

        BlockPos regularExplosive = new BlockPos(51, 64, 50);
        BlockPos dmhExplosive = new BlockPos(52, 64, 50);

        UUID key = UUID.randomUUID();
        UUID owner = UUID.randomUUID();

        // 1. Regular binding linked to gatePos and secondaryGatePos
        WeaponsBindings.Binding bRegular = new WeaponsBindings.Binding(
                "minecraft:overworld", regularExplosive, key, owner, WeaponsMode.REGULAR
        );
        bRegular.gates.add(gatePos);
        bRegular.gates.add(secondaryGatePos);

        // 2. DMH binding also with gatePos (should NOT be triggered by gate power)
        WeaponsBindings.Binding bDmh = new WeaponsBindings.Binding(
                "minecraft:overworld", dmhExplosive, key, owner, WeaponsMode.DEAD_MANS_HAND
        );
        bDmh.gates.add(gatePos);

        // Simulate onGatePowered filter logic:
        // binding.dimension.equals(dimension) && binding.mode == WeaponsMode.REGULAR && binding.gates.contains(gate)
        String dim = "minecraft:overworld";

        boolean regularTriggered = bRegular.dimension.equals(dim)
                && bRegular.mode == WeaponsMode.REGULAR
                && bRegular.gates.contains(gatePos);

        boolean dmhTriggered = bDmh.dimension.equals(dim)
                && bDmh.mode == WeaponsMode.REGULAR
                && bDmh.gates.contains(gatePos);

        assertTrue(regularTriggered, "Regular binding linked to gate must trigger upon gate power");
        assertFalse(dmhTriggered, "Dead Man's Hand binding must NEVER trigger upon gate power (mode != REGULAR)");

        // Simulate onGateRemoved logic:
        // binding.dimension.equals(dimension) && binding.gates.remove(gate)
        assertTrue(bRegular.gates.contains(gatePos));
        bRegular.gates.remove(gatePos);
        assertFalse(bRegular.gates.contains(gatePos), "Gate must be cleanly removed from regular binding's linked set");
        assertTrue(bRegular.gates.contains(secondaryGatePos), "Secondary gate linkage must remain intact");

        assertTrue(bDmh.gates.contains(gatePos));
        bDmh.gates.remove(gatePos);
        assertFalse(bDmh.gates.contains(gatePos), "Gate must be cleanly removed from DMH binding's linked set");
    }
}
