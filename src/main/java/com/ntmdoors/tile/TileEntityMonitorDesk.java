package com.ntmdoors.tile;

import com.ntmdoors.block.BlockMonitorDesk;
import com.ntmdoors.block.BlockMonitorSetup;
import com.ntmdoors.monitor.MonitorDevice;
import com.ntmdoors.monitor.MonitorDisplay;
import com.ntmdoors.monitor.MonitorWallpaper;
import com.ntmdoors.monitor.NotepadDocument;
import com.ntmdoors.monitor.SecurityEvents;
import com.ntmdoors.monitor.SecurityNet;
import com.ntmdoors.monitor.SecurityNetSummary;
import com.ntmdoors.network.NetworkUtil;
import com.ntmdoors.util.MonitorPasswords;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
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
 * Per-desk terminal state. There is exactly one tile entity per monitor desk, on the bottom core
 * cell; it is tickless and stores the password hash, the "no password" choice and the named
 * Security nets. Each net owns its own confirmed device list and at most one linked Terminal Relay
 * Key.
 *
 * <p>The full NBT (world save) keeps the hash and the nets. The update tag sent to clients carries
 * the password flags plus a compact display snapshot for each physical screen. The hash itself
 * never leaves the server.</p>
 */
public class TileEntityMonitorDesk extends TileEntitySynced implements ITickable {

    public static final int MAX_SCREENS = 6;

