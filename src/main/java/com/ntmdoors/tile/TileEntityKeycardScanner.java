package com.ntmdoors.tile;

import com.ntmdoors.door.DoorState;
import com.ntmdoors.item.ItemCreativeKeycard;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Keycard reader tile entity. It owns the list of doors bound to it and the screen state.
 *
 * <p>Binding is a short session: a screwdriver sneak-click opens it, sneak-clicking closed doors
 * selects them, and an empty-hand click on the scanner commits the selection. The binding is also
 * recorded on each door, so destroying the scanner leaves the doors bound; a replacement scanner
 * placed in the same spot adopts them again on its first tick.</p>
 */
public class TileEntityKeycardScanner extends TileEntitySynced implements ITickable {

    public enum State {
        IDLE, BINDING, SCANNING, GRANTED, DENIED
    }

    private static final State[] STATES = State.values();

    /** Same cap as the keypad's multi binding. */
    public static final int MAX_DOORS = 9;
    public static final double MAX_BIND_DISTANCE = 32D;

    /** Ticks the "Scanning" text stays up before the result is shown. */
    private static final int SCAN_TICKS = 20;
    /** Ticks the granted / denied result stays up. */
    private static final int RESULT_TICKS = 40;
    /** A binding session the player walks away from expires after a minute. */
    private static final int BINDING_TICKS = 20 * 60;

    private final List<BlockPos> boundDoors = new ArrayList<>();
    /** Doors selected in the current session but not committed yet. */
    private final List<BlockPos> pendingDoors = new ArrayList<>();

    private State state = State.IDLE;
    private int ticks;
    /** Count shown by the screen: bound doors plus the pending selection. */
    private int visibleCount;

    /** Server only. The player currently binding this scanner, and the session clock. */
    @Nullable
    private UUID bindingPlayer;
    private int bindingTicks;
    /** Server only. The player whose card started the current scan. */
    @Nullable
    private UUID scanningPlayer;

    private boolean restorePending;

    // ------------------------------------------------------------ state accessors

    public State getState() {
        return state;
    }

    public int getVisibleCount() {
        return visibleCount;
    }

    public boolean isBinding() {
        return bindingPlayer != null;
    }

    public boolean isBindingFor(@Nullable EntityPlayer player) {
        return player != null && bindingPlayer != null && bindingPlayer.equals(player.getUniqueID());
    }

    // ------------------------------------------------------------ ticking

    @Override
    public void update() {
        if (world == null || world.isRemote) return;

        if (restorePending) {
            restorePending = false;
            restoreBindings();
        }

        if (bindingPlayer != null) {
            boolean gone = world.getPlayerEntityByUUID(bindingPlayer) == null;
            if (gone || --bindingTicks <= 0) stopBinding(null);
        }

        switch (state) {
            case SCANNING:
                if (--ticks <= 0) resolveScan();
                break;
            case GRANTED:
            case DENIED:
                if (--ticks <= 0) {
                    state = bindingPlayer != null ? State.BINDING : State.IDLE;
                    ticks = 0;
                    sync();
                }
                break;
            default:
                break;
        }
    }

    // ------------------------------------------------------------ binding session

    /** Server side: opens or closes the binding session for this player. */
    public void toggleBinding(EntityPlayer player) {
        if (isBindingFor(player)) {
            stopBinding(player);
            return;
        }
        if (bindingPlayer != null) {
            msg(player, "ntmdoors.msg.scanner_busy");
            return;
        }
        pendingDoors.clear();
        bindingPlayer = player.getUniqueID();
        bindingTicks = BINDING_TICKS;
        state = State.BINDING;
        ticks = 0;
        refreshCount();
        msg(player, "ntmdoors.msg.scanner_binding_on");
        sync();
    }

    /** Ends the session, dropping anything that was selected but not committed. */
    public void stopBinding(@Nullable EntityPlayer player) {
        pendingDoors.clear();
        bindingPlayer = null;
        bindingTicks = 0;
        state = State.IDLE;
        ticks = 0;
        refreshCount();
        if (player != null) msg(player, "ntmdoors.msg.scanner_binding_off");
        sync();
    }

    /** Server side: adds a closed door to the pending selection. */
    public void selectDoor(TileEntityDoorGeneric door, EntityPlayer player) {
        if (!isBindingFor(player) || door == null) return;

        if (door.getState() != DoorState.CLOSED) {
            msg(player, "ntmdoors.msg.bind_open");
            return;
        }
        if (pos.distanceSq(door.getPos()) > MAX_BIND_DISTANCE * MAX_BIND_DISTANCE) {
            msg(player, "ntmdoors.msg.bind_far");
            return;
        }
        if (door.isBoundToScanner()) {
            msg(player, "ntmdoors.msg.scanner_taken");
            return;
        }
        if (pendingDoors.contains(door.getPos()) || boundDoors.contains(door.getPos())) {
            return;
        }
        if (boundDoors.size() + pendingDoors.size() >= MAX_DOORS) {
            msg(player, "ntmdoors.msg.scanner_full");
            return;
        }

        pendingDoors.add(door.getPos().toImmutable());
        refreshCount();
        msg(player, "ntmdoors.msg.scanner_bound");
        sync();
    }

