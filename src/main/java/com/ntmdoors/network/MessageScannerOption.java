package com.ntmdoors.network;

import com.ntmdoors.tile.TileEntityKeycardScanner;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
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
    public static final int RESET_CODE = 7;
    public static final int REDSTONE_SCAN_FAIL = 8;
    public static final int REDSTONE_SCAN_SUCCESS = 9;
    public static final int REDSTONE_MATCH_FAIL = 10;
    public static final int REDSTONE_MATCH_SUCCESS = 11;
    public static final int REDSTONE_KEY_FAIL = 12;
    public static final int REDSTONE_KEY_SUCCESS = 13;
    public static final int REDSTONE_BIOMETRIC_FAIL = 14;
    public static final int REDSTONE_BIOMETRIC_SUCCESS = 15;
    public static final int REDSTONE_LOCKOUT = 16;
    public static final int OPTION_COUNT = 17;

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
            if (!NetworkUtil.isWithinReach(player, message.pos)) return;

            TileEntity te = player.world.getTileEntity(message.pos);
            if (!(te instanceof TileEntityKeycardScanner)) return;
            TileEntityKeycardScanner scanner = (TileEntityKeycardScanner) te;
            if (!scanner.hasConsoleAccess(player)) return;
            // Raising lockdown mid-stroke would fight the door, so settings wait for a still door.
            // The Redstone event switches do not touch a door, so they stay editable.
            boolean redstone = message.option >= REDSTONE_SCAN_FAIL;
            if (!redstone && scanner.hasMovingDoor()) return;

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
                case RESET_CODE:
                    if (scanner.resetSecurityCode()) {
                        player.sendStatusMessage(new TextComponentTranslation(
                                "ntmdoors.msg.scanner_code_reset"), true);
                    }
                    break;
                case REDSTONE_SCAN_FAIL:
                    scanner.setRedstoneEvent(TileEntityKeycardScanner.RS_SCAN_FAIL, message.value);
                    break;
                case REDSTONE_SCAN_SUCCESS:
                    scanner.setRedstoneEvent(TileEntityKeycardScanner.RS_SCAN_SUCCESS, message.value);
                    break;
                case REDSTONE_MATCH_FAIL:
                    scanner.setRedstoneEvent(TileEntityKeycardScanner.RS_MATCH_FAIL, message.value);
                    break;
                case REDSTONE_MATCH_SUCCESS:
                    scanner.setRedstoneEvent(TileEntityKeycardScanner.RS_MATCH_SUCCESS, message.value);
                    break;
                case REDSTONE_KEY_FAIL:
                    scanner.setRedstoneEvent(TileEntityKeycardScanner.RS_KEY_FAIL, message.value);
                    break;
                case REDSTONE_KEY_SUCCESS:
                    scanner.setRedstoneEvent(TileEntityKeycardScanner.RS_KEY_SUCCESS, message.value);
                    break;
                case REDSTONE_BIOMETRIC_FAIL:
                    scanner.setRedstoneEvent(TileEntityKeycardScanner.RS_BIOMETRIC_FAIL, message.value);
                    break;
                case REDSTONE_BIOMETRIC_SUCCESS:
                    scanner.setRedstoneEvent(TileEntityKeycardScanner.RS_BIOMETRIC_SUCCESS, message.value);
                    break;
                case REDSTONE_LOCKOUT:
                    scanner.setRedstoneEvent(TileEntityKeycardScanner.RS_LOCKOUT, message.value);
                    break;
                default:
                    break;
            }
        }
    }
}