    public TileEntityMonitorDesk() {
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

    private String passwordHash = "";
    private boolean passwordSet;
    private boolean passwordSkipped;
    private final List<SecurityNet> nets = new ArrayList<>();
    private final List<NotepadDocument> notepadDocuments = new ArrayList<>();
    private final int[] screenPages = new int[MAX_SCREENS];
    private final String[] screenWallpapers = new String[MAX_SCREENS];
    private int nextNetId;
    private int nextNotepadId;
    private int syncedNetCount;
    private int syncedDeviceCount;
    private int syncedEventMask;

    // One independent terminal page per physical screen.
    private final int[] displayStages = new int[MAX_SCREENS];
    private final int[] displayNetIds = new int[MAX_SCREENS];
    private final int[] displayNotepadDocIds = new int[MAX_SCREENS];
    private final boolean[] displayEventsTabs = new boolean[MAX_SCREENS];
    private final boolean[] displayViewing = new boolean[MAX_SCREENS];
    private final int[] displayNetsScrolls = new int[MAX_SCREENS];
    private final int[] displaySecurityScrolls = new int[MAX_SCREENS];
    private final int[] displayEventsScrolls = new int[MAX_SCREENS];
    private final UUID[] displayViewers = new UUID[MAX_SCREENS];

    // The last stable page, kept separately so a lock screen or prompt can be shown without
    // losing the page the monitor should reopen on.
    private final int[] resumeStages = new int[MAX_SCREENS];
    private final int[] resumeNetIds = new int[MAX_SCREENS];
    private final int[] resumeNotepadDocIds = new int[MAX_SCREENS];
    private final boolean[] resumeEventsTabs = new boolean[MAX_SCREENS];
    private final int[] resumeNetsScrolls = new int[MAX_SCREENS];
    private final int[] resumeSecurityScrolls = new int[MAX_SCREENS];
    private final int[] resumeEventsScrolls = new int[MAX_SCREENS];

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

    /** Server side: stores the already-hashed password and clears the "skipped" choice. */
    public boolean setPasswordHash(String hash) {
        if (world == null || world.isRemote || !MonitorPasswords.isValidHash(hash)) return false;
        passwordHash = hash;
        passwordSet = true;
        passwordSkipped = false;
        sync();
        return true;
    }

    /** Server side: the player chose not to protect this desk. */
    public boolean skipPasswordSetup() {
        if (world == null || world.isRemote) return false;
        passwordSkipped = true;
        sync();
        return true;
    }

    /** Server side: checks a client-supplied hash against the stored hash. */
    public boolean checkPasswordHash(String hash) {
        return passwordSet && MonitorPasswords.matches(passwordHash, hash);
    }

    /** Server side: removes the password and allows the setup prompt to appear again. */
    public boolean clearPassword() {
        if (world == null || world.isRemote) return false;
        passwordHash = "";
        passwordSet = false;
        passwordSkipped = false;
        sync();
        return true;
    }

    public List<SecurityNet> getNets() {
        return Collections.unmodifiableList(nets);
    }

    public int getScreenPage(int index) {
        if (index < 0 || index >= screenPages.length) return 0;
        return screenPages[index];
    }

    /** Server side: changes what one physical screen displays. */
    public boolean setScreenPage(int index, int page) {
        if (world == null || world.isRemote) return false;
        if (index < 0 || index >= screenPages.length || screenPages[index] == page) {
            return false;
        }
        screenPages[index] = page;
        sync();
        return true;
    }

    /** The cosmetic wallpaper shown by one physical screen; empty means the terminal page. */
    public String getScreenWallpaper(int index) {
        if (index < 0 || index >= MAX_SCREENS) return "";
        String value = screenWallpapers[index];
        return value == null ? "" : value;
    }

    /** Server side: stores one screen's cosmetic wallpaper description. */
    public boolean setScreenWallpaper(int index, String wallpaper) {
        if (world == null || world.isRemote) return false;
        if (index < 0 || index >= MAX_SCREENS) return false;
        String normalized = MonitorWallpaper.normalize(wallpaper);
        String current = getScreenWallpaper(index);
        if (current.equals(normalized)) return false;
        screenWallpapers[index] = normalized;
        sync();
        return true;
    }

    public int getSyncedNetCount() {
        return syncedNetCount;
    }

    public int getSyncedDeviceCount() {
        return syncedDeviceCount;
    }

    public int getSyncedEventMask() {
        return syncedEventMask;
    }

    public int getScreenStage(int index) {
        return index >= 0 && index < MAX_SCREENS
                ? displayStages[index] : MonitorDisplay.DEFAULT_STAGE;
    }

    public int getScreenNetId(int index) {
        return index >= 0 && index < MAX_SCREENS ? displayNetIds[index] : -1;
    }

    public int getScreenNotepadDocId(int index) {
        return index >= 0 && index < MAX_SCREENS ? displayNotepadDocIds[index] : -1;
    }

    public boolean getScreenEventsTab(int index) {
        return index >= 0 && index < MAX_SCREENS && displayEventsTabs[index];
    }

    public boolean isScreenViewing(int index) {
        return index >= 0 && index < MAX_SCREENS && displayViewing[index];
    }

    public int getScreenNetsScroll(int index) {
        return index >= 0 && index < MAX_SCREENS ? displayNetsScrolls[index] : 0;
    }

    public int getScreenSecurityScroll(int index) {
        return index >= 0 && index < MAX_SCREENS ? displaySecurityScrolls[index] : 0;
    }

    public int getScreenEventsScroll(int index) {
        return index >= 0 && index < MAX_SCREENS ? displayEventsScrolls[index] : 0;
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
            resumeStages[screenIndex] = stage;
            resumeNetIds[screenIndex] = netId;
            resumeNotepadDocIds[screenIndex] = notepadDocId;
            resumeEventsTabs[screenIndex] = eventsTab;
            resumeNetsScrolls[screenIndex] = Math.max(0, netsScroll);
            resumeSecurityScrolls[screenIndex] = Math.max(0, securityScroll);
            resumeEventsScrolls[screenIndex] = Math.max(0, eventsScroll);
        }

        if (!viewing && !stable) {
            // Closing on the lock screen or a prompt leaves the last real page visible.
            restoreResumeDisplay(screenIndex);
        } else {
            displayStages[screenIndex] = stage;
            displayNetIds[screenIndex] = netId;
            displayNotepadDocIds[screenIndex] = notepadDocId;
            displayEventsTabs[screenIndex] = eventsTab;
            displayNetsScrolls[screenIndex] = Math.max(0, netsScroll);
            displaySecurityScrolls[screenIndex] = Math.max(0, securityScroll);
            displayEventsScrolls[screenIndex] = Math.max(0, eventsScroll);
        }
        displayViewing[screenIndex] = viewing;
        displayViewers[screenIndex] = viewing ? viewer : null;
        sync();
        return true;
    }

    private static boolean isStableStage(int stage) {
        return stage == MonitorDisplay.STAGE_MENU
                || stage == MonitorDisplay.STAGE_SECURITY_HOME
                || stage == MonitorDisplay.STAGE_SECURITY
                || stage == MonitorDisplay.STAGE_NOTEPAD;
    }

