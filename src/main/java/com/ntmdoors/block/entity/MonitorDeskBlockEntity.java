package com.ntmdoors.block.entity;

import com.ntmdoors.block.BlockMonitorDesk;
import com.ntmdoors.block.BlockMonitorSetup;
import com.ntmdoors.item.ItemTerminalBackupKey;
import com.ntmdoors.monitor.MonitorDevice;
import com.ntmdoors.monitor.MonitorDisplay;
import com.ntmdoors.monitor.MonitorWallpaper;
import com.ntmdoors.monitor.NotepadDocument;
import com.ntmdoors.monitor.SecurityEvents;
import com.ntmdoors.monitor.SecurityNet;
import com.ntmdoors.monitor.SecurityNetSummary;
import com.ntmdoors.monitor.TerminalBackupData;
import com.ntmdoors.screen.TerminalBackupScreenHandler;
import com.ntmdoors.sound.ModSounds;
import com.ntmdoors.util.MonitorPasswords;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Modern Fabric 1.20.1 implementation of the Monitor Desk Block Entity.
 * Hosted exclusively on the bottom-centre core cell of the multiblock desk.
 */
public class MonitorDeskBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory {

    public static final int MAX_SCREENS = 6;
    public static final int BACKUP_SLOT_LEFT = 0;
    public static final int BACKUP_SLOT_RIGHT = 1;
    public static final int BACKUP_SLOT_COUNT = 2;
    public static final int BACKUP_TICKS = 200; // 10 seconds @ 20 tps

    public enum BackupFailure {
        NONE,
        EMPTY,
        INVALID_ITEM,
        NO_DATA,
        TARGET_NOT_FRESH
    }

    public enum RestoreFailure {
        NONE,
        EMPTY,
        INVALID_ITEM,
        NO_DATA
    }

    private String passwordHash = "";
    private boolean passwordSet;
    private boolean passwordSkipped;
    private final List<SecurityNet> nets = new ArrayList<>();
    private final List<NotepadDocument> notepadDocuments = new ArrayList<>();
    private final String[] screenWallpapers = new String[MAX_SCREENS];
    private int nextNetId;
    private int nextNotepadId;
    private int displayRevision;

    // Terminal USB backup inventory & state machine
    private final SimpleInventory backupInventory = new SimpleInventory(BACKUP_SLOT_COUNT);
    private boolean backupActive;
    private boolean restoreMode;
    private int backupTicks;
    private boolean backupSuccess;

    // Per-screen terminal OS state
    private final int[] displayStages = new int[MAX_SCREENS];
    private final int[] displayNetIds = new int[MAX_SCREENS];
    private final int[] displayNotepadDocIds = new int[MAX_SCREENS];
    private final boolean[] displayEventsTabs = new boolean[MAX_SCREENS];
    private final boolean[] displayViewing = new boolean[MAX_SCREENS];
    private final UUID[] displayViewers = new UUID[MAX_SCREENS];

    // Last stable resume stage for private modal auto-restore
    private final int[] resumeStages = new int[MAX_SCREENS];
    private final int[] resumeNetIds = new int[MAX_SCREENS];
    private final int[] resumeNotepadDocIds = new int[MAX_SCREENS];

    // Atomic dismantle guard
    private final AtomicBoolean dismantling = new AtomicBoolean(false);
    private boolean creativeBroken = false;

