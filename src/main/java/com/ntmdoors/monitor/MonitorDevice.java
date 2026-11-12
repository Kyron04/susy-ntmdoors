package com.ntmdoors.monitor;

import com.ntmdoors.block.BlockMonitorDesk;
import com.ntmdoors.block.BlockMonitorSetup;
import com.ntmdoors.block.door.BlockDoorGeneric;
import com.ntmdoors.block.entity.DoorBlockEntity;
import com.ntmdoors.block.entity.KeycardScannerBlockEntity;
import com.ntmdoors.block.entity.KeypadBlockEntity;
import com.ntmdoors.block.entity.MotionSensorBlockEntity;
import com.ntmdoors.block.entity.RedstoneGateBlockEntity;
import com.ntmdoors.block.entity.SpeakerBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One device that a Security Terminal can list. Doors are stored at their core cell; every other
 * device is stored at the block that owns its block entity.
 */
public final class MonitorDevice {

    /** The six device families the Security screen knows about. */
    public enum Type {
        DOOR("Door"),
        KEYPAD("Keypad"),
        SCANNER("Keycard Scanner"),
        MOTION_SENSOR("Motion Sensor"),
        REDSTONE_GATE("Redstone Gate"),
        SPEAKER("Speaker");

        private static final Type[] VALUES = values();
        private final String displayName;

        Type(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }

        public static Type byId(int id) {
            return VALUES[Math.floorMod(id, VALUES.length)];
        }

