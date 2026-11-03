package com.ntmdoors.tile;

import com.ntmdoors.block.BlockMotionSensor;
import com.ntmdoors.item.ItemCreativeKeycard;
import com.ntmdoors.item.ItemMotionRelayKey;
import com.ntmdoors.item.ItemRfidCard;
import com.ntmdoors.util.Biometrics;
import com.ntmdoors.util.HeldItems;
import com.ntmdoors.util.PrivateKeys;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Motion Sensor. It passively scans players inside the selected cells and checks the cards in
 * their inventories against the private keys and biometric identities assigned here.
 *
 * <p>The editable volume is a 5x5 footprint projecting five blocks away from the mounting surface
 * along the sensor's facing. Each footprint cell stores a depth (0-5): the number of selected
 * blocks from the surface outward. The centre cell is the sensor itself and always keeps at least
 * depth one when an area is bound.</p>
 */
public class TileEntityMotionSensor extends TileEntitySynced implements ITickable, RedstoneBindingHost {

    /** Four scans per second is enough for a light and keeps the inventory checks cheap. */
    private static final int SCAN_INTERVAL = 10;
    public static final int GRID_SIZE = 5;
    public static final int MAX_DEPTH = 5;
    public static final int CENTER = 2;

    public static final int RS_AUTHORIZED = 0;
    public static final int RS_UNAUTHORIZED = 1;
    public static final int RS_DISABLED = 2;
    public static final int RS_ENABLED = 3;
    public static final int RS_EVENT_COUNT = 4;

    /** One enrolled identity: the UUID stored on a card plus the cached name to display. */
    public static final class BiometricId {

        public final String uuid;
        public final String name;

        public BiometricId(String uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }
    }

    private final List<PrivateKeys.Key> assignedKeys = new ArrayList<>();
    private final List<BiometricId> assignedIds = new ArrayList<>();
    /** Server-only set of players currently inside the detection volume. */
    private final Set<UUID> playersInside = new HashSet<>();
    private final boolean[] redstoneEvents = new boolean[RS_EVENT_COUNT];
    private final RedstoneBinding redstoneBinding = new RedstoneBinding(this);

    private boolean matchHolder = true;
    private boolean ignoreKeys;
    private boolean ignoreIds;
    private boolean enabled = true;

    /** One depth 0-5 per grid cell, indexed u + v * 5. */
    private final byte[] areaDepths = new byte[GRID_SIZE * GRID_SIZE];
    private boolean areaBound;
    /** Cached culling box for the fixed 5x5x5 projection. */
    @javax.annotation.Nullable
    private AxisAlignedBB projectionBounds;
    @javax.annotation.Nullable
    private EnumFacing projectionFacing;

    private int scanTicks;
    private BlockMotionSensor.Status status = BlockMotionSensor.Status.IDLE;

    // ------------------------------------------------------------ state / options

    public BlockMotionSensor.Status getStatus() {
        return status;
    }

    /** Server side: changes the light and syncs the new actual state to nearby clients. */
    public boolean setStatus(BlockMotionSensor.Status status) {
        if (status == null || this.status == status) return false;
        this.status = status;
        sync();
        return true;
    }

    /** Creative preview helper; the real detection loop overwrites it on the next scan. */
    public boolean cycleStatus() {
        BlockMotionSensor.Status[] values = BlockMotionSensor.Status.values();
        return setStatus(values[(status.ordinal() + 1) % values.length]);
    }

    public boolean isMatchHolder() {
        return matchHolder;
    }

    public boolean setMatchHolder(boolean value) {
        if (world == null || world.isRemote || matchHolder == value) return false;
        matchHolder = value;
        sync();
        return true;
    }

    public boolean isIgnoreKeys() {
        return ignoreKeys;
    }

    public boolean setIgnoreKeys(boolean value) {
        if (world == null || world.isRemote || ignoreKeys == value) return false;
        ignoreKeys = value;
        sync();
        return true;
    }

    public boolean isIgnoreIds() {
        return ignoreIds;
    }

