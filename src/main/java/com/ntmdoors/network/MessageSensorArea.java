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
 * Client to server: a Bound Area page edit. The values are sensor-relative offsets; the server
 * re-validates the 5x5x5 limit and that the sensor remains inside the box.
 */
public class MessageSensorArea implements IMessage {

    private BlockPos pos;
    private boolean clear;
    private int minX, maxX, minY, maxY, minZ, maxZ;

    public MessageSensorArea() {
    }

    public MessageSensorArea(BlockPos pos, boolean clear,
                             int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        this.pos = pos;
        this.clear = clear;
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
        this.minZ = minZ;
        this.maxZ = maxZ;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = NetworkUtil.readPos(buf);
        clear = buf.readBoolean();
        minX = buf.readByte();
        maxX = buf.readByte();
        minY = buf.readByte();
        maxY = buf.readByte();
        minZ = buf.readByte();
        maxZ = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        NetworkUtil.writePos(buf, pos);
        buf.writeBoolean(clear);
        buf.writeByte(minX);
        buf.writeByte(maxX);
        buf.writeByte(minY);
        buf.writeByte(maxY);
        buf.writeByte(minZ);
        buf.writeByte(maxZ);
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

            if (!sensor.setArea(message.minX, message.maxX, message.minY, message.maxY,
                    message.minZ, message.maxZ)) {
                player.sendStatusMessage(new TextComponentTranslation(
                        "ntmdoors.msg.motion_area_invalid"), true);
            }
        }
    }
}
