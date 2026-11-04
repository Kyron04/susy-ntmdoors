package com.ntmdoors.network;

import com.ntmdoors.monitor.MonitorAccess;
import com.ntmdoors.tile.TileEntityDoorGeneric;
import com.ntmdoors.tile.TileEntityKeycardScanner;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.List;

/**
 * Client to server: the player clicked a linked door in the Security list. The door's controlling
 * scanner positions are server-side data, so the server resolves the first loaded scanner and
 * replies with where to open the scanner screen.
 */
public class MessageMonitorDoorScanner implements IMessage {

    private BlockPos doorPos;

    public MessageMonitorDoorScanner() {
    }

    public MessageMonitorDoorScanner(BlockPos doorPos) {
        this.doorPos = doorPos;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        doorPos = NetworkUtil.readPos(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        NetworkUtil.writePos(buf, doorPos);
    }

    public static class Handler implements IMessageHandler<MessageMonitorDoorScanner, IMessage> {

        @Override
        public IMessage onMessage(MessageMonitorDoorScanner message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> apply(player, message));
            return null;
        }

        private static void apply(EntityPlayerMP player, MessageMonitorDoorScanner message) {
            if (message.doorPos == null || !MonitorAccess.canAccess(player, message.doorPos)) {
                return;
            }

            TileEntity te = player.world.getTileEntity(message.doorPos);
            if (!(te instanceof TileEntityDoorGeneric)) {
                send(player, message.doorPos, null, MessageMonitorDoorScannerResult.NO_SCANNER);
                return;
            }

            List<BlockPos> scanners = ((TileEntityDoorGeneric) te).getBoundScanners();
            if (scanners.isEmpty()) {
                send(player, message.doorPos, null, MessageMonitorDoorScannerResult.NO_SCANNER);
                return;
            }

            boolean anyLoaded = false;
            for (BlockPos scannerPos : scanners) {
                if (!player.world.isBlockLoaded(scannerPos)) continue;
                anyLoaded = true;
                TileEntity scanner = player.world.getTileEntity(scannerPos);
                if (scanner instanceof TileEntityKeycardScanner) {
                    MonitorAccess.authorizeDevice(player, scannerPos);
                    send(player, message.doorPos, scannerPos,
                            MessageMonitorDoorScannerResult.FOUND);
                    return;
                }
            }

            send(player, message.doorPos, null, anyLoaded
                    ? MessageMonitorDoorScannerResult.NO_SCANNER
                    : MessageMonitorDoorScannerResult.NOT_LOADED);
        }

        private static void send(EntityPlayerMP player, BlockPos doorPos, BlockPos scannerPos,
                                 int result) {
            ModNetwork.INSTANCE.sendTo(new MessageMonitorDoorScannerResult(
                    doorPos, scannerPos, result), player);
        }
    }
}
