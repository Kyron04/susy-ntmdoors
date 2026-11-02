package com.ntmdoors.network;

import com.ntmdoors.item.ItemCreativeKeycard;
import com.ntmdoors.item.ItemOperatorKeycard;
import com.ntmdoors.tile.TileEntityRedstoneGate;
import com.ntmdoors.util.HeldItems;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Client to server: the Redstone Gate screen changed one setting. */
public class MessageGateOption implements IMessage {

    public static final int STAY_ACTIVE = 0;
    public static final int INVERT = 1;
    public static final int ADD_1 = 2;
    public static final int ADD_5 = 3;
    public static final int ADD_10 = 4;
    public static final int ADD_20 = 5;
    public static final int SUB_1 = 6;
    public static final int SUB_5 = 7;
    public static final int SUB_10 = 8;
    public static final int SUB_20 = 9;
    public static final int OPTION_COUNT = 10;

    private BlockPos pos;
    private int option;
    private boolean value;

    public MessageGateOption() {
    }

    public MessageGateOption(BlockPos pos, int option, boolean value) {
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

    public static class Handler implements IMessageHandler<MessageGateOption, IMessage> {

        @Override
        public IMessage onMessage(MessageGateOption message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> apply(player, message));
            return null;
        }

        private static void apply(EntityPlayerMP player, MessageGateOption message) {
            if (message.pos == null) return;
            if (message.option < 0 || message.option >= OPTION_COUNT) return;
            if (!NetworkUtil.isWithinReach(player, message.pos)) return;
            if (!HeldItems.holds(player, ItemOperatorKeycard.class)
                    && !ItemCreativeKeycard.isHeldBy(player)) {
                return;
            }

            TileEntity te = player.world.getTileEntity(message.pos);
            if (!(te instanceof TileEntityRedstoneGate)) return;
            TileEntityRedstoneGate gate = (TileEntityRedstoneGate) te;

            switch (message.option) {
                case STAY_ACTIVE:
                    gate.setStayActive(message.value);
                    break;
                case INVERT:
                    gate.setInverted(message.value);
                    break;
                case ADD_1:
                    gate.adjustActiveTicks(20);
                    break;
                case ADD_5:
                    gate.adjustActiveTicks(5 * 20);
                    break;
                case ADD_10:
                    gate.adjustActiveTicks(10 * 20);
                    break;
                case ADD_20:
                    gate.adjustActiveTicks(20 * 20);
                    break;
                case SUB_1:
                    gate.adjustActiveTicks(-20);
                    break;
                case SUB_5:
                    gate.adjustActiveTicks(-5 * 20);
                    break;
                case SUB_10:
                    gate.adjustActiveTicks(-10 * 20);
                    break;
                case SUB_20:
                    gate.adjustActiveTicks(-20 * 20);
                    break;
                default:
                    break;
            }
        }
    }
}