    public boolean setIgnoreIds(boolean value) {
        if (world == null || world.isRemote || ignoreIds == value) return false;
        ignoreIds = value;
        sync();
        return true;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Server side: disables detection and shows the yellow maintenance light. */
    public boolean setEnabled(boolean value) {
        if (world == null || world.isRemote || enabled == value) return false;
        enabled = value;
        if (!value) {
            playersInside.clear();
            setStatus(BlockMotionSensor.Status.MAINTENANCE);
        } else {
            setStatus(areaBound ? BlockMotionSensor.Status.IDLE : BlockMotionSensor.Status.MAINTENANCE);
        }
        sync();
        fireRedstoneEvent(value ? RS_ENABLED : RS_DISABLED);
        return true;
    }

    /** The console is opened by the Motion Relay Key; the Creative Keycard remains the debug key. */
    public boolean hasConsoleAccess(EntityPlayer player) {
        return player != null && (HeldItems.holds(player, ItemMotionRelayKey.class)
                || HeldItems.holds(player, ItemCreativeKeycard.class));
    }

    // ------------------------------------------------------------ redstone events

    public boolean isRedstoneEvent(int event) {
        return event >= 0 && event < RS_EVENT_COUNT && redstoneEvents[event];
    }

    /** Server side: arms or disarms one event output. */
    public boolean setRedstoneEvent(int event, boolean value) {
        if (world == null || world.isRemote || event < 0 || event >= RS_EVENT_COUNT
                || redstoneEvents[event] == value) {
            return false;
        }
        redstoneEvents[event] = value;
        sync();
        return true;
    }

    /** Server side: pulses every linked gate when this event is enabled. */
    public void fireRedstoneEvent(int event) {
        if (isRedstoneEvent(event)) redstoneBinding.fire();
    }

    @Override
    public RedstoneBinding getRedstoneBinding() {
        return redstoneBinding;
    }

    @Override
    public void syncRedstoneState() {
        sync();
    }

    @Override
    public void onRedstoneBindingChanged() {
        sync();
    }

    @Override
    public void sendRedstoneMessage(EntityPlayer player, String key) {
        msg(player, key);
    }

    // ------------------------------------------------------------ bound area

    public boolean hasArea() {
        return areaBound;
    }

    /** Depth for one footprint cell, 0-5. */
    public int getAreaDepth(int u, int v) {
        if (u < 0 || u >= GRID_SIZE || v < 0 || v >= GRID_SIZE) return 0;
        return areaDepths[u + v * GRID_SIZE] & 0xFF;
    }

    /** Server side: replaces the footprint depths. The centre cell must keep the sensor included. */
    public boolean setAreaDepths(byte[] depths) {
        if (world == null || world.isRemote || depths == null || depths.length != areaDepths.length) {
            return false;
        }
        for (byte depth : depths) {
            if (depth < 0 || depth > MAX_DEPTH) return false;
        }
        int centre = depths[CENTER + CENTER * GRID_SIZE];
        if (centre < 1) return false;

        System.arraycopy(depths, 0, areaDepths, 0, areaDepths.length);
        areaBound = true;
        sync();
        if (enabled) setStatus(BlockMotionSensor.Status.IDLE);
        return true;
    }

    /** Server side: returns the sensor to the unbound, inactive state. */
    public boolean clearArea() {
        if (world == null || world.isRemote || !areaBound) return false;
        Arrays.fill(areaDepths, (byte) 0);
        areaBound = false;
        playersInside.clear();
        setStatus(BlockMotionSensor.Status.MAINTENANCE);
        sync();
        return true;
    }

    /** The world cells selected by the current footprint, in sensor-local order. */
    public List<BlockPos> getSelectedCells() {
        if (!areaBound) return Collections.emptyList();

        List<BlockPos> cells = new ArrayList<>();
        for (int v = 0; v < GRID_SIZE; v++) {
            for (int u = 0; u < GRID_SIZE; u++) {
                int depth = getAreaDepth(u, v);
                for (int d = 0; d < depth; d++) cells.add(cellAt(u, v, d));
            }
        }
        return cells;
    }

    /** The complete 5x5x5 projection, used by the renderer's culling box. */
    public AxisAlignedBB getProjectionBounds() {
        EnumFacing facing = facing();
        if (projectionBounds != null && projectionFacing == facing) return projectionBounds;

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int d = 0; d < MAX_DEPTH; d++) {
            for (int v = 0; v < GRID_SIZE; v++) {
                for (int u = 0; u < GRID_SIZE; u++) {
                    BlockPos cell = cellAt(u, v, d);
                    minX = Math.min(minX, cell.getX());
                    minY = Math.min(minY, cell.getY());
                    minZ = Math.min(minZ, cell.getZ());
                    maxX = Math.max(maxX, cell.getX());
                    maxY = Math.max(maxY, cell.getY());
                    maxZ = Math.max(maxZ, cell.getZ());
                }
            }
        }

        projectionFacing = facing;
        projectionBounds = new AxisAlignedBB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
        return projectionBounds;
    }

