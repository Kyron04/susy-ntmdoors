package com.ntmdoors.block.entity;

import com.ntmdoors.block.ModBlocks;
import com.ntmdoors.block.MotionSensorBlock;
import com.ntmdoors.block.MotionSensorStatus;
import com.ntmdoors.item.ItemCreativeKeycard;
import com.ntmdoors.item.ItemRfidCard;
import com.ntmdoors.util.Biometrics;
import com.ntmdoors.util.PrivateKeys;
import com.ntmdoors.util.RedstoneBinding;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.*;

public class MotionSensorBlockEntity extends BlockEntity implements RedstoneBindingHost {

    public static final int SCAN_INTERVAL = 10;
    public static final int GRID_SIZE = 5;
    public static final int MAX_DEPTH = 5;
    public static final int CENTER = 2; // (2, 2) is index 12

    public static final int RS_AUTHORIZED = 0;
    public static final int RS_UNAUTHORIZED = 1;
    public static final int RS_DISABLED = 2;
    public static final int RS_ENABLED = 3;
    public static final int RS_EVENT_COUNT = 4;

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
    private final Set<UUID> entitiesInside = new HashSet<>();
    private final boolean[] redstoneEvents = new boolean[RS_EVENT_COUNT];
    private final RedstoneBinding redstoneBinding = new RedstoneBinding(this);

    private boolean matchHolder = true;
    private boolean ignoreKeys;
    private boolean ignoreIds;
    private boolean enabled = true;
    private boolean detectPlayers = true;
    private boolean detectHostile = true;
    private boolean detectNeutral = true;
    private boolean persistWithinBounds;
    private boolean persistOutOfBounds;

    private final byte[] areaDepths = new byte[GRID_SIZE * GRID_SIZE];
    private boolean areaBound;

    private int scanTicks;
    private MotionSensorStatus status = MotionSensorStatus.IDLE;

