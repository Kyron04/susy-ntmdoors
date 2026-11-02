package com.ntmdoors.network;

import com.ntmdoors.tile.TileEntitySafe;
import com.ntmdoors.util.ModSounds;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client to server: one row on the safe's options page. Only a player with a live access session
 * may change the toggles or clear the code.
 */
public class MessageSafeOption implements IMessage {

    public static final int MATCH_HOLDER = 0;
    public static final int LOCKOUT = 1;
    public static final int RESET_CODE = 2;
    public static final int BINDING_ACTIVITIES = 3;
    public static final int REDSTONE_CODE_FAIL = 4;
    public static final int REDSTONE_CODE_SUCCESS = 5;
    public static final int REDSTONE_BINDING = 6;
    public static final int REDSTONE_LOCKOUT = 7;
    public static final int REDSTONE_SCAN_FAIL = 8;
    public static final int REDSTONE_SCAN_SUCCESS = 9;
    public static final int REDSTONE_MATCH_FAIL = 10;
    public static final int REDSTONE_MATCH_SUCCESS = 11;
    public static final int OPTION_COUNT = 12;

    private BlockPos pos;
    private int option;
    private boolean value;

    public MessageSafeOption() {
    }

    public MessageSafeOption(BlockPos pos, int option, boolean value) {
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

    public static class Handler implements IMessageHandler<MessageSafeOption, IMessage> {

        @Override
        public IMessage onMessage(MessageSafeOption message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> apply(player, message));
            return null;
        }

        private static void apply(EntityPlayerMP player, MessageSafeOption message) {
            if (message.pos == null) return;
            if (message.option < 0 || message.option >= OPTION_COUNT) return;
            if (!NetworkUtil.isWithinReach(player, message.pos)) return;

            TileEntity te = player.world.getTileEntity(message.pos);
            if (!(te instanceof TileEntitySafe)) return;
            TileEntitySafe safe = (TileEntitySafe) te;
            if (!safe.hasAccessSessionFor(player)) return;

            switch (message.option) {
                case MATCH_HOLDER:
                    safe.setMatchHolder(message.value);
                    break;
                case LOCKOUT:
                    safe.setLockoutMode(message.value);
                    break;
                case BINDING_ACTIVITIES:
                    safe.setBindingActivities(message.value);
                    break;
                case REDSTONE_CODE_FAIL:
                    safe.setRedstoneEvent(TileEntitySafe.RS_CODE_FAIL, message.value);
                    break;
                case REDSTONE_CODE_SUCCESS:
                    safe.setRedstoneEvent(TileEntitySafe.RS_CODE_SUCCESS, message.value);
                    break;
                case REDSTONE_BINDING:
                    safe.setRedstoneEvent(TileEntitySafe.RS_BINDING, message.value);
                    break;
                case REDSTONE_LOCKOUT:
                    safe.setRedstoneEvent(TileEntitySafe.RS_LOCKOUT, message.value);
                    break;
                case REDSTONE_SCAN_FAIL:
                    safe.setRedstoneEvent(TileEntitySafe.RS_SCAN_FAIL, message.value);
                    break;
                case REDSTONE_SCAN_SUCCESS:
                    safe.setRedstoneEvent(TileEntitySafe.RS_SCAN_SUCCESS, message.value);
                    break;
                case REDSTONE_MATCH_FAIL:
                    safe.setRedstoneEvent(TileEntitySafe.RS_MATCH_FAIL, message.value);
                    break;
                case REDSTONE_MATCH_SUCCESS:
                    safe.setRedstoneEvent(TileEntitySafe.RS_MATCH_SUCCESS, message.value);
                    break;
                case RESET_CODE:
                    if (safe.resetCode()) {
                        player.sendStatusMessage(new TextComponentTranslation(
                                "ntmdoors.msg.safe_code_reset"), true);
                    }
                    break;
                default:
                    return;
            }

            player.world.playSound(null, safe.getPos(), ModSounds.BUTTON9,
                    SoundCategory.BLOCKS, 0.8F, 1F);
        }
    }
}
