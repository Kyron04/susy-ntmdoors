package com.ntmdoors.network;

import com.ntmdoors.monitor.NotepadDocument;
import com.ntmdoors.tile.TileEntityMonitorDesk;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client to server: list, create, save, rename or delete the shared notes stored on a monitor
 * desk. The server always answers with the complete current notepad state.
 */
public class MessageNotepadAction implements IMessage {

    public static final int LIST = 0;
    public static final int CREATE = 1;
    public static final int SAVE = 2;
    public static final int RENAME = 3;
    public static final int DELETE = 4;

    private BlockPos deskPos;
    private int action;
    private int documentId;
    private String value = "";

    public MessageNotepadAction() {
    }

    public MessageNotepadAction(BlockPos deskPos, int action, int documentId, String value) {
        this.deskPos = deskPos;
        this.action = action;
        this.documentId = documentId;
        this.value = value == null ? "" : value;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        deskPos = NetworkUtil.readPos(buf);
        action = buf.readByte();
        documentId = buf.readInt();
        value = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        NetworkUtil.writePos(buf, deskPos);
        buf.writeByte(action);
        buf.writeInt(documentId);
        ByteBufUtils.writeUTF8String(buf, value);
    }

    public static class Handler implements IMessageHandler<MessageNotepadAction, IMessage> {

        @Override
        public IMessage onMessage(MessageNotepadAction message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> apply(player, message));
            return null;
        }

        private static void apply(EntityPlayerMP player, MessageNotepadAction message) {
            if (message.deskPos == null
                    || !NetworkUtil.isWithinReach(player, message.deskPos)) {
                return;
            }
            TileEntityMonitorDesk desk = TileEntityMonitorDesk.at(player.world, message.deskPos);
            if (desk == null) return;

            int focusId = -1;
            switch (message.action) {
                case CREATE:
                    NotepadDocument created = desk.createNotepadDocument();
                    if (created != null) {
                        focusId = created.id;
                    }
                    break;
                case SAVE:
                    desk.saveNotepadText(message.documentId, message.value);
                    focusId = message.documentId;
                    break;
                case RENAME:
                    desk.renameNotepadDocument(message.documentId, message.value);
                    focusId = message.documentId;
                    break;
                case DELETE:
                    desk.deleteNotepadDocument(message.documentId);
                    break;
                case LIST:
                default:
                    break;
            }
            ModNetwork.INSTANCE.sendTo(new MessageNotepadData(
                    desk.getPos(), focusId, desk.getNotepadDocuments()), player);
        }
    }
}
