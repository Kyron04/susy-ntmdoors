package com.ntmdoors.tile;

import com.ntmdoors.item.ItemCreativeKeycard;
import com.ntmdoors.item.ItemEmancipationRelayKey;
import com.ntmdoors.item.ItemOperatorKeycard;
import com.ntmdoors.item.ItemRfidCard;
import com.ntmdoors.util.Biometrics;
import com.ntmdoors.util.EmancipationFieldGeometry;
import com.ntmdoors.util.HeldItems;
import com.ntmdoors.util.ModSounds;
import com.ntmdoors.util.PrivateKeys;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.play.server.SPacketSoundEffect;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The bottom cell of an Emancipation Grill. It owns the pair's security state, the thin field
 * cell, and the per-tick player barrier.
 *
 * <p>The first grill placed owns the pair. The second grill adopts that owner and keeps a copy for
 * display and breaker checks, but the GUI and field checks use the pair's security core. An enabled
 * field can be Solid (blocks unauthorized players and mobs), Neutral (visible but inert) or Lethal
 * (kills unauthorized players and mobs, and erases other entities on contact). Players pass with an
 * identity on an RFID/Operator Keycard, or a Creative Keycard, subject to the console's Match User,
 * Ignore Private Key and Ignore Biometric ID toggles. The Detect Players, Detect Hostile Mobs and
 * Detect Neutral Mobs toggles decide which living entity categories the field acts on. Ownership
 * alone does not grant passage.</p>
 */
public class TileEntityEmancipationGrill extends TileEntitySynced implements ITickable, RedstoneBindingHost {

    /** How an enabled field treats a player without clearance. */
    public enum FieldMode {
        SOLID,
        NEUTRAL,
        LETHAL,
        WALL;

        public static FieldMode byId(int id) {
            FieldMode[] values = values();
            return id >= 0 && id < values.length ? values[id] : SOLID;
        }
    }

    private static final DamageSource DISINTEGRATION =
            new DamageSource("emancipation_grill").setDamageBypassesArmor().setDamageIsAbsolute();

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
    /** Players already told they are denied, so the message/sound fires once per entry. */
    private final Set<UUID> deniedInside = new HashSet<>();
    private final RedstoneBinding redstoneBinding = new RedstoneBinding(this);
    /** Gate outputs currently controlling the field, including explicit off sources. */
    private final Map<BlockPos, Boolean> linkedGates = new HashMap<>();
    /** Last tick this grill pulsed its linked gates; their own output edges are ignored briefly. */
    private long lastSignalPulseTick = -1000L;
    private long lastDisintegrateSoundTick = -1000L;

    @Nullable
    private UUID ownerId;
    private String ownerName = "";
    /** True on the first grill placed in the pair; the second grill adopts this one's owner. */
    private boolean pairOwner = true;

    // ------------------------------------------------------------ field settings
    private boolean fieldEnabled = true;
    private FieldMode fieldMode = FieldMode.SOLID;
    private boolean matchUser = true;
    private boolean ignorePrivateKey;
    private boolean ignoreBiometricId;
    private boolean ignoreInventory;
    private boolean ignoreItems;
    private boolean audioEnabled = true;
    private boolean detectPlayers = true;
    private boolean detectHostile = true;
    private boolean detectNeutral = true;

    /** Redstone event outputs, in the order shown on the grill's Redstone page. */
    public static final int RS_SOLID_PASS = 0;
    public static final int RS_SOLID_BLOCK = 1;
    public static final int RS_LETHAL_KILL = 2;
    public static final int RS_MATCH_USER_PASS = 3;
    public static final int RS_MATCH_USER_FAIL = 4;
    public static final int RS_EVENT_COUNT = 5;
    private final boolean[] redstoneEvents = new boolean[RS_EVENT_COUNT];

    // ------------------------------------------------------------ field geometry

    /**
     * The thin energy cell between this grill and its partner, or null while unpaired.
     * Both grills in a pair compute the same box.
     */
    @Nullable
    public AxisAlignedBB getFieldBox() {
        if (world == null || !isFieldEnabled()) return null;
        return EmancipationFieldGeometry.getFieldBox(world, pos);
    }

    /** The matching grill on the opposite wall, or null while unpaired. */
    @Nullable
    public BlockPos getPartner() {
        if (world == null) return null;
        return EmancipationFieldGeometry.findPartner(world, pos);
    }

    public boolean isPairOwner() {
        return pairOwner;
    }

