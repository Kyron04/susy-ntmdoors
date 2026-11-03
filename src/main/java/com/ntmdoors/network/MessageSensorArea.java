package com.ntmdoors.network;

import com.ntmdoors.tile.TileEntityMotionSensor;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client to server: the complete 5x5 footprint depth map from the Bound Area page. The server
 * validates every depth and that the centre cell still contains the sensor.
 */
public class MessageSensorArea implements IMessage {

    private BlockPos pos;
    private boolean clear;
    private byte[] depths = new byte[TileEntityMotionSensor.GRID_SIZE * TileEntityMotionSensor.GRID_SIZE];

    public MessageSensorArea() {
    }

    public MessageSensorArea(BlockPos pos, boolean clear, byte[] depths) {
        this.pos = pos;
        this.clear = clear;
        this.depths = depths == null ? new byte[0] : depths;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = NetworkUtil.readPos(buf);
        clear = buf.readBoolean();
        int length = buf.readUnsignedByte();
        depths = new byte[length];
        buf.readBytes(depths);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        NetworkUtil.writePos(buf, pos);
        buf.writeBoolean(clear);
        buf.writeByte(depths.length);
        buf.writeBytes(depths);
    }

    public static class Handler implements IMessageHandler<MessageSensorArea, IMessage> {

        @Override
        public IMessage onMessage(MessageSensorArea message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> apply(player, message));
            return null;
        }

        private static void apply(EntityPlayerMP player, MessageSensorArea message) {
            if (message.pos == null) return;
            if (!NetworkUtil.isWithinReach(player, message.pos)) return;

            TileEntity te = player.world.getTileEntity(message.pos);
            if (!(te instanceof TileEntityMotionSensor)) return;
            TileEntityMotionSensor sensor = (TileEntityMotionSensor) te;
            if (!sensor.hasConsoleAccess(player)) return;

            if (message.clear) {
                if (sensor.clearArea()) {
                    player.sendStatusMessage(new TextComponentTranslation(
                            "ntmdoors.msg.motion_area_cleared"), true);
                }
                return;
            }

            if (!sensor.setAreaDepths(message.depths)) {
                player.sendStatusMessage(new TextComponentTranslation(
                        "ntmdoors.msg.motion_area_invalid"), true);
            }
        }
    }
}
