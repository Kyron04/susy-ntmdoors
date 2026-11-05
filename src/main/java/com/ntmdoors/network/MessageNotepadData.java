package com.ntmdoors.network;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.monitor.NotepadDocument;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

/** Server to client: the complete shared notepad state for one monitor desk. */
public class MessageNotepadData implements IMessage {

    private BlockPos deskPos;
    private int focusId = -1;
    private List<NotepadDocument> documents = new ArrayList<>();

    public MessageNotepadData() {
    }

    public MessageNotepadData(BlockPos deskPos, int focusId, List<NotepadDocument> documents) {
        this.deskPos = deskPos;
        this.focusId = focusId;
        for (NotepadDocument document : documents) {
            this.documents.add(new NotepadDocument(
                    document.id, document.getName(), document.getText()));
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        deskPos = NetworkUtil.readPos(buf);
        focusId = buf.readInt();
        int count = Math.min(buf.readUnsignedByte(), NotepadDocument.MAX_DOCUMENTS);
        documents = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int id = buf.readInt();
            String name = ByteBufUtils.readUTF8String(buf);
            String text = ByteBufUtils.readUTF8String(buf);
            documents.add(new NotepadDocument(id, name, text));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        NetworkUtil.writePos(buf, deskPos);
        buf.writeInt(focusId);
        int count = Math.min(documents.size(), NotepadDocument.MAX_DOCUMENTS);
        buf.writeByte(count);
        for (int i = 0; i < count; i++) {
            NotepadDocument document = documents.get(i);
            buf.writeInt(document.id);
            ByteBufUtils.writeUTF8String(buf, document.getName());
            ByteBufUtils.writeUTF8String(buf, document.getText());
        }
    }

    public static class Handler implements IMessageHandler<MessageNotepadData, IMessage> {

        @Override
        public IMessage onMessage(MessageNotepadData message, MessageContext ctx) {
            if (message.deskPos != null) {
                NTMDoors.proxy.handleNotepadData(message.deskPos, message.focusId,
                        message.documents);
            }
            return null;
        }
    }
}
