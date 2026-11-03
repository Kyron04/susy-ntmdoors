package com.ntmdoors.tile;

import com.ntmdoors.block.BlockMotionSensor;
import com.ntmdoors.item.ItemCreativeKeycard;
import com.ntmdoors.item.ItemMotionRelayKey;
import com.ntmdoors.item.ItemRfidCard;
import com.ntmdoors.util.Biometrics;
import com.ntmdoors.util.HeldItems;
import com.ntmdoors.util.PrivateKeys;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Motion Sensor. It passively scans the players inside its bound area and checks the cards in
 * their inventories against the private keys and biometric identities assigned here.
 *
 * <p>The selectable volume is a 5x5x5 cube centred on the sensor. The active area is a sub-box of
 * that cube, selected with the Motion Relay Key or the Bound Area page, and must contain the
 * sensor. An unbound sensor stays inactive and shows the yellow light.</p>
 */
public class TileEntityMotionSensor extends TileEntitySynced implements ITickable {

    /** Four scans per second is enough for a light and keeps the inventory checks cheap. */
    private static final int SCAN_INTERVAL = 10;
    /** The selectable volume extends two blocks in every direction from the sensor. */
    public static final int MAX_OFFSET = 2;
    /** A binding session the player walks away from expires after a minute. */
    private static final int AREA_BINDING_TICKS = 20 * 60;

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

    private boolean matchHolder = true;
    private boolean ignoreKeys;
    private boolean ignoreIds;
    private boolean enabled = true;

    /** Active area offsets from the sensor block. Only meaningful while {@link #areaBound}. */
    private boolean areaBound;
    private int minX = -MAX_OFFSET;
    private int maxX = MAX_OFFSET;
    private int minY = -MAX_OFFSET;
    private int maxY = MAX_OFFSET;
    private int minZ = -MAX_OFFSET;
    private int maxZ = MAX_OFFSET;

