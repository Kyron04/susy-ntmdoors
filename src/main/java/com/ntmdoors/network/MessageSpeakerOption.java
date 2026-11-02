package com.ntmdoors.network;

import com.ntmdoors.tile.TileEntitySpeaker;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Client to server: the Speaker screen changed its enabled state or selected sound. */
public class MessageSpeakerOption implements IMessage {

    public static final int ENABLED = 0;
    public static final int SOUND = 1;
    public static final int RANGE = 2;
    public static final int VOLUME = 3;
    public static final int OPTION_COUNT = 4;

    private BlockPos pos;
    private int option;
    private int value;

    public MessageSpeakerOption() {
    }

    public MessageSpeakerOption(BlockPos pos, int option, int value) {
        this.pos = pos;
        this.option = option;
        this.value = value;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = NetworkUtil.readPos(buf);
        option = buf.readByte();
        value = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        NetworkUtil.writePos(buf, pos);
        buf.writeByte(option);
        buf.writeInt(value);
    }

    public static class Handler implements IMessageHandler<MessageSpeakerOption, IMessage> {

        @Override
        public IMessage onMessage(MessageSpeakerOption message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> apply(player, message));
            return null;
        }

        private static void apply(EntityPlayerMP player, MessageSpeakerOption message) {
            if (message.pos == null) return;
            if (message.option < 0 || message.option >= OPTION_COUNT) return;
            if (!NetworkUtil.isWithinReach(player, message.pos)) return;

            TileEntity te = player.world.getTileEntity(message.pos);
            if (!(te instanceof TileEntitySpeaker)) return;
            TileEntitySpeaker speaker = (TileEntitySpeaker) te;

            switch (message.option) {
                case ENABLED:
                    speaker.setEnabled(message.value != 0);
                    break;
                case SOUND:
                    speaker.setSound(message.value);
                    break;
                case RANGE:
                    speaker.setRange(message.value);
                    break;
                case VOLUME:
                    speaker.setVolume(message.value);
                    break;
                default:
                    break;
            }
        }
    }
}
