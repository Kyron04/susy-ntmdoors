package com.ntmdoors.weapons;

import com.ntmdoors.util.BlockPositions;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-save registry of explosives bound by a Weapons Relay Key. Stored world-wide (keyed by
 * dimension + position) so it works for any explosive block, including blocks without a tile
 * entity such as vanilla TNT.
 */
public class WeaponsBindings extends WorldSavedData {

    public static final String DATA_NAME = "ntmdoors_weapons_bindings";

    /** One bound explosive and the gates it is linked to. */
    public static final class Binding {
        public final int dimension;
        public final BlockPos pos;
        public final UUID keyId;
        public final UUID ownerId;
        public final WeaponsMode mode;
        public final Set<BlockPos> gates = new LinkedHashSet<>();

        Binding(int dimension, BlockPos pos, UUID keyId, UUID ownerId, WeaponsMode mode) {
            this.dimension = dimension;
            this.pos = pos;
            this.keyId = keyId;
            this.ownerId = ownerId;
            this.mode = mode;
        }
    }

    private final Map<String, Binding> bindings = new LinkedHashMap<>();

    public WeaponsBindings() {
        super(DATA_NAME);
    }

    public WeaponsBindings(String name) {
        super(name);
    }

    // ------------------------------------------------------------ lookup

    public static WeaponsBindings get(World world) {
        if (world == null || world.isRemote) return null;
        World overworld = world.getMinecraftServer() == null
                ? world : world.getMinecraftServer().getWorld(0);
        MapStorage storage = overworld.getMapStorage();
        WeaponsBindings data = (WeaponsBindings) storage.getOrLoadData(
                WeaponsBindings.class, DATA_NAME);
        if (data == null) {
            data = new WeaponsBindings();
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    /** The live binding at a position, or null. Stale records are pruned on access. */
    public static Binding find(World world, BlockPos pos) {
        WeaponsBindings data = get(world);
        if (data == null || pos == null) return null;
        String key = key(world.provider.getDimension(), pos);
        Binding binding = data.bindings.get(key);
        if (binding != null && !Explosives.isExplosive(world, pos)) {
            data.bindings.remove(key);
            data.markDirty();
            return null;
        }
        return binding;
    }

    public static Binding bind(World world, BlockPos pos, UUID keyId, UUID ownerId,
                               WeaponsMode mode) {
        WeaponsBindings data = get(world);
        if (data == null || pos == null || keyId == null) return null;
        Binding binding = new Binding(world.provider.getDimension(), pos.toImmutable(),
                keyId, ownerId, mode);
        data.bindings.put(key(binding.dimension, binding.pos), binding);
        data.markDirty();
        return binding;
    }

    public static void unbind(World world, BlockPos pos) {
        WeaponsBindings data = get(world);
        if (data == null || pos == null) return;
        if (data.bindings.remove(key(world.provider.getDimension(), pos)) != null) {
            data.markDirty();
        }
    }

    /** Adds or removes one gate link; returns true when it is now linked. */
    public static boolean toggleGate(World world, BlockPos explosive, BlockPos gate) {
        WeaponsBindings data = get(world);
        if (data == null || explosive == null || gate == null) return false;
        Binding binding = data.bindings.get(key(world.provider.getDimension(), explosive));
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
        int dimension = world.provider.getDimension();
        List<Binding> triggered = new ArrayList<>();
        for (Binding binding : data.bindings.values()) {
            if (binding.dimension == dimension
                    && binding.mode == WeaponsMode.REGULAR
                    && binding.gates.contains(gate)) {
                triggered.add(binding);
            }
        }
        for (Binding binding : triggered) {
            if (!world.isBlockLoaded(binding.pos)) {
                // Keep the binding for a later pulse instead of force-loading the chunk.
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
        if (world.isBlockLoaded(binding.pos) && Explosives.isExplosive(world, binding.pos)) {
            Explosives.trigger(world, binding.pos);
        }
    }

    /** All bindings of the requested mode within {@code radius} blocks (Chebyshev distance). */
    public static List<Binding> findNear(World world, BlockPos pos, int radius,
                                         WeaponsMode mode) {
        List<Binding> out = new ArrayList<>();
        WeaponsBindings data = get(world);
        if (data == null || pos == null) return out;
        int dimension = world.provider.getDimension();
        for (Binding binding : data.bindings.values()) {
            if (binding.dimension != dimension) continue;
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
        int dimension = world.provider.getDimension();
        boolean changed = false;
        for (Binding binding : data.bindings.values()) {
            if (binding.dimension == dimension && binding.gates.remove(gate)) {
                changed = true;
            }
        }
        if (changed) data.markDirty();
    }

    // ------------------------------------------------------------ persistence

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        bindings.clear();
        NBTTagList list = nbt.getTagList("bindings", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            try {
                Binding binding = new Binding(tag.getInteger("dim"),
                        BlockPos.fromLong(tag.getLong("pos")),
                        UUID.fromString(tag.getString("key")),
                        UUID.fromString(tag.getString("owner")),
                        WeaponsMode.byId(tag.getInteger("mode")));
                binding.gates.addAll(BlockPositions.decodeList(tag.getString("gates")));
                bindings.put(key(binding.dimension, binding.pos), binding);
            } catch (IllegalArgumentException ignored) {
                // Skip malformed entries instead of losing the whole save.
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for (Binding binding : bindings.values()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("dim", binding.dimension);
            tag.setLong("pos", binding.pos.toLong());
            tag.setString("key", binding.keyId.toString());
            tag.setString("owner", binding.ownerId.toString());
            tag.setInteger("mode", binding.mode.id);
            tag.setString("gates", BlockPositions.encodeList(binding.gates));
            list.appendTag(tag);
        }
        nbt.setTag("bindings", list);
        return nbt;
    }

    private static String key(int dimension, BlockPos pos) {
        return dimension + ":" + pos.toLong();
    }
}