    /** Server side: marks the first grill in a pair as the security owner. */
    public boolean setPairOwner(boolean value) {
        if (world == null || world.isRemote || pairOwner == value) return false;
        pairOwner = value;
        sync();
        return true;
    }

    /**
     * The grill that holds the pair's owner and assigned credentials. New pairs mark the first
     * grill explicitly; old pairs fall back to the lower block position.
     */
    public TileEntityEmancipationGrill getSecurityCore() {
        if (world == null) return this;
        BlockPos partnerPos = EmancipationFieldGeometry.findPartner(world, pos);
        if (partnerPos == null) return this;

        TileEntity te = world.getTileEntity(partnerPos);
        if (!(te instanceof TileEntityEmancipationGrill)) return this;
        TileEntityEmancipationGrill partner = (TileEntityEmancipationGrill) te;

        if (pairOwner && !partner.pairOwner) return this;
        if (partner.pairOwner && !pairOwner) return partner;
        return pos.toLong() <= partnerPos.toLong() ? this : partner;
    }

    // ------------------------------------------------------------ ownership

    @Nullable
    public UUID getOwnerId() {
        return ownerId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public boolean isOwnedBy(EntityPlayer player) {
        return player != null && ownerId != null && ownerId.equals(player.getUniqueID());
    }

    /** Server side: records the placer as owner if the grill has no owner yet. */
    public boolean setOwner(EntityPlayer player) {
        if (world == null || world.isRemote || player == null || ownerId != null) return false;
        ownerId = player.getUniqueID();
        ownerName = player.getName();
        sync();
        return true;
    }

    /** Server side: the second grill of a pair adopts the first grill's owner. */
    public void copyOwnerFrom(TileEntityEmancipationGrill other) {
        if (world == null || world.isRemote || other == null) return;
        ownerId = other.ownerId;
        ownerName = other.ownerName;
        sync();
    }

    /** The pair's owner can open the console with a Relay Key or Operator Keycard. */
    public boolean hasConsoleAccess(EntityPlayer player) {
        if (player == null) return false;
        TileEntityEmancipationGrill core = getSecurityCore();
        if (core != this) return core.hasConsoleAccess(player);

        if (HeldItems.holds(player, ItemCreativeKeycard.class)) return true;
        if (!HeldItems.holds(player, ItemEmancipationRelayKey.class)
                && !HeldItems.holds(player, ItemOperatorKeycard.class)) {
            return false;
        }
        return ownerId == null || isOwnedBy(player);
    }

    // ------------------------------------------------------------ redstone binding

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
        markDirty();
    }

    @Override
    public void sendRedstoneMessage(EntityPlayer player, String key) {
        if (player != null) {
            player.sendStatusMessage(new TextComponentTranslation(key), true);
        }
    }

    /** Called by a gate host when it starts a link. */
    public void onGateLinked(BlockPos source, boolean powered) {
        applyGateLink(source, powered, true);
    }

    /** Called on load: restore the saved power state without forcing a configured field off. */
    public void restoreGateLink(BlockPos source, boolean powered) {
        applyGateLink(source, powered, false);
    }

    private void applyGateLink(BlockPos source, boolean powered, boolean initialLink) {
        if (source == null || world == null || world.isRemote) return;
        TileEntityEmancipationGrill core = getSecurityCore();
        if (core != this) {
            if (initialLink) core.onGateLinked(source, powered);
            else core.restoreGateLink(source, powered);
            return;
        }
        linkedGates.put(source.toImmutable(), powered);
        // A real link seeds the field once, so the player can start configuring from the gate's
        // current state. A reload only restores the link and leaves the configured field alone.
        if (initialLink) applyLinkedGatePower(true);
    }

    /** Called by a gate host when the link is removed or the gate is broken. */
    public void onGateUnlinked(BlockPos source) {
        if (source == null || world == null || world.isRemote) return;
        TileEntityEmancipationGrill core = getSecurityCore();
        if (core != this) {
            core.onGateUnlinked(source);
            return;
        }
        linkedGates.remove(source);
        // Unlinking releases control without changing a field the player has configured.
    }

    /** Called by a linked gate whenever its output changes. */
    public void setLinkedPower(BlockPos source, boolean powered) {
        if (source == null || world == null || world.isRemote) return;
        TileEntityEmancipationGrill core = getSecurityCore();
        if (core != this) {
            core.setLinkedPower(source, powered);
            return;
        }
        BlockPos key = source.toImmutable();
        // Track the gate state, but do not let a signal pulse this grill just sent to that gate
        // feed back into its own field.
        long sincePulse = world.getTotalWorldTime() - lastSignalPulseTick;
        if (sincePulse >= 0 && sincePulse <= 1
                && (linkedGates.containsKey(key) || redstoneBinding.getGates().contains(key))) {
            linkedGates.put(key, powered);
            return;
        }
        Boolean current = linkedGates.get(key);
        if (current != null && current == powered) return;
        linkedGates.put(key, powered);
        applyLinkedGatePower(false);
    }

