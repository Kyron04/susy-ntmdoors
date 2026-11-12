package com.ntmdoors.block.entity;

import com.ntmdoors.item.ItemBiometricKeycard;
import com.ntmdoors.item.ItemOperatorKeycard;
import com.ntmdoors.item.ItemRelayKey;
import com.ntmdoors.item.ItemRfidCard;
import com.ntmdoors.item.ModItems;
import com.ntmdoors.screen.KeycardProgrammerScreenHandler;
import com.ntmdoors.sound.ModSounds;
import com.ntmdoors.util.Biometrics;
import com.ntmdoors.util.PrivateKeys;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Keycard Programmer block entity managing 7 inventory slots, key binding, palette recoloring, and legacy backup.
 */
public class KeycardProgrammerBlockEntity extends BlockEntity implements Inventory, ExtendedScreenHandlerFactory {

    public static final int INVENTORY_SIZE = 7;
    public static final int SLOT_RELAY_ONE = 0;
    public static final int SLOT_OPERATOR = 1;
    public static final int SLOT_RELAY_TWO = 2;
    public static final int SLOT_RFID_PAGE_ONE = 3;
    public static final int SLOT_RFID_PAGE_TWO = 4;
    public static final int SLOT_LEGACY_LEFT = 5;
    public static final int SLOT_LEGACY_RIGHT = 6;

    public static final int BACKUP_TICKS_TOTAL = 200; // 10 seconds

    public enum BindFailure {
        NONE,
        EMPTY,
        NO_KEY,
        NO_BIOMETRIC,
        INVALID_TARGET,
        BLANK_CARD,
        ALREADY_BOUND,
        BIOMETRIC_TAKEN,
        BIOMETRIC_ALREADY
    }

    public enum BackupFailure {
        NONE,
        EMPTY,
        NOT_KEY,
        NO_DATA,
        WRONG_TARGET,
        TARGET_FULL
    }

    private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(INVENTORY_SIZE, ItemStack.EMPTY);

    private int page = 0;
    private boolean backupActive = false;
    private int backupTicks = 0;
    private boolean backupSuccess = false;
    private int successTicks = 0;

