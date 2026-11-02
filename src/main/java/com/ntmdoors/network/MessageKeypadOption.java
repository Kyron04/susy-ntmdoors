package com.ntmdoors.network;

import com.ntmdoors.item.ItemRedstoneRelayKey;
import com.ntmdoors.tile.TileEntityDoorKeypad;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client to server: the Redstone Relay Key keypad screen changed a Redstone toggle or ran one of
 * the keypad actions. Every request is checked against the held key and the block position.
 */
public class MessageKeypadOption implements IMessage {

    public static final int ON_FAIL = 0;
    public static final int ON_SUCCESS = 1;
    public static final int RESET_CODE = 2;
    public static final int OVERRIDE_DOOR_LOCKS = 3;
    public static final int FORCE_CLOSE = 4;
    public static final int OPTION_COUNT = 5;

    private BlockPos pos;
    private int option;
    private boolean value;

    public MessageKeypadOption() {
    }

    public MessageKeypadOption(BlockPos pos, int option, boolean value) {
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

    public static class Handler implements IMessageHandler<MessageKeypadOption, IMessage> {

        @Override
        public IMessage onMessage(MessageKeypadOption message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> apply(player, message));
            return null;
        }

        private static void apply(EntityPlayerMP player, MessageKeypadOption message) {
            if (message.pos == null) return;
            if (message.option < 0 || message.option >= OPTION_COUNT) return;
            if (!ItemRedstoneRelayKey.isHeldBy(player)) return;
            if (!NetworkUtil.isWithinReach(player, message.pos)) return;

            TileEntity te = player.world.getTileEntity(message.pos);
            if (!(te instanceof TileEntityDoorKeypad)) return;
            TileEntityDoorKeypad keypad = (TileEntityDoorKeypad) te;
            if (!keypad.hasCodeSessionFor(player)) return;

            switch (message.option) {
                case ON_FAIL:
                    keypad.setRedstoneEvent(TileEntityDoorKeypad.RS_FAIL, message.value);
                    break;
                case ON_SUCCESS:
                    keypad.setRedstoneEvent(TileEntityDoorKeypad.RS_SUCCESS, message.value);
                    break;
                case RESET_CODE:
                    if (keypad.resetCodeFromGui()) {
                        player.sendStatusMessage(new TextComponentTranslation(
                                "ntmdoors.msg.keypad_code_reset"), true);
                    }
                    break;
                case OVERRIDE_DOOR_LOCKS:
                    keypad.forceOpenDoors();
                    break;
                case FORCE_CLOSE:
                    keypad.forceCloseDoors();
                    break;
                default:
                    break;
            }
        }
    }
}
