package com.ntmdoors.block.entity;

import com.ntmdoors.block.KeypadBlock;
import com.ntmdoors.door.DoorState;
import com.ntmdoors.sound.ModSounds;
import com.ntmdoors.util.Codes;
import com.ntmdoors.util.Facings;
import com.ntmdoors.util.RedstoneBinding;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.ntmdoors.block.entity.RedstoneBindingHost;

/**
 * Keypad block entity managing 1-6 digit codes, button hits, feedback states, and door cluster synchronization.
 */
public class KeypadBlockEntity extends BlockEntity implements RedstoneBindingHost {

    public enum Mode {
        SET_CODE,
        ENTER_CODE,
        RESET_CONFIRM,
        RESET_ENTER
    }

    public enum Feedback {
        NONE,
        CODE_SET,
        RESET_DONE,
        SUCCESS,
        DENIED,
        UNABLE,
        DISABLED,
        CODE_SYNCED
    }

    public enum BindMode {
        NONE,
        DISTANCE,
        MULTI
    }

    public static final class Button {
        public final float x;
        public final float y;
        public Button(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    public static final Button[] BUTTON_LAYOUT = {
            new Button(0.234375F, 0.62890625F),  // 0: digit 9 (group "K1")
            new Button(0F, 0.62890625F),         // 1: digit 8 (group "K2")
            new Button(-0.234375F, 0.62890625F), // 2: digit 7 (group "K3")
            new Button(0.234375F, 0.453125F),    // 3: digit 6 (group "K4")
            new Button(0F, 0.453125F),           // 4: digit 5 (group "K5")
            new Button(-0.234375F, 0.453125F),   // 5: digit 4 (group "K6")
            new Button(0.234375F, 0.27734375F),  // 6: digit 3 (group "K7")
            new Button(0F, 0.27734375F),         // 7: digit 2 (group "K8")
            new Button(-0.234375F, 0.27734375F), // 8: digit 1 (group "K9")
            new Button(0.234375F, 0.1015625F),   // 9: Reset R (group "KReset")
            new Button(0F, 0.1015625F),          // 10: digit 0 (group "K0")
            new Button(-0.234375F, 0.1015625F)   // 11: Enter E (group "KReturn")
    };

    public static final float BUTTON_HALF_WIDTH = 0.078125F;
    public static final float BUTTON_HALF_HEIGHT = 0.05859375F;

    public static final int BUTTON_RESET = 9;
    public static final int BUTTON_ZERO = 10;
    public static final int BUTTON_ENTER = 11;
    public static final double MAX_BIND_DISTANCE = 32.0;
    public static final int MULTI_SLOTS = 9;

    // Server-only data
    private String storedCode = "";
    private String securityCode = "";
    private final List<BlockPos> boundDoors = new ArrayList<>();
    private final RedstoneBinding redstoneBinding = new RedstoneBinding();

    // Synchronized state
    private String entry = "";
    private Mode mode = Mode.SET_CODE;
    private Feedback feedback = Feedback.NONE;
    private BindMode bindMode = BindMode.NONE;
    private boolean enabled = true;
    private final int[] cooldowns = new int[12];
    private int feedbackTicks;
    private int blackScreenTicks;

    public KeypadBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.KEYPAD, pos, state);
    }

    public KeypadBlockEntity(net.minecraft.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        redstoneBinding.setHost(this);
    }

    @Override
    public void onRedstoneBindingChanged() {
        markDirty();
        sync();
    }

    public static void tick(World world, BlockPos pos, BlockState state, KeypadBlockEntity be) {
        be.tick();
    }

    public void tick() {
        for (int i = 0; i < cooldowns.length; i++) {
            if (cooldowns[i] > 0) cooldowns[i]--;
        }

        if (feedbackTicks > 0) {
            feedbackTicks--;
            if (feedbackTicks == 0) {
                if (feedback == Feedback.CODE_SET) {
                    blackScreenTicks = 20;
                }
                feedback = Feedback.NONE;
                sync();
            }
        }

        if (blackScreenTicks > 0) {
            blackScreenTicks--;
            if (blackScreenTicks == 0) {
                sync();
            }
        }
    }

