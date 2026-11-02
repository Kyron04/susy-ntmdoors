package com.ntmdoors.network;

import com.ntmdoors.item.ItemRedstoneRelayKey;
import com.ntmdoors.tile.TileEntityDoorKeypad;
import com.ntmdoors.util.Codes;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client to server: the Redstone Relay Key opened a keypad and the player entered its GUI security
 * code. The first successful entry sets the code; later entries must match it.
 */
public class MessageKeypadSecurityCode implements IMessage {

    private BlockPos pos;
    private String code;

    public MessageKeypadSecurityCode() {
    }

    public MessageKeypadSecurityCode(BlockPos pos, String code) {
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

    public static class Handler implements IMessageHandler<MessageKeypadSecurityCode, IMessage> {

        @Override
        public IMessage onMessage(MessageKeypadSecurityCode message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> apply(player, message));
            return null;
        }

        private static void apply(EntityPlayerMP player, MessageKeypadSecurityCode message) {
            if (message.pos == null) return;
            if (!ItemRedstoneRelayKey.isHeldBy(player)) return;
            if (!NetworkUtil.isWithinReach(player, message.pos)) return;

            TileEntity te = player.world.getTileEntity(message.pos);
            if (!(te instanceof TileEntityDoorKeypad)) return;
            TileEntityDoorKeypad keypad = (TileEntityDoorKeypad) te;

            String code = Codes.sanitise(message.code, TileEntityDoorKeypad.CODE_LENGTH);
            if (code.isEmpty()) {
                result(player, message.pos, MessageKeypadSecurityCodeResult.DENIED);
                return;
            }

            if (!keypad.hasSecurityCode()) {
                if (!keypad.setSecurityCode(code)) {
                    result(player, message.pos, MessageKeypadSecurityCodeResult.DENIED);
                    return;
                }
                keypad.openCodeSession(player);
                result(player, message.pos, MessageKeypadSecurityCodeResult.SET);
            } else if (keypad.checkSecurityCode(code)) {
                keypad.openCodeSession(player);
                result(player, message.pos, MessageKeypadSecurityCodeResult.ACCEPTED);
            } else {
                result(player, message.pos, MessageKeypadSecurityCodeResult.DENIED);
            }
        }

        private static void result(EntityPlayerMP player, BlockPos pos, int result) {
            ModNetwork.INSTANCE.sendTo(new MessageKeypadSecurityCodeResult(pos, result), player);
        }
    }
}