    /** Server-only area-binding session. */
    @javax.annotation.Nullable
    private UUID areaBindingPlayer;
    @javax.annotation.Nullable
    private BlockPos areaCornerA;
    private int areaBindingTicks;

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
        return true;
    }

    /** The console is opened by the Motion Relay Key; the Creative Keycard remains the debug key. */
    public boolean hasConsoleAccess(EntityPlayer player) {
        return player != null && (HeldItems.holds(player, ItemMotionRelayKey.class)
                || HeldItems.holds(player, ItemCreativeKeycard.class));
    }

    // ------------------------------------------------------------ bound area

    public boolean hasArea() {
        return areaBound;
    }

    public int getAreaMinX() {
        return minX;
    }

    public int getAreaMaxX() {
        return maxX;
    }

    public int getAreaMinY() {
        return minY;
    }

    public int getAreaMaxY() {
        return maxY;
    }

    public int getAreaMinZ() {
        return minZ;
    }

    public int getAreaMaxZ() {
        return maxZ;
    }

    /** The complete selectable 5x5x5 cube, used for the selection preview. */
    public AxisAlignedBB getMaxBox() {
        return new AxisAlignedBB(pos.getX() - MAX_OFFSET, pos.getY() - MAX_OFFSET,
                pos.getZ() - MAX_OFFSET, pos.getX() + MAX_OFFSET + 1,
                pos.getY() + MAX_OFFSET + 1, pos.getZ() + MAX_OFFSET + 1);
    }

    /** The active area, or null while no area has been bound. */
    @javax.annotation.Nullable
    public AxisAlignedBB getActiveBox() {
        if (!areaBound) return null;
        return new AxisAlignedBB(pos.getX() + minX, pos.getY() + minY, pos.getZ() + minZ,
                pos.getX() + maxX + 1, pos.getY() + maxY + 1, pos.getZ() + maxZ + 1);
    }

    /** Server side: binds a sub-box. Offsets are relative to the sensor and must contain it. */
    public boolean setArea(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        if (world == null || world.isRemote) return false;
        if (!validArea(minX, maxX, minY, maxY, minZ, maxZ)) return false;

        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
        this.minZ = minZ;
        this.maxZ = maxZ;
        areaBound = true;
        sync();
        if (enabled) setStatus(BlockMotionSensor.Status.IDLE);
        return true;
    }

    /** Server side: returns the sensor to the unbound, inactive state. */
    public boolean clearArea() {
        if (world == null || world.isRemote || !areaBound) return false;
        areaBound = false;
        resetAreaFields();
        playersInside.clear();
        setStatus(BlockMotionSensor.Status.MAINTENANCE);
        sync();
        return true;
    }

    /** The editor starts from the full 5x5x5 template whenever no area is bound. */
    private void resetAreaFields() {
        minX = -MAX_OFFSET;
        maxX = MAX_OFFSET;
        minY = -MAX_OFFSET;
        maxY = MAX_OFFSET;
        minZ = -MAX_OFFSET;
        maxZ = MAX_OFFSET;
    }

    /** Offsets are inclusive and the sensor (0,0,0) must stay inside the box. */
    private static boolean validArea(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        if (minX < -MAX_OFFSET || maxX > MAX_OFFSET
                || minY < -MAX_OFFSET || maxY > MAX_OFFSET
                || minZ < -MAX_OFFSET || maxZ > MAX_OFFSET) {
            return false;
        }
        if (minX > maxX || minY > maxY || minZ > maxZ) return false;
        return minX <= 0 && maxX >= 0
                && minY <= 0 && maxY >= 0
                && minZ <= 0 && maxZ >= 0;
    }

    // ------------------------------------------------------------ area binding

    public boolean isAreaBindingFor(EntityPlayer player) {
        return player != null && areaBindingPlayer != null
                && areaBindingPlayer.equals(player.getUniqueID());
    }

    /** Server side: starts a two-corner selection session for this player. */
    public boolean startAreaBinding(EntityPlayer player) {
        if (world == null || world.isRemote || player == null) return false;
        if (areaBindingPlayer != null && !areaBindingPlayer.equals(player.getUniqueID())) {
            msg(player, "ntmdoors.msg.motion_area_busy");
            return false;
        }

        cancelOtherAreaBindings(world, player, this);
        areaBindingPlayer = player.getUniqueID();
        areaCornerA = null;
        areaBindingTicks = AREA_BINDING_TICKS;
        showAreaOutline(true);
        msg(player, "ntmdoors.msg.motion_area_started");
        return true;
    }

    /** Server side: cancels the current session for this player. */
    public boolean cancelAreaBinding(EntityPlayer player) {
        if (!isAreaBindingFor(player)) return false;
        stopAreaBinding();
        msg(player, "ntmdoors.msg.motion_area_cancelled");
        return true;
    }

    /**
     * Server side: records one corner. The first click arms the session; the second binds the box.
     * Invalid corners are refused without losing the first one.
     */
    public boolean setAreaCorner(EntityPlayer player, BlockPos clicked) {
        if (world == null || world.isRemote || player == null || clicked == null) return false;
        if (!isAreaBindingFor(player)) return false;

        int ox = clicked.getX() - pos.getX();
        int oy = clicked.getY() - pos.getY();
        int oz = clicked.getZ() - pos.getZ();
        if (Math.abs(ox) > MAX_OFFSET || Math.abs(oy) > MAX_OFFSET || Math.abs(oz) > MAX_OFFSET) {
            msg(player, "ntmdoors.msg.motion_area_invalid");
            return false;
        }

        if (areaCornerA == null) {
            areaCornerA = clicked.toImmutable();
            areaBindingTicks = AREA_BINDING_TICKS;
            showAreaOutline(true);
            msg(player, "ntmdoors.msg.motion_area_first");
            return true;
        }

        int newMinX = Math.min(areaCornerA.getX(), clicked.getX()) - pos.getX();
        int newMaxX = Math.max(areaCornerA.getX(), clicked.getX()) - pos.getX();
        int newMinY = Math.min(areaCornerA.getY(), clicked.getY()) - pos.getY();
        int newMaxY = Math.max(areaCornerA.getY(), clicked.getY()) - pos.getY();
        int newMinZ = Math.min(areaCornerA.getZ(), clicked.getZ()) - pos.getZ();
        int newMaxZ = Math.max(areaCornerA.getZ(), clicked.getZ()) - pos.getZ();

        if (!setArea(newMinX, newMaxX, newMinY, newMaxY, newMinZ, newMaxZ)) {
            msg(player, "ntmdoors.msg.motion_area_invalid");
            return false;
        }

        stopAreaBinding();
        showAreaOutline(false);
        msg(player, "ntmdoors.msg.motion_area_bound",
                newMaxX - newMinX + 1, newMaxY - newMinY + 1, newMaxZ - newMinZ + 1);
        return true;
    }

    /** The sensor whose area-binding session belongs to this player, or null. */
    @javax.annotation.Nullable
    public static TileEntityMotionSensor findAreaBinding(net.minecraft.world.World world, EntityPlayer player) {
        if (world == null || player == null) return null;
        for (TileEntity te : world.loadedTileEntityList) {
            if (te instanceof TileEntityMotionSensor
                    && ((TileEntityMotionSensor) te).isAreaBindingFor(player)) {
                return (TileEntityMotionSensor) te;
            }
        }
        return null;
    }

    private void stopAreaBinding() {
        areaBindingPlayer = null;
        areaCornerA = null;
        areaBindingTicks = 0;
    }

    private static void cancelOtherAreaBindings(net.minecraft.world.World world, EntityPlayer player,
                                                TileEntity except) {
        for (TileEntity te : world.loadedTileEntityList) {
            if (te == except || !(te instanceof TileEntityMotionSensor)) continue;
            TileEntityMotionSensor sensor = (TileEntityMotionSensor) te;
            if (sensor.isAreaBindingFor(player)) sensor.stopAreaBinding();
        }
    }

    /** Draws the selectable cube or the active area with short-lived red dust particles. */
    public void showAreaOutline(boolean max) {
        if (world == null || world.isRemote) return;
        AxisAlignedBB box = max ? getMaxBox() : getActiveBox();
        if (box == null) return;

        for (double x = box.minX; x <= box.maxX; x += 0.5D) {
            particle(x, box.minY, box.minZ);
            particle(x, box.minY, box.maxZ);
            particle(x, box.maxY, box.minZ);
            particle(x, box.maxY, box.maxZ);
        }
        for (double y = box.minY; y <= box.maxY; y += 0.5D) {
            particle(box.minX, y, box.minZ);
            particle(box.minX, y, box.maxZ);
            particle(box.maxX, y, box.minZ);
            particle(box.maxX, y, box.maxZ);
        }
        for (double z = box.minZ; z <= box.maxZ; z += 0.5D) {
            particle(box.minX, box.minY, z);
            particle(box.maxX, box.minY, z);
            particle(box.minX, box.maxY, z);
            particle(box.maxX, box.maxY, z);
        }
    }

    private void particle(double x, double y, double z) {
        world.spawnParticle(EnumParticleTypes.END_ROD, x, y, z, 0, 0, 0);
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

        if (areaBindingPlayer != null) {
            EntityPlayer player = world.getPlayerEntityByUUID(areaBindingPlayer);
            if (player == null || --areaBindingTicks <= 0) stopAreaBinding();
        }

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

        AxisAlignedBB box = getActiveBox();
        if (box == null) {
            setStatus(BlockMotionSensor.Status.MAINTENANCE);
            return;
        }

        Set<UUID> current = new HashSet<>();
        boolean authorized = false;
        boolean unauthorized = false;

        for (EntityPlayer player : world.playerEntities) {
            if (player == null || !player.isEntityAlive() || player.isSpectator()) continue;
            Vec3d centre = new Vec3d(player.posX, player.posY + player.height * 0.5D, player.posZ);
            if (!box.contains(centre)) continue;

            current.add(player.getUniqueID());
            if (hasClearance(player)) authorized = true;
            else unauthorized = true;
        }

        playersInside.clear();
        playersInside.addAll(current);

        // An intruder takes priority over an authorized player standing in the same volume.
        if (unauthorized) setStatus(BlockMotionSensor.Status.MOTION);
        else if (authorized) setStatus(BlockMotionSensor.Status.AUTHORIZED);
        else setStatus(BlockMotionSensor.Status.IDLE);
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

    private void writeArea(NBTTagCompound tag) {
        tag.setBoolean("areaBound", areaBound);
        tag.setInteger("areaMinX", minX);
        tag.setInteger("areaMaxX", maxX);
        tag.setInteger("areaMinY", minY);
        tag.setInteger("areaMaxY", maxY);
        tag.setInteger("areaMinZ", minZ);
        tag.setInteger("areaMaxZ", maxZ);
    }

    private void readArea(NBTTagCompound tag) {
        areaBound = tag.getBoolean("areaBound");
        resetAreaFields();
        if (tag.hasKey("areaMinX")) minX = tag.getInteger("areaMinX");
        if (tag.hasKey("areaMaxX")) maxX = tag.getInteger("areaMaxX");
        if (tag.hasKey("areaMinY")) minY = tag.getInteger("areaMinY");
        if (tag.hasKey("areaMaxY")) maxY = tag.getInteger("areaMaxY");
        if (tag.hasKey("areaMinZ")) minZ = tag.getInteger("areaMinZ");
        if (tag.hasKey("areaMaxZ")) maxZ = tag.getInteger("areaMaxZ");
        if (!validArea(minX, maxX, minY, maxY, minZ, maxZ)) {
            areaBound = false;
            resetAreaFields();
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

    private static void msg(EntityPlayer player, String key, Object... args) {
        player.sendStatusMessage(new TextComponentTranslation(key, args), true);
    }
}