    /** Local cell (u,v,d) to the world. d=0 is the sensor's plane. */
    private BlockPos cellAt(int u, int v, int d) {
        return pos.offset(uAxis(), u - CENTER)
                .offset(vAxis(), v - CENTER)
                .offset(facing(), d);
    }

    private EnumFacing facing() {
        if (world == null) return EnumFacing.NORTH;
        IBlockState state = world.getBlockState(pos);
        return state.getBlock() instanceof BlockMotionSensor
                ? state.getValue(BlockMotionSensor.FACING) : EnumFacing.NORTH;
    }

    /** Horizontal sensors look across; vertical sensors use world east/south for the grid. */
    private EnumFacing uAxis() {
        EnumFacing facing = facing();
        return facing.getAxis().isHorizontal() ? facing.rotateY() : EnumFacing.EAST;
    }

    private EnumFacing vAxis() {
        EnumFacing facing = facing();
        return facing.getAxis().isHorizontal() ? EnumFacing.UP : EnumFacing.SOUTH;
    }

    // ------------------------------------------------------------ assignment

    public List<PrivateKeys.Key> getAssignedKeys() {
        return Collections.unmodifiableList(assignedKeys);
    }

    public List<BiometricId> getAssignedIds() {
        return Collections.unmodifiableList(assignedIds);
    }

    public boolean hasAssignedKey(String serial) {
        for (PrivateKeys.Key key : assignedKeys) {
            if (key.serial.equals(serial)) return true;
        }
        return false;
    }

    public boolean hasAssignedId(String uuid) {
        for (BiometricId id : assignedIds) {
            if (id.uuid.equals(uuid)) return true;
        }
        return false;
    }

    /** Server side: adds a key copied from the operator's card. */
    public boolean assignKey(PrivateKeys.Key key) {
        if (world == null || world.isRemote || key == null || key.serial.isEmpty()) return false;
        if (hasAssignedKey(key.serial)) return false;
        assignedKeys.add(key);
        sync();
        return true;
    }

    /** Server side: removes a key by serial. */
    public boolean unassignKey(String serial) {
        if (world == null || world.isRemote || serial == null) return false;
        for (int i = 0; i < assignedKeys.size(); i++) {
            if (assignedKeys.get(i).serial.equals(serial)) {
                assignedKeys.remove(i);
                sync();
                return true;
            }
        }
        return false;
    }

    /** Server side: adds an identity copied from the operator's card. */
    public boolean assignId(BiometricId id) {
        if (world == null || world.isRemote || id == null || id.uuid.isEmpty()) return false;
        if (hasAssignedId(id.uuid)) return false;
        assignedIds.add(id);
        sync();
        return true;
    }