    /**
     * A linked gate can raise the field while it is active. Only the initial link may lower it:
     * that first off state exists so the player can configure the grill after linking. Later gate
     * deactivation leaves the field alone, so signal pulses cannot switch it off.
     */
    private void applyLinkedGatePower(boolean initialLink) {
        if (linkedGates.isEmpty()) return;
        boolean powered = false;
        for (Boolean value : linkedGates.values()) {
            if (value) {
                powered = true;
                break;
            }
        }
        if (!powered && !initialLink) return;
        if (fieldEnabled != powered) {
            fieldEnabled = powered;
            deniedInside.clear();
            sync();
        }
    }

    // ------------------------------------------------------------ field settings

    public boolean isFieldEnabled() {
        TileEntityEmancipationGrill core = getSecurityCore();
        return core == this ? fieldEnabled : core.isFieldEnabled();
    }

    /** Server side: turns the whole field and its barrier on or off. */
    public boolean setFieldEnabled(boolean value) {
        if (world == null || world.isRemote) return false;
        TileEntityEmancipationGrill core = getSecurityCore();
        if (core != this) return core.setFieldEnabled(value);
        if (fieldEnabled == value) return false;
        fieldEnabled = value;
        deniedInside.clear();
        sync();
        return true;
    }

    public FieldMode getFieldMode() {
        TileEntityEmancipationGrill core = getSecurityCore();
        return core == this ? fieldMode : core.getFieldMode();
    }

    /** Server side: selects Solid, Neutral or Lethal field behavior. */
    public boolean setFieldMode(FieldMode value) {
        if (world == null || world.isRemote || value == null) return false;
        TileEntityEmancipationGrill core = getSecurityCore();
        if (core != this) return core.setFieldMode(value);
        if (fieldMode == value) return false;
        fieldMode = value;
        deniedInside.clear();
        sync();
        return true;
    }

    /** True when a biometric identity only works for the player it belongs to. */
    public boolean isMatchUser() {
        TileEntityEmancipationGrill core = getSecurityCore();
        return core == this ? matchUser : core.isMatchUser();
    }

    public boolean setMatchUser(boolean value) {
        if (world == null || world.isRemote) return false;
        TileEntityEmancipationGrill core = getSecurityCore();
        if (core != this) return core.setMatchUser(value);
        if (matchUser == value) return false;
        matchUser = value;
        sync();
        return true;
    }

    public boolean isIgnorePrivateKey() {
        TileEntityEmancipationGrill core = getSecurityCore();
        return core == this ? ignorePrivateKey : core.isIgnorePrivateKey();
    }

    public boolean setIgnorePrivateKey(boolean value) {
        if (world == null || world.isRemote) return false;
        TileEntityEmancipationGrill core = getSecurityCore();
        if (core != this) return core.setIgnorePrivateKey(value);
        if (ignorePrivateKey == value) return false;
        ignorePrivateKey = value;
        sync();
        return true;
    }

    public boolean isIgnoreBiometricId() {
        TileEntityEmancipationGrill core = getSecurityCore();
        return core == this ? ignoreBiometricId : core.isIgnoreBiometricId();
    }

    public boolean setIgnoreBiometricId(boolean value) {
        if (world == null || world.isRemote) return false;
        TileEntityEmancipationGrill core = getSecurityCore();
        if (core != this) return core.setIgnoreBiometricId(value);
        if (ignoreBiometricId == value) return false;
        ignoreBiometricId = value;
        sync();
        return true;
    }

    public boolean isIgnoreInventory() {
        TileEntityEmancipationGrill core = getSecurityCore();
        return core == this ? ignoreInventory : core.isIgnoreInventory();
    }

    public boolean setIgnoreInventory(boolean value) {
        if (world == null || world.isRemote) return false;
        TileEntityEmancipationGrill core = getSecurityCore();
        if (core != this) return core.setIgnoreInventory(value);
        if (ignoreInventory == value) return false;
        ignoreInventory = value;
        sync();
        return true;
    }

    public boolean isIgnoreItems() {
        TileEntityEmancipationGrill core = getSecurityCore();
        return core == this ? ignoreItems : core.isIgnoreItems();
    }

