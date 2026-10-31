package com.ntmdoors.network;

import com.ntmdoors.tile.TileEntityKeycardScanner;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client to server: the player activated one of the scanner's option rows. Gated by the same
 * security-code session as the entry lists, and refused while a bound door is moving so a
 * lockdown cannot be raised in the middle of a door stroke.
 */
public class MessageScannerOption implements IMessage {

    public static final int MATCH_HOLDER = 0;
    public static final int LOCKDOWN = 1;
    public static final int LOCKOUT = 2;
    public static final int IGNORE_KEYS = 3;
    public static final int IGNORE_IDS = 4;
    public static final int FORCE_OPEN = 5;
    public static final int FORCE_CLOSE = 6;
    public static final int OPTION_COUNT = 7;

    private BlockPos pos;
    private int option;
    private boolean value;

    public MessageScannerOption() {
    }

    public MessageScannerOption(BlockPos pos, int option, boolean value) {
        this.pos = pos;
        this.option = option;
        this.value = value;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        boolean present = buf.readBoolean();
        pos = present ? BlockPos.fromLong(buf.readLong()) : null;
        option = buf.readByte();
        value = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(pos != null);
        if (pos != null) buf.writeLong(pos.toLong());
        buf.writeByte(option);
        buf.writeBoolean(value);
    }

    public static class Handler implements IMessageHandler<MessageScannerOption, IMessage> {

        @Override
        public IMessage onMessage(MessageScannerOption message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> apply(player, message));
            return null;
        }

        private static void apply(EntityPlayerMP player, MessageScannerOption message) {
            if (message.pos == null) return;
            if (message.option < 0 || message.option >= OPTION_COUNT) return;
            if (player.getDistanceSq(message.pos.getX() + 0.5D, message.pos.getY() + 0.5D,
                    message.pos.getZ() + 0.5D) > 64D) {
                return;
            }

            TileEntity te = player.world.getTileEntity(message.pos);
            if (!(te instanceof TileEntityKeycardScanner)) return;
            TileEntityKeycardScanner scanner = (TileEntityKeycardScanner) te;
            if (!scanner.hasBoundDoors() || !scanner.hasCodeSessionFor(player)) return;
            // Raising lockdown mid-stroke would fight the door, so options wait for a still door.
            if (scanner.hasMovingDoor()) return;

            switch (message.option) {
                case MATCH_HOLDER:
                    scanner.setMatchHolder(message.value);
                    break;
                case LOCKDOWN:
                    scanner.setLockdown(message.value);
                    break;
                case LOCKOUT:
                    scanner.setLockoutMode(message.value);
                    break;
                case IGNORE_KEYS:
                    scanner.setIgnoreKeys(message.value);
                    break;
                case IGNORE_IDS:
                    scanner.setIgnoreIds(message.value);
                    break;
                case FORCE_OPEN:
                    // Manual actions: the value is irrelevant, the click is the command.
                    scanner.forceOpenDoors();
                    break;
                case FORCE_CLOSE:
                    scanner.forceCloseDoors();
                    break;
                default:
                    break;
            }
        }
    }
}
