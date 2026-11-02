package com.ntmdoors.network;

import com.ntmdoors.item.ItemRedstoneRelayKey;
import com.ntmdoors.tile.TileEntityDoorKeypad;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client to server: the Redstone Relay Key screen asked to set a keypad's first code. The server
 * owns the code and refuses to replace one that is already set; Reset Code is a separate action.
 */
public class MessageKeypadCode implements IMessage {

    private BlockPos pos;
    private String code;

    public MessageKeypadCode() {
    }

    public MessageKeypadCode(BlockPos pos, String code) {
        this.pos = pos;
        this.code = code;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = NetworkUtil.readPos(buf);
        code = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        NetworkUtil.writePos(buf, pos);
        ByteBufUtils.writeUTF8String(buf, code == null ? "" : code);
    }

    public static class Handler implements IMessageHandler<MessageKeypadCode, IMessage> {

        @Override
        public IMessage onMessage(MessageKeypadCode message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> apply(player, message));
            return null;
        }

        private static void apply(EntityPlayerMP player, MessageKeypadCode message) {
            if (message.pos == null) return;
            if (!ItemRedstoneRelayKey.isHeldBy(player)) return;
            if (!NetworkUtil.isWithinReach(player, message.pos)) return;

            TileEntity te = player.world.getTileEntity(message.pos);
            if (!(te instanceof TileEntityDoorKeypad)) return;
            TileEntityDoorKeypad keypad = (TileEntityDoorKeypad) te;
            if (!keypad.hasCodeSessionFor(player)) return;

            if (keypad.hasStoredCode()) {
                player.sendStatusMessage(new TextComponentTranslation(
                        "ntmdoors.msg.keypad_code_exists"), true);
                return;
            }
            if (!keypad.setFirstCode(message.code)) {
                player.sendStatusMessage(new TextComponentTranslation(
                        "ntmdoors.msg.keypad_code_invalid"), true);
                return;
            }
            player.sendStatusMessage(new TextComponentTranslation(
                    "ntmdoors.msg.keypad_code_set"), true);
        }
    }
}