    private final PropertyDelegate propertyDelegate = new PropertyDelegate() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> backupTicks;
                case 1 -> backupActive ? 1 : 0;
                case 2 -> backupSuccess ? 1 : 0;
                case 3 -> getBackupFailure().ordinal();
                case 4 -> restoreMode ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> backupTicks = value;
                case 1 -> backupActive = (value != 0);
                case 2 -> backupSuccess = (value != 0);
                case 4 -> restoreMode = (value != 0);
            }
        }

        @Override
        public int size() {
            return 5;
        }
    };

    public MonitorDeskBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MONITOR_DESK, pos, state);
        for (int i = 0; i < MAX_SCREENS; i++) {
            displayStages[i] = MonitorDisplay.DEFAULT_STAGE;
            displayNetIds[i] = -1;
            displayNotepadDocIds[i] = -1;
            resumeStages[i] = MonitorDisplay.DEFAULT_STAGE;
            resumeNetIds[i] = -1;
            resumeNotepadDocIds[i] = -1;
            screenWallpapers[i] = "";
        }
    }

    public boolean beginDismantle() {
        return dismantling.compareAndSet(false, true);
    }

    public boolean isCreativeBroken() {
        return creativeBroken;
    }

    public void setCreativeBroken(boolean creativeBroken) {
        this.creativeBroken = creativeBroken;
    }

    public boolean hasPassword() {
        return passwordSet;
    }

    public boolean isPasswordSkipped() {
        return passwordSkipped;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean setPasswordHash(String hash) {
        if (world != null && world.isClient()) return false;
        if (passwordSet || !MonitorPasswords.isValidHash(hash)) return false;
        this.passwordHash = hash;
        this.passwordSet = true;
        this.passwordSkipped = false;
        markDirty();
        sync();
        return true;
    }

    public boolean skipPasswordSetup() {
        if (world != null && world.isClient()) return false;
        if (passwordSet || passwordSkipped) return false;
        this.passwordSkipped = true;
        markDirty();
        sync();
        return true;
    }

    public boolean checkPasswordHash(String hash) {
        return passwordSet && MonitorPasswords.matches(passwordHash, hash);
    }

    public int getDisplayRevision() {
        return displayRevision;
    }

    public void bumpDisplayRevision() {
        displayRevision++;
        markDirty();
        sync();
    }

    // ------------------------------------------------------------ Screen & Wallpaper OS

    public int getScreenStage(int screenIndex) {
        if (screenIndex < 0 || screenIndex >= MAX_SCREENS) return MonitorDisplay.DEFAULT_STAGE;
        return displayStages[screenIndex];
    }

    public void setScreenStage(int screenIndex, int stage) {
        if (screenIndex < 0 || screenIndex >= MAX_SCREENS) return;
        displayStages[screenIndex] = stage;
        if (stage != MonitorDisplay.STAGE_ENTER && stage != MonitorDisplay.STAGE_CONFIRM
                && stage != MonitorDisplay.STAGE_DELETE_NET && stage != MonitorDisplay.STAGE_ASK) {
            resumeStages[screenIndex] = stage;
            resumeNetIds[screenIndex] = displayNetIds[screenIndex];
            resumeNotepadDocIds[screenIndex] = displayNotepadDocIds[screenIndex];
        }
        markDirty();
        sync();
    }

    public int getScreenNetId(int screenIndex) {
        if (screenIndex < 0 || screenIndex >= MAX_SCREENS) return -1;
        return displayNetIds[screenIndex];
    }

    public void setScreenNetId(int screenIndex, int netId) {
        if (screenIndex < 0 || screenIndex >= MAX_SCREENS) return;
        displayNetIds[screenIndex] = netId;
        resumeNetIds[screenIndex] = netId;
        markDirty();
        sync();
    }

    public int getScreenNotepadDocId(int screenIndex) {
        if (screenIndex < 0 || screenIndex >= MAX_SCREENS) return -1;
        return displayNotepadDocIds[screenIndex];
    }

    public void setScreenNotepadDocId(int screenIndex, int docId) {
        if (screenIndex < 0 || screenIndex >= MAX_SCREENS) return;
        displayNotepadDocIds[screenIndex] = docId;
        resumeNotepadDocIds[screenIndex] = docId;
        markDirty();
        sync();
    }

    public String getScreenWallpaper(int screenIndex) {
        if (screenIndex < 0 || screenIndex >= MAX_SCREENS) return "";
        return screenWallpapers[screenIndex] == null ? "" : screenWallpapers[screenIndex];
    }

    public void setScreenWallpaper(int screenIndex, String wallpaper) {
        if (screenIndex < 0 || screenIndex >= MAX_SCREENS) return;
        this.screenWallpapers[screenIndex] = MonitorWallpaper.normalize(wallpaper);
        markDirty();
        sync();
    }

    public boolean isScreenViewing(int screenIndex) {
        if (screenIndex < 0 || screenIndex >= MAX_SCREENS) return false;
        return displayViewing[screenIndex];
    }

    public void setScreenViewing(int screenIndex, boolean viewing, @Nullable UUID viewer) {
        if (screenIndex < 0 || screenIndex >= MAX_SCREENS) return;
        displayViewing[screenIndex] = viewing;
        displayViewers[screenIndex] = viewer;
        if (!viewing) {
            // Restore stable stage
            displayStages[screenIndex] = resumeStages[screenIndex];
            displayNetIds[screenIndex] = resumeNetIds[screenIndex];
            displayNotepadDocIds[screenIndex] = resumeNotepadDocIds[screenIndex];
        }
        markDirty();
        sync();
    }

    // ------------------------------------------------------------ Security Net Manager

    public List<SecurityNet> getNets() {
        return Collections.unmodifiableList(nets);
    }

    @Nullable
    public SecurityNet getNet(int id) {
        for (SecurityNet net : nets) {
            if (net.id == id) return net;
        }
        return null;
    }

    public SecurityNet createNet(@Nullable String name) {
        int id = nextNetId++;
        SecurityNet net = new SecurityNet(id, name);
        nets.add(net);
        SecurityEvents.updateDesk(this);
        markDirty();
        sync();
        return net;
    }

    public boolean removeNet(int id) {
        SecurityNet target = getNet(id);
        if (target != null && nets.remove(target)) {
            for (int i = 0; i < MAX_SCREENS; i++) {
                if (displayNetIds[i] == id) displayNetIds[i] = -1;
                if (resumeNetIds[i] == id) resumeNetIds[i] = -1;
            }
            SecurityEvents.updateDesk(this);
            markDirty();
            sync();
            return true;
        }
        return false;
    }

    public void renameNet(int id, String name) {
        SecurityNet net = getNet(id);
        if (net != null) {
            net.setName(name);
            markDirty();
            sync();
        }
    }

    public void setNetDevices(int netId, List<MonitorDevice> devices) {
        SecurityNet net = getNet(netId);
        if (net != null) {
            net.setDevices(devices);
            SecurityEvents.updateDesk(this);
            markDirty();
            sync();
        }
    }

    public void setNetEvent(int netId, int eventId, boolean enabled) {
        SecurityNet net = getNet(netId);
        if (net != null) {
            net.setEventEnabled(eventId, enabled);
            SecurityEvents.updateDesk(this);
            markDirty();
            sync();
        }
    }

    public List<SecurityNetSummary> getNetSummaries() {
        List<SecurityNetSummary> summaries = new ArrayList<>(nets.size());
        for (SecurityNet net : nets) {
            summaries.add(new SecurityNetSummary(net.id, net.displayName(), net.getDevices().size()));
        }
        return summaries;
    }

    // ------------------------------------------------------------ Multi-document Notepad

    public List<NotepadDocument> getNotepadDocuments() {
        return Collections.unmodifiableList(notepadDocuments);
    }

    @Nullable
    public NotepadDocument getNotepadDocument(int id) {
        for (NotepadDocument doc : notepadDocuments) {
            if (doc.getId() == id) return doc;
        }
        return null;
    }

    @Nullable
    public NotepadDocument createNotepadDocument() {
        if (notepadDocuments.size() >= NotepadDocument.MAX_DOCUMENTS) return null;
        int id = nextNotepadId++;
        String defaultName = "Document " + (notepadDocuments.size() + 1);
        NotepadDocument doc = new NotepadDocument(id, defaultName, "");
        notepadDocuments.add(doc);
        markDirty();
        sync();
        return doc;
    }

    public boolean saveNotepadText(int id, String text) {
        NotepadDocument doc = getNotepadDocument(id);
        if (doc != null) {
            doc.setText(text);
            markDirty();
            sync();
            return true;
        }
        return false;
    }

    public boolean renameNotepadDocument(int id, String name) {
        NotepadDocument doc = getNotepadDocument(id);
        if (doc != null) {
            doc.setName(name);
            markDirty();
            sync();
            return true;
        }
        return false;
    }

    public boolean deleteNotepadDocument(int id) {
        NotepadDocument doc = getNotepadDocument(id);
        if (doc != null && notepadDocuments.remove(doc)) {
            for (int i = 0; i < MAX_SCREENS; i++) {
                if (displayNotepadDocIds[i] == id) displayNotepadDocIds[i] = -1;
                if (resumeNotepadDocIds[i] == id) resumeNotepadDocIds[i] = -1;
            }
            markDirty();
            sync();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------ Terminal Backup Inventory & Machine

    public SimpleInventory getBackupInventory() {
        return backupInventory;
    }

    public ItemStack getBackupSlot(int index) {
        return index >= 0 && index < BACKUP_SLOT_COUNT ? backupInventory.getStack(index) : ItemStack.EMPTY;
    }

    public static boolean isBackupKey(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemTerminalBackupKey;
    }

    public BackupFailure getBackupFailure() {
        ItemStack source = backupInventory.getStack(BACKUP_SLOT_LEFT);
        ItemStack target = backupInventory.getStack(BACKUP_SLOT_RIGHT);
        if (source.isEmpty() || target.isEmpty()) return BackupFailure.EMPTY;
        if (!isBackupKey(source) || !isBackupKey(target)) return BackupFailure.INVALID_ITEM;
        if (!TerminalBackupData.hasBackup(source)) return BackupFailure.NO_DATA;
        NbtCompound targetTag = target.getNbt();
        if (targetTag != null && !targetTag.isEmpty()) return BackupFailure.TARGET_NOT_FRESH;
        return BackupFailure.NONE;
    }

    public RestoreFailure getRestoreFailure() {
        ItemStack source = backupInventory.getStack(BACKUP_SLOT_LEFT);
        if (source.isEmpty()) return RestoreFailure.EMPTY;
        if (!isBackupKey(source)) return RestoreFailure.INVALID_ITEM;
        if (!TerminalBackupData.hasBackup(source)) return RestoreFailure.NO_DATA;
        return RestoreFailure.NONE;
    }

    public boolean isBackingUp() {
        return backupActive;
    }

    public boolean isRestoreMode() {
        return restoreMode;
    }

    public boolean uploadBackupToDrive() {
        ItemStack source = backupInventory.getStack(BACKUP_SLOT_LEFT);
        if (!isBackupKey(source)) return false;
        NbtCompound snapshot = createSnapshot();
        String dim = world != null ? world.getRegistryKey().getValue().toString() : "minecraft:overworld";
        if (TerminalBackupData.write(source, snapshot, dim, pos)) {
            if (world != null && !world.isClient()) {
                world.playSound(null, pos, ModSounds.SCANNER_BUTTON3, SoundCategory.BLOCKS, 0.8F, 1.0F);
            }
            markDirty();
            return true;
        }
        return false;
    }

    public boolean startBackup() {
        if (backupActive || getBackupFailure() != BackupFailure.NONE) return false;
        this.backupActive = true;
        this.restoreMode = false;
        this.backupTicks = BACKUP_TICKS;
        this.backupSuccess = false;
        markDirty();
        return true;
    }

    public boolean startRestore() {
        if (backupActive || getRestoreFailure() != RestoreFailure.NONE) return false;
        this.backupActive = true;
        this.restoreMode = true;
        this.backupTicks = BACKUP_TICKS;
        this.backupSuccess = false;
        markDirty();
        return true;
    }

    public void cancelBackup() {
        if (backupActive) {
            this.backupActive = false;
            this.backupTicks = 0;
            if (world != null && !world.isClient()) {
                world.playSound(null, pos, ModSounds.SCANNER_BUTTON8, SoundCategory.BLOCKS, 0.8F, 1.0F);
            }
            markDirty();
        }
    }

    public NbtCompound createSnapshot() {
        NbtCompound root = new NbtCompound();
        root.putInt("Schema", TerminalBackupData.SCHEMA);
        root.putString("PasswordHash", passwordHash);
        root.putBoolean("PasswordSet", passwordSet);
        root.putBoolean("PasswordSkipped", passwordSkipped);

        NbtList netsList = new NbtList();
        for (SecurityNet net : nets) {
            netsList.add(net.toNbt());
        }
        root.put("SecurityNets", netsList);
        root.putInt("NextNetId", nextNetId);

        NbtList noteList = new NbtList();
        for (NotepadDocument doc : notepadDocuments) {
            noteList.add(doc.toNbt());
        }
        root.put("Notepad", noteList);
        root.putInt("NextNotepadId", nextNotepadId);

        NbtList wallList = new NbtList();
        for (int i = 0; i < MAX_SCREENS; i++) {
            NbtCompound w = new NbtCompound();
            w.putString("Wallpaper", getScreenWallpaper(i));
            w.putInt("Stage", getScreenStage(i));
            wallList.add(w);
        }
        root.put("Wallpapers", wallList);
        return root;
    }

    public void restoreSnapshot(NbtCompound snapshot) {
        if (snapshot == null || snapshot.getInt("Schema") != TerminalBackupData.SCHEMA) return;

        if (snapshot.contains("PasswordSet")) {
            this.passwordHash = snapshot.getString("PasswordHash");
            this.passwordSet = snapshot.getBoolean("PasswordSet");
            this.passwordSkipped = snapshot.getBoolean("PasswordSkipped");
        }

        nets.clear();
        NbtList netList = snapshot.getList("SecurityNets", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < netList.size(); i++) {
            SecurityNet net = SecurityNet.fromNbt(netList.getCompound(i));
            if (net != null) nets.add(net);
        }
        this.nextNetId = snapshot.getInt("NextNetId");

        notepadDocuments.clear();
        NbtList noteList = snapshot.getList("Notepad", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < noteList.size(); i++) {
            notepadDocuments.add(NotepadDocument.fromNbt(noteList.getCompound(i)));
        }
        this.nextNotepadId = snapshot.getInt("NextNotepadId");

        if (snapshot.contains("Wallpapers", NbtElement.LIST_TYPE)) {
            NbtList wallList = snapshot.getList("Wallpapers", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < Math.min(wallList.size(), MAX_SCREENS); i++) {
                NbtCompound w = wallList.getCompound(i);
                screenWallpapers[i] = w.getString("Wallpaper");
                displayStages[i] = w.getInt("Stage");
                resumeStages[i] = displayStages[i];
            }
        }

        SecurityEvents.updateDesk(this);
        bumpDisplayRevision();
    }

    public void dropBackupContents(World world, BlockPos pos) {
        if (world != null && !world.isClient()) {
            ItemScatterer.spawn(world, pos, backupInventory);
        }
    }

    // ------------------------------------------------------------ Tick

    public static void tick(World world, BlockPos pos, BlockState state, MonitorDeskBlockEntity desk) {
        if (world == null || world.isClient()) return;

        // Viewer proximity check
        for (int i = 0; i < MAX_SCREENS; i++) {
            if (desk.displayViewing[i]) {
                UUID viewerUuid = desk.displayViewers[i];
                PlayerEntity player = viewerUuid != null ? world.getPlayerByUuid(viewerUuid) : null;
                if (player == null || player.isRemoved() || player.getWorld() != world
                        || player.squaredDistanceTo(pos.toCenterPos()) > 64.0) {
                    desk.setScreenViewing(i, false, null);
                }
            }
        }

        // Backup 10-second ticker
        if (desk.backupActive) {
            if (!desk.restoreMode && desk.getBackupFailure() != BackupFailure.NONE) {
                desk.cancelBackup();
                return;
            }
            if (desk.restoreMode && desk.getRestoreFailure() != RestoreFailure.NONE) {
                desk.cancelBackup();
                return;
            }

            desk.backupTicks--;
            if (desk.backupTicks <= 0) {
                desk.backupActive = false;
                desk.backupSuccess = true;

                if (desk.restoreMode) {
                    ItemStack source = desk.backupInventory.getStack(BACKUP_SLOT_LEFT);
                    NbtCompound snapshot = TerminalBackupData.read(source);
                    if (snapshot != null) {
                        desk.restoreSnapshot(snapshot);
                    }
                } else {
                    ItemStack source = desk.backupInventory.getStack(BACKUP_SLOT_LEFT);
                    ItemStack target = desk.backupInventory.getStack(BACKUP_SLOT_RIGHT);
                    NbtCompound snapshot = TerminalBackupData.read(source);
                    if (snapshot != null) {
                        String dim = world.getRegistryKey().getValue().toString();
                        TerminalBackupData.write(target, snapshot, dim, pos);
                    }
                }
                world.playSound(null, pos, ModSounds.SCANNER_BUTTON3, SoundCategory.BLOCKS, 0.8F, 1.0F);
                desk.markDirty();
                desk.sync();
            }
        }
    }

    public void sync() {
        if (world instanceof ServerWorld serverWorld) {
            serverWorld.getChunkManager().markForUpdate(pos);
        }
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        return createNbt();
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        passwordHash = nbt.getString("PasswordHash");
        passwordSet = nbt.getBoolean("PasswordSet");
        passwordSkipped = nbt.getBoolean("PasswordSkipped");

        nets.clear();
        NbtList netList = nbt.getList("SecurityNets", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < netList.size(); i++) {
            SecurityNet net = SecurityNet.fromNbt(netList.getCompound(i));
            if (net != null) nets.add(net);
        }
        nextNetId = nbt.getInt("NextNetId");

        notepadDocuments.clear();
        NbtList noteList = nbt.getList("Notepad", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < noteList.size(); i++) {
            notepadDocuments.add(NotepadDocument.fromNbt(noteList.getCompound(i)));
        }
        nextNotepadId = nbt.getInt("NextNotepadId");

        NbtList wallList = nbt.getList("Wallpapers", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < Math.min(wallList.size(), MAX_SCREENS); i++) {
            NbtCompound w = wallList.getCompound(i);
            screenWallpapers[i] = w.getString("Wallpaper");
            displayStages[i] = w.getInt("Stage");
            resumeStages[i] = displayStages[i];
            displayNetIds[i] = w.getInt("NetId");
            displayNotepadDocIds[i] = w.getInt("DocId");
        }

        Inventories.readNbt(nbt, backupInventory.stacks);
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putString("PasswordHash", passwordHash);
        nbt.putBoolean("PasswordSet", passwordSet);
        nbt.putBoolean("PasswordSkipped", passwordSkipped);

        NbtList netList = new NbtList();
        for (SecurityNet net : nets) {
            netList.add(net.toNbt());
        }
        nbt.put("SecurityNets", netList);
        nbt.putInt("NextNetId", nextNetId);

        NbtList noteList = new NbtList();
        for (NotepadDocument doc : notepadDocuments) {
            noteList.add(doc.toNbt());
        }
        nbt.put("Notepad", noteList);
        nbt.putInt("NextNotepadId", nextNotepadId);

        NbtList wallList = new NbtList();
        for (int i = 0; i < MAX_SCREENS; i++) {
            NbtCompound w = new NbtCompound();
            w.putString("Wallpaper", getScreenWallpaper(i));
            w.putInt("Stage", displayStages[i]);
            w.putInt("NetId", displayNetIds[i]);
            w.putInt("DocId", displayNotepadDocIds[i]);
            wallList.add(w);
        }
        nbt.put("Wallpapers", wallList);

        Inventories.writeNbt(nbt, backupInventory.stacks);
    }

    // ------------------------------------------------------------ ExtendedScreenHandlerFactory

    @Override
    public Text getDisplayName() {
        return Text.translatable("container.ntmdoors.terminal_backup");
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new TerminalBackupScreenHandler(syncId, playerInventory, backupInventory, propertyDelegate, pos, restoreMode);
    }

    @Override
    public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeBoolean(restoreMode);
    }
}