    private final PropertyDelegate propertyDelegate = new PropertyDelegate() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> page;
                case 1 -> backupActive ? 1 : 0;
                case 2 -> backupTicks;
                case 3 -> backupSuccess ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> page = value;
                case 1 -> backupActive = (value == 1);
                case 2 -> backupTicks = value;
                case 3 -> backupSuccess = (value == 1);
            }
        }

        @Override
        public int size() {
            return 4;
        }
    };

    public KeycardProgrammerBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.KEYCARD_PROGRAMMER, pos, state);
    }

    public KeycardProgrammerBlockEntity(net.minecraft.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static void tick(World world, BlockPos pos, BlockState state, KeycardProgrammerBlockEntity be) {
        be.tick();
    }

    public void tick() {
        if (backupActive) {
            if (getBackupFailure() != BackupFailure.NONE) {
                backupActive = false;
                backupTicks = 0;
                if (world != null) {
                    world.playSound(null, pos, ModSounds.SCANNER_BUTTON8, SoundCategory.BLOCKS, 0.8F, 1.0F);
                }
                markDirty();
                return;
            }

            backupTicks++;
            if (backupTicks >= BACKUP_TICKS_TOTAL) {
                completeBackup();
            }
        }

        if (backupSuccess) {
            successTicks--;
            if (successTicks <= 0) {
                backupSuccess = false;
                markDirty();
            }
        }
    }

    public static BindFailure checkBindFailure(ItemStack source, ItemStack target, int row) {
        if (source.isEmpty() || target.isEmpty()) return BindFailure.EMPTY;
        if (!(source.getItem() instanceof ItemRelayKey || source.getItem() instanceof ItemOperatorKeycard)) return BindFailure.NO_KEY;

        boolean sourceHasKey = !PrivateKeys.isBlank(source);
        boolean sourceHasBio = Biometrics.isBound(source);
        if (!sourceHasKey && !sourceHasBio) return BindFailure.NO_KEY;

        if (row == 0) {
            if (!(target.getItem() instanceof ItemOperatorKeycard)) return BindFailure.INVALID_TARGET;
        } else {
            if (!(target.getItem() instanceof ItemRfidCard)) return BindFailure.INVALID_TARGET;
            if (target.getItem() == ModItems.BLANK_RFID_CARD) return BindFailure.BLANK_CARD;
        }

        if (sourceHasBio && Biometrics.isBound(target)) {
            return BindFailure.BIOMETRIC_TAKEN;
        }

        if (sourceHasKey && !sourceHasBio) {
            boolean hasNewKey = false;
            for (PrivateKeys.Key key : PrivateKeys.getKeys(source)) {
                if (!PrivateKeys.hasSerial(target, key.serial)) {
                    hasNewKey = true;
                    break;
                }
            }
            if (!hasNewKey) return BindFailure.ALREADY_BOUND;
        }

        return BindFailure.NONE;
    }

    public BindFailure getBindFailure(int row) {
        ItemStack source = inventory.get(row == 0 ? SLOT_RELAY_ONE : SLOT_RELAY_TWO);
        ItemStack target = inventory.get(row == 0 ? SLOT_OPERATOR : SLOT_RFID_PAGE_ONE);
        return checkBindFailure(source, target, row);
    }

    public boolean bind(int row) {
        if (getBindFailure(row) != BindFailure.NONE) return false;

        ItemStack source = inventory.get(row == 0 ? SLOT_RELAY_ONE : SLOT_RELAY_TWO);
        ItemStack target = inventory.get(row == 0 ? SLOT_OPERATOR : SLOT_RFID_PAGE_ONE);

        int added = PrivateKeys.addAll(source, target);
        boolean copiedBio = false;
        if (Biometrics.isBound(source) && !Biometrics.isBound(target)) {
            copiedBio = Biometrics.copyOwner(source, target);
        }

        if (added > 0 || copiedBio) {
            if (world != null) {
                world.playSound(null, pos, ModSounds.SCANNER_BUTTON3, SoundCategory.BLOCKS, 0.8F, 1.0F);
            }
            markDirty();
            return true;
        }
        return false;
    }

    public boolean applyColor(int paletteIndex) {
        if (paletteIndex < 0 || paletteIndex >= ModItems.PROGRAMMER_CARDS.size()) return false;

        ItemStack current = inventory.get(SLOT_RFID_PAGE_TWO);
        if (current.isEmpty() || !(current.getItem() instanceof ItemRfidCard)) return false;
        if (current.getItem() instanceof ItemBiometricKeycard || current.getItem() instanceof ItemOperatorKeycard) {
            return false;
        }

        ItemRfidCard targetCard = ModItems.PROGRAMMER_CARDS.get(paletteIndex);
        ItemStack replacement = new ItemStack(targetCard, current.getCount());
        if (current.hasNbt() && current.getNbt() != null) {
            replacement.setNbt(current.getNbt().copy()); // Strictly preserve all NBT tags!
        }

        inventory.set(SLOT_RFID_PAGE_TWO, replacement);
        if (world != null) {
            world.playSound(null, pos, ModSounds.BUTTON9, SoundCategory.BLOCKS, 0.8F, 1.0F);
        }
        markDirty();
        return true;
    }

    public BackupFailure getBackupFailure() {
        ItemStack source = inventory.get(SLOT_LEGACY_LEFT);
        ItemStack target = inventory.get(SLOT_LEGACY_RIGHT);

        if (source.isEmpty() || target.isEmpty()) return BackupFailure.EMPTY;

        boolean sourceIsRelay = source.getItem() instanceof ItemRelayKey;
        boolean sourceIsOperator = source.getItem() instanceof ItemOperatorKeycard;
        if (!sourceIsRelay && !sourceIsOperator) return BackupFailure.NOT_KEY;

        if (!source.hasNbt() || (PrivateKeys.isBlank(source) && !Biometrics.isBound(source))) {
            return BackupFailure.NO_DATA;
        }

        boolean targetIsRelay = target.getItem() instanceof ItemRelayKey;
        boolean targetIsOperator = target.getItem() instanceof ItemOperatorKeycard;
        if (!targetIsRelay && !targetIsOperator) return BackupFailure.WRONG_TARGET;

        // Operator Keycard -> Operator Keycard only
        if (sourceIsOperator && !targetIsOperator) return BackupFailure.WRONG_TARGET;

        // Target must be blank
        if (!PrivateKeys.isBlank(target) || Biometrics.isBound(target)) {
            return BackupFailure.TARGET_FULL;
        }

        return BackupFailure.NONE;
    }

    public boolean startBackup() {
        if (getBackupFailure() != BackupFailure.NONE) return false;

        backupActive = true;
        backupTicks = 0;
        backupSuccess = false;
        markDirty();
        return true;
    }

    private void completeBackup() {
        ItemStack source = inventory.get(SLOT_LEGACY_LEFT);
        ItemStack target = inventory.get(SLOT_LEGACY_RIGHT);

        if (source.hasNbt() && source.getNbt() != null) {
            target.setNbt(source.getNbt().copy());
        }

        backupActive = false;
        backupTicks = 0;
        backupSuccess = true;
        successTicks = 10;

        if (world != null) {
            world.playSound(null, pos, ModSounds.SCANNER_BUTTON3, SoundCategory.BLOCKS, 0.8F, 1.0F);
        }
        markDirty();
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
        markDirty();
    }

    public boolean isBackupActive() {
        return backupActive;
    }

    public int getBackupTicks() {
        return backupTicks;
    }

    public boolean isBackupSuccess() {
        return backupSuccess;
    }

    public PropertyDelegate getPropertyDelegate() {
        return propertyDelegate;
    }

    // --- Inventory Implementation ---

    @Override
    public int size() {
        return INVENTORY_SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : inventory) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getStack(int slot) {
        return (slot >= 0 && slot < inventory.size()) ? inventory.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack result = Inventories.splitStack(inventory, slot, amount);
        if (!result.isEmpty()) markDirty();
        return result;
    }

    @Override
    public ItemStack removeStack(int slot) {
        ItemStack result = Inventories.removeStack(inventory, slot);
        if (!result.isEmpty()) markDirty();
        return result;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        if (slot >= 0 && slot < inventory.size()) {
            inventory.set(slot, stack);
            if (stack.getCount() > getMaxCountPerStack()) {
                stack.setCount(getMaxCountPerStack());
            }
            markDirty();
        }
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return Inventory.canPlayerUse(this, player);
    }

    @Override
    public void clear() {
        inventory.clear();
        markDirty();
    }

    // --- ScreenHandler & Extended Data ---

    @Override
    public Text getDisplayName() {
        return Text.translatable("container.ntmdoors.keycard_programmer");
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new KeycardProgrammerScreenHandler(syncId, playerInventory, this, propertyDelegate);
    }

    @Override
    public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    // --- Persistence ---

    @Override
    public void writeNbt(NbtCompound tag) {
        super.writeNbt(tag);
        Inventories.writeNbt(tag, inventory);
        tag.putInt("page", page);
        tag.putBoolean("backupActive", backupActive);
        tag.putInt("backupTicks", backupTicks);
    }

    @Override
    public void readNbt(NbtCompound tag) {
        super.readNbt(tag);
        inventory.clear();
        Inventories.readNbt(tag, inventory);
        page = tag.getInt("page");
        backupActive = tag.getBoolean("backupActive");
        backupTicks = tag.getInt("backupTicks");
    }
}