    private void restoreResumeDisplay(int index) {
        displayStages[index] = resumeStages[index];
        displayNetIds[index] = resumeNetIds[index];
        displayNotepadDocIds[index] = resumeNotepadDocIds[index];
        displayEventsTabs[index] = resumeEventsTabs[index];
        displayNetsScrolls[index] = resumeNetsScrolls[index];
        displaySecurityScrolls[index] = resumeSecurityScrolls[index];
        displayEventsScrolls[index] = resumeEventsScrolls[index];
    }

    public int getScreenResumeStage(int index) {
        return index >= 0 && index < MAX_SCREENS
                ? resumeStages[index] : MonitorDisplay.DEFAULT_STAGE;
    }

    public int getScreenResumeNetId(int index) {
        return index >= 0 && index < MAX_SCREENS ? resumeNetIds[index] : -1;
    }

    public boolean getScreenResumeEventsTab(int index) {
        return index >= 0 && index < MAX_SCREENS && resumeEventsTabs[index];
    }

    public int getScreenResumeNetsScroll(int index) {
        return index >= 0 && index < MAX_SCREENS ? resumeNetsScrolls[index] : 0;
    }

    public int getScreenResumeSecurityScroll(int index) {
        return index >= 0 && index < MAX_SCREENS ? resumeSecurityScrolls[index] : 0;
    }

    public int getScreenResumeEventsScroll(int index) {
        return index >= 0 && index < MAX_SCREENS ? resumeEventsScrolls[index] : 0;
    }

