package com.ntmdoors.network;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.monitor.SecurityNetSummary;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

/** Server to client: Security net rows for the landing page, plus the newly created net id. */
public class MessageMonitorNets implements IMessage {

    private BlockPos deskPos;
    private int createdNetId = -1;
    private List<SecurityNetSummary> nets = new ArrayList<>();

    public MessageMonitorNets() {
    }

    public MessageMonitorNets(BlockPos deskPos, int createdNetId, List<SecurityNetSummary> nets) {
        this.deskPos = deskPos;
        this.createdNetId = createdNetId;
        this.nets = new ArrayList<>(nets);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        deskPos = NetworkUtil.readPos(buf);
        createdNetId = buf.readInt();
        int count = buf.readUnsignedShort();
        nets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int id = buf.readInt();
            String name = ByteBufUtils.readUTF8String(buf);
            int devices = buf.readInt();
            nets.add(new SecurityNetSummary(id, name, devices));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        NetworkUtil.writePos(buf, deskPos);
        buf.writeInt(createdNetId);
        buf.writeShort(nets.size());
        for (SecurityNetSummary net : nets) {
            buf.writeInt(net.id);
            ByteBufUtils.writeUTF8String(buf, net.name);
            buf.writeInt(net.deviceCount);
        }
    }

    public static class Handler implements IMessageHandler<MessageMonitorNets, IMessage> {

        @Override
        public IMessage onMessage(MessageMonitorNets message, MessageContext ctx) {
            if (message.deskPos != null) {
                NTMDoors.proxy.handleMonitorNets(
                        message.deskPos, message.createdNetId, message.nets);
            }
            return null;
        }
    }
}