    public boolean setIgnoreItems(boolean value) {
        if (world == null || world.isRemote) return false;
        TileEntityEmancipationGrill core = getSecurityCore();
        if (core != this) return core.setIgnoreItems(value);
        if (ignoreItems == value) return false;
        ignoreItems = value;
        sync();
        return true;
    }

    public boolean isAudioEnabled() {
        TileEntityEmancipationGrill core = getSecurityCore();
        return core == this ? audioEnabled : core.isAudioEnabled();
    }

    public boolean setAudioEnabled(boolean value) {
        if (world == null || world.isRemote) return false;
        TileEntityEmancipationGrill core = getSecurityCore();
        if (core != this) return core.setAudioEnabled(value);
        if (audioEnabled == value) return false;
        audioEnabled = value;
        sync();
        return true;
    }

    public boolean isDetectPlayers() {
        TileEntityEmancipationGrill core = getSecurityCore();
        return core == this ? detectPlayers : core.isDetectPlayers();
    }

    public boolean setDetectPlayers(boolean value) {
        if (world == null || world.isRemote) return false;
        TileEntityEmancipationGrill core = getSecurityCore();
        if (core != this) return core.setDetectPlayers(value);
        if (detectPlayers == value) return false;
        detectPlayers = value;
        sync();
        return true;
    }

    public boolean isDetectHostile() {
        TileEntityEmancipationGrill core = getSecurityCore();
        return core == this ? detectHostile : core.isDetectHostile();
    }

    public boolean setDetectHostile(boolean value) {
        if (world == null || world.isRemote) return false;
        TileEntityEmancipationGrill core = getSecurityCore();
        if (core != this) return core.setDetectHostile(value);
        if (detectHostile == value) return false;
        detectHostile = value;
        sync();
        return true;
    }

    public boolean isDetectNeutral() {
        TileEntityEmancipationGrill core = getSecurityCore();
        return core == this ? detectNeutral : core.isDetectNeutral();
    }

    public boolean setDetectNeutral(boolean value) {
        if (world == null || world.isRemote) return false;
        TileEntityEmancipationGrill core = getSecurityCore();
        if (core != this) return core.setDetectNeutral(value);
        if (detectNeutral == value) return false;
        detectNeutral = value;
        sync();
        return true;
    }

    public boolean isRedstoneEvent(int event) {
        if (event < 0 || event >= RS_EVENT_COUNT) return false;
        TileEntityEmancipationGrill core = getSecurityCore();
        return core == this ? redstoneEvents[event] : core.isRedstoneEvent(event);
    }

    public boolean setRedstoneEvent(int event, boolean value) {
        if (world == null || world.isRemote || event < 0 || event >= RS_EVENT_COUNT) return false;
        TileEntityEmancipationGrill core = getSecurityCore();
        if (core != this) return core.setRedstoneEvent(event, value);
        if (redstoneEvents[event] == value) return false;
        redstoneEvents[event] = value;
        sync();
        return true;
    }

    private void pulseRedstone(int event) {
        if (event >= 0 && event < RS_EVENT_COUNT && redstoneEvents[event]) {
            // Grill events are edge notifications, so keep the gate on for a single tick.
            lastSignalPulseTick = world == null ? lastSignalPulseTick : world.getTotalWorldTime();
            redstoneBinding.fireOneTick();
        }
    }

    // ------------------------------------------------------------ credential assignment

    public List<PrivateKeys.Key> getAssignedKeys() {
        TileEntityEmancipationGrill core = getSecurityCore();
        return core == this ? Collections.unmodifiableList(assignedKeys) : core.getAssignedKeys();
    }

    public List<BiometricId> getAssignedIds() {
        TileEntityEmancipationGrill core = getSecurityCore();
        return core == this ? Collections.unmodifiableList(assignedIds) : core.getAssignedIds();
    }

    public boolean hasAssignedKey(String serial) {
        TileEntityEmancipationGrill core = getSecurityCore();
        return core == this ? hasLocalKey(serial) : core.hasLocalKey(serial);
    }

    public boolean hasAssignedId(String uuid) {
        TileEntityEmancipationGrill core = getSecurityCore();
        return core == this ? hasLocalId(uuid) : core.hasLocalId(uuid);
    }

    private boolean hasLocalKey(String serial) {
        for (PrivateKeys.Key key : assignedKeys) {
            if (key.serial.equals(serial)) return true;
        }
        return false;
    }

    private boolean hasLocalId(String uuid) {
        for (BiometricId id : assignedIds) {
            if (id.uuid.equals(uuid)) return true;
        }
        return false;
    }

