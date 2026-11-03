package com.ntmdoors.network;

import com.ntmdoors.tile.TileEntityMotionSensor;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Client to server: one toggle row on the Motion Sensor's options page. */
public class MessageSensorOption implements IMessage {

    public static final int MATCH_HOLDER = 0;
    public static final int IGNORE_KEYS = 1;
    public static final int IGNORE_IDS = 2;
    public static final int ENABLED = 3;
    public static final int REDSTONE_AUTHORIZED = 4;
    public static final int REDSTONE_UNAUTHORIZED = 5;
    public static final int REDSTONE_DISABLED = 6;
    public static final int REDSTONE_ENABLED = 7;
    public static final int DETECT_PLAYERS = 8;
    public static final int DETECT_HOSTILE = 9;
    public static final int DETECT_NEUTRAL = 10;
    public static final int PERSIST_WITHIN_BOUNDS = 11;
    public static final int PERSIST_OUT_OF_BOUNDS = 12;
    public static final int OPTION_COUNT = 13;

    private BlockPos pos;
    private int option;
    private boolean value;

    public MessageSensorOption() {
    }

    public MessageSensorOption(BlockPos pos, int option, boolean value) {
        this.pos = pos;
        this.option = option;
        this.value = value;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = NetworkUtil.readPos(buf);
        option = buf.readByte();
        value = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        NetworkUtil.writePos(buf, pos);
        buf.writeByte(option);
        buf.writeBoolean(value);
    }

    public static class Handler implements IMessageHandler<MessageSensorOption, IMessage> {

        @Override
        public IMessage onMessage(MessageSensorOption message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> apply(player, message));
            return null;
        }

        private static void apply(EntityPlayerMP player, MessageSensorOption message) {
            if (message.pos == null) return;
            if (message.option < 0 || message.option >= OPTION_COUNT) return;
            if (!NetworkUtil.isWithinReach(player, message.pos)) return;

            TileEntity te = player.world.getTileEntity(message.pos);
            if (!(te instanceof TileEntityMotionSensor)) return;
            TileEntityMotionSensor sensor = (TileEntityMotionSensor) te;
            if (!sensor.hasConsoleAccess(player)) return;

            switch (message.option) {
                case MATCH_HOLDER:
                    sensor.setMatchHolder(message.value);
                    break;
                case IGNORE_KEYS:
                    sensor.setIgnoreKeys(message.value);
                    break;
                case IGNORE_IDS:
                    sensor.setIgnoreIds(message.value);
                    break;
                case ENABLED:
                    sensor.setEnabled(message.value);
                    break;
                case REDSTONE_AUTHORIZED:
                    sensor.setRedstoneEvent(TileEntityMotionSensor.RS_AUTHORIZED, message.value);
                    break;
                case REDSTONE_UNAUTHORIZED:
                    sensor.setRedstoneEvent(TileEntityMotionSensor.RS_UNAUTHORIZED, message.value);
                    break;
                case REDSTONE_DISABLED:
                    sensor.setRedstoneEvent(TileEntityMotionSensor.RS_DISABLED, message.value);
                    break;
                case REDSTONE_ENABLED:
                    sensor.setRedstoneEvent(TileEntityMotionSensor.RS_ENABLED, message.value);
                    break;
                case DETECT_PLAYERS:
                    sensor.setDetectPlayers(message.value);
                    break;
                case DETECT_HOSTILE:
                    sensor.setDetectHostile(message.value);
                    break;
                case DETECT_NEUTRAL:
                    sensor.setDetectNeutral(message.value);
                    break;
                case PERSIST_WITHIN_BOUNDS:
                    sensor.setPersistWithinBounds(message.value);
                    break;
                case PERSIST_OUT_OF_BOUNDS:
                    sensor.setPersistOutOfBounds(message.value);
                    break;
                default:
                    break;
            }
        }
    }
}
