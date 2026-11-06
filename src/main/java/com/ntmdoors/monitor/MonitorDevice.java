package com.ntmdoors.monitor;

import com.ntmdoors.block.BlockDoorGeneric;
import com.ntmdoors.block.BlockMonitorDesk;
import com.ntmdoors.block.BlockMonitorSetup;
import com.ntmdoors.tile.TileEntityDoorGeneric;
import com.ntmdoors.tile.TileEntityDoorKeypad;
import com.ntmdoors.tile.TileEntityKeycardScanner;
import com.ntmdoors.tile.TileEntityMotionSensor;
import com.ntmdoors.tile.TileEntityRedstoneGate;
import com.ntmdoors.tile.TileEntitySpeaker;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One device that a Security Terminal can list. Doors are stored at their core cell; every other
 * device is stored at the block that owns its tile entity.
 *
 * <p>The same entry shape is used by the Terminal Relay Key's pending list and by the desk's
 * confirmed list, so confirming a session is a straight copy:</p>
 *
 * <pre>
 * { Type: "DOOR", Pos: 123456789, Name: "Main Entrance" }
 * </pre>
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
    /**
     * Runtime view state sent to the terminal. It is not part of the saved net entry: the device
     * tile remains the source of truth. Unloaded devices keep the default so old NBT stays valid.
     */
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

    /** A copy carrying the live tile state for the terminal list. */
    public MonitorDevice withState(boolean enabled, boolean loaded) {
        return new MonitorDevice(type, pos, name, enabled, loaded);
    }

    public boolean matches(MonitorDevice other) {
        return other != null && type == other.type && pos.equals(other.pos);
    }

    public NBTTagCompound toNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("Type", type.name());
        tag.setLong("Pos", pos.toLong());
        tag.setString("Name", name);
        return tag;
    }

    @Nullable
    public static MonitorDevice fromNBT(NBTTagCompound tag) {
        Type type = Type.byName(tag.getString("Type"));
        if (type == null || !tag.hasKey("Pos")) return null;
        BlockPos pos = BlockPos.fromLong(tag.getLong("Pos"));
        return new MonitorDevice(type, pos, tag.getString("Name"));
    }

    public static NBTTagList toNBTList(List<MonitorDevice> devices) {
        NBTTagList list = new NBTTagList();
        for (MonitorDevice device : devices) list.appendTag(device.toNBT());
        return list;
    }

    public static List<MonitorDevice> fromNBTList(NBTTagList list) {
        List<MonitorDevice> devices = new ArrayList<>(list.tagCount());
        for (int i = 0; i < list.tagCount(); i++) {
            MonitorDevice device = fromNBT(list.getCompoundTagAt(i));
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

        IBlockState state = world.getBlockState(clicked);
        Block block = state.getBlock();
        if (block instanceof BlockDoorGeneric) {
            TileEntityDoorGeneric core = ((BlockDoorGeneric) block).getCore(world, clicked, state);
            return core == null ? null : new MonitorDevice(Type.DOOR, core.getPos(), null);
        }

        TileEntity te = world.getTileEntity(clicked);
        if (te instanceof TileEntityDoorKeypad) {
            return new MonitorDevice(Type.KEYPAD, clicked, null);
        }
        if (te instanceof TileEntityKeycardScanner) {
            return new MonitorDevice(Type.SCANNER, clicked, null);
        }
        if (te instanceof TileEntityMotionSensor) {
            return new MonitorDevice(Type.MOTION_SENSOR, clicked, null);
        }
        if (te instanceof TileEntityRedstoneGate) {
            return new MonitorDevice(Type.REDSTONE_GATE, clicked, null);
        }
        if (te instanceof TileEntitySpeaker) {
            return new MonitorDevice(Type.SPEAKER, clicked, null);
        }
        return null;
    }

    /** The bottom core of a monitor desk, or null when the block is not part of one. */
    @Nullable
    public static BlockPos deskCore(World world, BlockPos clicked) {
        if (world == null || clicked == null) return null;

        IBlockState state = world.getBlockState(clicked);
        Block block = state.getBlock();
        if (block instanceof BlockMonitorSetup) {
            BlockMonitorSetup setup = (BlockMonitorSetup) block;
            return clicked.subtract(setup.offset(
                    state.getValue(BlockMonitorSetup.COLUMN),
                    state.getValue(BlockMonitorSetup.FACING)));
        }
        if (block instanceof BlockMonitorDesk) {
            BlockMonitorDesk desk = (BlockMonitorDesk) block;
            return clicked.subtract(desk.offset(
                    state.getValue(desk.columnProperty()),
                    state.getValue(BlockMonitorDesk.FACING)));
        }
        return null;
    }

    /** Doors use their Open/Close buttons; the other five families have an enabled state. */
    public static boolean supportsToggle(MonitorDevice device) {
        return device != null && device.type != Type.DOOR;
    }

    /** Reads the live enabled state from the device tile. Doors always report enabled. */
    public static boolean isEnabled(World world, MonitorDevice device) {
        if (device == null || device.type == Type.DOOR || world == null) return true;
        TileEntity te = world.getTileEntity(device.pos);
        if (te instanceof TileEntityDoorKeypad) {
            return ((TileEntityDoorKeypad) te).isEnabled();
        }
        if (te instanceof TileEntityKeycardScanner) {
            return ((TileEntityKeycardScanner) te).isEnabled();
        }
        if (te instanceof TileEntityMotionSensor) {
            return ((TileEntityMotionSensor) te).isEnabled();
        }
        if (te instanceof TileEntityRedstoneGate) {
            return ((TileEntityRedstoneGate) te).isEnabled();
        }
        if (te instanceof TileEntitySpeaker) {
            return ((TileEntitySpeaker) te).isEnabled();
        }
        return true;
    }

    /** Server side: changes the live enabled state of one linked device. */
    public static boolean setEnabled(World world, MonitorDevice device, boolean enabled) {
        if (device == null || device.type == Type.DOOR || world == null || world.isRemote) {
            return false;
        }
        TileEntity te = world.getTileEntity(device.pos);
        if (te instanceof TileEntityDoorKeypad) {
            return ((TileEntityDoorKeypad) te).setEnabled(enabled);
        }
        if (te instanceof TileEntityKeycardScanner) {
            return ((TileEntityKeycardScanner) te).setEnabled(enabled);
        }
        if (te instanceof TileEntityMotionSensor) {
            return ((TileEntityMotionSensor) te).setEnabled(enabled);
        }
        if (te instanceof TileEntityRedstoneGate) {
            return ((TileEntityRedstoneGate) te).setEnabled(enabled);
        }
        if (te instanceof TileEntitySpeaker) {
            return ((TileEntitySpeaker) te).setEnabled(enabled);
        }
        return false;
    }

    /**
     * True when the device still exists. Unloaded chunks are treated as valid so a device in a
     * remote part of the base does not disappear from the list just because the player walked away.
     */
    public static boolean stillValid(World world, MonitorDevice device) {
        if (device == null) return false;
        if (!world.isBlockLoaded(device.pos)) return true;
        MonitorDevice current = find(world, device.pos);
        return current != null && current.type == device.type && current.pos.equals(device.pos);
    }
}