    /** Server side: adds a key copied from the operator's card. */
    public boolean assignKey(PrivateKeys.Key key) {
        if (world == null || world.isRemote || key == null || key.serial.isEmpty()) return false;
        TileEntityEmancipationGrill core = getSecurityCore();
        if (core != this) return core.assignKey(key);
        if (hasLocalKey(key.serial)) return false;
        assignedKeys.add(key);
        sync();
        return true;
    }

    /** Server side: removes a key by serial. */
    public boolean unassignKey(String serial) {
        if (world == null || world.isRemote || serial == null) return false;
        TileEntityEmancipationGrill core = getSecurityCore();
        if (core != this) return core.unassignKey(serial);
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
        TileEntityEmancipationGrill core = getSecurityCore();
        if (core != this) return core.assignId(id);
        if (hasLocalId(id.uuid)) return false;
        assignedIds.add(id);
        sync();
        return true;
    }

    /** Server side: removes an identity by UUID. */
    public boolean unassignId(String uuid) {
        if (world == null || world.isRemote || uuid == null) return false;
        TileEntityEmancipationGrill core = getSecurityCore();
        if (core != this) return core.unassignId(uuid);
        for (int i = 0; i < assignedIds.size(); i++) {
            if (assignedIds.get(i).uuid.equals(uuid)) {
                assignedIds.remove(i);
                sync();
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------ field clearance

    /**
     * True when any card in the player's main inventory or offhand grants access. The field is a
     * solid wall by default; a player carrying an assigned key, an assigned biometric identity, or
     * a Creative Keycard passes. Ownership only controls the console and the Breaker key.
     */
    public boolean hasClearance(EntityPlayer player) {
        if (player == null) return false;
        TileEntityEmancipationGrill core = getSecurityCore();
        if (core != this) return core.hasClearance(player);

        for (ItemStack stack : player.inventory.mainInventory) {
            if (grantsAccess(stack, player)) return true;
        }
        return grantsAccess(player.getHeldItemOffhand(), player);
    }

    /**
     * Matches the scanner/sensor credential rules. Private keys and biometric IDs can each be
     * ignored independently. With Match User on, a bound biometric card is personal and its whole
     * card is refused when carried by someone else, including any private keys stored on it.
     */
    private boolean grantsAccess(ItemStack stack, EntityPlayer player) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() instanceof ItemCreativeKeycard) return true;
        if (!(stack.getItem() instanceof ItemRfidCard)) return false;

        if (matchUser && !ignoreBiometricId && Biometrics.isBound(stack)
                && !Biometrics.isOwnedBy(stack, player)) {
            return false;
        }
        if (!ignorePrivateKey) {
            for (PrivateKeys.Key key : PrivateKeys.get(stack)) {
                if (hasLocalKey(key.serial)) return true;
            }
        }
        if (ignoreBiometricId || !Biometrics.isBound(stack)) return false;
        if (!hasLocalId(Biometrics.ownerUuid(stack))) return false;
        return !matchUser || Biometrics.isOwnedBy(stack, player);
    }

    /** 1 = matching biometric holder, -1 = mismatched assigned holder, 0 = no match signal. */
    private int matchUserSignal(EntityPlayer player) {
        if (!matchUser || ignoreBiometricId || player == null) return 0;
        boolean mismatch = false;
        for (ItemStack stack : player.inventory.mainInventory) {
            int signal = matchUserSignal(stack, player);
            if (signal > 0) return 1;
            if (signal < 0) mismatch = true;
        }
        int held = matchUserSignal(player.getHeldItemOffhand(), player);
        if (held > 0) return 1;
        if (held < 0) mismatch = true;
        return mismatch ? -1 : 0;
    }

    private int matchUserSignal(ItemStack stack, EntityPlayer player) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemRfidCard)) return 0;
        if (!Biometrics.isBound(stack) || !hasLocalId(Biometrics.ownerUuid(stack))) return 0;
        return Biometrics.isOwnedBy(stack, player) ? 1 : -1;
    }

    @Override
    public void update() {
        if (world == null || world.isRemote) return;
        redstoneBinding.update();

        // A leftover second grill becomes a standalone first grill again once its partner is gone.
        if (!pairOwner && getPartner() == null) {
            pairOwner = true;
            sync();
        }
        if (!pairOwner) return;
        if (getSecurityCore() != this) return;

        AxisAlignedBB box = getFieldBox();
        if (box == null) {
            deniedInside.clear();
            return;
        }

        FieldMode mode = getFieldMode();
        if (mode == FieldMode.NEUTRAL) {
            deniedInside.clear();
            return;
        }

        AxisAlignedBB scan = box.grow(2.0D);
        AxisAlignedBB near = box.grow(0.5D);
        Set<UUID> present = new HashSet<>();
        for (EntityLivingBase entity : world.getEntitiesWithinAABB(
                EntityLivingBase.class, scan)) {
            if (entity == null || !entity.isEntityAlive() || entity instanceof EntityArmorStand) {
                continue;
            }

            if (entity instanceof EntityPlayer) {
                EntityPlayer player = (EntityPlayer) entity;
                if (!detectPlayers || player.isSpectator()) continue;
            } else {
                boolean hostile = entity instanceof IMob;
                if (hostile ? !detectHostile : !detectNeutral) continue;
            }

            boolean inside = entity.getEntityBoundingBox().intersects(box);
            boolean crossing = crossesField(entity, box);
            boolean nearby = mode == FieldMode.SOLID
                    && entity.getEntityBoundingBox().intersects(near);
            if (!inside && !crossing && !nearby) continue;

            UUID id = entity.getUniqueID();
            int matchSignal = entity instanceof EntityPlayer
                    ? matchUserSignal((EntityPlayer) entity) : 0;
            present.add(id);
            if (mode == FieldMode.WALL) {
                // No clearance checks, no messages and no lethal effects: just push everyone back.
                if (inside || crossing) clampEntity(entity, box);
                continue;
            }
            if (entity instanceof EntityPlayer && hasClearance((EntityPlayer) entity)) {
                if (crossing) {
                    if (mode == FieldMode.SOLID) pulseRedstone(RS_SOLID_PASS);
                    if (matchSignal > 0) pulseRedstone(RS_MATCH_USER_PASS);
                }
                deniedInside.remove(id);
                continue;
            }
            if (!inside && !crossing) continue;

            if (mode == FieldMode.LETHAL) {
                if (entity instanceof EntityPlayer
                        && ((EntityPlayer) entity).isCreative()) {
                    continue;
                }
                if (matchSignal < 0) pulseRedstone(RS_MATCH_USER_FAIL);
                disintegrate(entity);
                continue;
            }

            boolean firstDenial = deniedInside.add(id);
            if (firstDenial) {
                pulseRedstone(RS_SOLID_BLOCK);
                if (matchSignal < 0) pulseRedstone(RS_MATCH_USER_FAIL);
            }
            clampEntity(entity, box);
            if (entity instanceof EntityPlayer && firstDenial) {
                EntityPlayer player = (EntityPlayer) entity;
                player.sendStatusMessage(new TextComponentTranslation(
                        "ntmdoors.msg.emancipation_denied"), true);
                world.playSound(null, player.posX, player.posY, player.posZ,
                        ModSounds.SCANNER_BUTTON8, SoundCategory.PLAYERS, 0.7F, 0.9F);
            }
        }
        if (mode == FieldMode.LETHAL) {
            eradicateNonLiving(box);
        }
        deniedInside.retainAll(present);
    }

    /**
     * Lethal fields erase non-living entities that touch or cross the plane. The wider scan catches
     * fast projectiles that would otherwise tunnel through a one-pixel-thick field between ticks.
     */
    private void eradicateNonLiving(AxisAlignedBB box) {
        AxisAlignedBB scan = box.grow(8.0D);
        for (Entity entity : world.getEntitiesWithinAABB(Entity.class, scan)) {
            if (entity == null || !entity.isEntityAlive()) continue;
            if (entity instanceof EntityLivingBase && !(entity instanceof EntityArmorStand)) {
                continue;
            }
            if (ignoreItems && entity instanceof EntityItem) continue;
            if (entity.getEntityBoundingBox().intersects(box) || crossesField(entity, box)) {
                entity.setDead();
                playDisintegrateSound(entity, true);
            }
        }
    }

    private void playDisintegrateSound(Entity entity, boolean rateLimit) {
        if (world == null || entity == null || !audioEnabled) return;
        long now = world.getTotalWorldTime();
        if (rateLimit) {
            if (now - lastDisintegrateSoundTick < 8L) return;
            lastDisintegrateSoundTick = now;
        }
        playSoundNear(entity.posX, entity.posY + entity.height * 0.5D, entity.posZ,
                ModSounds.EMANCIPATION_DISINTEGRATE);
    }

    /** Sends a one-shot only to players within the grill's three-block sound radius. */
    private void playSoundNear(double x, double y, double z, SoundEvent sound) {
        if (world == null) return;
        double range = 3.0D;
        for (EntityPlayer player : world.playerEntities) {
            if (!(player instanceof EntityPlayerMP)) continue;
            if (player.getDistanceSq(x, y, z) > range * range) continue;
            ((EntityPlayerMP) player).connection.sendPacket(new SPacketSoundEffect(
                    sound, SoundCategory.BLOCKS, x, y, z, 0.5F, 1.0F));
        }
    }

    /**
     * Lethal field contact. Creative players are left alone; otherwise items and armor are cleared
     * first unless Ignore Inventory is on, so the normal death drop cannot scatter them.
     */
    private void disintegrate(EntityLivingBase entity) {
        if (entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entity;
            if (player.isCreative()) return;
            if (!ignoreInventory) destroyPlayerInventory(player);
        } else if (!ignoreInventory) {
            destroyMobEquipment(entity);
        }
        entity.attackEntityFrom(DISINTEGRATION, Float.MAX_VALUE);
        if (!entity.isEntityAlive()) {
            playDisintegrateSound(entity, false);
            pulseRedstone(RS_LETHAL_KILL);
        }
    }

    private static void destroyPlayerInventory(EntityPlayer player) {
        for (int i = 0; i < player.inventory.mainInventory.size(); i++) {
            player.inventory.mainInventory.set(i, ItemStack.EMPTY);
        }
        for (int i = 0; i < player.inventory.armorInventory.size(); i++) {
            player.inventory.armorInventory.set(i, ItemStack.EMPTY);
        }
        for (int i = 0; i < player.inventory.offHandInventory.size(); i++) {
            player.inventory.offHandInventory.set(i, ItemStack.EMPTY);
        }
        player.inventory.markDirty();
        if (player instanceof EntityPlayerMP) {
            ((EntityPlayerMP) player).inventoryContainer.detectAndSendChanges();
        }
    }

    /** Mobs do not have an InventoryPlayer, so strip their equipment slots instead. */
    private static void destroyMobEquipment(EntityLivingBase entity) {
        for (EntityEquipmentSlot slot : EntityEquipmentSlot.values()) {
            entity.setItemStackToSlot(slot, ItemStack.EMPTY);
        }
    }

    /** True when the entity's last movement segment crossed the field plane inside the opening. */
    private static boolean crossesField(Entity entity, AxisAlignedBB box) {
        boolean spansX = (box.maxX - box.minX) > (box.maxZ - box.minZ);
        double plane = spansX
                ? (box.minZ + box.maxZ) * 0.5D
                : (box.minX + box.maxX) * 0.5D;
        double previous = spansX ? entity.lastTickPosZ : entity.lastTickPosX;
        double current = spansX ? entity.posZ : entity.posX;
        if (!((previous < plane && current >= plane)
                || (previous > plane && current <= plane))) {
            return false;
        }

        double along = spansX ? entity.posX : entity.posZ;
        double half = entity.width * 0.5D;
        double alongMin = spansX ? box.minX : box.minZ;
        double alongMax = spansX ? box.maxX : box.maxZ;
        if (along + half < alongMin || along - half > alongMax) return false;
        return entity.posY + entity.height >= box.minY && entity.posY <= box.maxY;
    }

    /**
     * Forces an unauthorized entity back to the side of the field it approached from. The teleport
     * is server-authoritative, so a client that keeps holding forward cannot walk through.
     */
    private static void clampEntity(Entity entity, AxisAlignedBB box) {
        boolean spansX = (box.maxX - box.minX) > (box.maxZ - box.minZ);
        double plane = spansX
                ? (box.minZ + box.maxZ) * 0.5D
                : (box.minX + box.maxX) * 0.5D;
        double previous = spansX ? entity.lastTickPosZ : entity.lastTickPosX;
        double current = spansX ? entity.posZ : entity.posX;

        boolean positive = previous > plane;
        if (Math.abs(previous - plane) < 1.0E-4D || previous == current) {
            positive = current >= plane;
        }

        double margin = Math.max(0.35D, entity.width * 0.5D + 0.05D);
        double target = plane + (positive ? margin : -margin);
        if (spansX) {
            teleport(entity, entity.posX, entity.posY, target);
            entity.motionZ = 0.0D;
        } else {
            teleport(entity, target, entity.posY, entity.posZ);
            entity.motionX = 0.0D;
        }
        entity.velocityChanged = true;
    }

    private static void teleport(Entity entity, double x, double y, double z) {
        entity.setPositionAndUpdate(x, y, z);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (world != null && !world.isRemote) {
            RedstoneBinding.restoreGrillLinks(world, pos);
        }
    }

    @Override
    public void invalidate() {
        deniedInside.clear();
        super.invalidate();
    }

    // ------------------------------------------------------------ render bounds

    /**
     * The field can extend up to ten blocks away and three blocks up, far outside this tile
     * entity's own block. Without this box the whole field is culled as soon as the bottom grill
     * leaves the camera frustum, which makes it vanish at some viewing angles and at range.
     */
    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        AxisAlignedBB field = getFieldBox();
        if (field != null) return field.grow(1.0D);
        return new AxisAlignedBB(pos).grow(1.0D);
    }

    /**
     * The default is 64 blocks, which cuts the field off long before the terrain does at high
     * render distances. Let the normal chunk/frustum checks decide visibility instead.
     */
    @Override
    public double getMaxRenderDistanceSquared() {
        return Double.MAX_VALUE;
    }

    // ------------------------------------------------------------ persistence/sync

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
        assignedIds.clear();
        if (!tag.hasKey("assignedIds", Constants.NBT.TAG_LIST)) return;
        NBTTagList list = tag.getTagList("assignedIds", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            String uuid = entry.getString("Id");
            if (uuid.isEmpty()) continue;
            assignedIds.add(new BiometricId(uuid, entry.getString("Name")));
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        redstoneBinding.readFromNBT(tag);

        ownerId = null;
        String owner = tag.getString("owner");
        if (!owner.isEmpty()) {
            try {
                ownerId = UUID.fromString(owner);
            } catch (IllegalArgumentException ignored) {
                // Treat a malformed owner as ownerless instead of losing the whole grill.
            }
        }
        ownerName = tag.getString("ownerName");
        pairOwner = !tag.hasKey("pairOwner") || tag.getBoolean("pairOwner");

        fieldEnabled = !tag.hasKey("fieldEnabled") || tag.getBoolean("fieldEnabled");
        fieldMode = FieldMode.byId(tag.hasKey("fieldMode") ? tag.getByte("fieldMode") : 0);
        matchUser = !tag.hasKey("matchUser") || tag.getBoolean("matchUser");
        ignorePrivateKey = tag.getBoolean("ignorePrivateKey");
        ignoreBiometricId = tag.getBoolean("ignoreBiometricId");
        ignoreInventory = tag.getBoolean("ignoreInventory");
        ignoreItems = tag.getBoolean("ignoreItems");
        audioEnabled = !tag.hasKey("audioEnabled") || tag.getBoolean("audioEnabled");
        detectPlayers = !tag.hasKey("detectPlayers") || tag.getBoolean("detectPlayers");
        detectHostile = !tag.hasKey("detectHostile") || tag.getBoolean("detectHostile");
        detectNeutral = !tag.hasKey("detectNeutral") || tag.getBoolean("detectNeutral");
        for (int i = 0; i < RS_EVENT_COUNT; i++) {
            redstoneEvents[i] = tag.getBoolean("redstoneEvent" + i);
        }

        assignedKeys.clear();
        if (tag.hasKey("assignedKeys", Constants.NBT.TAG_LIST)) {
            assignedKeys.addAll(PrivateKeys.fromList(
                    tag.getTagList("assignedKeys", Constants.NBT.TAG_COMPOUND)));
        }
        readIds(tag);
        deniedInside.clear();
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        redstoneBinding.writeToNBT(tag);

        tag.setString("owner", ownerId == null ? "" : ownerId.toString());
        tag.setString("ownerName", ownerName);
        tag.setBoolean("pairOwner", pairOwner);
        tag.setBoolean("fieldEnabled", fieldEnabled);
        tag.setByte("fieldMode", (byte) fieldMode.ordinal());
        tag.setBoolean("matchUser", matchUser);
        tag.setBoolean("ignorePrivateKey", ignorePrivateKey);
        tag.setBoolean("ignoreBiometricId", ignoreBiometricId);
        tag.setBoolean("ignoreInventory", ignoreInventory);
        tag.setBoolean("ignoreItems", ignoreItems);
        tag.setBoolean("audioEnabled", audioEnabled);
        tag.setBoolean("detectPlayers", detectPlayers);
        tag.setBoolean("detectHostile", detectHostile);
        tag.setBoolean("detectNeutral", detectNeutral);
        for (int i = 0; i < RS_EVENT_COUNT; i++) {
            tag.setBoolean("redstoneEvent" + i, redstoneEvents[i]);
        }
        tag.setTag("assignedKeys", PrivateKeys.toList(assignedKeys));
        tag.setTag("assignedIds", encodeIds());
        return tag;
    }
}