    /** Clears stale live-view flags if a viewer left the desk or logged out. */
    @Override
    public void update() {
        if (world == null || world.isRemote) return;
        boolean changed = false;
        for (int i = 0; i < MAX_SCREENS; i++) {
            if (!displayViewing[i]) continue;
            UUID viewer = displayViewers[i];
            EntityPlayer player = viewer == null ? null : world.getPlayerEntityByUUID(viewer);
            if (player == null || player.world != world || !NetworkUtil.isWithinReach(player, pos)) {
                displayViewing[i] = false;
                displayViewers[i] = null;
                if (!isStableStage(displayStages[i])) restoreResumeDisplay(i);
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

    /** Server side: changes one net's display name. */
    public boolean setNetName(int netId, String name) {
        if (world != null && world.isRemote) return false;
        SecurityNet net = getNet(netId);
        if (net == null) return false;
        net.setName(name);
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
            if (displayNetIds[i] >= 0 && getNet(displayNetIds[i]) == null) {
                displayNetIds[i] = -1;
                if (displayStages[i] == MonitorDisplay.STAGE_SECURITY
                        || displayStages[i] == MonitorDisplay.STAGE_DELETE_NET
                        || displayStages[i] == MonitorDisplay.STAGE_NAME_KEY) {
                    displayStages[i] = MonitorDisplay.STAGE_SECURITY_HOME;
                }
            }
            if (resumeNetIds[i] >= 0 && getNet(resumeNetIds[i]) == null) {
                resumeNetIds[i] = -1;
                if (resumeStages[i] == MonitorDisplay.STAGE_SECURITY
                        || resumeStages[i] == MonitorDisplay.STAGE_DELETE_NET
                        || resumeStages[i] == MonitorDisplay.STAGE_NAME_KEY) {
                    resumeStages[i] = MonitorDisplay.STAGE_SECURITY_HOME;
                }
            }
            if (displayStages[i] == MonitorDisplay.STAGE_NOTEPAD
                    && getNotepadDocument(displayNotepadDocIds[i]) == null) {
                displayNotepadDocIds[i] = notepadDocuments.isEmpty()
                        ? -1 : notepadDocuments.get(0).id;
                if (displayNotepadDocIds[i] < 0) {
                    displayStages[i] = MonitorDisplay.STAGE_MENU;
                }
            }
            if (resumeStages[i] == MonitorDisplay.STAGE_NOTEPAD
                    && getNotepadDocument(resumeNotepadDocIds[i]) == null) {
                resumeNotepadDocIds[i] = notepadDocuments.isEmpty()
                        ? -1 : notepadDocuments.get(0).id;
                if (resumeNotepadDocIds[i] < 0) {
                    resumeStages[i] = MonitorDisplay.STAGE_MENU;
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
        markDirty();
        return true;
    }

    /** Server side: clears a net's key link. */
    public boolean unlinkKey(int netId) {
        if (world != null && world.isRemote) return false;
        SecurityNet net = getNet(netId);
        if (net == null) return false;
        net.unlinkKey();
        markDirty();
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
            if (displayNotepadDocIds[i] == removedId) {
                displayNotepadDocIds[i] = fallback;
                if (fallback < 0 && displayStages[i] == MonitorDisplay.STAGE_NOTEPAD) {
                    displayStages[i] = MonitorDisplay.STAGE_MENU;
                }
            }
            if (resumeNotepadDocIds[i] == removedId) {
                resumeNotepadDocIds[i] = fallback;
                if (fallback < 0 && resumeStages[i] == MonitorDisplay.STAGE_NOTEPAD) {
                    resumeStages[i] = MonitorDisplay.STAGE_MENU;
                }
            }
        }
    }

    /** Legacy first-net view, used only by pre-net migration paths. */
    public List<MonitorDevice> getDevices() {
        SecurityNet net = getNet(0);
        return net == null ? Collections.emptyList() : net.getDevices();
    }

    /** Legacy first-net view, used only by pre-net migration paths. */
    public boolean setDevices(List<MonitorDevice> newDevices) {
        SecurityNet net = getNet(0);
        if (net == null) net = createNet("");
        return net != null && setNetDevices(net.id, newDevices);
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
        tag.setIntArray("ScreenPages", screenPages);
        tag.setInteger("NextNetId", nextNetId);
        writeScreenStates(tag, false);
        // Keep the old single-list tag populated so an older build still sees net one.
        tag.setTag("Devices", MonitorDevice.toNBTList(getDevices()));
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        for (int i = 0; i < MAX_SCREENS; i++) screenWallpapers[i] = "";
        if (tag.hasKey("PasswordHash")) {
            passwordHash = tag.getString("PasswordHash");
        }
        if (tag.hasKey("PasswordSet")) {
            passwordSet = tag.getBoolean("PasswordSet");
        } else {
            passwordSet = !passwordHash.isEmpty();
        }
        passwordSkipped = tag.getBoolean("PasswordSkipped");
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
        if (tag.hasKey("ScreenPages")) {
            int[] pages = tag.getIntArray("ScreenPages");
            for (int i = 0; i < screenPages.length && i < pages.length; i++) {
                screenPages[i] = pages[i];
            }
        }
        syncedNetCount = tag.getInteger("NetCount");
        syncedDeviceCount = tag.getInteger("DeviceCount");
        syncedEventMask = tag.getInteger("EventMask");

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
                displayStages[i] = MonitorDisplay.DEFAULT_STAGE;
                displayNetIds[i] = -1;
                displayNotepadDocIds[i] = -1;
                displayEventsTabs[i] = false;
                displayViewing[i] = false;
                displayNetsScrolls[i] = 0;
                displaySecurityScrolls[i] = 0;
                displayEventsScrolls[i] = 0;
                screenWallpapers[i] = "";
                resumeStages[i] = MonitorDisplay.DEFAULT_STAGE;
                resumeNetIds[i] = -1;
                resumeNotepadDocIds[i] = -1;
                resumeEventsTabs[i] = false;
                resumeNetsScrolls[i] = 0;
                resumeSecurityScrolls[i] = 0;
                resumeEventsScrolls[i] = 0;
            }
            NBTTagList screenList = tag.getTagList("ScreenStates", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < screenList.tagCount() && i < MAX_SCREENS; i++) {
                NBTTagCompound screen = screenList.getCompoundTagAt(i);
                displayStages[i] = screen.getInteger("Stage");
                displayNetIds[i] = screen.getInteger("NetId");
                displayNotepadDocIds[i] = screen.hasKey("NotepadDocId")
                        ? screen.getInteger("NotepadDocId") : -1;
                displayEventsTabs[i] = screen.getBoolean("EventsTab");
                displayViewing[i] = screen.getBoolean("Viewing");
                displayNetsScrolls[i] = screen.getInteger("NetsScroll");
                displaySecurityScrolls[i] = screen.getInteger("SecurityScroll");
                displayEventsScrolls[i] = screen.getInteger("EventsScroll");
                screenWallpapers[i] = MonitorWallpaper.normalize(screen.getString("Wallpaper"));
                resumeStages[i] = screen.hasKey("ResumeStage")
                        ? screen.getInteger("ResumeStage") : displayStages[i];
                resumeNetIds[i] = screen.hasKey("ResumeNetId")
                        ? screen.getInteger("ResumeNetId") : displayNetIds[i];
                resumeNotepadDocIds[i] = screen.hasKey("ResumeNotepadDocId")
                        ? screen.getInteger("ResumeNotepadDocId") : displayNotepadDocIds[i];
                resumeEventsTabs[i] = screen.hasKey("ResumeEventsTab")
                        ? screen.getBoolean("ResumeEventsTab") : displayEventsTabs[i];
                resumeNetsScrolls[i] = screen.hasKey("ResumeNetsScroll")
                        ? screen.getInteger("ResumeNetsScroll") : displayNetsScrolls[i];
                resumeSecurityScrolls[i] = screen.hasKey("ResumeSecurityScroll")
                        ? screen.getInteger("ResumeSecurityScroll") : displaySecurityScrolls[i];
                resumeEventsScrolls[i] = screen.hasKey("ResumeEventsScroll")
                        ? screen.getInteger("ResumeEventsScroll") : displayEventsScrolls[i];
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
                displayStages[i] = stage;
                displayNetIds[i] = netId;
                displayNotepadDocIds[i] = -1;
                displayEventsTabs[i] = eventsTab;
                displayViewing[i] = false;
                displayNetsScrolls[i] = netsScroll;
                displaySecurityScrolls[i] = securityScroll;
                displayEventsScrolls[i] = eventsScroll;
                resumeStages[i] = stage;
                resumeNetIds[i] = netId;
                resumeNotepadDocIds[i] = -1;
                resumeEventsTabs[i] = eventsTab;
                resumeNetsScrolls[i] = netsScroll;
                resumeSecurityScrolls[i] = securityScroll;
                resumeEventsScrolls[i] = eventsScroll;
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
        int deviceCount = 0;
        int eventMask = 0;
        for (SecurityNet net : nets) {
            deviceCount += net.getDevices().size();
            eventMask |= net.getEventMask();
        }
        tag.setInteger("NetCount", nets.size());
        tag.setInteger("DeviceCount", deviceCount);
        tag.setInteger("EventMask", eventMask);
        tag.setIntArray("ScreenPages", screenPages);
        writeDisplaySync(tag);
        return tag;
    }

    /** Writes one state row per physical screen. Viewing is transient and only sent to clients. */
    private void writeScreenStates(NBTTagCompound tag, boolean includeViewing) {
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < MAX_SCREENS; i++) {
            NBTTagCompound screen = new NBTTagCompound();
            screen.setInteger("Stage", displayStages[i]);
            screen.setInteger("NetId", displayNetIds[i]);
            screen.setInteger("NotepadDocId", displayNotepadDocIds[i]);
            screen.setBoolean("EventsTab", displayEventsTabs[i]);
            screen.setInteger("NetsScroll", displayNetsScrolls[i]);
            screen.setInteger("SecurityScroll", displaySecurityScrolls[i]);
            screen.setInteger("EventsScroll", displayEventsScrolls[i]);
            screen.setString("Wallpaper", getScreenWallpaper(i));
            screen.setInteger("ResumeStage", resumeStages[i]);
            screen.setInteger("ResumeNetId", resumeNetIds[i]);
            screen.setInteger("ResumeNotepadDocId", resumeNotepadDocIds[i]);
            screen.setBoolean("ResumeEventsTab", resumeEventsTabs[i]);
            screen.setInteger("ResumeNetsScroll", resumeNetsScrolls[i]);
            screen.setInteger("ResumeSecurityScroll", resumeSecurityScrolls[i]);
            screen.setInteger("ResumeEventsScroll", resumeEventsScrolls[i]);
            if (includeViewing) screen.setBoolean("Viewing", displayViewing[i]);
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
            int netId = displayNetIds[i];
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
            if (displayStages[i] != MonitorDisplay.STAGE_NOTEPAD) continue;
            int documentId = displayNotepadDocIds[i];
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