    /** Server side: removes an identity by UUID. */
    public boolean unassignId(String uuid) {
        if (world == null || world.isRemote || uuid == null) return false;
        for (int i = 0; i < assignedIds.size(); i++) {
            if (assignedIds.get(i).uuid.equals(uuid)) {
                assignedIds.remove(i);
                sync();
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------ detection

    @Override
    public void update() {
        if (world == null || world.isRemote) return;
        redstoneBinding.update();
        if (++scanTicks < SCAN_INTERVAL) return;
        scanTicks = 0;
        updateDetection();
    }

    private void updateDetection() {
        if (!enabled || !areaBound) {
            playersInside.clear();
            setStatus(BlockMotionSensor.Status.MAINTENANCE);
            return;
        }

        List<AxisAlignedBB> boxes = new ArrayList<>();
        for (BlockPos cell : getSelectedCells()) {
            boxes.add(new AxisAlignedBB(cell.getX(), cell.getY(), cell.getZ(),
                    cell.getX() + 1, cell.getY() + 1, cell.getZ() + 1));
        }
        if (boxes.isEmpty()) {
            playersInside.clear();
            setStatus(BlockMotionSensor.Status.MAINTENANCE);
            return;
        }

        Set<UUID> current = new HashSet<>();
        boolean authorized = false;
        boolean unauthorized = false;
        boolean enteredAuthorized = false;
        boolean enteredUnauthorized = false;

        for (EntityPlayer player : world.playerEntities) {
            if (player == null || !player.isEntityAlive() || player.isSpectator()) continue;

            boolean inside = false;
            AxisAlignedBB playerBox = player.getEntityBoundingBox();
            for (AxisAlignedBB box : boxes) {
                if (playerBox.intersects(box)) {
                    inside = true;
                    break;
                }
            }
            if (!inside) continue;

            UUID id = player.getUniqueID();
            boolean wasInside = playersInside.contains(id);
            boolean clearance = hasClearance(player);
            current.add(id);
            if (clearance) authorized = true;
            else unauthorized = true;
            if (!wasInside) {
                if (clearance) enteredAuthorized = true;
                else enteredUnauthorized = true;
            }
        }

        playersInside.clear();
        playersInside.addAll(current);

        // An intruder takes priority over an authorized player standing in the same volume.
        if (unauthorized) setStatus(BlockMotionSensor.Status.MOTION);
        else if (authorized) setStatus(BlockMotionSensor.Status.AUTHORIZED);
        else setStatus(BlockMotionSensor.Status.IDLE);

        // Only entry edges fire; standing inside does not keep pulsing the gates.
        if (enteredAuthorized) fireRedstoneEvent(RS_AUTHORIZED);
        if (enteredUnauthorized) fireRedstoneEvent(RS_UNAUTHORIZED);
    }

    /**
     * True when any card in the player's main inventory or offhand grants access. A player with a
     * valid card and an invalid card still counts as authorized.
     */
    public boolean hasClearance(EntityPlayer player) {
        if (player == null) return false;

        for (ItemStack stack : player.inventory.mainInventory) {
            if (grantsAccess(stack, player)) return true;
        }
        return grantsAccess(player.getHeldItemOffhand(), player);
    }

    /**
     * Matches the scanner's credential rules: a biometric card is personal when holder matching is
     * on, private keys are skipped when ignored, and a valid key or enrolled identity is enough.
     */
    private boolean grantsAccess(ItemStack stack, EntityPlayer player) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() instanceof ItemCreativeKeycard) return true;
        if (!(stack.getItem() instanceof ItemRfidCard)) return false;

        // A stolen biometric card is refused as a whole, including any private keys on it.
        if (matchHolder && !ignoreIds && Biometrics.isBound(stack)
                && !Biometrics.isOwnedBy(stack, player)) {
            return false;
        }
        if (!ignoreKeys) {
            for (PrivateKeys.Key key : PrivateKeys.get(stack)) {
                if (hasAssignedKey(key.serial)) return true;
            }
        }
        if (ignoreIds || !Biometrics.isBound(stack)) return false;
        if (!hasAssignedId(Biometrics.ownerUuid(stack))) return false;
        return !matchHolder || Biometrics.isOwnedBy(stack, player);
    }

    // ------------------------------------------------------------ persistence / sync

    /** Identities are a small list of { Id, Name } compounds, so names may contain any character. */
    private NBTTagList encodeIds() {
        NBTTagList list = new NBTTagList();
        for (BiometricId id : assignedIds) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString("Id", id.uuid);
            entry.setString("Name", id.name);
            list.appendTag(entry);
        }
        return list;
    }

    private void readIds(NBTTagCompound tag) {
        if (!tag.hasKey("assignedIds", Constants.NBT.TAG_LIST)) return;
        assignedIds.clear();
        NBTTagList list = tag.getTagList("assignedIds", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            String uuid = entry.getString("Id");
            if (uuid.isEmpty()) continue;
            assignedIds.add(new BiometricId(uuid, entry.getString("Name")));
        }
    }