    /** Server side: commits the pending selection. Called by an empty-hand click. */
    public boolean confirm(EntityPlayer player) {
        if (!isBindingFor(player)) return false;

        for (BlockPos doorPos : pendingDoors) {
            if (boundDoors.contains(doorPos)) continue;
            TileEntity te = world.getTileEntity(doorPos);
            if (!(te instanceof TileEntityDoorGeneric)) continue;
            TileEntityDoorGeneric door = (TileEntityDoorGeneric) te;
            door.bindToScanner(pos);
            boundDoors.add(doorPos);
        }
        pendingDoors.clear();

        bindingPlayer = null;
        bindingTicks = 0;
        state = State.IDLE;
        ticks = 0;
        refreshCount();
        msg(player, "ntmdoors.msg.scanner_confirmed");
        sync();
        return true;
    }

    // ------------------------------------------------------------ scanning

    /** Server side: starts a scan for the card the player is holding. */
    public void useCard(EntityPlayer player) {
        if (state == State.SCANNING) return;
        scanningPlayer = player.getUniqueID();
        state = State.SCANNING;
        ticks = SCAN_TICKS;
        sync();
    }

    private void resolveScan() {
        EntityPlayer player = scanningPlayer == null ? null : world.getPlayerEntityByUUID(scanningPlayer);
        boolean granted = player != null && ItemCreativeKeycard.isHeldBy(player);

        if (granted) {
            openBoundDoors();
            state = State.GRANTED;
            if (player != null) msg(player, "ntmdoors.msg.scanner_granted");
        } else {
            state = State.DENIED;
            if (player != null) msg(player, "ntmdoors.msg.scanner_denied");
        }
        ticks = RESULT_TICKS;
        scanningPlayer = null;
        sync();
    }

    private void openBoundDoors() {
        for (int i = boundDoors.size() - 1; i >= 0; i--) {
            BlockPos doorPos = boundDoors.get(i);
            TileEntity te = world.getTileEntity(doorPos);
            if (te instanceof TileEntityDoorGeneric) {
                ((TileEntityDoorGeneric) te).request(true);
            } else {
                boundDoors.remove(i);
            }
        }
        refreshCount();
    }

    // ------------------------------------------------------------ restore / bookkeeping

    /**
     * A replacement scanner picks the doors back up. Each door remembers which scanner position it
     * was bound to, so no data has to survive on the scanner itself.
     */
    private void restoreBindings() {
        if (world == null) return;
        int before = boundDoors.size();
        for (TileEntity te : world.loadedTileEntityList) {
            if (!(te instanceof TileEntityDoorGeneric)) continue;
            if (te.getPos().distanceSq(pos) > MAX_BIND_DISTANCE * MAX_BIND_DISTANCE) continue;
            TileEntityDoorGeneric door = (TileEntityDoorGeneric) te;
            if (door.isBoundToScanner(pos) && !boundDoors.contains(door.getPos())) {
                boundDoors.add(door.getPos().toImmutable());
            }
        }
        refreshCount();
        if (boundDoors.size() != before) sync();
    }

    private void refreshCount() {
        visibleCount = boundDoors.size() + pendingDoors.size();
    }

    /** Doors are stored as "x,y,z;x,y,z" so the format never depends on NBT list helpers. */
    private String encodeDoors() {
        StringBuilder out = new StringBuilder();
        for (BlockPos doorPos : boundDoors) {
            if (out.length() > 0) out.append(';');
            out.append(doorPos.getX()).append(',').append(doorPos.getY()).append(',').append(doorPos.getZ());
        }
        return out.toString();
    }

    private static void msg(EntityPlayer player, String key) {
        player.sendStatusMessage(new TextComponentTranslation(key), true);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        restorePending = world != null && !world.isRemote;
    }

    // ------------------------------------------------------------ persistence / sync

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setString("boundDoors", encodeDoors());
        tag.setByte("scanState", (byte) state.ordinal());
        tag.setInteger("scanTicks", ticks);
        tag.setInteger("visibleCount", visibleCount);
        tag.setInteger("bindingTicks", bindingTicks);
        if (bindingPlayer != null) tag.setString("bindingPlayer", bindingPlayer.toString());
        if (scanningPlayer != null) tag.setString("scanningPlayer", scanningPlayer.toString());
        return tag;
    }

    /** Clients only need what the screen and the lenses show. */
    @Override
    protected NBTTagCompound writeSyncTag(NBTTagCompound tag) {
        tag.setByte("scanState", (byte) state.ordinal());
        tag.setInteger("scanTicks", ticks);
        tag.setInteger("visibleCount", visibleCount);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);

        if (tag.hasKey("boundDoors")) {
            boundDoors.clear();
            String raw = tag.getString("boundDoors");
            if (!raw.isEmpty()) {
                for (String part : raw.split(";")) {
                    String[] xyz = part.split(",");
                    if (xyz.length != 3) continue;
                    boundDoors.add(new BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]),
                            Integer.parseInt(xyz[2])));
                }
            }
        }
        if (tag.hasKey("scanState")) {
            state = STATES[Math.floorMod(tag.getByte("scanState"), STATES.length)];
        }
        ticks = tag.getInteger("scanTicks");
        visibleCount = tag.getInteger("visibleCount");
        bindingTicks = tag.getInteger("bindingTicks");
        bindingPlayer = tag.hasKey("bindingPlayer") ? UUID.fromString(tag.getString("bindingPlayer")) : null;
        scanningPlayer = tag.hasKey("scanningPlayer") ? UUID.fromString(tag.getString("scanningPlayer")) : null;
    }
}
