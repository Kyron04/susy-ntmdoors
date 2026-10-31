package com.ntmdoors.tile;

import com.ntmdoors.client.ScannerLoopSound;
import com.ntmdoors.door.DoorState;
import com.ntmdoors.item.ItemCreativeKeycard;
import com.ntmdoors.item.ItemRfidCard;
import com.ntmdoors.util.ModSounds;
import com.ntmdoors.util.PrivateKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Keycard reader tile entity. It owns the list of doors bound to it and the screen state.
 *
 * <p>Binding is a short session: a screwdriver sneak-click opens it, sneak-clicking closed doors
 * selects them, and a screwdriver click on the scanner commits the selection. The binding is also
 * recorded on each door, so destroying the scanner leaves the doors bound; a replacement scanner
 * placed in the same spot adopts them again on its first tick.</p>
 */
public class TileEntityKeycardScanner extends TileEntitySynced implements ITickable {

    public enum State {
        IDLE, BINDING, SCANNING, GRANTED, DENIED, SYNCED
    }

    private static final State[] STATES = State.values();

    /** Same cap as the keypad's multi binding. */
    public static final int MAX_DOORS = 9;
    public static final double MAX_BIND_DISTANCE = 32D;
    /** Digits allowed in the Operator Keycard security code, matching a keypad. */
    public static final int CODE_LENGTH = 6;
    /** How long a correct code leaves the assignment screen usable. */
    public static final int CODE_SESSION_TICKS = 20 * 120;

    /** Ticks the "Scanning" text stays up before the result is shown. */
    private static final int SCAN_TICKS = 20;
    /** Ticks the granted / denied result stays up. */
    private static final int RESULT_TICKS = 40;
    /** A binding session the player walks away from expires after a minute. */
    private static final int BINDING_TICKS = 20 * 60;
    /** Scanner-opened doors close themselves again after ten seconds. */
    private static final int AUTO_CLOSE_TICKS = 20 * 10;
    /** Quieter than the door movement sounds: a scanner sits at head height next to the player. */
    private static final float SOUND_VOLUME = 0.8F;

    private final List<BlockPos> boundDoors = new ArrayList<>();
    /** Doors selected in the current session but not committed yet. */
    private final List<BlockPos> pendingDoors = new ArrayList<>();

    private State state = State.IDLE;
    private int ticks;
    /** Count shown by the screen: bound doors plus the pending selection. */
    private int visibleCount;
    /** Synced committed door count, so a client can tell the scanner is in use. */
    private int boundDoorCount;
    /** Synced mirror of {@link #securityCode}; the digits themselves never leave the server. */
    private boolean codeSet;

    /**
     * The 1-6 digit code an Operator Keycard sets on its first visit. Empty until then; it is never
     * sent to clients, only whether one exists.
     */
    private String securityCode = "";

    /** Private keys that may open this scanner's doors. Synced so the assignment screen sees them. */
    private final List<PrivateKeys.Key> assignedKeys = new ArrayList<>();

    /** Server only. The player who just passed the code and may edit the assignment list. */
    @Nullable
    private UUID codeSessionPlayer;
    private int codeSessionTicks;

    /** Server only. The player currently binding this scanner, and the session clock. */
    @Nullable
    private UUID bindingPlayer;
    private int bindingTicks;
    /** Server only. The player whose card started the current scan. */
    @Nullable
    private UUID scanningPlayer;

    private boolean restorePending;

    /** Client-side copy of the scanning loop; null while no scan is running. */
    @SideOnly(Side.CLIENT)
    private ScannerLoopSound scanSound;

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

    /** True once at least one door is committed; the Operator Keycard screen only applies then. */
    public boolean hasBoundDoors() {
        return boundDoorCount > 0;
    }

    public int getBoundDoorCount() {
        return boundDoorCount;
    }

    /** True once an Operator Keycard has set the scanner's security code. */
    public boolean hasSecurityCode() {
        return codeSet;
    }

    /**
     * Server side: stores the code chosen on the first Operator Keycard visit. A scanner can only
     * ever be given one code; breaking it is the only way to start over.
     */
    public boolean setSecurityCode(String raw) {
        if (world == null || world.isRemote || hasSecurityCode()) return false;
        String code = sanitiseCode(raw);
        if (code.isEmpty()) return false;
        securityCode = code;
        codeSet = true;
        sync();
        return true;
    }

