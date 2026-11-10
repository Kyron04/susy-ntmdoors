package com.ntmdoors.tile;

import com.ntmdoors.util.BlockPositions;
import com.ntmdoors.util.TimedSources;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Security-door ownership, opt-in redstone access, wireless gate power input, and the gate-link
 * backrefs needed to remove a door from gate bindings without scanning the world. Owned by
 * {@link TileEntityDoorGeneric}; the door keeps state-machine/redstone-edge behavior.
 */
final class DoorSecurityAccess {

    private final TileEntityDoorGeneric door;

    @Nullable
    private UUID ownerId;
    private String ownerName = "";
    /** Security doors ignore redstone and gate links until the owner opts in with the Monocle. */
    private boolean redstoneEnabled;
    /** Linked gate sources currently powering this door. */
    private final TimedSources linkedPower = new TimedSources();
    /** Gates that have linked this door as an output target, for targeted unlinking. */
    private final Set<BlockPos> linkedGates = new LinkedHashSet<>();
    /** Recheck saved power on the first server tick after loading. */
    boolean redstoneCheckPending;

    DoorSecurityAccess(TileEntityDoorGeneric door) {
        this.door = door;
    }

    boolean acceptsRedstone(boolean securityDoor) {
        return !securityDoor || (ownerId != null && redstoneEnabled);
    }

    boolean isWirelessPowered(World world) {
        return world != null && linkedPower.isActive(world.getTotalWorldTime());
    }

    void tick(World world) {
        if (world == null || world.isRemote) return;
        if (redstoneCheckPending) {
            redstoneCheckPending = false;
            door.checkRedstone();
        }
        if (linkedPower.prune(world.getTotalWorldTime())) door.checkRedstone();
    }

    void onLoad(World world) {
        redstoneCheckPending = world != null && !world.isRemote;
    }

    void clear() {
        linkedPower.clear();
        linkedGates.clear();
    }

    /**
     * Server side: a linked Redstone Gate reports its output. The source is removed on the falling
     * edge; the expiry lets the door recover if a source unloads without sending one.
     */
    void setLinkedPower(World world, BlockPos gatePos, boolean powered, boolean securityDoor,
                        boolean breached) {
        if (world == null || world.isRemote || gatePos == null) return;
        if (!acceptsRedstone(securityDoor) || breached) return;
        TimedSources.Update change = linkedPower.apply(gatePos, powered, world.getTotalWorldTime(),
                RedstoneBinding.LINK_TIMEOUT_TICKS);
        if (change == TimedSources.Update.NONE) return;
        door.checkRedstone();
    }

    void onGateLinked(BlockPos gatePos) {
        if (gatePos != null) linkedGates.add(gatePos.toImmutable());
    }

    void onGateUnlinked(BlockPos gatePos) {
        if (gatePos != null) linkedGates.remove(gatePos);
    }

    @Nullable
    UUID getOwnerId() {
        return ownerId;
    }

    String getOwnerName() {
        return ownerName;
    }

    boolean isRedstoneEnabled() {
        return redstoneEnabled;
    }

    boolean isOwnedBy(EntityPlayer player) {
        return player != null && ownerId != null && ownerId.equals(player.getUniqueID());
    }

    /** Server side: records the player who placed this security door. */
    void setOwner(World world, EntityPlayer player, boolean securityDoor) {
        if (world == null || world.isRemote || player == null) return;
        if (!securityDoor || ownerId != null) return;
        ownerId = player.getUniqueID();
        ownerName = player.getName();
        door.markDirty();
    }

    /**
     * Server side: the owner toggles whether this security door accepts redstone and gate links.
     * Other players are refused so they cannot silently open a path through a protected door.
     */
    boolean toggleRedstoneAccess(World world, BlockPos pos, EntityPlayer player, boolean securityDoor) {
        if (world == null || world.isRemote || player == null || !securityDoor) return false;
        if (ownerId == null) {
            rejectText(world, pos, player, new TextComponentTranslation("ntmdoors.msg.security_redstone_no_owner"));
            return false;
        }
        if (!ownerId.equals(player.getUniqueID())) {
            String owner = ownerName.isEmpty() ? "the owner" : ownerName;
            rejectText(world, pos, player, new TextComponentTranslation(
                    "ntmdoors.msg.security_redstone_owner_only", owner));
            return false;
        }
        ownerName = player.getName();
        setRedstoneEnabled(world, pos, !redstoneEnabled);
        player.sendStatusMessage(new TextComponentTranslation(redstoneEnabled
                ? "ntmdoors.msg.security_redstone_enabled"
                : "ntmdoors.msg.security_redstone_disabled"), true);
        return true;
    }