    public int buttonAt(double hitX, double hitY, double hitZ) {
        Direction f = getCachedState().get(HorizontalFacingBlock.FACING);
        double cx = hitX - 0.5;
        double cz = hitZ - 0.5;
        double xModel = Facings.localX(cx, cz, f) / KeypadBlock.MODEL_SCALE_X;
        double yModel = (hitY - KeypadBlock.MODEL_OFFSET_Y) / KeypadBlock.MODEL_SCALE_Y;

        for (int id = 0; id < BUTTON_LAYOUT.length; id++) {
            Button b = BUTTON_LAYOUT[id];
            if (Math.abs(xModel - b.x) <= BUTTON_HALF_WIDTH && Math.abs(yModel - b.y) <= BUTTON_HALF_HEIGHT) {
                return id;
            }
        }
        return -1;
    }

    public void pressButton(int id, PlayerEntity player) {
        if (!enabled) {
            feedback = Feedback.DISABLED;
            feedbackTicks = 20;
            if (world != null) world.playSound(null, pos, ModSounds.SCANNER_BUTTON8, SoundCategory.BLOCKS, 0.8F, 1.0F);
            sync();
            return;
        }

        if (id == BUTTON_RESET) {
            pressReset();
        } else if (id == BUTTON_ZERO) {
            enterDigit(0);
        } else if (id == BUTTON_ENTER) {
            pressEnter(player);
        } else if (id >= 0 && id <= 8) {
            enterDigit(9 - id);
        }
    }