    private void writeOptions(NBTTagCompound tag) {
        tag.setBoolean("matchHolder", matchHolder);
        tag.setBoolean("ignoreKeys", ignoreKeys);
        tag.setBoolean("ignoreIds", ignoreIds);
        tag.setBoolean("enabled", enabled);
    }

    private void readOptions(NBTTagCompound tag) {
        // Matching and detection default on so older sensors and fresh placements start working.
        matchHolder = !tag.hasKey("matchHolder") || tag.getBoolean("matchHolder");
        ignoreKeys = tag.getBoolean("ignoreKeys");
        ignoreIds = tag.getBoolean("ignoreIds");
        enabled = !tag.hasKey("enabled") || tag.getBoolean("enabled");
    }

    private void writeEvents(NBTTagCompound tag) {
        byte[] out = new byte[RS_EVENT_COUNT];
        for (int i = 0; i < RS_EVENT_COUNT; i++) out[i] = (byte) (redstoneEvents[i] ? 1 : 0);
        tag.setByteArray("redstoneEvents", out);
    }

    private void readEvents(NBTTagCompound tag) {
        Arrays.fill(redstoneEvents, false);
        byte[] saved = tag.getByteArray("redstoneEvents");
        for (int i = 0; i < Math.min(saved.length, RS_EVENT_COUNT); i++) {
            redstoneEvents[i] = saved[i] != 0;
        }
    }

    private void writeArea(NBTTagCompound tag) {
        tag.setBoolean("areaBound", areaBound);
        tag.setByteArray("areaDepths", Arrays.copyOf(areaDepths, areaDepths.length));
    }

    private void readArea(NBTTagCompound tag) {
        areaBound = tag.getBoolean("areaBound");
        Arrays.fill(areaDepths, (byte) 0);
        byte[] saved = tag.getByteArray("areaDepths");
        if (saved.length == areaDepths.length) {
            for (int i = 0; i < saved.length; i++) {
                areaDepths[i] = (byte) Math.max(0, Math.min(MAX_DEPTH, saved[i] & 0xFF));
            }
        }
        int centre = areaDepths[CENTER + CENTER * GRID_SIZE];
        if (!areaBound || centre < 1) {
            areaBound = false;
            Arrays.fill(areaDepths, (byte) 0);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setByte("status", (byte) status.ordinal());
        tag.setTag("assignedKeys", PrivateKeys.toList(assignedKeys));
        tag.setTag("assignedIds", encodeIds());
        writeOptions(tag);
        writeArea(tag);
        redstoneBinding.writeToNBT(tag);
        writeEvents(tag);
        return tag;
    }

    @Override
    protected NBTTagCompound writeSyncTag(NBTTagCompound tag) {
        // TileEntity.readFromNBT rebuilds pos from x/y/z, so the sync tag must carry them.
        super.writeToNBT(tag);
        tag.setByte("status", (byte) status.ordinal());
        tag.setTag("assignedKeys", PrivateKeys.toList(assignedKeys));
        tag.setTag("assignedIds", encodeIds());
        writeOptions(tag);
        writeArea(tag);
        redstoneBinding.writeSyncTag(tag);
        writeEvents(tag);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        status = BlockMotionSensor.Status.values()[tag.getByte("status") & 3];

        if (tag.hasKey("assignedKeys", Constants.NBT.TAG_LIST)) {
            assignedKeys.clear();
            assignedKeys.addAll(PrivateKeys.fromList(
                    tag.getTagList("assignedKeys", Constants.NBT.TAG_COMPOUND)));
        }
        readIds(tag);
        readOptions(tag);
        readArea(tag);
        redstoneBinding.readFromNBT(tag);
        readEvents(tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        super.onDataPacket(net, packet);
        if (world != null && world.isRemote) {
            // The block state itself does not change; force the chunk mesh to ask for the new
            // actual state from this tile entity.
            world.markBlockRangeForRenderUpdate(pos, pos);
        }
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return getProjectionBounds().grow(1.0D);
    }

    @Override
    public double getMaxRenderDistanceSquared() {
        return 16384D;
    }

    private static void msg(EntityPlayer player, String key) {
        player.sendStatusMessage(new TextComponentTranslation(key), true);
    }
}