    /** True when the supplied digits match the stored code. */
    public boolean checkSecurityCode(String raw) {
        return hasSecurityCode() && securityCode.equals(sanitiseCode(raw));
    }

    /** Digits only, at most {@link #CODE_LENGTH} characters. */
    public static String sanitiseCode(String raw) {
        if (raw == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < raw.length() && out.length() < CODE_LENGTH; i++) {
            char c = raw.charAt(i);
            if (c >= '0' && c <= '9') out.append(c);
        }
        return out.toString();
    }

    // ------------------------------------------------------------ key assignment

    /** The private keys currently allowed to open this scanner's doors. */
    public List<PrivateKeys.Key> getAssignedKeys() {
        return Collections.unmodifiableList(assignedKeys);
    }

    public boolean hasAssignedKey(String serial) {
        for (PrivateKeys.Key key : assignedKeys) {
            if (key.serial.equals(serial)) return true;
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

    /** Server side: a correct code opens the assignment screen for this player. */
    public void openCodeSession(EntityPlayer player) {
        if (player == null) return;
        codeSessionPlayer = player.getUniqueID();
        codeSessionTicks = CODE_SESSION_TICKS;
    }

    public boolean hasCodeSessionFor(@Nullable EntityPlayer player) {
        return player != null && codeSessionPlayer != null
                && codeSessionTicks > 0
                && codeSessionPlayer.equals(player.getUniqueID());
    }

    /** True when either held card carries any private key assigned to this scanner. */
    private boolean holdsAssignedKey(EntityPlayer player) {
        return grantsAccess(player.getHeldItemMainhand())
                || grantsAccess(player.getHeldItemOffhand());
    }

    private boolean grantsAccess(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemRfidCard)) return false;
        for (PrivateKeys.Key key : PrivateKeys.get(stack)) {
            if (hasAssignedKey(key.serial)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------ ticking

    @Override
    public void update() {
        if (world == null) return;
        if (world.isRemote) {
            updateClientSound();
            return;
        }

        if (restorePending) {
            restorePending = false;
            restoreBindings();
        }

        if (bindingPlayer != null) {
            boolean gone = world.getPlayerEntityByUUID(bindingPlayer) == null;
            if (gone || --bindingTicks <= 0) stopBinding(null);
        }

        if (codeSessionPlayer != null && --codeSessionTicks <= 0) {
            codeSessionPlayer = null;
            codeSessionTicks = 0;
        }

        switch (state) {
            case SCANNING:
                if (--ticks <= 0) resolveScan();
                break;
            case GRANTED:
            case DENIED:
            case SYNCED:
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

    /** Starts the card-read loop on the client when the synced state becomes SCANNING. */
    @SideOnly(Side.CLIENT)
    private void updateClientSound() {
        if (state == State.SCANNING) {
            if (scanSound == null) {
                scanSound = new ScannerLoopSound(this, ModSounds.SCANNER_BELL1, SOUND_VOLUME);
                Minecraft.getMinecraft().getSoundHandler().playSound(scanSound);
            }
        } else {
            stopClientSound();
        }
    }

    @SideOnly(Side.CLIENT)
    private void stopClientSound() {
        if (scanSound == null) return;
        SoundHandler handler = Minecraft.getMinecraft().getSoundHandler();
        if (handler != null) handler.stopSound(scanSound);
        scanSound = null;
    }

    @Override
    public void invalidate() {
        if (world != null && world.isRemote) stopClientSound();
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        if (world != null && world.isRemote) stopClientSound();
        super.onChunkUnload();
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
        if (door.isKeypadBound()) {
            msg(player, "ntmdoors.msg.scanner_keypad");
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

    /** Server side: commits the pending selection. Called by a screwdriver click. */
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
        world.playSound(null, pos, ModSounds.SCANNER_BUTTON3, SoundCategory.BLOCKS, SOUND_VOLUME, 1F);
        sync();
    }

    private void resolveScan() {
        EntityPlayer player = scanningPlayer == null ? null : world.getPlayerEntityByUUID(scanningPlayer);
        boolean granted = player != null
                && (ItemCreativeKeycard.isHeldBy(player) || holdsAssignedKey(player));

        if (granted) {
            world.playSound(null, pos, ModSounds.SCANNER_BUTTON3, SoundCategory.BLOCKS, SOUND_VOLUME, 1F);
            openBoundDoors();
            state = State.GRANTED;
            if (player != null) msg(player, "ntmdoors.msg.scanner_granted");
        } else {
            world.playSound(null, pos, ModSounds.SCANNER_BUTTON8, SoundCategory.BLOCKS, SOUND_VOLUME, 1F);
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
                TileEntityDoorGeneric door = (TileEntityDoorGeneric) te;
                door.request(true);
                door.closeAfter(AUTO_CLOSE_TICKS);
            } else {
                boundDoors.remove(i);
            }
        }
        refreshCount();
    }

    // ------------------------------------------------------------ restore / bookkeeping

    /**
     * A replacement scanner picks the doors back up, and a scanner mounted against a door that
     * another scanner already controls joins it too. Each door remembers the scanners that own it,
     * so no data has to survive on the scanner itself.
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

        // Mounted against a door that already belongs to another scanner: join it.
        for (TileEntityDoorGeneric door : adjacentDoors()) {
            if (!door.isBoundToScanner() || boundDoors.contains(door.getPos())) continue;
            door.bindToScanner(pos);
            boundDoors.add(door.getPos().toImmutable());
        }

        refreshCount();
        if (boundDoors.size() != before) {
            state = State.SYNCED;
            ticks = RESULT_TICKS;
            sync();
        }
    }

    /** Door cores in the 26 cells around this scanner, the same rule the keypad uses. */
    private List<TileEntityDoorGeneric> adjacentDoors() {
        List<TileEntityDoorGeneric> out = new ArrayList<>();
        if (world == null) return out;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockPos neighbour = pos.add(dx, dy, dz);
                    if (!world.isBlockLoaded(neighbour)) continue;
                    TileEntity te = world.getTileEntity(neighbour);
                    TileEntityDoorGeneric core = null;
                    if (te instanceof TileEntityDoorGeneric) {
                        core = (TileEntityDoorGeneric) te;
                    } else if (te instanceof TileEntityDoorPart) {
                        BlockPos corePos = ((TileEntityDoorPart) te).corePos;
                        TileEntity coreTe = corePos == null ? null : world.getTileEntity(corePos);
                        if (coreTe instanceof TileEntityDoorGeneric) core = (TileEntityDoorGeneric) coreTe;
                    }
                    if (core != null && !out.contains(core)) out.add(core);
                }
            }
        }
        return out;
    }

    private void refreshCount() {
        boundDoorCount = boundDoors.size();
        visibleCount = boundDoorCount + pendingDoors.size();
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
        tag.setString("securityCode", securityCode);
        tag.setBoolean("codeSet", codeSet);
        tag.setInteger("boundDoorCount", boundDoorCount);
        tag.setTag("assignedKeys", PrivateKeys.toList(assignedKeys));
        if (bindingPlayer != null) tag.setString("bindingPlayer", bindingPlayer.toString());
        if (scanningPlayer != null) tag.setString("scanningPlayer", scanningPlayer.toString());
        return tag;
    }

    /** Clients only need what the screen and the lenses show, plus the tile position. */
    @Override
    protected NBTTagCompound writeSyncTag(NBTTagCompound tag) {
        // TileEntity.readFromNBT rebuilds pos from x/y/z, so the sync tag must carry them.
        super.writeToNBT(tag);
        tag.setByte("scanState", (byte) state.ordinal());
        tag.setInteger("scanTicks", ticks);
        tag.setInteger("visibleCount", visibleCount);
        tag.setBoolean("codeSet", codeSet);
        tag.setInteger("boundDoorCount", boundDoorCount);
        tag.setTag("assignedKeys", PrivateKeys.toList(assignedKeys));
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
            boundDoorCount = boundDoors.size();
        } else if (tag.hasKey("boundDoorCount")) {
            // Client sync tag: only the number of committed doors is sent.
            boundDoorCount = tag.getInteger("boundDoorCount");
        }
        if (tag.hasKey("securityCode")) {
            securityCode = sanitiseCode(tag.getString("securityCode"));
        }
        codeSet = tag.hasKey("codeSet") ? tag.getBoolean("codeSet") : !securityCode.isEmpty();
        if (tag.hasKey("assignedKeys", Constants.NBT.TAG_LIST)) {
            assignedKeys.clear();
            assignedKeys.addAll(PrivateKeys.fromList(
                    tag.getTagList("assignedKeys", Constants.NBT.TAG_COMPOUND)));
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

    /** Matches the keypad and the doors: the screen stays legible out to 128 blocks. */
    @Override
    public double getMaxRenderDistanceSquared() {
        return 16384D;
    }
}
