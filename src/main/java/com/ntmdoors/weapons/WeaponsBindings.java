package com.ntmdoors.weapons;

import com.ntmdoors.util.BlockPositions;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-save registry of explosives bound by a Weapons Relay Key on Fabric 1.20.1.
 * Extends PersistentState and is stored on the Overworld's PersistentStateManager.
 */
public class WeaponsBindings extends PersistentState {

    public static final String DATA_NAME = "ntmdoors_weapons_bindings";

    /** One bound explosive and the gates it is linked to. */
    public static final class Binding {
        public final String dimension;
        public final BlockPos pos;
        public final UUID keyId;
        public final UUID ownerId;
        public final WeaponsMode mode;
        public final Set<BlockPos> gates = new LinkedHashSet<>();

        public Binding(String dimension, BlockPos pos, UUID keyId, UUID ownerId, WeaponsMode mode) {
            this.dimension = dimension != null ? dimension : "";
            this.pos = pos;
            this.keyId = keyId;
            this.ownerId = ownerId;
            this.mode = mode;
        }
    }

    private final Map<String, Binding> bindings = new LinkedHashMap<>();

    public WeaponsBindings() {
        super();
    }

    public static String dimKey(World world) {
        if (world == null) return "";
        return world.getRegistryKey().getValue().toString();
    }

    public static WeaponsBindings get(World world) {
        if (world == null || world.isClient()) return null;
        if (world instanceof ServerWorld serverWorld) {
            return serverWorld.getServer().getOverworld().getPersistentStateManager().getOrCreate(
                    WeaponsBindings::fromNbt,
                    WeaponsBindings::new,
                    DATA_NAME
            );
        }
        return null;
    }

    /** The live binding at a position, or null. Stale records are pruned on access. */
    public static Binding find(World world, BlockPos pos) {
        WeaponsBindings data = get(world);
        if (data == null || pos == null) return null;
        String key = key(dimKey(world), pos);
        Binding binding = data.bindings.get(key);
        if (binding != null && !Explosives.isExplosive(world, pos)) {
            data.bindings.remove(key);
            data.markDirty();
            return null;
        }
        return binding;
    }

    public static Binding bind(World world, BlockPos pos, UUID keyId, UUID ownerId, WeaponsMode mode) {
        WeaponsBindings data = get(world);
        if (data == null || pos == null || keyId == null) return null;
        Binding binding = new Binding(dimKey(world), pos.toImmutable(), keyId, ownerId, mode);
        data.bindings.put(key(binding.dimension, binding.pos), binding);
        data.markDirty();
        return binding;
    }

    public static void unbind(World world, BlockPos pos) {
        WeaponsBindings data = get(world);
        if (data == null || pos == null) return;
        if (data.bindings.remove(key(dimKey(world), pos)) != null) {
            data.markDirty();
        }
    }

    /** Adds or removes one gate link; returns true when it is now linked. */
    public static boolean toggleGate(World world, BlockPos explosive, BlockPos gate) {
        WeaponsBindings data = get(world);
        if (data == null || explosive == null || gate == null) return false;
        Binding binding = data.bindings.get(key(dimKey(world), explosive));
        if (binding == null) return false;
        BlockPos immutable = gate.toImmutable();
        boolean linked;
        if (binding.gates.remove(immutable)) {
            linked = false;
        } else {
            binding.gates.add(immutable);
            linked = true;
        }
        data.markDirty();
        return linked;
    }

    /** Fires every Regular-bound explosive linked to this gate, then drops those bindings. */
    public static void onGatePowered(World world, BlockPos gate) {
        WeaponsBindings data = get(world);
        if (data == null || gate == null) return;
        String dimension = dimKey(world);
        List<Binding> triggered = new ArrayList<>();
        for (Binding binding : data.bindings.values()) {
            if (binding.dimension.equals(dimension)
                    && binding.mode == WeaponsMode.REGULAR
                    && binding.gates.contains(gate)) {
                triggered.add(binding);
            }
        }
        for (Binding binding : triggered) {
            if (!world.isChunkLoaded(binding.pos.getX() >> 4, binding.pos.getZ() >> 4)) {
                // Keep the binding for a later pulse instead of force-loading the chunk
                continue;
            }
            trigger(world, binding);
        }
        if (!triggered.isEmpty()) data.markDirty();
    }

    /** Removes a binding and detonates the explosive it protects, when the block is still present. */
    public static void trigger(World world, Binding binding) {
        WeaponsBindings data = get(world);
        if (data == null || binding == null) return;
        data.bindings.remove(key(binding.dimension, binding.pos));
        data.markDirty();
        if (world.isChunkLoaded(binding.pos.getX() >> 4, binding.pos.getZ() >> 4)
                && Explosives.isExplosive(world, binding.pos)) {
            Explosives.trigger(world, binding.pos);
        }
    }

    /** All bindings of the requested mode within radius blocks (Chebyshev distance). */
    public static List<Binding> findNear(World world, BlockPos pos, int radius, WeaponsMode mode) {
        List<Binding> out = new ArrayList<>();
        WeaponsBindings data = get(world);
        if (data == null || pos == null) return out;
        String dimension = dimKey(world);
        for (Binding binding : data.bindings.values()) {
            if (!binding.dimension.equals(dimension)) continue;
            if (mode != null && binding.mode != mode) continue;
            if (Math.abs(binding.pos.getX() - pos.getX()) <= radius
                    && Math.abs(binding.pos.getY() - pos.getY()) <= radius
                    && Math.abs(binding.pos.getZ() - pos.getZ()) <= radius) {
                out.add(binding);
            }
        }
        return out;
    }

    /** Drops a broken gate from every binding in its dimension. */
    public static void onGateRemoved(World world, BlockPos gate) {
        WeaponsBindings data = get(world);
        if (data == null || gate == null) return;
        String dimension = dimKey(world);
        boolean changed = false;
        for (Binding binding : data.bindings.values()) {
            if (binding.dimension.equals(dimension) && binding.gates.remove(gate)) {
                changed = true;
            }
        }
        if (changed) data.markDirty();
    }

    // ------------------------------------------------------------ persistence

    public static WeaponsBindings fromNbt(NbtCompound nbt) {
        WeaponsBindings data = new WeaponsBindings();
        NbtList list = nbt.getList("bindings", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound tag = list.getCompound(i);
            try {
                String dim = tag.contains("dim", NbtElement.STRING_TYPE)
                        ? tag.getString("dim")
                        : String.valueOf(tag.getInt("dim"));
                Binding binding = new Binding(
                        dim,
                        BlockPos.fromLong(tag.getLong("pos")),
                        UUID.fromString(tag.getString("key")),
                        UUID.fromString(tag.getString("owner")),
                        WeaponsMode.byId(tag.getInt("mode"))
                );
                binding.gates.addAll(BlockPositions.decodeList(tag.getString("gates")));
                data.bindings.put(key(binding.dimension, binding.pos), binding);
            } catch (Exception ignored) {
                // Skip malformed entries
            }
        }
        return data;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        for (Binding binding : bindings.values()) {
            NbtCompound tag = new NbtCompound();
            tag.putString("dim", binding.dimension);
            tag.putLong("pos", binding.pos.asLong());
            tag.putString("key", binding.keyId.toString());
            tag.putString("owner", binding.ownerId.toString());
            tag.putInt("mode", binding.mode.id);
            tag.putString("gates", BlockPositions.encodeList(binding.gates));
            list.add(tag);
        }
        nbt.put("bindings", list);
        return nbt;
    }

    private static String key(String dimension, BlockPos pos) {
        return dimension + ":" + pos.asLong();
    }
}
