package com.ntmdoors.tile;

import com.ntmdoors.item.ItemBiometricOperatorKeycard;
import com.ntmdoors.network.MessageSafeResult;
import com.ntmdoors.util.Biometrics;
import com.ntmdoors.util.HeldItems;
import com.ntmdoors.util.ModSounds;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentTranslation;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Biometric safe. It shares the 27-slot inventory, toggles and lockout with the code safe, but the
 * only card it reads is the Biometric Operator Keycard. Match user makes that card personal; with
 * it off any bound biometric operator card opens the safe.
 */
public class TileEntityBiometricSafe extends TileEntitySafe {

    /** The card-read pause, matching the scanner's one-second read. */
    private static final int SCAN_TICKS = 20;

    @Nullable
    private UUID scanPlayer;
    private int scanTicks;
    private ItemStack scanCard = ItemStack.EMPTY;

    /** Biometric identity of the player who placed this safe; empty on legacy safes. */
    private String ownerUuid = "";
    private String ownerName = "";

    @Override
    public boolean isBiometric() {
        return true;
    }

    @Override
    public boolean supportsCode() {
        return false;
    }

    /** True when this safe was placed by a player and is bound to their biometric ID. */
    public boolean hasOwner() {
        return !ownerUuid.isEmpty();
    }

    public String getOwnerName() {
        return ownerName;
    }

    /**
     * Server side: records the placer's biometric ID. The first owner wins, and a legacy safe with
     * no identity stays open to every valid biometric card, exactly as before.
     */
    public boolean bindOwner(EntityPlayer player) {
        if (world == null || world.isRemote || player == null || hasOwner()) return false;
        ownerUuid = player.getUniqueID().toString();
        ownerName = player.getName();
        sync();
        return true;
    }

    /** True when the card carries the safe owner's biometric ID, or the safe has no owner. */
    public boolean matchesOwner(ItemStack card) {
        return !hasOwner() || ownerUuid.equals(Biometrics.ownerUuid(card));
    }

    /** Server side: starts the short read, or refuses a card that cannot open this safe. */
    public void tryBiometricAccess(EntityPlayer player) {
        if (world == null || world.isRemote || player == null) return;

        ItemStack card = HeldItems.find(player, ItemBiometricOperatorKeycard.class);
        if (card.isEmpty()) {
            msg(player, "ntmdoors.msg.safe_biometric_required");
            return;
        }
        if (scanPlayer != null) {
            if (!scanPlayer.equals(player.getUniqueID())) {
                playFail();
                sendResult(player, MessageSafeResult.DENIED);
            }
            msg(player, "ntmdoors.msg.safe_busy");
            return;
        }
        if (!Biometrics.isBound(card)) {
            fireRedstoneEvent(RS_SCAN_FAIL);
            recordFailure();
            playFail();
            sendResult(player, MessageSafeResult.DENIED);
            msg(player, "ntmdoors.msg.safe_biometric_denied");
            return;
        }
        if (!matchesOwner(card)) {
            fireRedstoneEvent(RS_SCAN_FAIL);
            recordFailure();
            playFail();
            sendResult(player, MessageSafeResult.DENIED);
            player.sendStatusMessage(new TextComponentTranslation(
                    "ntmdoors.msg.safe_biometric_owner", ownerName), true);
            return;
        }
        if (isLockedOut() && !bypassesLockout(card, player)) {
            fireRedstoneEvent(RS_SCAN_FAIL);
            playFail();
            sendResult(player, MessageSafeResult.LOCKED_OUT);
            msg(player, "ntmdoors.msg.safe_locked_out");
            return;
        }
        if (isMatchHolder() && !Biometrics.isOwnedBy(card, player)) {
            fireRedstoneEvent(RS_MATCH_FAIL);
            fireRedstoneEvent(RS_SCAN_FAIL);
            recordFailure();
            playFail();
            sendResult(player, MessageSafeResult.DENIED);
            msg(player, "ntmdoors.msg.safe_biometric_denied");
            return;
        }
        if (isMatchHolder()) fireRedstoneEvent(RS_MATCH_SUCCESS);

        scanPlayer = player.getUniqueID();
        scanTicks = SCAN_TICKS;
        scanCard = card.copy();
        world.playSound(null, pos, ModSounds.SCANNER_BUTTON3, SoundCategory.BLOCKS, 0.8F, 1F);
    }

    @Override
    public void update() {
        super.update();
        if (world == null || world.isRemote || scanPlayer == null) return;
        if (--scanTicks > 0) return;
        resolveScan();
    }

    private void resolveScan() {
        EntityPlayer player = world.getPlayerEntityByUUID(scanPlayer);
        ItemStack card = scanCard;
        scanPlayer = null;
        scanCard = ItemStack.EMPTY;

        if (player == null) return;

        boolean valid = card.getItem() instanceof ItemBiometricOperatorKeycard
                && Biometrics.isBound(card)
                && matchesOwner(card)
                && (!isMatchHolder() || Biometrics.isOwnedBy(card, player));
        // A lockout raised during the read still applies, unless this card is allowed to bypass it.
        if (valid && isLockedOut() && !bypassesLockout(card, player)) valid = false;

        if (valid) {
            resetFailures();
            grantAccess(player);
            scheduleOpen(player);
            playSuccess();
            fireRedstoneEvent(RS_SCAN_SUCCESS);
            sendResult(player, MessageSafeResult.ACCEPTED);
            msg(player, "ntmdoors.msg.safe_biometric_granted");
        } else {
            fireRedstoneEvent(RS_SCAN_FAIL);
            recordFailure();
            playFail();
            sendResult(player, MessageSafeResult.DENIED);
            msg(player, isLockedOut() ? "ntmdoors.msg.safe_locked_out"
                    : "ntmdoors.msg.safe_biometric_denied");
        }
        sync();
    }

    /** The owner's card can still reach a safe that someone else locked out. */
    private boolean bypassesLockout(ItemStack card, EntityPlayer player) {
        if (!Biometrics.isBound(card) || !matchesOwner(card)) return false;
        return !isMatchHolder() || Biometrics.isOwnedBy(card, player);
    }

    // ------------------------------------------------------------ persistence / sync

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setString("safeOwner", ownerUuid);
        tag.setString("safeOwnerName", ownerName);
        return tag;
    }

    @Override
    protected NBTTagCompound writeSyncTag(NBTTagCompound tag) {
        super.writeSyncTag(tag);
        tag.setString("safeOwner", ownerUuid);
        tag.setString("safeOwnerName", ownerName);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        ownerUuid = tag.getString("safeOwner");
        ownerName = tag.getString("safeOwnerName");
    }
}
