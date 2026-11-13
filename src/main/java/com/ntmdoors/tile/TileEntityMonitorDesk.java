package com.ntmdoors.tile;

import com.ntmdoors.block.BlockMonitorDesk;
import com.ntmdoors.block.BlockMonitorSetup;
import com.ntmdoors.client.BackupLoopSound;
import com.ntmdoors.item.ItemTerminalBackupKey;
import com.ntmdoors.monitor.MonitorDevice;
import com.ntmdoors.monitor.MonitorDisplay;
import com.ntmdoors.monitor.MonitorWallpaper;
import com.ntmdoors.monitor.NotepadDocument;
import com.ntmdoors.monitor.SecurityEvents;
import com.ntmdoors.monitor.SecurityNet;
import com.ntmdoors.monitor.SecurityNetSummary;
import com.ntmdoors.monitor.TerminalBackupData;
import com.ntmdoors.network.NetworkUtil;
import com.ntmdoors.util.ModSounds;
import com.ntmdoors.util.MonitorPasswords;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-desk terminal state. There is exactly one tile entity per monitor desk, on the bottom core
 * cell; it stores the password hash, the "no password" choice, the named Security nets and the
 * terminal backup slots. Each net owns its own confirmed device list and at most one linked
 * Terminal Relay Key. The tile only ticks while a backup copy or its success hold is active.
 *
 * <p>The full NBT (world save) keeps the hash and the nets. The update tag sent to clients carries
 * the password flags plus a compact display snapshot for each physical screen. The hash itself
 * never leaves the server.</p>
 */
public class TileEntityMonitorDesk extends TileEntitySynced implements ITickable {

    public static final int MAX_SCREENS = 6;

    public static final int BACKUP_SLOT_LEFT = 0;
    public static final int BACKUP_SLOT_RIGHT = 1;
    public static final int BACKUP_SLOT_COUNT = 2;
    /** Ten seconds of backup time, matching the programmer and its five progress frames. */
    public static final int BACKUP_TICKS = 20 * 10;
    private static final int BACKUP_SUCCESS_HOLD_TICKS = 10;
    private static final int BACKUP_NOTICE_TICKS = 80;
    private static final float BACKUP_VOLUME = 0.8F;
    private static final float BACKUP_LOOP_VOLUME = BACKUP_VOLUME / 2F;

    /** Why the current backup slots cannot start a copy. */
    public enum BackupFailure {
        NONE,
        EMPTY,
        INVALID_ITEM,
        NO_DATA,
        TARGET_NOT_FRESH
    }

    /** Why the key in the restore slot cannot restore this terminal. */
    public enum RestoreFailure {
        NONE,
        EMPTY,
        INVALID_ITEM,
        NO_DATA
    }

    /** Per-physical-screen terminal state: live display, resume page, wallpaper and viewer. */
    private static final class ScreenState {
        int stage = MonitorDisplay.DEFAULT_STAGE;
        int netId = -1;
        int notepadDocId = -1;
        boolean eventsTab;
        boolean viewing;
        int netsScroll;
        int securityScroll;
        int eventsScroll;
        UUID viewer;
        String wallpaper = "";

        int resumeStage = MonitorDisplay.DEFAULT_STAGE;
        int resumeNetId = -1;
        int resumeNotepadDocId = -1;
        boolean resumeEventsTab;
        int resumeNetsScroll;
        int resumeSecurityScroll;
        int resumeEventsScroll;

        void reset() {
            stage = MonitorDisplay.DEFAULT_STAGE;
            netId = -1;
            notepadDocId = -1;
            eventsTab = false;
            viewing = false;
            netsScroll = 0;
            securityScroll = 0;
            eventsScroll = 0;
            viewer = null;
            wallpaper = "";
            resumeStage = MonitorDisplay.DEFAULT_STAGE;
            resumeNetId = -1;
            resumeNotepadDocId = -1;
            resumeEventsTab = false;
            resumeNetsScroll = 0;
            resumeSecurityScroll = 0;
            resumeEventsScroll = 0;
        }
    }

    public TileEntityMonitorDesk() {
        for (int i = 0; i < MAX_SCREENS; i++) {
            screens[i] = new ScreenState();
        }
    }

    private String passwordHash = "";
    private boolean passwordSet;
    private boolean passwordSkipped;
    private final List<SecurityNet> nets = new ArrayList<>();
    private final List<NotepadDocument> notepadDocuments = new ArrayList<>();
    private int nextNetId;
    private int nextNotepadId;
    private int displayRevision;

    // Two terminal-key slots used by the backup console. They are separate from the Security nets
    // and are persisted with the desk so a key cannot be lost by closing the screen.
    private final ItemStack[] backupSlots = {ItemStack.EMPTY, ItemStack.EMPTY};
    private final BackupInventory backupInventory = new BackupInventory();
    private boolean backupActive;
    private boolean restoreMode;
    private int backupTicks;
    private boolean backupSuccess;
    private int successTicks;
    private int lastSyncedBackupFrame = -1;
    private String backupNotice = "";
    private int backupNoticeTicks;

    @SideOnly(Side.CLIENT)
    @Nullable
    private BackupLoopSound backupSound;

    // One independent terminal page per physical screen (live display, resume page, viewer).
    private final ScreenState[] screens = new ScreenState[MAX_SCREENS];
    /** Number of screens with a live viewer; lets an idle desk skip its whole tick loop. */
    private int viewingScreens;

    // Client-side copy of the data needed to draw the screens.
    private final List<SecurityNetSummary> displayNets = new ArrayList<>();
    private final Map<Integer, MonitorDisplay.NetData> displayNetData = new HashMap<>();
    private final List<NotepadDocument> displayNotepadNames = new ArrayList<>();
    private final Map<Integer, String> displayNotepadTexts = new HashMap<>();

    /** Returns the core tile entity, creating it if an old desk has not produced one yet. */
    public static TileEntityMonitorDesk at(World world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof TileEntityMonitorDesk) return (TileEntityMonitorDesk) tile;
        if (tile != null || world.isRemote) return null;

        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        if (!(block instanceof BlockMonitorSetup) && !(block instanceof BlockMonitorDesk)) return null;
        if (!block.hasTileEntity(state)) return null;

