package com.ntmdoors.network;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.monitor.MonitorDevice;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

/** Server to client: one Security net's device list and linked-key display state. */
public class MessageMonitorDevices implements IMessage {

    private static final int MAX_DEVICES = 512;

    private BlockPos deskPos;
    private int netId;
    private String netName;
    private String linkedKeyName;
    private int eventMask;
    private List<MonitorDevice> devices = new ArrayList<>();

    public MessageMonitorDevices() {
    }

    public MessageMonitorDevices(BlockPos deskPos, int netId, String netName,
                                 String linkedKeyName, int eventMask,
                                 List<MonitorDevice> devices) {
        this.deskPos = deskPos;
        this.netId = netId;
        this.netName = netName;
        this.linkedKeyName = linkedKeyName;
        this.eventMask = eventMask;
        this.devices = new ArrayList<>(devices);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        deskPos = NetworkUtil.readPos(buf);
        netId = buf.readInt();
        netName = ByteBufUtils.readUTF8String(buf);
        linkedKeyName = ByteBufUtils.readUTF8String(buf);
        eventMask = buf.readInt();
        int count = Math.min(buf.readUnsignedShort(), MAX_DEVICES);
        devices = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            MonitorDevice.Type type = MonitorDevice.Type.byId(buf.readByte());
            BlockPos pos = BlockPos.fromLong(buf.readLong());
            String name = ByteBufUtils.readUTF8String(buf);
            devices.add(new MonitorDevice(type, pos, name));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        NetworkUtil.writePos(buf, deskPos);
        buf.writeInt(netId);
        ByteBufUtils.writeUTF8String(buf, netName == null ? "" : netName);
        ByteBufUtils.writeUTF8String(buf, linkedKeyName == null ? "" : linkedKeyName);
        buf.writeInt(eventMask);
        int count = Math.min(devices.size(), MAX_DEVICES);
        buf.writeShort(count);
        for (int i = 0; i < count; i++) {
            MonitorDevice device = devices.get(i);
            buf.writeByte(device.type.ordinal());
            buf.writeLong(device.pos.toLong());
            ByteBufUtils.writeUTF8String(buf, device.name);
        }
    }

    public static class Handler implements IMessageHandler<MessageMonitorDevices, IMessage> {

        @Override
        public IMessage onMessage(MessageMonitorDevices message, MessageContext ctx) {
            if (message.deskPos != null) {
                NTMDoors.proxy.handleMonitorDevices(message.deskPos, message.netId,
                        message.netName, message.linkedKeyName, message.eventMask,
                        message.devices);
            }
            return null;
        }
    }
}
