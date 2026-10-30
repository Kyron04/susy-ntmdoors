package com.ntmdoors.network;

import com.ntmdoors.item.ItemOperatorKeycard;
import com.ntmdoors.tile.TileEntityKeycardScanner;
import com.ntmdoors.util.ModSounds;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client to server: the player pressed Enter on the scanner security screen. The server owns the
 * code, so the client only ever sees whether one exists and the digits are validated here.
 */
public class MessageScannerCode implements IMessage {

    private BlockPos pos;
    private String code;

    public MessageScannerCode() {
    }

    public MessageScannerCode(BlockPos pos, String code) {
        this.pos = pos;
        this.code = code;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        boolean present = buf.readBoolean();
        pos = present ? BlockPos.fromLong(buf.readLong()) : null;
        code = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(pos != null);
        if (pos != null) buf.writeLong(pos.toLong());
        ByteBufUtils.writeUTF8String(buf, code == null ? "" : code);
    }

    public static class Handler implements IMessageHandler<MessageScannerCode, IMessage> {

        @Override
        public IMessage onMessage(MessageScannerCode message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> submit(player, message));
            return null;
        }

        private static void submit(EntityPlayerMP player, MessageScannerCode message) {
            if (message.pos == null) return;
            if (!holdsOperatorKeycard(player)) {
                result(player, message.pos, MessageScannerCodeResult.DENIED);
                return;
            }
            // Same reach as the screen: a scanner one block away plus some slack.
            if (player.getDistanceSq(message.pos.getX() + 0.5D, message.pos.getY() + 0.5D,
                    message.pos.getZ() + 0.5D) > 64D) {
                result(player, message.pos, MessageScannerCodeResult.DENIED);
                return;
            }

            TileEntity te = player.world.getTileEntity(message.pos);
            if (!(te instanceof TileEntityKeycardScanner)) {
                result(player, message.pos, MessageScannerCodeResult.DENIED);
                return;
            }
            TileEntityKeycardScanner scanner = (TileEntityKeycardScanner) te;
            if (!scanner.hasBoundDoors()) {
                player.sendStatusMessage(new TextComponentTranslation("ntmdoors.msg.scanner_code_unbound"), true);
                result(player, message.pos, MessageScannerCodeResult.UNBOUND);
                return;
            }

            String code = TileEntityKeycardScanner.sanitiseCode(message.code);
            if (code.isEmpty()) {
                player.world.playSound(null, message.pos, ModSounds.SCANNER_BUTTON8,
                        SoundCategory.BLOCKS, 0.8F, 1F);
                result(player, message.pos, MessageScannerCodeResult.DENIED);
                return;
            }

            if (!scanner.hasSecurityCode()) {
                if (!scanner.setSecurityCode(code)) {
                    result(player, message.pos, MessageScannerCodeResult.DENIED);
                    return;
                }
                player.world.playSound(null, message.pos, ModSounds.SCANNER_BUTTON3,
                        SoundCategory.BLOCKS, 0.8F, 1F);
                player.sendStatusMessage(new TextComponentTranslation("ntmdoors.msg.scanner_code_set"), true);
                result(player, message.pos, MessageScannerCodeResult.SET);
                return;
            }

            if (scanner.checkSecurityCode(code)) {
                player.world.playSound(null, message.pos, ModSounds.SCANNER_BUTTON3,
                        SoundCategory.BLOCKS, 0.8F, 1F);
                player.sendStatusMessage(new TextComponentTranslation("ntmdoors.msg.scanner_code_accepted"), true);
                result(player, message.pos, MessageScannerCodeResult.ACCEPTED);
            } else {
                player.world.playSound(null, message.pos, ModSounds.SCANNER_BUTTON8,
                        SoundCategory.BLOCKS, 0.8F, 1F);
                player.sendStatusMessage(new TextComponentTranslation("ntmdoors.msg.scanner_code_denied"), true);
                result(player, message.pos, MessageScannerCodeResult.DENIED);
            }
        }

        private static void result(EntityPlayerMP player, BlockPos pos, int result) {
            ModNetwork.INSTANCE.sendTo(new MessageScannerCodeResult(pos, result), player);
        }

        /** Either hand is fine; the screen is opened with the card that way too. */
        private static boolean holdsOperatorKeycard(EntityPlayerMP player) {
            return player.getHeldItemMainhand().getItem() instanceof ItemOperatorKeycard
                    || player.getHeldItemOffhand().getItem() instanceof ItemOperatorKeycard;
        }
    }
}
