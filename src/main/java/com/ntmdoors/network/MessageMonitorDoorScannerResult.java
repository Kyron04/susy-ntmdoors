package com.ntmdoors.network;

import com.ntmdoors.NTMDoors;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Server to client: the scanner resolved for a linked door, or why none could be opened. */
public class MessageMonitorDoorScannerResult implements IMessage {

    public static final int FOUND = 0;
    public static final int NO_SCANNER = 1;
    public static final int NOT_LOADED = 2;

    private BlockPos doorPos;
    private BlockPos scannerPos;
    private int result;

    public MessageMonitorDoorScannerResult() {
    }

    public MessageMonitorDoorScannerResult(BlockPos doorPos, BlockPos scannerPos, int result) {
        this.doorPos = doorPos;
        this.scannerPos = scannerPos;
        this.result = result;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        doorPos = NetworkUtil.readPos(buf);
        scannerPos = NetworkUtil.readPos(buf);
        result = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        NetworkUtil.writePos(buf, doorPos);
        NetworkUtil.writePos(buf, scannerPos);
        buf.writeByte(result);
    }

    public static class Handler implements IMessageHandler<MessageMonitorDoorScannerResult, IMessage> {

        @Override
        public IMessage onMessage(MessageMonitorDoorScannerResult message, MessageContext ctx) {
            if (message.doorPos != null) {
                NTMDoors.proxy.handleMonitorDoorScannerResult(
                        message.doorPos, message.scannerPos, message.result);
            }
            return null;
        }
    }
}
