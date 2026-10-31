package com.ntmdoors.network;

import com.ntmdoors.item.ItemOperatorKeycard;
import com.ntmdoors.tile.TileEntityKeycardScanner;
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
 * Client to server: the player clicked a key on the scanner assignment screen. The server checks
 * the operator card, the code session and the scanner before moving the key between the lists.
 */
public class MessageScannerKey implements IMessage {

    private BlockPos pos;
    private String serial;
    private boolean assign;

    public MessageScannerKey() {
    }

    public MessageScannerKey(BlockPos pos, String serial, boolean assign) {
        this.pos = pos;
        this.serial = serial;
        this.assign = assign;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        boolean present = buf.readBoolean();
        pos = present ? BlockPos.fromLong(buf.readLong()) : null;
        serial = ByteBufUtils.readUTF8String(buf);
        assign = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(pos != null);
        if (pos != null) buf.writeLong(pos.toLong());
        ByteBufUtils.writeUTF8String(buf, serial == null ? "" : serial);
        buf.writeBoolean(assign);
    }

    public static class Handler implements IMessageHandler<MessageScannerKey, IMessage> {

        @Override
        public IMessage onMessage(MessageScannerKey message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> apply(player, message));
            return null;
        }

        private static void apply(EntityPlayerMP player, MessageScannerKey message) {
            if (message.pos == null || message.serial == null || message.serial.isEmpty()) return;
            if (player.getDistanceSq(message.pos.getX() + 0.5D, message.pos.getY() + 0.5D,
                    message.pos.getZ() + 0.5D) > 64D) {
                return;
            }

            TileEntity te = player.world.getTileEntity(message.pos);
            if (!(te instanceof TileEntityKeycardScanner)) return;
            TileEntityKeycardScanner scanner = (TileEntityKeycardScanner) te;
            if (!scanner.hasBoundDoors() || !scanner.hasCodeSessionFor(player)) return;

            if (!message.assign) {
                scanner.unassignKey(message.serial);
                return;
            }

            ItemStack card = operatorCard(player);
            if (card.isEmpty()) return;
            for (PrivateKeys.Key key : PrivateKeys.get(card)) {
                if (key.serial.equals(message.serial)) {
                    scanner.assignKey(key);
                    return;
                }
            }
        }

        /** Either hand is fine, matching the screen's available-key list. */
        private static ItemStack operatorCard(EntityPlayerMP player) {
            ItemStack main = player.getHeldItemMainhand();
            if (main.getItem() instanceof ItemOperatorKeycard) return main;
            ItemStack off = player.getHeldItemOffhand();
            return off.getItem() instanceof ItemOperatorKeycard ? off : ItemStack.EMPTY;
        }
    }
}