    private void setRedstoneEnabled(World world, BlockPos pos, boolean value) {
        if (redstoneEnabled == value) return;
        boolean wasDriven = door.isRedstoneDrivenForSecurityToggle()
                || (world != null && linkedPower.isActive(world.getTotalWorldTime()));
        redstoneEnabled = value;
        if (value) {
            door.checkRedstone();
        } else {
            door.clearPoweredState();
            linkedPower.clear();
            unlinkAllLoadedGates(world, pos);
            // Close only this cell. Other segments of a linked group keep their own redstone
            // state, so an enabled neighbour is not forced shut by this owner's opt-out.
            if (wasDriven) door.requestCloseIfMovable();
        }
        door.markDirty();
        door.sync();
    }

    /** Server side: drops this door from linked gates using known backrefs. */
    void unlinkKnownGates(World world, BlockPos pos) {
        if (world == null || world.isRemote) return;
        unlinkKnownGatesInternal(world, pos.toImmutable());
        linkedGates.clear();
    }

    /**
     * Server side: drops this door from every loaded gate's receiver list. Scans loaded gates so
     * links from worlds saved before gate backrefs were tracked are still removed.
     */
    void unlinkAllLoadedGates(World world, BlockPos pos) {
        if (world == null || world.isRemote) return;
        BlockPos self = pos.toImmutable();
        unlinkKnownGatesInternal(world, self);
        linkedGates.clear();
        for (TileEntity te : new ArrayList<>(world.loadedTileEntityList)) {
            if (te instanceof TileEntityRedstoneGate) {
                ((TileEntityRedstoneGate) te).getRedstoneBinding().onDoorRemoved(self);
            }
        }
    }

    private void unlinkKnownGatesInternal(World world, BlockPos self) {
        for (BlockPos gatePos : new ArrayList<>(linkedGates)) {
            if (!world.isBlockLoaded(gatePos)) continue;
            TileEntity te = world.getTileEntity(gatePos);
            if (te instanceof TileEntityRedstoneGate) {
                ((TileEntityRedstoneGate) te).getRedstoneBinding().onDoorRemoved(self);
            }
        }
    }

    void writeToNBT(NBTTagCompound tag, boolean securityDoor) {
        tag.setString("linkedPower", BlockPositions.encodeList(linkedPower.keys()));
        if (!linkedGates.isEmpty()) tag.setString("linkedGates", BlockPositions.encodeList(linkedGates));
        if (securityDoor) {
            tag.setString("owner", ownerId == null ? "" : ownerId.toString());
            tag.setString("ownerName", ownerName);
            tag.setBoolean("redstoneEnabled", redstoneEnabled);
        }
    }

    void stripSyncTag(NBTTagCompound tag) {
        tag.removeTag("linkedPower");
        tag.removeTag("linkedGates");
        tag.removeTag("owner");
        tag.removeTag("ownerName");
    }

    void readFromNBT(NBTTagCompound tag, World world, boolean breached) {
        ownerId = null;
        ownerName = "";
        redstoneEnabled = false;
        String owner = tag.getString("owner");
        if (!owner.isEmpty()) {
            try {
                ownerId = UUID.fromString(owner);
            } catch (IllegalArgumentException ignored) {
                // Treat a malformed owner as ownerless instead of losing the whole door.
            }
        }
        ownerName = tag.getString("ownerName");
        redstoneEnabled = tag.getBoolean("redstoneEnabled");
        linkedPower.clear();
        if (!breached && tag.hasKey("linkedPower") && world != null) {
            linkedPower.load(BlockPositions.decodeList(tag.getString("linkedPower")),
                    world.getTotalWorldTime() + RedstoneBinding.LINK_TIMEOUT_TICKS);
        }
        linkedGates.clear();
        linkedGates.addAll(BlockPositions.decodeList(tag.getString("linkedGates")));
    }

    private static void rejectText(World world, BlockPos pos, EntityPlayer player, TextComponentTranslation text) {
        world.playSound(null, pos, SoundEvents.BLOCK_METAL_PRESSPLATE_CLICK_ON, SoundCategory.BLOCKS, 1F, 0.6F);
        player.sendStatusMessage(text, true);
    }
}
