package com.ntmdoors.block.entity;

import com.ntmdoors.util.BlockPositions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import com.ntmdoors.util.TimedSources;
import org.jetbrains.annotations.Nullable;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Security-door ownership, opt-in redstone access, and gate-link backrefs.
 */
public final class DoorSecurityAccess {

    private final DoorBlockEntity door;

    @Nullable
    private UUID ownerId;
    private String ownerName = "";
    private boolean redstoneEnabled;
    private final TimedSources linkedPower = new TimedSources();
    private final Set<BlockPos> linkedGates = new LinkedHashSet<>();
    boolean redstoneCheckPending;

    public DoorSecurityAccess(DoorBlockEntity door) {
        this.door = door;
    }

    public boolean acceptsRedstone(boolean securityDoor) {
        return !securityDoor || (ownerId != null && redstoneEnabled);
    }

    public boolean isWirelessPowered(World world) {
        return world != null && linkedPower.isActive(world.getTime());
    }

    public void tick(World world) {
        if (world == null || world.isClient) return;
        if (redstoneCheckPending) {
            redstoneCheckPending = false;
            door.checkRedstone();
        }
        if (linkedPower.prune(world.getTime())) door.checkRedstone();
    }

    public void clear() {
        linkedPower.clear();
        linkedGates.clear();
    }

    public void setLinkedPower(World world, BlockPos gatePos, boolean powered, boolean securityDoor, boolean breached) {
        if (world == null || world.isClient || gatePos == null) return;
        if (!acceptsRedstone(securityDoor) || breached) return;
        TimedSources.Update change = linkedPower.apply(gatePos, powered, world.getTime(), 100);
        if (change == TimedSources.Update.NONE) return;
        door.checkRedstone();
    }

    public void onGateLinked(BlockPos gatePos) {
        if (gatePos != null) linkedGates.add(gatePos.toImmutable());
    }

    public void onGateUnlinked(BlockPos gatePos) {
        if (gatePos != null) linkedGates.remove(gatePos);
    }

    @Nullable
    public UUID getOwnerId() {
        return ownerId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public boolean isRedstoneEnabled() {
        return redstoneEnabled;
    }

    public boolean isOwnedBy(PlayerEntity player) {
        return player != null && ownerId != null && ownerId.equals(player.getUuid());
    }

    public void setOwner(UUID ownerId, String ownerName) {
        if (this.ownerId != null) return;
        this.ownerId = ownerId;
        this.ownerName = ownerName != null ? ownerName : "";
        door.markDirty();
    }

    public void setOwner(@Nullable World world, PlayerEntity player, boolean securityDoor) {
        if ((world != null && world.isClient) || player == null) return;
        if (!securityDoor || ownerId != null) return;
        ownerId = player.getUuid();
        ownerName = player.getName().getString();
        door.markDirty();
    }

    public boolean toggleRedstoneAccess(@Nullable World world, BlockPos pos, PlayerEntity player, boolean securityDoor) {
        if ((world != null && world.isClient) || player == null || !securityDoor) return false;
        if (ownerId == null) {
            rejectText(world, pos, player, Text.translatable("ntmdoors.msg.security_redstone_no_owner"));
            return false;
        }
        if (!ownerId.equals(player.getUuid())) {
            String owner = ownerName.isEmpty() ? "the owner" : ownerName;
            rejectText(world, pos, player, Text.translatable("ntmdoors.msg.security_redstone_owner_only", owner));
            return false;
        }
        ownerName = player.getName().getString();
        setRedstoneEnabled(world, pos, !redstoneEnabled);
        player.sendMessage(Text.translatable(redstoneEnabled
                ? "ntmdoors.msg.security_redstone_enabled"
                : "ntmdoors.msg.security_redstone_disabled"), true);
        return true;
    }

    public void setRedstoneEnabled(boolean value) {
        setRedstoneEnabled(null, door.getPos(), value);
    }

    private void setRedstoneEnabled(@Nullable World world, BlockPos pos, boolean value) {
        if (redstoneEnabled == value) return;
        redstoneEnabled = value;
        if (value) {
            door.checkRedstone();
        } else {
            door.clearPoweredState();
            door.request(false);
        }
        door.markDirty();
        door.sync();
    }

    public void writeToNbt(NbtCompound tag, boolean securityDoor) {
        if (!linkedPower.isEmpty()) tag.putString("linkedPower", BlockPositions.encodeList(linkedPower.keys()));
        if (!linkedGates.isEmpty()) tag.putString("linkedGates", BlockPositions.encodeList(linkedGates));
        if (securityDoor) {
            tag.putString("owner", ownerId == null ? "" : ownerId.toString());
            tag.putString("ownerName", ownerName);
            tag.putBoolean("redstoneEnabled", redstoneEnabled);
        }
    }

    public void stripSyncNbt(NbtCompound tag) {
        tag.remove("linkedPower");
        tag.remove("linkedGates");
        tag.remove("owner");
        tag.remove("ownerName");
    }

    public void readFromNbt(NbtCompound tag, World world, boolean breached) {
        ownerId = null;
        ownerName = "";
        redstoneEnabled = false;
        String owner = tag.getString("owner");
        if (!owner.isEmpty()) {
            try {
                ownerId = UUID.fromString(owner);
            } catch (IllegalArgumentException ignored) {
            }
        }
        ownerName = tag.getString("ownerName");
        redstoneEnabled = tag.getBoolean("redstoneEnabled");
        linkedPower.clear();
        if (!breached && tag.contains("linkedPower") && world != null) {
            linkedPower.load(BlockPositions.decodeList(tag.getString("linkedPower")),
                    world.getTime() + 100);
        }
        linkedGates.clear();
        linkedGates.addAll(BlockPositions.decodeList(tag.getString("linkedGates")));
    }

    private static void rejectText(@Nullable World world, BlockPos pos, PlayerEntity player, Text text) {
        if (world != null) {
            world.playSound(null, pos, SoundEvents.BLOCK_METAL_PRESSURE_PLATE_CLICK_ON, SoundCategory.BLOCKS, 1F, 0.6F);
        }
        if (player != null) {
            player.sendMessage(text, true);
        }
    }
}