    private void enterDigit(int digit) {
        if (mode == Mode.RESET_CONFIRM) {
            mode = Mode.ENTER_CODE;
            entry = "";
        }
        if (entry.length() < 6) {
            entry += digit;
        }
        if (world != null) world.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.BLOCKS, 0.5F, 1.0F);
        cooldowns[digit == 0 ? BUTTON_ZERO : (9 - digit)] = 4;
        sync();
    }

    private void pressReset() {
        if (mode == Mode.RESET_CONFIRM) {
            mode = Mode.ENTER_CODE;
            entry = "";
        } else if (mode == Mode.RESET_ENTER) {
            mode = Mode.ENTER_CODE;
            entry = "";
        } else {
            entry = "";
        }
        if (world != null) world.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.BLOCKS, 0.5F, 1.0F);
        cooldowns[BUTTON_RESET] = 4;
        sync();
    }

    private void pressEnter(PlayerEntity player) {
        cooldowns[BUTTON_ENTER] = 4;

        if (mode == Mode.RESET_CONFIRM) {
            mode = Mode.ENTER_CODE;
            entry = "";
            sync();
            return;
        }

        if (mode == Mode.SET_CODE) {
            if (entry.length() >= 1) {
                storedCode = entry;
                entry = "";
                mode = Mode.ENTER_CODE;
                feedback = Feedback.CODE_SET;
                feedbackTicks = 20;
                for (DoorBlockEntity d : getControlledDoors()) {
                    d.bindKeypadCode(storedCode);
                }
                if (world != null) world.playSound(null, pos, ModSounds.SCANNER_BUTTON3, SoundCategory.BLOCKS, 0.8F, 1.0F);
            }
            sync();
            return;
        }

        if (mode == Mode.ENTER_CODE) {
            if (!storedCode.isEmpty() && entry.equals(storedCode)) {
                feedback = Feedback.SUCCESS;
                feedbackTicks = 20;
                entry = "";
                redstoneBinding.fireSuccess(world, pos);
                for (DoorBlockEntity d : getControlledDoors()) {
                    if (storedCode.equals(d.getKeypadCode())) {
                        d.toggle();
                    }
                }
                if (world != null) world.playSound(null, pos, ModSounds.SCANNER_BUTTON3, SoundCategory.BLOCKS, 0.8F, 1.0F);
            } else {
                feedback = Feedback.DENIED;
                feedbackTicks = 20;
                entry = "";
                redstoneBinding.fireFail(world, pos);
                if (world != null) world.playSound(null, pos, ModSounds.SCANNER_BUTTON8, SoundCategory.BLOCKS, 0.8F, 1.0F);
            }
            sync();
            return;
        }

        if (mode == Mode.RESET_ENTER) {
            boolean match = entry.equals(storedCode);
            if (!match) {
                for (DoorBlockEntity d : getControlledDoors()) {
                    if (entry.equals(d.getKeypadCode())) {
                        match = true;
                        break;
                    }
                }
            }
            if (match) {
                resetCodes(getControlledDoors());
                feedback = Feedback.RESET_DONE;
                feedbackTicks = 20;
                entry = "";
                if (world != null) world.playSound(null, pos, ModSounds.SCANNER_BUTTON3, SoundCategory.BLOCKS, 0.8F, 1.0F);
            } else {
                feedback = Feedback.DENIED;
                feedbackTicks = 20;
                entry = "";
                if (world != null) world.playSound(null, pos, ModSounds.SCANNER_BUTTON8, SoundCategory.BLOCKS, 0.8F, 1.0F);
            }
            sync();
        }
    }

    public void onScrewdriverSneak(PlayerEntity player) {
        if (hasStoredCode()) {
            if (mode == Mode.RESET_CONFIRM) {
                mode = Mode.RESET_ENTER;
                entry = "";
            } else {
                mode = Mode.RESET_CONFIRM;
                entry = "";
            }
            sync();
            return;
        }

        // Cycle bind mode
        cycleBindMode(player);
    }

    public void cycleBindMode(@org.jetbrains.annotations.Nullable PlayerEntity player) {
        switch (bindMode) {
            case NONE -> {
                bindMode = BindMode.DISTANCE;
                if (player != null) player.sendMessage(Text.translatable("ntmdoors.msg.mode_distance"), true);
            }
            case DISTANCE -> {
                bindMode = BindMode.MULTI;
                if (player != null) player.sendMessage(Text.translatable("ntmdoors.msg.mode_multi"), true);
            }
            case MULTI -> {
                bindMode = BindMode.NONE;
                if (player != null) player.sendMessage(Text.translatable("ntmdoors.msg.mode_off"), true);
            }
        }
        sync();
    }

    public void onScrewdriverUse() {
        if (hasStoredCode()) {
            if (mode == Mode.RESET_CONFIRM) {
                mode = Mode.RESET_ENTER;
                entry = "";
            } else {
                mode = Mode.RESET_CONFIRM;
                entry = "";
            }
            sync();
        }
    }

    public void bindDoor(DoorBlockEntity core, PlayerEntity player) {
        if (!core.acceptsKeypad()) {
            player.sendMessage(Text.translatable("ntmdoors.msg.bind_no_keypad"), true);
            if (world != null) world.playSound(null, pos, ModSounds.SCANNER_BUTTON8, SoundCategory.BLOCKS, 0.8F, 1.0F);
            return;
        }
        if (core.isBoundToScanner()) {
            player.sendMessage(Text.translatable("ntmdoors.msg.bind_scanner"), true);
            if (world != null) world.playSound(null, pos, ModSounds.SCANNER_BUTTON8, SoundCategory.BLOCKS, 0.8F, 1.0F);
            return;
        }
        if (core.getState() != DoorState.CLOSED) {
            player.sendMessage(Text.translatable("ntmdoors.msg.bind_open"), true);
            return;
        }
        if (pos.getSquaredDistance(core.getPos()) > MAX_BIND_DISTANCE * MAX_BIND_DISTANCE) {
            player.sendMessage(Text.translatable("ntmdoors.msg.bind_far"), true);
            return;
        }

        if (bindMode == BindMode.DISTANCE) {
            boundDoors.clear();
            boundDoors.add(core.getPos().toImmutable());
            player.sendMessage(Text.translatable("ntmdoors.msg.bind_success"), true);
            sync();
        } else if (bindMode == BindMode.MULTI) {
            if (boundDoors.contains(core.getPos())) {
                boundDoors.remove(core.getPos());
                player.sendMessage(Text.translatable("ntmdoors.msg.bind_cleared"), true);
            } else if (boundDoors.size() >= MULTI_SLOTS) {
                player.sendMessage(Text.translatable("ntmdoors.msg.bind_full"), true);
            } else {
                boundDoors.add(core.getPos().toImmutable());
                if (boundDoors.size() == 1) {
                    player.sendMessage(Text.translatable("ntmdoors.msg.bind_multi_success_one"), true);
                } else {
                    player.sendMessage(Text.translatable("ntmdoors.msg.bind_multi_success", boundDoors.size()), true);
                }
            }
            sync();
        }
    }

    public void autoSyncWithDoor() {
        if (world == null || world.isClient) return;
        DoorBlockEntity door = findAdjacentDoor();
        if (door != null) {
            if (!door.acceptsKeypad() || door.isBoundToScanner()) {
                feedback = Feedback.UNABLE;
                feedbackTicks = 60;
                sync();
            } else if (!door.getKeypadCode().isEmpty()) {
                storedCode = door.getKeypadCode();
                mode = Mode.ENTER_CODE;
                feedback = Feedback.CODE_SYNCED;
                feedbackTicks = 20;
                sync();
            }
        }
    }

    public DoorBlockEntity findAdjacentDoor() {
        if (world == null) return null;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockPos p = pos.add(dx, dy, dz);
                    BlockEntity be = world.getBlockEntity(p);
                    if (be instanceof DoorBlockEntity core) return core;
                    if (be instanceof DoorPartBlockEntity part) {
                        BlockEntity coreBe = world.getBlockEntity(part.getCorePos());
                        if (coreBe instanceof DoorBlockEntity core) return core;
                    }
                }
            }
        }
        return null;
    }

    public List<DoorBlockEntity> getControlledDoors() {
        List<DoorBlockEntity> list = new ArrayList<>();
        if (world == null) return list;

        if (bindMode == BindMode.NONE) {
            DoorBlockEntity adj = findAdjacentDoor();
            if (adj != null && adj.acceptsKeypad() && !adj.isBoundToScanner()) {
                list.add(adj);
            }
        } else {
            for (BlockPos p : boundDoors) {
                if (pos.getSquaredDistance(p) <= MAX_BIND_DISTANCE * MAX_BIND_DISTANCE) {
                    BlockEntity be = world.getBlockEntity(p);
                    if (be instanceof DoorBlockEntity d && d.acceptsKeypad() && !d.isBoundToScanner()) {
                        list.add(d);
                    }
                }
            }
        }
        return list;
    }

    public void resetCodes(List<DoorBlockEntity> doors) {
        if (world == null) return;
        Set<DoorBlockEntity> allDoors = new HashSet<>(doors);
        Set<KeypadBlockEntity> allKeypads = new HashSet<>();
        allKeypads.add(this);

        // Find sharing keypads within 32 blocks
        int r = (int) MAX_BIND_DISTANCE;
        for (int dx = -r; dx <= r; dx += 4) {
            for (int dy = -r; dy <= r; dy += 4) {
                for (int dz = -r; dz <= r; dz += 4) {
                    BlockPos p = pos.add(dx, dy, dz);
                    if (world.isChunkLoaded(p)) {
                        BlockEntity be = world.getBlockEntity(p);
                        if (be instanceof KeypadBlockEntity otherPad) {
                            for (DoorBlockEntity d : otherPad.getControlledDoors()) {
                                if (allDoors.contains(d)) {
                                    allKeypads.add(otherPad);
                                    allDoors.addAll(otherPad.getControlledDoors());
                                }
                            }
                        }
                    }
                }
            }
        }

        for (KeypadBlockEntity pad : allKeypads) {
            pad.storedCode = "";
            pad.mode = Mode.SET_CODE;
            pad.entry = "";
            pad.boundDoors.clear();
            pad.sync();
        }
        for (DoorBlockEntity d : allDoors) {
            d.clearKeypadCode();
        }
    }

    public boolean hasStoredCode() {
        return !storedCode.isEmpty();
    }

    public String getStoredCode() {
        return storedCode;
    }

    public void setStoredCode(String code) {
        this.storedCode = Codes.sanitise(code, 6);
        if (!this.storedCode.isEmpty()) {
            this.mode = Mode.ENTER_CODE;
        }
        markDirty();
    }

    public String getSecurityCode() {
        return securityCode;
    }

    public void setSecurityCode(String code) {
        this.securityCode = Codes.sanitise(code, 6);
        markDirty();
    }

    public String getEntry() {
        return entry;
    }

    public Mode getMode() {
        return mode;
    }

    public Feedback getFeedback() {
        return feedback;
    }

    public BindMode getBindMode() {
        return bindMode;
    }

    public boolean isLinking() {
        return bindMode != BindMode.NONE;
    }

    public int getCooldown(int id) {
        return (id >= 0 && id < cooldowns.length) ? cooldowns[id] : 0;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        sync();
    }

    public int getDoorCount() {
        return boundDoors.size();
    }

    public RedstoneBinding getRedstoneBinding() {
        return redstoneBinding;
    }

    public void sync() {
        markDirty();
        if (world != null && !world.isClient) {
            world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
        }
    }

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        NbtCompound tag = new NbtCompound();
        writeSyncNbt(tag);
        return tag;
    }

    public void writeSyncNbt(NbtCompound tag) {
        tag.putString("entry", entry);
        tag.putInt("mode", mode.ordinal());
        tag.putInt("feedback", feedback.ordinal());
        tag.putInt("bindMode", bindMode.ordinal());
        tag.putBoolean("enabled", enabled);
        tag.putInt("doorCount", boundDoors.size());
        tag.putInt("blackScreen", blackScreenTicks);
        tag.putIntArray("cooldowns", cooldowns);
    }

    @Override
    public void writeNbt(NbtCompound tag) {
        super.writeNbt(tag);
        writeSyncNbt(tag);
        tag.putString("storedCode", storedCode);
        tag.putString("securityCode", securityCode);
        NbtList doorList = new NbtList();
        for (BlockPos p : boundDoors) {
            NbtCompound c = new NbtCompound();
            c.putLong("pos", p.asLong());
            doorList.add(c);
        }
        tag.put("boundDoors", doorList);
        redstoneBinding.writeNbt(tag);
    }

    @Override
    public void readNbt(NbtCompound tag) {
        super.readNbt(tag);
        entry = tag.getString("entry");
        if (tag.contains("mode")) mode = Mode.values()[Math.min(tag.getInt("mode"), Mode.values().length - 1)];
        if (tag.contains("feedback")) feedback = Feedback.values()[Math.min(tag.getInt("feedback"), Feedback.values().length - 1)];
        if (tag.contains("bindMode")) bindMode = BindMode.values()[Math.min(tag.getInt("bindMode"), BindMode.values().length - 1)];
        if (tag.contains("enabled")) enabled = tag.getBoolean("enabled");
        if (tag.contains("blackScreen")) blackScreenTicks = tag.getInt("blackScreen");
        if (tag.contains("cooldowns")) {
            int[] cds = tag.getIntArray("cooldowns");
            System.arraycopy(cds, 0, cooldowns, 0, Math.min(cds.length, cooldowns.length));
        }

        // Server-only data
        if (tag.contains("storedCode")) storedCode = tag.getString("storedCode");
        if (tag.contains("securityCode")) securityCode = tag.getString("securityCode");
        boundDoors.clear();
        if (tag.contains("boundDoors", NbtElement.LIST_TYPE)) {
            NbtList doorList = tag.getList("boundDoors", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < doorList.size(); i++) {
                boundDoors.add(BlockPos.fromLong(doorList.getCompound(i).getLong("pos")));
            }
        }
        redstoneBinding.readNbt(tag);
    }
}
