package com.ntmdoors.test.security;

import com.ntmdoors.block.ModBlocks;
import com.ntmdoors.block.entity.ModBlockEntities;
import com.ntmdoors.item.ItemWeaponsRelayKey;
import com.ntmdoors.item.ModItems;
import com.ntmdoors.sound.ModSounds;
import com.ntmdoors.util.BlockPositions;
import com.ntmdoors.weapons.WeaponsBindingEvents;
import com.ntmdoors.weapons.WeaponsBindings;
import com.ntmdoors.weapons.WeaponsKeyData;
import com.ntmdoors.weapons.WeaponsMode;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.Bootstrap;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class WeaponsKeyTest {

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
    }

    public static class DummyPlayer extends PlayerEntity {
        public UUID customUuid = UUID.randomUUID();
        public String customName = "WeaponsTester";

        public DummyPlayer() {
            super(null, BlockPos.ORIGIN, 0.0f, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "WeaponsTester"));
        }

        @Override public UUID getUuid() { return customUuid; }
        @Override public Text getName() { return Text.literal(customName); }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isCreative() { return false; }
        @Override public void sendMessage(Text message, boolean overlay) {}
    }

    private DummyPlayer createDummyPlayer() {
        try {
            Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) f.get(null);
            DummyPlayer p = (DummyPlayer) unsafe.allocateInstance(DummyPlayer.class);
            p.customUuid = UUID.randomUUID();

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
    @DisplayName("Verify WeaponsMode enum mapping and safe fallback")
    public void testWeaponsModeEnum() {
        assertEquals(0, WeaponsMode.REGULAR.id);
        assertEquals(1, WeaponsMode.DEAD_MANS_HAND.id);

        assertEquals(WeaponsMode.REGULAR, WeaponsMode.byId(0));
        assertEquals(WeaponsMode.DEAD_MANS_HAND, WeaponsMode.byId(1));

        // Out-of-bound fallback to REGULAR
        assertEquals(WeaponsMode.REGULAR, WeaponsMode.byId(-1));
        assertEquals(WeaponsMode.REGULAR, WeaponsMode.byId(99));
    }

    @Test
    @DisplayName("Verify WeaponsKeyData NBT helpers: UUID creation, mode persistence, session lifecycle")
    public void testWeaponsKeyDataHelpers() {
        ItemStack key = new ItemStack(ModItems.WEAPONS_RELAY_KEY);

        // Initial default mode is REGULAR
        assertEquals(WeaponsMode.REGULAR, WeaponsKeyData.mode(key));
        assertNull(WeaponsKeyData.keyId(key));
        assertNull(WeaponsKeyData.sessionPos(key));
        assertEquals("", WeaponsKeyData.sessionDim(key));

        // Mode mutation
        WeaponsKeyData.setMode(key, WeaponsMode.DEAD_MANS_HAND);
        assertEquals(WeaponsMode.DEAD_MANS_HAND, WeaponsKeyData.mode(key));

        // UUID generation
        UUID id = WeaponsKeyData.ensureKeyId(key);
        assertNotNull(id);
        assertEquals(id, WeaponsKeyData.keyId(key));

        // Calling ensureKeyId again must be idempotent
        assertEquals(id, WeaponsKeyData.ensureKeyId(key));

        // Session start and clear
        BlockPos targetPos = new BlockPos(100, 64, -200);
        WeaponsKeyData.startSession(key, "minecraft:overworld", targetPos);
        assertEquals(targetPos, WeaponsKeyData.sessionPos(key));
        assertEquals("minecraft:overworld", WeaponsKeyData.sessionDim(key));

        WeaponsKeyData.clearSession(key);
        assertNull(WeaponsKeyData.sessionPos(key));
        assertEquals("", WeaponsKeyData.sessionDim(key));
    }

    @Test
    @DisplayName("Verify WeaponsKeyData.hasKey scans main inventory, armor slots, and offhand")
    public void testHasKeyInventoryScanning() {
        DummyPlayer player = createDummyPlayer();
        UUID searchId = UUID.randomUUID();

        // 1. Empty inventory -> false
        assertFalse(WeaponsKeyData.hasKey(player, searchId));

        // 2. Put key in main inventory slot 3
        ItemStack mainKey = new ItemStack(ModItems.WEAPONS_RELAY_KEY);
        mainKey.getOrCreateNbt().putString(WeaponsKeyData.KEY_ID, searchId.toString());
        player.getInventory().main.set(3, mainKey);
        assertTrue(WeaponsKeyData.hasKey(player, searchId));

        // 3. Foreign key ID -> false
        UUID otherId = UUID.randomUUID();
        assertFalse(WeaponsKeyData.hasKey(player, otherId));

        // Clear slot 3
        player.getInventory().main.set(3, ItemStack.EMPTY);
        assertFalse(WeaponsKeyData.hasKey(player, searchId));

        // 4. Put key in armor slot 1
        player.getInventory().armor.set(1, mainKey);
        assertTrue(WeaponsKeyData.hasKey(player, searchId));
        player.getInventory().armor.set(1, ItemStack.EMPTY);

        // 5. Put key in offhand slot
        player.getInventory().offHand.set(0, mainKey);
        assertTrue(WeaponsKeyData.hasKey(player, searchId));
        player.getInventory().offHand.set(0, ItemStack.EMPTY);

        // 6. Foreign item with matching NBT must NOT pass
        ItemStack dirt = new ItemStack(Items.DIRT);
        dirt.getOrCreateNbt().putString(WeaponsKeyData.KEY_ID, searchId.toString());
        player.getInventory().main.set(0, dirt);
        assertFalse(WeaponsKeyData.hasKey(player, searchId), "Foreign items with key NBT must be rejected");
    }

    @Test
    @DisplayName("Verify WeaponsBindings NBT serialization roundtrip and gate decoding")
    public void testWeaponsBindingsNbtRoundtrip() {
        NbtCompound root = new NbtCompound();

        UUID key1 = UUID.randomUUID();
        UUID owner1 = UUID.randomUUID();
        UUID key2 = UUID.randomUUID();
        UUID owner2 = UUID.randomUUID();

        BlockPos pos1 = new BlockPos(10, 60, -20);
        BlockPos pos2 = new BlockPos(55, 70, 88);

        Set<BlockPos> gates1 = Collections.singleton(new BlockPos(12, 60, -18));
        List<BlockPos> gates2 = Arrays.asList(new BlockPos(50, 70, 80), new BlockPos(60, 70, 90));

        // Build NBT data directly
        net.minecraft.nbt.NbtList list = new net.minecraft.nbt.NbtList();

        NbtCompound b1 = new NbtCompound();
        b1.putString("dim", "minecraft:overworld");
        b1.putLong("pos", pos1.asLong());
        b1.putString("key", key1.toString());
        b1.putString("owner", owner1.toString());
        b1.putInt("mode", WeaponsMode.REGULAR.id);
        b1.putString("gates", BlockPositions.encodeList(gates1));
        list.add(b1);

        NbtCompound b2 = new NbtCompound();
        b2.putString("dim", "minecraft:the_nether");
        b2.putLong("pos", pos2.asLong());
        b2.putString("key", key2.toString());
        b2.putString("owner", owner2.toString());
        b2.putInt("mode", WeaponsMode.DEAD_MANS_HAND.id);
        b2.putString("gates", BlockPositions.encodeList(gates2));
        list.add(b2);

        root.put("bindings", list);

        // Deserialize
        WeaponsBindings loaded = WeaponsBindings.fromNbt(root);
        assertNotNull(loaded);

        // Reserialize
        NbtCompound roundtripTag = loaded.writeNbt(new NbtCompound());
        net.minecraft.nbt.NbtList roundtripList = roundtripTag.getList("bindings", net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
        assertEquals(2, roundtripList.size());

        NbtCompound r1 = roundtripList.getCompound(0);
        assertEquals("minecraft:overworld", r1.getString("dim"));
        assertEquals(pos1.asLong(), r1.getLong("pos"));
        assertEquals(key1.toString(), r1.getString("key"));
        assertEquals(WeaponsMode.REGULAR.id, r1.getInt("mode"));
        assertEquals(BlockPositions.encodeList(gates1), r1.getString("gates"));

        NbtCompound r2 = roundtripList.getCompound(1);
        assertEquals("minecraft:the_nether", r2.getString("dim"));
        assertEquals(pos2.asLong(), r2.getLong("pos"));
        assertEquals(key2.toString(), r2.getString("key"));
        assertEquals(WeaponsMode.DEAD_MANS_HAND.id, r2.getInt("mode"));
        assertEquals(BlockPositions.encodeList(gates2), r2.getString("gates"));
    }

    @Test
    @DisplayName("Verify WeaponsKeyMode network packet payload roundtrip encoding/decoding")
    public void testWeaponsKeyModePacketCodec() {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeEnumConstant(Hand.OFF_HAND);
        buf.writeByte(WeaponsMode.DEAD_MANS_HAND.id);

        Hand readHand = buf.readEnumConstant(Hand.class);
        WeaponsMode readMode = WeaponsMode.byId(buf.readByte());

        assertEquals(Hand.OFF_HAND, readHand);
        assertEquals(WeaponsMode.DEAD_MANS_HAND, readMode);
    }
}
