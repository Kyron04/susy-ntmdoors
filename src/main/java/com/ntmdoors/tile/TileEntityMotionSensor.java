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
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Motion Sensor. It passively scans the players inside its 5x5x5 volume and checks the cards in
 * their inventories against the private keys and biometric identities assigned here.
 *
 * <p>Phase one uses the whole 5x5x5 around the sensor and only drives the status light. The Bound
 * Area and Redstone pages will replace the fixed volume and add the gate output later.</p>
 */
public class TileEntityMotionSensor extends TileEntitySynced implements ITickable {

    /** Four scans per second is enough for a light and keeps the inventory checks cheap. */
    private static final int SCAN_INTERVAL = 10;

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
            setStatus(BlockMotionSensor.Status.IDLE);
        }
        sync();
        return true;
    }

    /** The console is opened by the Motion Relay Key; the Creative Keycard remains the debug key. */
    public boolean hasConsoleAccess(EntityPlayer player) {
        return player != null && (HeldItems.holds(player, ItemMotionRelayKey.class)
                || HeldItems.holds(player, ItemCreativeKeycard.class));
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

    /** The full temporary 5x5x5 volume. The Bound Area page will refine this later. */
    public AxisAlignedBB getDetectionBox() {
        return new AxisAlignedBB(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1).grow(2.0D);
    }

    @Override
    public void update() {
        if (world == null || world.isRemote) return;
        if (++scanTicks < SCAN_INTERVAL) return;
        scanTicks = 0;
        updateDetection();
    }

    private void updateDetection() {
        if (!enabled) {
            playersInside.clear();
            setStatus(BlockMotionSensor.Status.MAINTENANCE);
            return;
        }

        AxisAlignedBB box = getDetectionBox();
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

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setByte("status", (byte) status.ordinal());
        tag.setTag("assignedKeys", PrivateKeys.toList(assignedKeys));
        tag.setTag("assignedIds", encodeIds());
        writeOptions(tag);
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
}
