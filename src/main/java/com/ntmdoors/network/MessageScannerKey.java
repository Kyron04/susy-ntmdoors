package com.ntmdoors.network;

import com.ntmdoors.item.ItemOperatorKeycard;
import com.ntmdoors.tile.TileEntityKeycardScanner;
import com.ntmdoors.util.Biometrics;
import com.ntmdoors.util.PrivateKeys;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client to server: the player clicked a row on the scanner assignment screen. The server checks
 * the operator card, the code session and the scanner before moving the entry between the lists.
 * An entry is either a private key serial or a biometric UUID; the flag selects which.
 */
public class MessageScannerKey implements IMessage {

    private BlockPos pos;
    private String value;
    private boolean assign;
    private boolean biometric;

    public MessageScannerKey() {
    }

    public MessageScannerKey(BlockPos pos, String value, boolean assign, boolean biometric) {
        this.pos = pos;
        this.value = value;
        this.assign = assign;
        this.biometric = biometric;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        boolean present = buf.readBoolean();
        pos = present ? BlockPos.fromLong(buf.readLong()) : null;
        value = ByteBufUtils.readUTF8String(buf);
        assign = buf.readBoolean();
        biometric = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(pos != null);
        if (pos != null) buf.writeLong(pos.toLong());
        ByteBufUtils.writeUTF8String(buf, value == null ? "" : value);
        buf.writeBoolean(assign);
        buf.writeBoolean(biometric);
    }

    public static class Handler implements IMessageHandler<MessageScannerKey, IMessage> {

        @Override
        public IMessage onMessage(MessageScannerKey message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> apply(player, message));
            return null;
        }

        private static void apply(EntityPlayerMP player, MessageScannerKey message) {
            if (message.pos == null || message.value == null || message.value.isEmpty()) return;
            if (player.getDistanceSq(message.pos.getX() + 0.5D, message.pos.getY() + 0.5D,
                    message.pos.getZ() + 0.5D) > 64D) {
                return;
            }

            TileEntity te = player.world.getTileEntity(message.pos);
            if (!(te instanceof TileEntityKeycardScanner)) return;
            TileEntityKeycardScanner scanner = (TileEntityKeycardScanner) te;
            if (!scanner.hasBoundDoors() || !scanner.hasCodeSessionFor(player)) return;

            if (!message.assign) {
                if (message.biometric) scanner.unassignId(message.value);
                else scanner.unassignKey(message.value);
                return;
            }

            ItemStack card = operatorCard(player);
            if (card.isEmpty()) return;
            if (message.biometric) {
                assignIdentity(scanner, card, message.value);
                return;
            }
            for (PrivateKeys.Key key : PrivateKeys.get(card)) {
                if (key.serial.equals(message.value)) {
                    scanner.assignKey(key);
                    return;
                }
            }
        }

        /** Only the identity actually carried by the held card can be enrolled. */
        private static void assignIdentity(TileEntityKeycardScanner scanner, ItemStack card, String uuid) {
            if (!Biometrics.isBound(card) || !Biometrics.ownerUuid(card).equals(uuid)) return;
            scanner.assignId(new TileEntityKeycardScanner.BiometricId(uuid, Biometrics.ownerName(card)));
        }

        /** Either hand is fine, matching the screen's available-entry list. */
        private static ItemStack operatorCard(EntityPlayerMP player) {
            ItemStack main = player.getHeldItemMainhand();
            if (main.getItem() instanceof ItemOperatorKeycard) return main;
            ItemStack off = player.getHeldItemOffhand();
            return off.getItem() instanceof ItemOperatorKeycard ? off : ItemStack.EMPTY;
        }
    }
}