        TileEntityMonitorDesk monitor = new TileEntityMonitorDesk();
        world.setTileEntity(pos, monitor);
        return monitor;
    }

    public boolean hasPassword() {
        return passwordSet;
    }

    public boolean isPasswordSkipped() {
        return passwordSkipped;
    }

    /**
     * Server-side authorization fingerprint. It changes if the desk's password state changes, so
     * an old terminal session cannot keep working after a new password is set.
     */
    public String getPasswordFingerprint() {
        return passwordHash + ":" + passwordSet + ":" + passwordSkipped;
    }

    /** Increments whenever a client receives a new display snapshot. */
    public int getDisplayRevision() {
        return displayRevision;
    }

    /** Server side: stores the already-hashed password and clears the "skipped" choice. */
    public boolean setPasswordHash(String hash) {
        if (world == null || world.isRemote || passwordSet
                || !MonitorPasswords.isValidHash(hash)) {
            return false;
        }
        passwordHash = hash;
        passwordSet = true;
        passwordSkipped = false;
        sync();
        return true;
    }

    /** Server side: the player chose not to protect this desk. */
    public boolean skipPasswordSetup() {
        if (world == null || world.isRemote || passwordSet || passwordSkipped) return false;
        passwordSkipped = true;
        sync();
        return true;
    }

    /** Server side: checks a client-supplied hash against the stored hash. */
    public boolean checkPasswordHash(String hash) {
        return passwordSet && MonitorPasswords.matches(passwordHash, hash);
    }

    // ------------------------------------------------------------ terminal backup

    /** The two-slot inventory shown by the backup console. */
    public IInventory getBackupInventory() {
        return backupInventory;
    }

    public ItemStack getBackupSlot(int index) {
        return index >= 0 && index < BACKUP_SLOT_COUNT ? backupSlots[index] : ItemStack.EMPTY;
    }

    public static boolean isBackupKey(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemTerminalBackupKey;
    }

    /** Client and server: validates a source/target key pair without changing either stack. */
    public static BackupFailure getBackupFailure(ItemStack source, ItemStack target) {
        if (source.isEmpty() || target.isEmpty()) return BackupFailure.EMPTY;
        if (!isBackupKey(source) || !isBackupKey(target)) return BackupFailure.INVALID_ITEM;
        if (!TerminalBackupData.hasBackup(source)) return BackupFailure.NO_DATA;
        NBTTagCompound targetTag = target.getTagCompound();
        if (targetTag != null && !targetTag.isEmpty()) return BackupFailure.TARGET_NOT_FRESH;
        return BackupFailure.NONE;
    }

    /** Server side: validates the current backup slots. */
    public BackupFailure getBackupFailure() {
        return getBackupFailure(backupSlots[BACKUP_SLOT_LEFT], backupSlots[BACKUP_SLOT_RIGHT]);
    }

    /** Client and server: checks a key without changing it. */
    public static RestoreFailure getRestoreFailure(ItemStack source) {
        if (source.isEmpty()) return RestoreFailure.EMPTY;
        if (!isBackupKey(source)) return RestoreFailure.INVALID_ITEM;
        if (!TerminalBackupData.hasBackup(source)) return RestoreFailure.NO_DATA;
        return RestoreFailure.NONE;
    }

    /** Server side: validates the current restore slot. */
    public RestoreFailure getRestoreFailure() {
        return getRestoreFailure(backupSlots[BACKUP_SLOT_LEFT]);
    }

    /**
     * Server side: writes this desk's terminal data onto the key in the left slot. The key keeps
     * its item identity; only its backup tag is replaced.
     */
    public boolean uploadBackupToDrive() {
        if (world == null || world.isRemote) return false;
        if (backupActive) {
            setBackupNotice("Operation already running");
            return false;
        }

        ItemStack source = backupSlots[BACKUP_SLOT_LEFT];
        if (!isBackupKey(source)) {
            setBackupNotice("Insert a Terminal Backup Key");
            return false;
        }

        NBTTagCompound snapshot = createBackupSnapshot();
        if (!TerminalBackupData.write(source, snapshot, world.provider.getDimension(), pos)) {
            setBackupNotice("Terminal data is too large");
            return false;
        }

        backupSuccess = false;
        successTicks = 0;
        lastSyncedBackupFrame = -1;
        restoreMode = false;
        markDirty();
        setBackupNotice("Uploaded " + nets.size() + " nets, "
                + notepadDocuments.size() + " documents");
        return true;
    }

    /** Server side: starts the ten-second copy from the left key to the fresh right key. */
    public boolean startBackup() {
        if (world == null || world.isRemote || backupActive) return false;
        if (getBackupFailure() != BackupFailure.NONE) return false;

        backupActive = true;
        restoreMode = false;
        backupSuccess = false;
        backupTicks = BACKUP_TICKS;
        successTicks = 0;
        lastSyncedBackupFrame = 0;
        backupNotice = "";
        backupNoticeTicks = 0;
        sync();
        return true;
    }

    /** Server side: starts the ten-second restore from the key in the left slot. */
    public boolean startRestore() {
        if (world == null || world.isRemote || backupActive) return false;
        if (getRestoreFailure() != RestoreFailure.NONE) return false;
        if (TerminalBackupData.read(backupSlots[BACKUP_SLOT_LEFT]) == null) {
            setBackupNotice("Backup key is unreadable");
            return false;
        }

        backupActive = true;
        restoreMode = true;
        backupSuccess = false;
        backupTicks = BACKUP_TICKS;
        successTicks = 0;
        lastSyncedBackupFrame = 0;
        backupNotice = "";
        backupNoticeTicks = 0;
        sync();
        return true;
    }

    public boolean isBackingUp() {
        return backupActive;
    }

    public boolean isRestoreMode() {
        return restoreMode;
    }

    public boolean isBackupSuccess() {
        return backupSuccess;
    }

    /** 0..1 progress, including the brief full-bar state after success. */
    public float getBackupProgress() {
        if (backupSuccess) return 1F;
        if (!backupActive) return 0F;
        return 1F - (float) backupTicks / (float) BACKUP_TICKS;
    }

    /** Index into the five supplied GUI frames: 0, 25, 50, 75 or 100 percent. */
    public int getBackupFrame() {
        int frame = (int) (getBackupProgress() * 4F);
        return Math.max(0, Math.min(4, frame));
    }

    public String getBackupNotice() {
        return backupNoticeTicks > 0 ? backupNotice : "";
    }

    /** Builds the uncompressed terminal snapshot stored on a backup key. */
    public NBTTagCompound createBackupSnapshot() {
        NBTTagCompound snapshot = new NBTTagCompound();
        snapshot.setInteger("Schema", TerminalBackupData.SCHEMA);
        snapshot.setString("PasswordHash", passwordHash);
        snapshot.setBoolean("PasswordSet", passwordSet);
        snapshot.setBoolean("PasswordSkipped", passwordSkipped);

        NBTTagList netList = new NBTTagList();
        for (SecurityNet net : nets) netList.appendTag(net.toNBT());
        snapshot.setTag("SecurityNets", netList);
        snapshot.setInteger("NextNetId", nextNetId);

        NBTTagList noteList = new NBTTagList();
        for (NotepadDocument document : notepadDocuments) {
            noteList.appendTag(document.toNBT());
        }
        snapshot.setTag("Notepad", noteList);
        snapshot.setInteger("NextNotepadId", nextNotepadId);

        // Screen states include the per-screen wallpapers and the last stable terminal page.
        writeScreenStates(snapshot, false);
        return snapshot;
    }

    private void setBackupNotice(String notice) {
        backupNotice = notice == null ? "" : notice;
        backupNoticeTicks = backupNotice.isEmpty() ? 0 : BACKUP_NOTICE_TICKS;
        sync();
    }

    /** Server side: advances the copy bar, then copies the source key's NBT to the target key. */
    private void updateBackup() {
        if (backupActive) {
            if (backupTicks > 0) backupTicks--;
            if (backupTicks <= 0) {
                if (restoreMode) {
                    completeRestore();
                } else {
                    completeBackup();
                }
            } else {
                int frame = getBackupFrame();
                if (frame != lastSyncedBackupFrame) {
                    lastSyncedBackupFrame = frame;
                    sync();
                }
            }
        } else if (backupSuccess) {
            if (successTicks > 0) successTicks--;
            if (successTicks <= 0) {
                backupSuccess = false;
                lastSyncedBackupFrame = -1;
                sync();
            }
        }

        if (backupNoticeTicks > 0) {
            backupNoticeTicks--;
            if (backupNoticeTicks == 0) {
                backupNotice = "";
                sync();
            }
        }
    }

    private void completeBackup() {
        if (getBackupFailure() != BackupFailure.NONE) {
            cancelBackup(true);
            return;
        }

        ItemStack source = backupSlots[BACKUP_SLOT_LEFT];
        ItemStack target = backupSlots[BACKUP_SLOT_RIGHT];
        NBTTagCompound sourceTag = source.getTagCompound();
        if (sourceTag == null || sourceTag.isEmpty()) {
            cancelBackup(true);
            return;
        }

        target.setTagCompound(sourceTag.copy());
        markDirty();

        backupActive = false;
        backupTicks = 0;
        backupSuccess = true;
        successTicks = BACKUP_SUCCESS_HOLD_TICKS;
        lastSyncedBackupFrame = 4;
        backupNotice = "Backup copied";
        backupNoticeTicks = BACKUP_NOTICE_TICKS;
        world.playSound(null, pos, ModSounds.SCANNER_BUTTON3, SoundCategory.BLOCKS,
                BACKUP_VOLUME, 1F);
        sync();
    }

    private void completeRestore() {
        if (getRestoreFailure() != RestoreFailure.NONE) {
            cancelBackup(true);
            return;
        }

        ItemStack source = backupSlots[BACKUP_SLOT_LEFT];
        NBTTagCompound snapshot = TerminalBackupData.read(source);
        if (snapshot == null) {
            cancelBackup(true);
            setBackupNotice("Backup key is unreadable");
            return;
        }
        if (!applyBackupSnapshot(snapshot)) {
            cancelBackup(true);
            return;
        }

        backupActive = false;
        backupTicks = 0;
        restoreMode = true;
        backupSuccess = true;
        successTicks = BACKUP_SUCCESS_HOLD_TICKS;
        lastSyncedBackupFrame = 4;
        backupNotice = "Terminal restored";
        backupNoticeTicks = BACKUP_NOTICE_TICKS;
        world.playSound(null, pos, ModSounds.SCANNER_BUTTON3, SoundCategory.BLOCKS,
                BACKUP_VOLUME, 1F);
        sync();
    }

    /**
     * Reuses the world-save reader for a snapshot. The saved x/y/z must be replaced with this
     * desk's position so the live tile entity is not moved to the source desk.
     */
    private boolean applyBackupSnapshot(NBTTagCompound snapshot) {
        if (snapshot == null || world == null || world.isRemote) return false;
        String currentHash = passwordHash;
        boolean currentSet = passwordSet;
        boolean currentSkipped = passwordSkipped;

        NBTTagCompound copy = snapshot.copy();
        // A snapshot is terminal data, not desk storage or live client cache.
        copy.removeTag("BackupItems");
        copy.removeTag("BackupActive");
        copy.removeTag("BackupRestoreMode");
        copy.removeTag("BackupTicks");
        copy.removeTag("BackupSuccess");
        copy.removeTag("BackupSuccessTicks");
        copy.removeTag("BackupNotice");
        copy.removeTag("BackupNoticeTicks");
        copy.removeTag("DisplayNets");
        copy.removeTag("DisplayNetData");
        copy.removeTag("DisplayNotepadNames");
        copy.removeTag("DisplayNotepadTexts");
        copy.setInteger("x", pos.getX());
        copy.setInteger("y", pos.getY());
        copy.setInteger("z", pos.getZ());
        readFromNBT(copy);

        // Backups made before password support did not capture the lock; keep the current one.
        if (!copy.hasKey("PasswordSet")) {
            passwordHash = currentHash;
            passwordSet = currentSet;
            passwordSkipped = currentSkipped;
        }

        // Reuse the normal load-time validation for restored screen/net/document ids.
        onLoad();
        displayRevision++;
        return true;
    }

    /** Cancels a running backup; playSound is false when the block is being removed. */
    private void cancelBackup(boolean playSound) {
        if (!backupActive && !backupSuccess) return;
        boolean wasActive = backupActive;
        backupActive = false;
        backupTicks = 0;
        backupSuccess = false;
        successTicks = 0;
        lastSyncedBackupFrame = -1;
        if (playSound && wasActive && world != null) {
            world.playSound(null, pos, ModSounds.SCANNER_BUTTON8, SoundCategory.BLOCKS,
                    BACKUP_VOLUME, 1F);
        }
        markDirty();
        if (world != null && !world.isRemote) sync();
    }

    private void onBackupSlotChanged() {
        if (world == null || world.isRemote) return;
        if (backupActive) {
            cancelBackup(true);
            setBackupNotice("Operation cancelled");
        } else if (backupSuccess) {
            backupSuccess = false;
            successTicks = 0;
            lastSyncedBackupFrame = -1;
            sync();
        }
    }

    // ------------------------------------------------------------ backup sound

    @SideOnly(Side.CLIENT)
    private void updateClientSound() {
        if (backupActive) {
            if (backupSound == null) {
                backupSound = new BackupLoopSound(this, this::isBackingUp,
                        ModSounds.SCANNER_BELL1, BACKUP_LOOP_VOLUME);
                Minecraft.getMinecraft().getSoundHandler().playSound(backupSound);
            }
        } else {
            stopClientSound();
        }
    }

    @SideOnly(Side.CLIENT)
    private void stopClientSound() {
        if (backupSound == null) return;
        SoundHandler handler = Minecraft.getMinecraft().getSoundHandler();
        if (handler != null) handler.stopSound(backupSound);
        backupSound = null;
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

    public List<SecurityNet> getNets() {
        return Collections.unmodifiableList(nets);
    }

    /** The cosmetic wallpaper shown by one physical screen; empty means the terminal page. */
    public String getScreenWallpaper(int index) {
        if (index < 0 || index >= MAX_SCREENS) return "";
        String value = screens[index].wallpaper;
        return value == null ? "" : value;
    }

    /** Server side: stores one screen's cosmetic wallpaper description. */
    public boolean setScreenWallpaper(int index, String wallpaper) {
        if (world == null || world.isRemote) return false;
        if (index < 0 || index >= MAX_SCREENS) return false;
        String normalized = MonitorWallpaper.normalize(wallpaper);
        String current = getScreenWallpaper(index);
        if (current.equals(normalized)) return false;
        screens[index].wallpaper = normalized;
        sync();
        return true;
    }

    public int getScreenStage(int index) {
        return index >= 0 && index < MAX_SCREENS
                ? screens[index].stage : MonitorDisplay.DEFAULT_STAGE;
    }

    public int getScreenNetId(int index) {
        return index >= 0 && index < MAX_SCREENS ? screens[index].netId : -1;
    }

    public int getScreenNotepadDocId(int index) {
        return index >= 0 && index < MAX_SCREENS ? screens[index].notepadDocId : -1;
    }

    public boolean getScreenEventsTab(int index) {
        return index >= 0 && index < MAX_SCREENS && screens[index].eventsTab;
    }

    public int getScreenNetsScroll(int index) {
        return index >= 0 && index < MAX_SCREENS ? screens[index].netsScroll : 0;
    }

    public int getScreenSecurityScroll(int index) {
        return index >= 0 && index < MAX_SCREENS ? screens[index].securityScroll : 0;
    }

    public int getScreenEventsScroll(int index) {
        return index >= 0 && index < MAX_SCREENS ? screens[index].eventsScroll : 0;
    }

    public List<SecurityNetSummary> getDisplayNets() {
        return Collections.unmodifiableList(displayNets);
    }

    @Nullable
    public MonitorDisplay.NetData getDisplayNetData(int netId) {
        return displayNetData.get(netId);
    }

    /** Client-side snapshot of the document names and the text of the visible documents. */
    public List<NotepadDocument> getDisplayNotepadDocuments() {
        List<NotepadDocument> documents = new ArrayList<>(displayNotepadNames.size());
        for (NotepadDocument document : displayNotepadNames) {
            String text = displayNotepadTexts.get(document.id);
            documents.add(new NotepadDocument(document.id, document.getName(),
                    text == null ? "" : text));
        }
        return documents;
    }

    /**
     * Server side: stores one physical screen's terminal page. Every screen keeps its own page,
     * net, tab and scroll state, so using one monitor never changes the others.
     */
    public boolean setScreenDisplayState(int screenIndex, int stage, int netId,
                                         int notepadDocId, boolean eventsTab, int netsScroll,
                                         int securityScroll, int eventsScroll,
                                         @Nullable UUID viewer, boolean viewing) {
        if (world == null || world.isRemote) return false;
        if (screenIndex < 0 || screenIndex >= MAX_SCREENS) return false;
        if (viewing && viewer == null) return false;
        if (!MonitorDisplay.isValidStage(stage)) stage = MonitorDisplay.DEFAULT_STAGE;

        if (stage == MonitorDisplay.STAGE_NOTEPAD) {
            if (getNotepadDocument(notepadDocId) == null) {
                notepadDocId = notepadDocuments.isEmpty() ? -1 : notepadDocuments.get(0).id;
            }
            netId = -1;
        } else if (stage == MonitorDisplay.STAGE_SECURITY
                || stage == MonitorDisplay.STAGE_DELETE_NET
                || stage == MonitorDisplay.STAGE_NAME_KEY) {
            if (getNet(netId) == null) {
                netId = -1;
                if (stage != MonitorDisplay.STAGE_NAME_KEY) {
                    stage = MonitorDisplay.STAGE_SECURITY_HOME;
                }
            }
            notepadDocId = -1;
        } else {
            netId = -1;
            notepadDocId = -1;
        }

        boolean stable = isStableStage(stage);
        if (stable) {
            screens[screenIndex].resumeStage = stage;
            screens[screenIndex].resumeNetId = netId;
            screens[screenIndex].resumeNotepadDocId = notepadDocId;
            screens[screenIndex].resumeEventsTab = eventsTab;
            screens[screenIndex].resumeNetsScroll = Math.max(0, netsScroll);
            screens[screenIndex].resumeSecurityScroll = Math.max(0, securityScroll);
            screens[screenIndex].resumeEventsScroll = Math.max(0, eventsScroll);
        }

        if (!viewing && !stable) {
            // Closing on the lock screen or a prompt leaves the last real page visible.
            restoreResumeDisplay(screenIndex);
        } else {
            screens[screenIndex].stage = stage;
            screens[screenIndex].netId = netId;
            screens[screenIndex].notepadDocId = notepadDocId;
            screens[screenIndex].eventsTab = eventsTab;
            screens[screenIndex].netsScroll = Math.max(0, netsScroll);
            screens[screenIndex].securityScroll = Math.max(0, securityScroll);
            screens[screenIndex].eventsScroll = Math.max(0, eventsScroll);
        }
        boolean wasViewing = screens[screenIndex].viewing;
        screens[screenIndex].viewing = viewing;
        if (wasViewing != viewing) {
            viewingScreens = Math.max(0, viewingScreens + (viewing ? 1 : -1));
        }
        screens[screenIndex].viewer = viewing ? viewer : null;
        sync();
        return true;
    }

    private void refreshViewingCount() {
        int count = 0;
        for (ScreenState screen : screens) {
            if (screen.viewing) count++;
        }
        viewingScreens = count;
    }

    private static boolean isStableStage(int stage) {
        return stage == MonitorDisplay.STAGE_MENU
                || stage == MonitorDisplay.STAGE_SECURITY_HOME
                || stage == MonitorDisplay.STAGE_SECURITY
                || stage == MonitorDisplay.STAGE_NOTEPAD;
    }

    private void restoreResumeDisplay(int index) {
        screens[index].stage = screens[index].resumeStage;
        screens[index].netId = screens[index].resumeNetId;
        screens[index].notepadDocId = screens[index].resumeNotepadDocId;
        screens[index].eventsTab = screens[index].resumeEventsTab;
        screens[index].netsScroll = screens[index].resumeNetsScroll;
        screens[index].securityScroll = screens[index].resumeSecurityScroll;
        screens[index].eventsScroll = screens[index].resumeEventsScroll;
    }

    public int getScreenResumeStage(int index) {
        return index >= 0 && index < MAX_SCREENS
                ? screens[index].resumeStage : MonitorDisplay.DEFAULT_STAGE;
    }

    public int getScreenResumeNetId(int index) {
        return index >= 0 && index < MAX_SCREENS ? screens[index].resumeNetId : -1;
    }

    public boolean getScreenResumeEventsTab(int index) {
        return index >= 0 && index < MAX_SCREENS && screens[index].resumeEventsTab;
    }

    public int getScreenResumeNetsScroll(int index) {
        return index >= 0 && index < MAX_SCREENS ? screens[index].resumeNetsScroll : 0;
    }

    public int getScreenResumeSecurityScroll(int index) {
        return index >= 0 && index < MAX_SCREENS ? screens[index].resumeSecurityScroll : 0;
    }

    public int getScreenResumeEventsScroll(int index) {
        return index >= 0 && index < MAX_SCREENS ? screens[index].resumeEventsScroll : 0;
    }

    /** Clears stale live-view flags if a viewer left the desk or logged out. */
    @Override
    public void update() {
        if (world == null) return;
        if (world.isRemote) {
            updateClientSound();
            return;
        }
        if (backupActive || backupSuccess || backupNoticeTicks > 0) {
            updateBackup();
        }
        if (viewingScreens <= 0) return;
        boolean changed = false;
        for (int i = 0; i < MAX_SCREENS; i++) {
            if (!screens[i].viewing) continue;
            UUID viewer = screens[i].viewer;
            EntityPlayer player = viewer == null ? null : world.getPlayerEntityByUUID(viewer);
            if (player == null || player.world != world || !NetworkUtil.isWithinReach(player, pos)) {
                screens[i].viewing = false;
                screens[i].viewer = null;
                viewingScreens = Math.max(0, viewingScreens - 1);
                if (!isStableStage(screens[i].stage)) restoreResumeDisplay(i);
                changed = true;
            }
        }
        if (changed) sync();
    }

    /** The core renders every screen cell, so its culling box must cover the whole desk. */
    @Override
    public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() {
        return new net.minecraft.util.math.AxisAlignedBB(
                pos.add(-2, 0, -2), pos.add(3, 3, 3));
    }

    @Nullable
    public SecurityNet getNet(int id) {
        for (SecurityNet net : nets) {
            if (net.id == id) return net;
        }
        return null;
    }

    /** Server side: creates an empty net; a blank name falls back to "Security net N". */
    @Nullable
    public SecurityNet createNet(String name) {
        if (world != null && world.isRemote) return null;
        SecurityNet net = new SecurityNet(nextNetId++, name);
        nets.add(net);
        SecurityEvents.updateDesk(this);
        sync();
        return net;
    }

    /** Server side: removes one net and returns it, or null when the net does not exist. */
    @Nullable
    public SecurityNet removeNet(int netId) {
        if (world != null && world.isRemote) return null;
        for (int i = 0; i < nets.size(); i++) {
            SecurityNet net = nets.get(i);
            if (net.id == netId) {
                nets.remove(i);
                SecurityEvents.updateDesk(this);
                sync();
                return net;
            }
        }
        return null;
    }

    /** Server side: replaces one net's confirmed device list. */
    public boolean setNetDevices(int netId, List<MonitorDevice> newDevices) {
        if (world != null && world.isRemote) return false;
        SecurityNet net = getNet(netId);
        if (net == null) return false;
        net.setDevices(newDevices);
        SecurityEvents.updateDesk(this);
        sync();
        return true;
    }

    /** Server side: stores one Events tab toggle for a net. */
    public boolean setNetEvent(int netId, int eventId, boolean enabled) {
        if (world != null && world.isRemote) return false;
        SecurityNet net = getNet(netId);
        if (net == null) return false;
        net.setEventEnabled(eventId, enabled);
        SecurityEvents.updateDesk(this);
        sync();
        return true;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        SecurityEvents.updateDesk(this);
        if (world == null || world.isRemote) return;
        for (int i = 0; i < MAX_SCREENS; i++) {
            if (screens[i].netId >= 0 && getNet(screens[i].netId) == null) {
                screens[i].netId = -1;
                if (screens[i].stage == MonitorDisplay.STAGE_SECURITY
                        || screens[i].stage == MonitorDisplay.STAGE_DELETE_NET
                        || screens[i].stage == MonitorDisplay.STAGE_NAME_KEY) {
                    screens[i].stage = MonitorDisplay.STAGE_SECURITY_HOME;
                }
            }
            if (screens[i].resumeNetId >= 0 && getNet(screens[i].resumeNetId) == null) {
                screens[i].resumeNetId = -1;
                if (screens[i].resumeStage == MonitorDisplay.STAGE_SECURITY
                        || screens[i].resumeStage == MonitorDisplay.STAGE_DELETE_NET
                        || screens[i].resumeStage == MonitorDisplay.STAGE_NAME_KEY) {
                    screens[i].resumeStage = MonitorDisplay.STAGE_SECURITY_HOME;
                }
            }
            if (screens[i].stage == MonitorDisplay.STAGE_NOTEPAD
                    && getNotepadDocument(screens[i].notepadDocId) == null) {
                screens[i].notepadDocId = notepadDocuments.isEmpty()
                        ? -1 : notepadDocuments.get(0).id;
                if (screens[i].notepadDocId < 0) {
                    screens[i].stage = MonitorDisplay.STAGE_MENU;
                }
            }
            if (screens[i].resumeStage == MonitorDisplay.STAGE_NOTEPAD
                    && getNotepadDocument(screens[i].resumeNotepadDocId) == null) {
                screens[i].resumeNotepadDocId = notepadDocuments.isEmpty()
                        ? -1 : notepadDocuments.get(0).id;
                if (screens[i].resumeNotepadDocId < 0) {
                    screens[i].resumeStage = MonitorDisplay.STAGE_MENU;
                }
            }
        }
    }

    /** Server side: links one key id/name to a net. */
    public boolean linkKey(int netId, String keyId, String keyName) {
        if (world != null && world.isRemote) return false;
        SecurityNet net = getNet(netId);
        if (net == null) return false;
        net.linkKey(keyId, keyName);
        // Notify clients as well: the linked key name is drawn on the physical monitors.
        sync();
        return true;
    }

    /** Server side: clears a net's key link. */
    public boolean unlinkKey(int netId) {
        if (world != null && world.isRemote) return false;
        SecurityNet net = getNet(netId);
        if (net == null) return false;
        net.unlinkKey();
        sync();
        return true;
    }

    public List<NotepadDocument> getNotepadDocuments() {
        return Collections.unmodifiableList(notepadDocuments);
    }

    @Nullable
    public NotepadDocument getNotepadDocument(int id) {
        for (NotepadDocument document : notepadDocuments) {
            if (document.id == id) return document;
        }
        return null;
    }

    /** Server side: creates a new blank note with an automatic name. */
    @Nullable
    public NotepadDocument createNotepadDocument() {
        if (world != null && world.isRemote) return null;
        if (notepadDocuments.size() >= NotepadDocument.MAX_DOCUMENTS) return null;
        int id = nextNotepadId++;
        String base = "Document " + (notepadDocuments.size() + 1);
        String name = base;
        int suffix = 2;
        while (nameExists(name)) {
            name = base + " (" + suffix++ + ")";
        }
        NotepadDocument document = new NotepadDocument(id, name, "");
        notepadDocuments.add(document);
        sync();
        return document;
    }

    private boolean nameExists(String name) {
        for (NotepadDocument document : notepadDocuments) {
            if (document.getName().equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    public boolean saveNotepadText(int id, String text) {
        if (world != null && world.isRemote) return false;
        NotepadDocument document = getNotepadDocument(id);
        if (document == null) return false;
        document.setText(text);
        sync();
        return true;
    }

    public boolean renameNotepadDocument(int id, String name) {
        if (world != null && world.isRemote) return false;
        NotepadDocument document = getNotepadDocument(id);
        if (document == null) return false;
        document.setName(name);
        sync();
        return true;
    }

    public boolean deleteNotepadDocument(int id) {
        if (world != null && world.isRemote) return false;
        for (int i = 0; i < notepadDocuments.size(); i++) {
            if (notepadDocuments.get(i).id == id) {
                notepadDocuments.remove(i);
                notepadDocumentRemoved(id);
                sync();
                return true;
            }
        }
        return false;
    }

    /** Keeps physical screens on a valid document after the selected note is deleted. */
    private void notepadDocumentRemoved(int removedId) {
        int fallback = notepadDocuments.isEmpty() ? -1 : notepadDocuments.get(0).id;
        for (int i = 0; i < MAX_SCREENS; i++) {
            if (screens[i].notepadDocId == removedId) {
                screens[i].notepadDocId = fallback;
                if (fallback < 0 && screens[i].stage == MonitorDisplay.STAGE_NOTEPAD) {
                    screens[i].stage = MonitorDisplay.STAGE_MENU;
                }
            }
            if (screens[i].resumeNotepadDocId == removedId) {
                screens[i].resumeNotepadDocId = fallback;
                if (fallback < 0 && screens[i].resumeStage == MonitorDisplay.STAGE_NOTEPAD) {
                    screens[i].resumeStage = MonitorDisplay.STAGE_MENU;
                }
            }
        }
    }

    /** Drops both backup keys into the world when the desk is broken. */
    public void dropBackupContents(World world, BlockPos pos) {
        cancelBackup(false);
        for (int i = 0; i < BACKUP_SLOT_COUNT; i++) {
            ItemStack stack = backupSlots[i];
            if (stack.isEmpty()) continue;
            InventoryHelper.spawnItemStack(world, pos.getX() + 0.5D, pos.getY() + 0.5D,
                    pos.getZ() + 0.5D, stack);
            backupSlots[i] = ItemStack.EMPTY;
        }
        markDirty();
    }

    /** Inventory adapter for the two backup slots; the container and slots only see this view. */
    private final class BackupInventory implements IInventory {

        @Override
        public int getSizeInventory() {
            return BACKUP_SLOT_COUNT;
        }

        @Override
        public boolean isEmpty() {
            return backupSlots[BACKUP_SLOT_LEFT].isEmpty()
                    && backupSlots[BACKUP_SLOT_RIGHT].isEmpty();
        }

        @Override
        public ItemStack getStackInSlot(int index) {
            return getBackupSlot(index);
        }

        @Override
        public ItemStack decrStackSize(int index, int count) {
            if (index < 0 || index >= BACKUP_SLOT_COUNT) return ItemStack.EMPTY;
            ItemStack stack = backupSlots[index];
            if (stack.isEmpty()) return ItemStack.EMPTY;

            ItemStack taken = stack.splitStack(count);
            if (stack.isEmpty()) backupSlots[index] = ItemStack.EMPTY;
            if (!taken.isEmpty()) onBackupSlotChanged();
            markDirty();
            return taken;
        }

        @Override
        public ItemStack removeStackFromSlot(int index) {
            if (index < 0 || index >= BACKUP_SLOT_COUNT) return ItemStack.EMPTY;
            ItemStack stack = backupSlots[index];
            backupSlots[index] = ItemStack.EMPTY;
            if (!stack.isEmpty()) {
                onBackupSlotChanged();
                markDirty();
            }
            return stack;
        }

        @Override
        public void setInventorySlotContents(int index, ItemStack stack) {
            if (index < 0 || index >= BACKUP_SLOT_COUNT) return;
            backupSlots[index] = stack == null ? ItemStack.EMPTY : stack;
            if (!backupSlots[index].isEmpty()
                    && backupSlots[index].getCount() > getInventoryStackLimit()) {
                backupSlots[index].setCount(getInventoryStackLimit());
            }
            onBackupSlotChanged();
            markDirty();
        }

        @Override
        public int getInventoryStackLimit() {
            return 1;
        }

        @Override
        public void markDirty() {
            TileEntityMonitorDesk.this.markDirty();
        }

        @Override
        public boolean isUsableByPlayer(EntityPlayer player) {
            return world != null && world.getTileEntity(pos) == TileEntityMonitorDesk.this
                    && player.getDistanceSq(pos.getX() + 0.5D, pos.getY() + 0.5D,
                    pos.getZ() + 0.5D) <= 64D;
        }

        @Override
        public void openInventory(EntityPlayer player) {
        }

        @Override
        public void closeInventory(EntityPlayer player) {
        }

        @Override
        public boolean isItemValidForSlot(int index, ItemStack stack) {
            return isBackupKey(stack);
        }

        @Override
        public int getField(int id) {
            return 0;
        }

        @Override
        public void setField(int id, int value) {
        }

        @Override
        public int getFieldCount() {
            return 0;
        }

        @Override
        public void clear() {
            boolean hadItem = !isEmpty();
            Arrays.fill(backupSlots, ItemStack.EMPTY);
            if (hadItem) onBackupSlotChanged();
            markDirty();
        }

        @Override
        public String getName() {
            return "container.ntmdoors.terminal_backup";
        }

        @Override
        public boolean hasCustomName() {
            return false;
        }

        @Override
        public ITextComponent getDisplayName() {
            return new TextComponentTranslation(getName());
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setString("PasswordHash", passwordHash);
        tag.setBoolean("PasswordSet", passwordSet);
        tag.setBoolean("PasswordSkipped", passwordSkipped);
        NBTTagList list = new NBTTagList();
        for (SecurityNet net : nets) list.appendTag(net.toNBT());
        tag.setTag("SecurityNets", list);
        NBTTagList notepad = new NBTTagList();
        for (NotepadDocument document : notepadDocuments) {
            notepad.appendTag(document.toNBT());
        }
        tag.setTag("Notepad", notepad);
        tag.setInteger("NextNotepadId", nextNotepadId);
        tag.setInteger("NextNetId", nextNetId);
        writeScreenStates(tag, false);
        writeBackupState(tag);
        return tag;
    }

    private void writeBackupState(NBTTagCompound tag) {
        NBTTagList items = new NBTTagList();
        for (int i = 0; i < BACKUP_SLOT_COUNT; i++) {
            if (backupSlots[i].isEmpty()) continue;
            NBTTagCompound entry = new NBTTagCompound();
            entry.setByte("Slot", (byte) i);
            backupSlots[i].writeToNBT(entry);
            items.appendTag(entry);
        }
        tag.setTag("BackupItems", items);
        tag.setBoolean("BackupActive", backupActive);
        tag.setBoolean("BackupRestoreMode", restoreMode);
        tag.setInteger("BackupTicks", backupTicks);
        tag.setBoolean("BackupSuccess", backupSuccess);
        tag.setInteger("BackupSuccessTicks", successTicks);
        tag.setString("BackupNotice", backupNotice);
        tag.setInteger("BackupNoticeTicks", backupNoticeTicks);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        for (int i = 0; i < MAX_SCREENS; i++) screens[i].reset();
        if (tag.hasKey("PasswordHash")) {
            passwordHash = tag.getString("PasswordHash");
        }
        if (tag.hasKey("PasswordSet")) {
            passwordSet = tag.getBoolean("PasswordSet");
        } else {
            passwordSet = !passwordHash.isEmpty();
        }
        passwordSkipped = tag.getBoolean("PasswordSkipped");
        if (tag.hasKey("BackupItems", Constants.NBT.TAG_LIST)) {
            Arrays.fill(backupSlots, ItemStack.EMPTY);
            NBTTagList backupItems = tag.getTagList("BackupItems", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < backupItems.tagCount(); i++) {
                NBTTagCompound entry = backupItems.getCompoundTagAt(i);
                int slot = entry.getByte("Slot") & 0xFF;
                if (slot >= 0 && slot < BACKUP_SLOT_COUNT) {
                    backupSlots[slot] = new ItemStack(entry);
                }
            }
        }
        backupActive = tag.getBoolean("BackupActive");
        restoreMode = tag.getBoolean("BackupRestoreMode");
        backupTicks = tag.getInteger("BackupTicks");
        backupSuccess = tag.getBoolean("BackupSuccess");
        successTicks = tag.getInteger("BackupSuccessTicks");
        backupNotice = tag.getString("BackupNotice");
        backupNoticeTicks = tag.getInteger("BackupNoticeTicks");
        lastSyncedBackupFrame = -1;
        if (tag.hasKey("Notepad", Constants.NBT.TAG_LIST)) {
            notepadDocuments.clear();
            NBTTagList notes = tag.getTagList("Notepad", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < notes.tagCount()
                    && notepadDocuments.size() < NotepadDocument.MAX_DOCUMENTS; i++) {
                notepadDocuments.add(NotepadDocument.fromNBT(notes.getCompoundTagAt(i)));
            }
            nextNotepadId = tag.getInteger("NextNotepadId");
            for (NotepadDocument document : notepadDocuments) {
                if (document.id >= nextNotepadId) nextNotepadId = document.id + 1;
            }
        }
        displayNets.clear();
        if (tag.hasKey("DisplayNets", Constants.NBT.TAG_LIST)) {
            NBTTagList displayList = tag.getTagList("DisplayNets", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < displayList.tagCount(); i++) {
                NBTTagCompound netTag = displayList.getCompoundTagAt(i);
                displayNets.add(new SecurityNetSummary(
                        netTag.getInteger("Id"), netTag.getString("Name"),
                        netTag.getInteger("DeviceCount")));
            }
        }

        displayNetData.clear();
        if (tag.hasKey("DisplayNetData", Constants.NBT.TAG_LIST)) {
            NBTTagList dataList = tag.getTagList("DisplayNetData", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < dataList.tagCount(); i++) {
                NBTTagCompound data = dataList.getCompoundTagAt(i);
                int id = data.getInteger("Id");
                List<MonitorDevice> devices = MonitorDevice.fromNBTList(
                        data.getTagList("Devices", Constants.NBT.TAG_COMPOUND));
                displayNetData.put(id, new MonitorDisplay.NetData(id,
                        data.getString("Name"), data.getInteger("EventMask"),
                        data.getString("KeyName"), devices));
            }
        }

        displayNotepadNames.clear();
        displayNotepadTexts.clear();
        if (tag.hasKey("DisplayNotepadNames", Constants.NBT.TAG_LIST)) {
            NBTTagList names = tag.getTagList("DisplayNotepadNames",
                    Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < names.tagCount(); i++) {
                NBTTagCompound note = names.getCompoundTagAt(i);
                displayNotepadNames.add(new NotepadDocument(note.getInteger("Id"),
                        note.getString("Name"), ""));
            }
        }
        if (tag.hasKey("DisplayNotepadTexts", Constants.NBT.TAG_LIST)) {
            NBTTagList texts = tag.getTagList("DisplayNotepadTexts",
                    Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < texts.tagCount(); i++) {
                NBTTagCompound note = texts.getCompoundTagAt(i);
                displayNotepadTexts.put(note.getInteger("Id"), note.getString("Text"));
            }
        }

        if (tag.hasKey("ScreenStates", Constants.NBT.TAG_LIST)) {
            for (int i = 0; i < MAX_SCREENS; i++) {
                screens[i].reset();
            }
            NBTTagList screenList = tag.getTagList("ScreenStates", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < screenList.tagCount() && i < MAX_SCREENS; i++) {
                NBTTagCompound screen = screenList.getCompoundTagAt(i);
                screens[i].stage = screen.getInteger("Stage");
                screens[i].netId = screen.getInteger("NetId");
                screens[i].notepadDocId = screen.hasKey("NotepadDocId")
                        ? screen.getInteger("NotepadDocId") : -1;
                screens[i].eventsTab = screen.getBoolean("EventsTab");
                screens[i].viewing = screen.getBoolean("Viewing");
                screens[i].netsScroll = screen.getInteger("NetsScroll");
                screens[i].securityScroll = screen.getInteger("SecurityScroll");
                screens[i].eventsScroll = screen.getInteger("EventsScroll");
                screens[i].wallpaper = MonitorWallpaper.normalize(screen.getString("Wallpaper"));
                screens[i].resumeStage = screen.hasKey("ResumeStage")
                        ? screen.getInteger("ResumeStage") : screens[i].stage;
                screens[i].resumeNetId = screen.hasKey("ResumeNetId")
                        ? screen.getInteger("ResumeNetId") : screens[i].netId;
                screens[i].resumeNotepadDocId = screen.hasKey("ResumeNotepadDocId")
                        ? screen.getInteger("ResumeNotepadDocId") : screens[i].notepadDocId;
                screens[i].resumeEventsTab = screen.hasKey("ResumeEventsTab")
                        ? screen.getBoolean("ResumeEventsTab") : screens[i].eventsTab;
                screens[i].resumeNetsScroll = screen.hasKey("ResumeNetsScroll")
                        ? screen.getInteger("ResumeNetsScroll") : screens[i].netsScroll;
                screens[i].resumeSecurityScroll = screen.hasKey("ResumeSecurityScroll")
                        ? screen.getInteger("ResumeSecurityScroll") : screens[i].securityScroll;
                screens[i].resumeEventsScroll = screen.hasKey("ResumeEventsScroll")
                        ? screen.getInteger("ResumeEventsScroll") : screens[i].eventsScroll;
            }
        } else if (tag.hasKey("DisplayStage")) {
            // Migration from the one-display build: every screen starts on the same page.
            int stage = tag.getInteger("DisplayStage");
            int netId = tag.hasKey("DisplayNetId") ? tag.getInteger("DisplayNetId") : -1;
            boolean eventsTab = tag.getBoolean("DisplayEventsTab");
            int netsScroll = tag.getInteger("DisplayNetsScroll");
            int securityScroll = tag.getInteger("DisplaySecurityScroll");
            int eventsScroll = tag.getInteger("DisplayEventsScroll");
            for (int i = 0; i < MAX_SCREENS; i++) {
                screens[i].reset();
                screens[i].stage = stage;
                screens[i].netId = netId;
                screens[i].eventsTab = eventsTab;
                screens[i].netsScroll = netsScroll;
                screens[i].securityScroll = securityScroll;
                screens[i].eventsScroll = eventsScroll;
                screens[i].resumeStage = stage;
                screens[i].resumeNetId = netId;
                screens[i].resumeEventsTab = eventsTab;
                screens[i].resumeNetsScroll = netsScroll;
                screens[i].resumeSecurityScroll = securityScroll;
                screens[i].resumeEventsScroll = eventsScroll;
            }
            if (netId >= 0 && tag.hasKey("DisplayNetName")) {
                displayNetData.put(netId, new MonitorDisplay.NetData(netId,
                        tag.getString("DisplayNetName"), tag.getInteger("DisplayEventMask"),
                        tag.getString("DisplayKeyName"),
                        MonitorDevice.fromNBTList(tag.getTagList(
                                "DisplayDevices", Constants.NBT.TAG_COMPOUND))));
            }
        }

        if (tag.hasKey("SecurityNets", Constants.NBT.TAG_LIST)) {
            nets.clear();
            NBTTagList list = tag.getTagList("SecurityNets", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < list.tagCount(); i++) {
                SecurityNet net = SecurityNet.fromNBT(list.getCompoundTagAt(i));
                if (net != null) nets.add(net);
            }
        } else if (tag.hasKey("Devices", Constants.NBT.TAG_LIST)) {
            // Migration from the one-net build.
            nets.clear();
            SecurityNet net = new SecurityNet(0, "");
            net.setDevices(MonitorDevice.fromNBTList(
                    tag.getTagList("Devices", Constants.NBT.TAG_COMPOUND)));
            nets.add(net);
        }

        nextNetId = tag.hasKey("NextNetId") ? tag.getInteger("NextNetId") : 0;
        for (SecurityNet net : nets) {
            if (net.id >= nextNetId) nextNetId = net.id + 1;
        }
        refreshViewingCount();
        displayRevision++;
    }

    /** Clients get the password flags, screen pages and the live display snapshot. */
    @Override
    protected NBTTagCompound writeSyncTag(NBTTagCompound tag) {
        // TileEntity.readFromNBT always reads x/y/z, so the stripped tag must still carry them.
        tag.setInteger("x", pos.getX());
        tag.setInteger("y", pos.getY());
        tag.setInteger("z", pos.getZ());
        tag.setBoolean("PasswordSet", passwordSet);
        tag.setBoolean("PasswordSkipped", passwordSkipped);
        tag.setBoolean("BackupActive", backupActive);
        tag.setBoolean("BackupRestoreMode", restoreMode);
        tag.setInteger("BackupTicks", backupTicks);
        tag.setBoolean("BackupSuccess", backupSuccess);
        tag.setInteger("BackupSuccessTicks", successTicks);
        tag.setString("BackupNotice", backupNotice);
        tag.setInteger("BackupNoticeTicks", backupNoticeTicks);
        writeDisplaySync(tag);
        return tag;
    }

    /** Writes one state row per physical screen. Viewing is transient and only sent to clients. */
    private void writeScreenStates(NBTTagCompound tag, boolean includeViewing) {
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < MAX_SCREENS; i++) {
            NBTTagCompound screen = new NBTTagCompound();
            screen.setInteger("Stage", screens[i].stage);
            screen.setInteger("NetId", screens[i].netId);
            screen.setInteger("NotepadDocId", screens[i].notepadDocId);
            screen.setBoolean("EventsTab", screens[i].eventsTab);
            screen.setInteger("NetsScroll", screens[i].netsScroll);
            screen.setInteger("SecurityScroll", screens[i].securityScroll);
            screen.setInteger("EventsScroll", screens[i].eventsScroll);
            screen.setString("Wallpaper", getScreenWallpaper(i));
            screen.setInteger("ResumeStage", screens[i].resumeStage);
            screen.setInteger("ResumeNetId", screens[i].resumeNetId);
            screen.setInteger("ResumeNotepadDocId", screens[i].resumeNotepadDocId);
            screen.setBoolean("ResumeEventsTab", screens[i].resumeEventsTab);
            screen.setInteger("ResumeNetsScroll", screens[i].resumeNetsScroll);
            screen.setInteger("ResumeSecurityScroll", screens[i].resumeSecurityScroll);
            screen.setInteger("ResumeEventsScroll", screens[i].resumeEventsScroll);
            if (includeViewing) screen.setBoolean("Viewing", screens[i].viewing);
            list.appendTag(screen);
        }
        tag.setTag("ScreenStates", list);
    }

    /**
     * Adds the compact data needed to draw every screen's current page. Net data is deduplicated
     * by net id, so six screens pointing at the same net only send its device list once.
     */
    private void writeDisplaySync(NBTTagCompound tag) {
        writeScreenStates(tag, true);

        boolean securityVisible = false;
        for (int i = 0; i < MAX_SCREENS; i++) {
            int stage = screens[i].stage;
            if (stage == MonitorDisplay.STAGE_SECURITY_HOME
                    || stage == MonitorDisplay.STAGE_SECURITY
                    || stage == MonitorDisplay.STAGE_DELETE_NET) {
                securityVisible = true;
                break;
            }
        }
        if (securityVisible) {
            NBTTagList netList = new NBTTagList();
            for (SecurityNet net : nets) {
                NBTTagCompound netTag = new NBTTagCompound();
                netTag.setInteger("Id", net.id);
                netTag.setString("Name", net.displayName());
                netTag.setInteger("DeviceCount", net.getDevices().size());
                netList.appendTag(netTag);
            }
            tag.setTag("DisplayNets", netList);

            NBTTagList dataList = new NBTTagList();
            Set<Integer> included = new HashSet<>();
            for (int i = 0; i < MAX_SCREENS; i++) {
                int netId = screens[i].netId;
                if (netId < 0 || !included.add(netId)) continue;
                SecurityNet selected = getNet(netId);
                if (selected == null) continue;
                NBTTagCompound data = new NBTTagCompound();
                data.setInteger("Id", selected.id);
                data.setString("Name", selected.displayName());
                data.setInteger("EventMask", selected.getEventMask());
                data.setString("KeyName", selected.getLinkedKeyName());
                List<MonitorDevice> devices = selected.getDevices();
                int limit = Math.min(devices.size(), 64);
                NBTTagList deviceList = new NBTTagList();
                for (int j = 0; j < limit; j++) {
                    deviceList.appendTag(devices.get(j).toNBT());
                }
                data.setTag("Devices", deviceList);
                dataList.appendTag(data);
            }
            tag.setTag("DisplayNetData", dataList);
        }

        boolean notepadVisible = false;
        for (int i = 0; i < MAX_SCREENS; i++) {
            if (screens[i].stage == MonitorDisplay.STAGE_NOTEPAD) {
                notepadVisible = true;
                break;
            }
        }
        if (!notepadVisible) return;

        NBTTagList noteNames = new NBTTagList();
        for (NotepadDocument document : notepadDocuments) {
            NBTTagCompound note = new NBTTagCompound();
            note.setInteger("Id", document.id);
            note.setString("Name", document.getName());
            noteNames.appendTag(note);
        }
        tag.setTag("DisplayNotepadNames", noteNames);

        NBTTagList noteTexts = new NBTTagList();
        Set<Integer> includedNotes = new HashSet<>();
        for (int i = 0; i < MAX_SCREENS; i++) {
            if (screens[i].stage != MonitorDisplay.STAGE_NOTEPAD) continue;
            int documentId = screens[i].notepadDocId;
            if (documentId < 0 || !includedNotes.add(documentId)) continue;
            NotepadDocument document = getNotepadDocument(documentId);
            if (document == null) continue;
            NBTTagCompound note = new NBTTagCompound();
            note.setInteger("Id", document.id);
            note.setString("Text", document.getText());
            noteTexts.appendTag(note);
        }
        tag.setTag("DisplayNotepadTexts", noteTexts);
    }
}
