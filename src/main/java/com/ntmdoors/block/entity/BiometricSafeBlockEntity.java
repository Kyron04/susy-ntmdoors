package com.ntmdoors.block.entity;

import com.ntmdoors.item.ItemBiometricOperatorKeycard;
import com.ntmdoors.sound.ModSounds;
import com.ntmdoors.util.Biometrics;
import com.ntmdoors.util.HeldItems;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class BiometricSafeBlockEntity extends SafeBlockEntity {

    private static final int SCAN_TICKS = 20;

    @Nullable
    private UUID scanPlayer;
    private int scanTicks;
    private ItemStack scanCard = ItemStack.EMPTY;

    private String ownerUuid = "";
    private String ownerName = "";

    public BiometricSafeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BIOMETRIC_SAFE, pos, state);
    }

    @Override
    public boolean isBiometric() {
        return true;
    }

    public boolean hasOwner() {
        return !ownerUuid.isEmpty();
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwner(UUID ownerId, String name) {
        this.ownerUuid = ownerId != null ? ownerId.toString() : "";
        this.ownerName = name != null ? name : "";
        markDirty();
    }

    public boolean isOwner(UUID uuid) {
        return uuid != null && uuid.toString().equals(ownerUuid);
    }

    public boolean bindOwner(PlayerEntity player) {
        if (world == null || world.isClient || player == null || hasOwner()) return false;
        ownerUuid = player.getUuid().toString();
        ownerName = player.getName().getString();
        markDirty();
        sync();
        return true;
    }

    public boolean matchesOwner(ItemStack card) {
        return !hasOwner() || ownerUuid.equals(Biometrics.ownerUuid(card));
    }

    public boolean bypassesLockout(ItemStack card, PlayerEntity player) {
        if (!Biometrics.isBound(card) || !matchesOwner(card)) return false;
        return !isMatchHolder() || Biometrics.isOwnedBy(card, player);
    }

    public void tryBiometricAccess(ServerPlayerEntity player) {
        if (world == null || world.isClient || player == null) return;

        ItemStack card = HeldItems.find(player, ItemBiometricOperatorKeycard.class);
        if (card.isEmpty()) {
            player.sendMessage(Text.translatable("ntmdoors.msg.safe_biometric_required"), true);
            return;
        }

        if (scanPlayer != null) {
            if (!scanPlayer.equals(player.getUuid())) {
                playFail();
                sendResult(player, (byte) 2);
            }
            player.sendMessage(Text.translatable("ntmdoors.msg.safe_busy"), true);
            return;
        }

        if (!Biometrics.isBound(card)) {
            fireRedstoneEvent(RS_SCAN_FAIL);
            recordFailure();
            playFail();
            sendResult(player, (byte) 2);
            player.sendMessage(Text.translatable("ntmdoors.msg.safe_biometric_denied"), true);
            return;
        }

        if (!matchesOwner(card)) {
            fireRedstoneEvent(RS_SCAN_FAIL);
            recordFailure();
            playFail();
            sendResult(player, (byte) 2);
            player.sendMessage(Text.translatable("ntmdoors.msg.safe_biometric_owner", ownerName), true);
            return;
        }

        if (isLockedOut() && !bypassesLockout(card, player)) {
            fireRedstoneEvent(RS_SCAN_FAIL);
            playFail();
            sendResult(player, (byte) 3);
            player.sendMessage(Text.translatable("ntmdoors.msg.safe_locked_out"), true);
            return;
        }

        if (isMatchHolder() && !Biometrics.isOwnedBy(card, player)) {
            fireRedstoneEvent(RS_MATCH_FAIL);
            fireRedstoneEvent(RS_SCAN_FAIL);
            recordFailure();
            playFail();
            sendResult(player, (byte) 2);
            player.sendMessage(Text.translatable("ntmdoors.msg.safe_biometric_denied"), true);
            return;
        }

        if (isMatchHolder()) fireRedstoneEvent(RS_MATCH_SUCCESS);

        scanPlayer = player.getUuid();
        scanTicks = SCAN_TICKS;
        scanCard = card.copy();
        world.playSound(null, pos, ModSounds.SCANNER_BUTTON3, SoundCategory.BLOCKS, 0.8F, 1.0F);
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state) {
        super.tick(world, pos, state);
        if (world.isClient || scanPlayer == null) return;
        if (--scanTicks <= 0) {
            resolveScan();
        }
    }

    private void resolveScan() {
        if (world == null || world.isClient || scanPlayer == null) return;
        ServerPlayerEntity player = (ServerPlayerEntity) world.getPlayerByUuid(scanPlayer);
        ItemStack card = scanCard;
        scanPlayer = null;
        scanCard = ItemStack.EMPTY;

        if (player == null) return;

        boolean valid = card.getItem() instanceof ItemBiometricOperatorKeycard
                && Biometrics.isBound(card)
                && matchesOwner(card)
                && (!isMatchHolder() || Biometrics.isOwnedBy(card, player));

        if (valid && isLockedOut() && !bypassesLockout(card, player)) valid = false;

        if (valid) {
            resetFailures();
            grantAccess(player);
            scheduleOpen(player);
            playSuccess();
            fireRedstoneEvent(RS_SCAN_SUCCESS);
            sendResult(player, (byte) 0); // ACCEPTED
            player.sendMessage(Text.translatable("ntmdoors.msg.safe_biometric_granted"), true);
        } else {
            fireRedstoneEvent(RS_SCAN_FAIL);
            recordFailure();
            playFail();
            sendResult(player, (byte) (isLockedOut() ? 3 : 2));
            player.sendMessage(Text.translatable(isLockedOut() ? "ntmdoors.msg.safe_locked_out" : "ntmdoors.msg.safe_biometric_denied"), true);
        }
        markDirty();
        sync();
    }

    @Override
    public void writeNbt(NbtCompound tag) {
        super.writeNbt(tag);
        tag.putString("safeOwner", ownerUuid);
        tag.putString("safeOwnerName", ownerName);
    }

    @Override
    public void readNbt(NbtCompound tag) {
        super.readNbt(tag);
        ownerUuid = tag.getString("safeOwner");
        ownerName = tag.getString("safeOwnerName");
    }
}