    public MotionSensorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MOTION_SENSOR, pos, state);
        Arrays.fill(areaDepths, (byte) 5);
        areaBound = true;
    }

    // --- State and Options ---

    public MotionSensorStatus getStatus() {
        return status;
    }

    public void setStatus(MotionSensorStatus status) {
        if (status == null || this.status == status) return;
        this.status = status;
        if (world != null && !world.isClient) {
            BlockState current = getCachedState();
            if (current.isOf(ModBlocks.MOTION_SENSOR)) {
                world.setBlockState(pos, current.with(MotionSensorBlock.STATUS, status), Block.NOTIFY_ALL);
            }
        }
        sync();
    }

    public void cycleStatus() {
        MotionSensorStatus[] values = MotionSensorStatus.values();
        setStatus(values[(status.ordinal() + 1) % values.length]);
    }

    public boolean isAreaBound() {
        return areaBound;
    }

    public byte[] getAreaDepths() {
        return areaDepths;
    }

    public byte getDepth(int u, int v) {
        if (u < 0 || u >= GRID_SIZE || v < 0 || v >= GRID_SIZE) return 0;
        return areaDepths[u + v * GRID_SIZE];
    }

    public void setAreaDepths(byte[] depths) {
        if (depths == null || depths.length != areaDepths.length) return;
        System.arraycopy(depths, 0, areaDepths, 0, areaDepths.length);
        int centreIdx = CENTER + CENTER * GRID_SIZE;
        if (areaDepths[centreIdx] < 1) {
            areaDepths[centreIdx] = 1;
        }
        areaBound = true;
        markDirty();
        sync();
    }

    public void clearArea() {
        areaBound = false;
        Arrays.fill(areaDepths, (byte) 0);
        setStatus(MotionSensorStatus.MAINTENANCE);
        markDirty();
        sync();
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) fireRedstoneEvent(RS_ENABLED);
        else fireRedstoneEvent(RS_DISABLED);
        markDirty();
        sync();
    }

    public boolean isDetectPlayers() { return detectPlayers; }
    public void setDetectPlayers(boolean val) { this.detectPlayers = val; markDirty(); sync(); }

    public boolean isDetectHostile() { return detectHostile; }
    public void setDetectHostile(boolean val) { this.detectHostile = val; markDirty(); sync(); }

    public boolean isDetectNeutral() { return detectNeutral; }
    public void setDetectNeutral(boolean val) { this.detectNeutral = val; markDirty(); sync(); }

    public boolean isMatchHolder() { return matchHolder; }
    public void setMatchHolder(boolean val) { this.matchHolder = val; markDirty(); sync(); }

    public boolean isIgnoreKeys() { return ignoreKeys; }
    public void setIgnoreKeys(boolean val) { this.ignoreKeys = val; markDirty(); sync(); }

    public boolean isIgnoreIds() { return ignoreIds; }
    public void setIgnoreIds(boolean val) { this.ignoreIds = val; markDirty(); sync(); }

    public boolean isPersistWithinBounds() { return persistWithinBounds; }
    public void setPersistWithinBounds(boolean val) {
        this.persistWithinBounds = val;
        if (val) persistOutOfBounds = false;
        markDirty();
        sync();
    }

    public boolean isPersistOutOfBounds() { return persistOutOfBounds; }
    public void setPersistOutOfBounds(boolean val) {
        this.persistOutOfBounds = val;
        if (val) persistWithinBounds = false;
        markDirty();
        sync();
    }

    public boolean isRedstoneEvent(int index) {
        if (index < 0 || index >= RS_EVENT_COUNT) return false;
        return redstoneEvents[index];
    }

    public void setRedstoneEvent(int index, boolean val) {
        if (index >= 0 && index < RS_EVENT_COUNT) {
            redstoneEvents[index] = val;
            markDirty();
            sync();
        }
    }

    public List<PrivateKeys.Key> getAssignedKeys() {
        return Collections.unmodifiableList(assignedKeys);
    }

    public List<BiometricId> getAssignedIds() {
        return Collections.unmodifiableList(assignedIds);
    }

    public void addKey(PrivateKeys.Key key) {
        if (key != null && !assignedKeys.contains(key)) {
            assignedKeys.add(key);
            markDirty();
            sync();
        }
    }

    public void removeKey(String serial) {
        if (assignedKeys.removeIf(k -> k.serial.equals(serial))) {
            markDirty();
            sync();
        }
    }

    public void addBiometricId(String uuid, String name) {
        if (uuid != null && assignedIds.stream().noneMatch(id -> id.uuid.equals(uuid))) {
            assignedIds.add(new BiometricId(uuid, name != null ? name : ""));
            markDirty();
            sync();
        }
    }

    public void removeBiometricId(String uuid) {
        if (assignedIds.removeIf(id -> id.uuid.equals(uuid))) {
            markDirty();
            sync();
        }
    }

    public boolean hasAssignedKey(String serial) {
        return assignedKeys.stream().anyMatch(k -> k.serial.equals(serial));
    }

    public boolean hasAssignedId(String uuid) {
        return assignedIds.stream().anyMatch(id -> id.uuid.equals(uuid));
    }

    // --- Coordinate & Projection Calculations ---

    public Direction getFacing() {
        if (getCachedState() == null || !getCachedState().contains(MotionSensorBlock.FACING)) {
            return Direction.NORTH;
        }
        return getCachedState().get(MotionSensorBlock.FACING);
    }

    public Direction getUAxis() {
        Direction f = getFacing();
        return f.getAxis().isHorizontal() ? f.rotateYClockwise() : Direction.EAST;
    }

    public Direction getVAxis() {
        Direction f = getFacing();
        return f.getAxis().isHorizontal() ? Direction.UP : Direction.SOUTH;
    }

    public BlockPos getCellPos(int u, int v, int d) {
        return pos.offset(getUAxis(), u - CENTER)
                .offset(getVAxis(), v - CENTER)
                .offset(getFacing(), d);
    }

    public Box getProjectionBounds() {
        Direction uAxis = getUAxis();
        Direction vAxis = getVAxis();
        Direction f = getFacing();

        BlockPos p1 = pos.offset(uAxis, -CENTER).offset(vAxis, -CENTER);
        BlockPos p2 = pos.offset(uAxis, 2).offset(vAxis, 2).offset(f, MAX_DEPTH);

        return new Box(
                Math.min(p1.getX(), p2.getX()),
                Math.min(p1.getY(), p2.getY()),
                Math.min(p1.getZ(), p2.getZ()),
                Math.max(p1.getX(), p2.getX()) + 1,
                Math.max(p1.getY(), p2.getY()) + 1,
                Math.max(p1.getZ(), p2.getZ()) + 1
        );
    }

    // --- Entity Detection & Clearance ---

    public boolean hasClearance(PlayerEntity player) {
        if (player.isCreative()) return true;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (checkCardClearance(stack, player)) return true;
        }
        ItemStack off = player.getOffHandStack();
        return checkCardClearance(off, player);
    }

    public boolean checkCardClearance(ItemStack stack, PlayerEntity player) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() instanceof ItemCreativeKeycard) return true;

        if (stack.getItem() instanceof ItemRfidCard) {
            if (matchHolder && !ignoreIds && Biometrics.isBound(stack) && !Biometrics.isOwnedBy(stack, player)) {
                return false; // Stolen card
            }
            if (!ignoreKeys) {
                for (PrivateKeys.Key key : PrivateKeys.get(stack)) {
                    if (hasAssignedKey(key.serial)) return true;
                }
            }
            if (!ignoreIds && hasAssignedId(Biometrics.ownerUuid(stack))) {
                return true;
            }
        }
        return false;
    }

    public void tick(World world, BlockPos pos, BlockState state) {
        if (world.isClient) return;
        redstoneBinding.update();

        if (++scanTicks < SCAN_INTERVAL) return;
        scanTicks = 0;

        if (!enabled || !areaBound) {
            setStatus(MotionSensorStatus.MAINTENANCE);
            if (persistWithinBounds) redstoneBinding.hold(pos, false);
            if (persistOutOfBounds) redstoneBinding.setHold(RedstoneBinding.HoldMode.VACANCY, false);
            return;
        }

        Box bounds = getProjectionBounds();
        List<LivingEntity> list = world.getEntitiesByClass(LivingEntity.class, bounds, e -> e.isAlive() && !(e instanceof ArmorStandEntity));

        Set<UUID> currentInside = new HashSet<>();
        boolean authorizedPresent = false;
        boolean unauthorizedPresent = false;

        for (LivingEntity entity : list) {
            if (entity.isSpectator()) continue;

            boolean inActiveCell = false;
            for (int u = 0; u < GRID_SIZE; u++) {
                for (int v = 0; v < GRID_SIZE; v++) {
                    int depth = areaDepths[u + v * GRID_SIZE];
                    if (depth <= 0) continue;
                    for (int d = 0; d < depth; d++) {
                        BlockPos cell = getCellPos(u, v, d);
                        Box cellBox = new Box(cell);
                        if (entity.getBoundingBox().intersects(cellBox)) {
                            inActiveCell = true;
                            break;
                        }
                    }
                    if (inActiveCell) break;
                }
                if (inActiveCell) break;
            }

            if (!inActiveCell) continue;

            boolean isHostile = entity instanceof HostileEntity || entity instanceof Monster;
            boolean isPlayer = entity instanceof PlayerEntity;

            if (isPlayer) {
                if (!detectPlayers) continue;
                PlayerEntity player = (PlayerEntity) entity;
                currentInside.add(player.getUuid());
                if (hasClearance(player)) {
                    authorizedPresent = true;
                } else {
                    unauthorizedPresent = true;
                }
            } else if (isHostile) {
                if (!detectHostile) continue;
                currentInside.add(entity.getUuid());
                unauthorizedPresent = true;
            } else {
                if (!detectNeutral) continue;
                currentInside.add(entity.getUuid());
                unauthorizedPresent = true;
            }
        }

        // Edge triggers on new arrivals
        for (UUID id : currentInside) {
            if (!entitiesInside.contains(id)) {
                if (unauthorizedPresent) fireRedstoneEvent(RS_UNAUTHORIZED);
                else if (authorizedPresent) fireRedstoneEvent(RS_AUTHORIZED);
                break;
            }
        }
        entitiesInside.clear();
        entitiesInside.addAll(currentInside);

        // Status determination
        if (unauthorizedPresent) {
            setStatus(MotionSensorStatus.MOTION);
        } else if (authorizedPresent) {
            setStatus(MotionSensorStatus.AUTHORIZED);
        } else {
            setStatus(MotionSensorStatus.IDLE);
        }

        boolean anyQualifying = authorizedPresent || unauthorizedPresent;
        if (persistWithinBounds) {
            redstoneBinding.hold(pos, anyQualifying);
        } else if (persistOutOfBounds) {
            redstoneBinding.setHold(RedstoneBinding.HoldMode.VACANCY, !anyQualifying);
        }
    }

    private void fireRedstoneEvent(int eventIndex) {
        if (eventIndex >= 0 && eventIndex < RS_EVENT_COUNT && redstoneEvents[eventIndex]) {
            redstoneBinding.fire();
        }
    }

    // --- RedstoneBindingHost ---

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
        sync();
    }

    @Override
    public void sendRedstoneMessage(PlayerEntity player, String key) {
        player.sendMessage(Text.translatable(key), true);
    }

    // --- NBT & Sync ---

    @Override
    public void writeNbt(NbtCompound tag) {
        super.writeNbt(tag);
        tag.putByte("status", (byte) status.ordinal());
        tag.putBoolean("areaBound", areaBound);
        tag.putByteArray("areaDepths", Arrays.copyOf(areaDepths, areaDepths.length));
        tag.putBoolean("matchHolder", matchHolder);
        tag.putBoolean("ignoreKeys", ignoreKeys);
        tag.putBoolean("ignoreIds", ignoreIds);
        tag.putBoolean("enabled", enabled);
        tag.putBoolean("detectPlayers", detectPlayers);
        tag.putBoolean("detectHostile", detectHostile);
        tag.putBoolean("detectNeutral", detectNeutral);
        tag.putBoolean("persistWithinBounds", persistWithinBounds);
        tag.putBoolean("persistOutOfBounds", persistOutOfBounds);

        byte[] events = new byte[RS_EVENT_COUNT];
        for (int i = 0; i < RS_EVENT_COUNT; i++) events[i] = (byte) (redstoneEvents[i] ? 1 : 0);
        tag.putByteArray("redstoneEvents", events);

        NbtList keysList = new NbtList();
        for (PrivateKeys.Key k : assignedKeys) {
            NbtCompound c = new NbtCompound();
            c.putString("name", k.name);
            c.putString("serial", k.serial);
            keysList.add(c);
        }
        tag.put("assignedKeys", keysList);

        NbtList idsList = new NbtList();
        for (BiometricId id : assignedIds) {
            NbtCompound c = new NbtCompound();
            c.putString("uuid", id.uuid);
            c.putString("name", id.name);
            idsList.add(c);
        }
        tag.put("assignedIds", idsList);

        redstoneBinding.writeNbt(tag);
    }

    @Override
    public void readNbt(NbtCompound tag) {
        super.readNbt(tag);
        int st = tag.getByte("status") & 3;
        status = MotionSensorStatus.values()[st];
        areaBound = tag.getBoolean("areaBound");

        byte[] savedDepths = tag.getByteArray("areaDepths");
        if (savedDepths.length == areaDepths.length) {
            System.arraycopy(savedDepths, 0, areaDepths, 0, areaDepths.length);
        }

        matchHolder = !tag.contains("matchHolder") || tag.getBoolean("matchHolder");
        ignoreKeys = tag.getBoolean("ignoreKeys");
        ignoreIds = tag.getBoolean("ignoreIds");
        enabled = !tag.contains("enabled") || tag.getBoolean("enabled");
        detectPlayers = !tag.contains("detectPlayers") || tag.getBoolean("detectPlayers");
        detectHostile = !tag.contains("detectHostile") || tag.getBoolean("detectHostile");
        detectNeutral = !tag.contains("detectNeutral") || tag.getBoolean("detectNeutral");
        persistWithinBounds = tag.getBoolean("persistWithinBounds");
        persistOutOfBounds = tag.getBoolean("persistOutOfBounds");

        Arrays.fill(redstoneEvents, false);
        byte[] savedEvents = tag.getByteArray("redstoneEvents");
        for (int i = 0; i < Math.min(savedEvents.length, RS_EVENT_COUNT); i++) {
            redstoneEvents[i] = savedEvents[i] != 0;
        }

        assignedKeys.clear();
        if (tag.contains("assignedKeys", NbtElement.LIST_TYPE)) {
            NbtList list = tag.getList("assignedKeys", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < list.size(); i++) {
                NbtCompound c = list.getCompound(i);
                assignedKeys.add(new PrivateKeys.Key(c.getString("name"), c.getString("serial")));
            }
        }

        assignedIds.clear();
        if (tag.contains("assignedIds", NbtElement.LIST_TYPE)) {
            NbtList list = tag.getList("assignedIds", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < list.size(); i++) {
                NbtCompound c = list.getCompound(i);
                assignedIds.add(new BiometricId(c.getString("uuid"), c.getString("name")));
            }
        }

        redstoneBinding.readNbt(tag);
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
        writeNbt(tag);
        return tag;
    }
}