        @Nullable
        public static Type byName(String name) {
            if (name == null) return null;
            try {
                return valueOf(name.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
    }

    public final Type type;
    public final BlockPos pos;
    public final String name;
    public final boolean enabled;
    public final boolean loaded;

    public MonitorDevice(Type type, BlockPos pos, @Nullable String name) {
        this(type, pos, name, true, true);
    }

    public MonitorDevice(Type type, BlockPos pos, @Nullable String name,
                         boolean enabled, boolean loaded) {
        this.type = type;
        this.pos = pos.toImmutable();
        this.name = name == null || name.isEmpty() ? type.displayName() : name;
        this.enabled = enabled;
        this.loaded = loaded;
    }

    public MonitorDevice withName(String name) {
        return new MonitorDevice(type, pos, name, enabled, loaded);
    }

    public MonitorDevice withState(boolean enabled, boolean loaded) {
        return new MonitorDevice(type, pos, name, enabled, loaded);
    }

    public boolean matches(MonitorDevice other) {
        return other != null && type == other.type && pos.equals(other.pos);
    }

    public NbtCompound toNbt() {
        NbtCompound tag = new NbtCompound();
        tag.putString("Type", type.name());
        tag.putLong("Pos", pos.asLong());
        tag.putString("Name", name);
        return tag;
    }

    @Nullable
    public static MonitorDevice fromNbt(NbtCompound tag) {
        Type type = Type.byName(tag.getString("Type"));
        if (type == null || !tag.contains("Pos")) return null;
        BlockPos pos = BlockPos.fromLong(tag.getLong("Pos"));
        return new MonitorDevice(type, pos, tag.getString("Name"));
    }

    public static NbtList toNbtList(List<MonitorDevice> devices) {
        NbtList list = new NbtList();
        if (devices != null) {
            for (MonitorDevice device : devices) list.add(device.toNbt());
        }
        return list;
    }

    public static List<MonitorDevice> fromNbtList(NbtList list) {
        List<MonitorDevice> devices = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            MonitorDevice device = fromNbt(list.getCompound(i));
            if (device != null) devices.add(device);
        }
        return devices;
    }

    /**
     * Resolves the block the player clicked to a bindable device. Door parts resolve to the door
     * core so one door is never listed several times.
     */
    @Nullable
    public static MonitorDevice find(World world, BlockPos clicked) {
        if (world == null || clicked == null) return null;

        BlockState state = world.getBlockState(clicked);
        Block block = state.getBlock();
        if (block instanceof BlockDoorGeneric doorBlock) {
            DoorBlockEntity core = doorBlock.getCore(world, clicked, state);
            return core == null ? null : new MonitorDevice(Type.DOOR, core.getPos(), null);
        }

        BlockEntity be = world.getBlockEntity(clicked);
        if (be instanceof KeypadBlockEntity) {
            return new MonitorDevice(Type.KEYPAD, clicked, null);
        }
        if (be instanceof KeycardScannerBlockEntity) {
            return new MonitorDevice(Type.SCANNER, clicked, null);
        }
        if (be instanceof MotionSensorBlockEntity) {
            return new MonitorDevice(Type.MOTION_SENSOR, clicked, null);
        }
        if (be instanceof RedstoneGateBlockEntity) {
            return new MonitorDevice(Type.REDSTONE_GATE, clicked, null);
        }
        if (be instanceof SpeakerBlockEntity) {
            return new MonitorDevice(Type.SPEAKER, clicked, null);
        }
        return null;
    }

    /** The bottom core of a monitor desk, or null when the block is not part of one. */
    @Nullable
    public static BlockPos deskCore(World world, BlockPos clicked) {
        if (world == null || clicked == null) return null;

        BlockState state = world.getBlockState(clicked);
        Block block = state.getBlock();
        if (block instanceof BlockMonitorSetup setup) {
            return clicked.subtract(setup.offset(
                    state.get(BlockMonitorSetup.COLUMN),
                    state.get(BlockMonitorSetup.FACING)));
        }
        if (block instanceof BlockMonitorDesk deskBlock) {
            return clicked.subtract(deskBlock.desk().offset(
                    deskBlock,
                    state.get(deskBlock.columnProperty()),
                    state.get(BlockMonitorDesk.FACING)));
        }
        return null;
    }

    /** Doors use their Open/Close buttons; the other five families have an enabled state. */
    public static boolean supportsToggle(MonitorDevice device) {
        return device != null && device.type != Type.DOOR;
    }

    /** Reads the live enabled state from the device block entity. Doors always report enabled. */
    public static boolean isEnabled(World world, MonitorDevice device) {
        if (device == null || device.type == Type.DOOR || world == null) return true;
        BlockEntity be = world.getBlockEntity(device.pos);
        if (be instanceof KeypadBlockEntity keypad) {
            return keypad.isEnabled();
        }
        if (be instanceof KeycardScannerBlockEntity scanner) {
            return scanner.isEnabled();
        }
        if (be instanceof MotionSensorBlockEntity sensor) {
            return sensor.isEnabled();
        }
        if (be instanceof RedstoneGateBlockEntity gate) {
            return gate.isEnabled();
        }
        if (be instanceof SpeakerBlockEntity speaker) {
            return speaker.isEnabled();
        }
        return true;
    }

    /** Server side: changes the live enabled state of one linked device. */
    public static boolean setEnabled(World world, MonitorDevice device, boolean enabled) {
        if (device == null || device.type == Type.DOOR || world == null || world.isClient()) {
            return false;
        }
        BlockEntity be = world.getBlockEntity(device.pos);
        if (be instanceof KeypadBlockEntity keypad) {
            keypad.setEnabled(enabled);
            return true;
        }
        if (be instanceof KeycardScannerBlockEntity scanner) {
            scanner.setEnabled(enabled);
            return true;
        }
        if (be instanceof MotionSensorBlockEntity sensor) {
            sensor.setEnabled(enabled);
            return true;
        }
        if (be instanceof RedstoneGateBlockEntity gate) {
            gate.setEnabled(enabled);
            return true;
        }
        if (be instanceof SpeakerBlockEntity speaker) {
            speaker.setEnabled(enabled);
            return true;
        }
        return false;
    }

    /**
     * True when the device still exists. Unloaded chunks are treated as valid so a device in a
     * remote part of the base does not disappear from the list just because the player walked away.
     */
    public static boolean stillValid(World world, MonitorDevice device) {
        if (device == null) return false;
        if (!world.isChunkLoaded(device.pos)) return true;
        MonitorDevice current = find(world, device.pos);
        return current != null && current.type == device.type && current.pos.equals(device.pos);
    }
}
