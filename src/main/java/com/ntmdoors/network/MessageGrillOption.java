// SPDX-License-Identifier: LGPL-3.0-only
package com.ntmdoors.network;

import com.ntmdoors.tile.TileEntityEmancipationGrill;
import com.ntmdoors.tile.TileEntityEmancipationGrill.FieldColour;
import com.ntmdoors.tile.TileEntityEmancipationGrill.FieldMode;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import io.netty.buffer.ByteBuf;

/**
 * Client to server: one option from the Emancipation Grill console. The server checks reach and
 * console access before changing the pair's security core.
 */
public class MessageGrillOption implements IMessage {

    public static final int MODE_SOLID = 0;
    public static final int MODE_NEUTRAL = 1;
    public static final int MODE_LETHAL = 2;
    public static final int MATCH_USER = 3;
    public static final int IGNORE_PRIVATE_KEY = 4;
    public static final int IGNORE_BIOMETRIC_ID = 5;
    public static final int ENABLE_FIELD = 6;
    public static final int DISABLE_FIELD = 7;
    public static final int IGNORE_INVENTORY = 8;
    public static final int DETECT_PLAYERS = 9;
    public static final int DETECT_HOSTILE = 10;
    public static final int DETECT_NEUTRAL = 11;
    public static final int IGNORE_ITEMS = 12;
    public static final int AUDIO = 13;
    public static final int REDSTONE_SOLID_PASS = 14;
    public static final int REDSTONE_SOLID_BLOCK = 15;
    public static final int REDSTONE_LETHAL_KILL = 16;
    public static final int REDSTONE_MATCH_USER_PASS = 17;
    public static final int REDSTONE_MATCH_USER_FAIL = 18;
    public static final int MODE_WALL = 19;
    public static final int COLOUR_RED = 20;
    public static final int COLOUR_GREEN = 21;
    public static final int COLOUR_BLUE = 22;
    public static final int MODE_FILTER = 23;
    public static final int CONTRABAND = 24;
    public static final int OPTION_COUNT = 25;

    private BlockPos pos;
    private int option;
    private boolean value;

    public MessageGrillOption() {
    }

    public MessageGrillOption(BlockPos pos, int option, boolean value) {
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

    public static class Handler implements IMessageHandler<MessageGrillOption, IMessage> {

        @Override
        public IMessage onMessage(MessageGrillOption message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> apply(player, message));
            return null;
        }

        private static void apply(EntityPlayerMP player, MessageGrillOption message) {
            if (message.pos == null) return;
            if (message.option < 0 || message.option >= OPTION_COUNT) return;
            if (!NetworkUtil.isWithinReach(player, message.pos)) return;

            TileEntity te = player.world.getTileEntity(message.pos);
            if (!(te instanceof TileEntityEmancipationGrill)) return;
            TileEntityEmancipationGrill grill = ((TileEntityEmancipationGrill) te).getSecurityCore();
            if (grill == null || !grill.hasConsoleAccess(player)) return;

            switch (message.option) {
                case MODE_SOLID:
                    grill.setFieldMode(FieldMode.SOLID);
                    break;
                case MODE_NEUTRAL:
                    grill.setFieldMode(FieldMode.NEUTRAL);
                    break;
                case MODE_LETHAL:
                    grill.setFieldMode(FieldMode.LETHAL);
                    break;
                case MODE_WALL:
                    grill.setFieldMode(FieldMode.WALL);
                    break;
                case COLOUR_RED:
                    grill.setFieldColour(FieldColour.RED);
                    break;
                case COLOUR_GREEN:
                    grill.setFieldColour(FieldColour.GREEN);
                    break;
                case COLOUR_BLUE:
                    grill.setFieldColour(FieldColour.BLUE);
                    break;
                case MODE_FILTER:
                    grill.setFieldMode(FieldMode.FILTER);
                    break;
                case CONTRABAND:
                    grill.setContrabandEnabled(message.value);
                    break;
                case MATCH_USER:
                    grill.setMatchUser(message.value);
                    break;
                case IGNORE_PRIVATE_KEY:
                    grill.setIgnorePrivateKey(message.value);
                    break;
                case IGNORE_BIOMETRIC_ID:
                    grill.setIgnoreBiometricId(message.value);
                    break;
                case ENABLE_FIELD:
                    grill.setFieldEnabled(true);
                    break;
                case DISABLE_FIELD:
                    grill.setFieldEnabled(false);
                    break;
                case IGNORE_INVENTORY:
                    grill.setIgnoreInventory(message.value);
                    break;
                case DETECT_PLAYERS:
                    grill.setDetectPlayers(message.value);
                    break;
                case DETECT_HOSTILE:
                    grill.setDetectHostile(message.value);
                    break;
                case DETECT_NEUTRAL:
                    grill.setDetectNeutral(message.value);
                    break;
                case IGNORE_ITEMS:
                    grill.setIgnoreItems(message.value);
                    break;
                case AUDIO:
                    grill.setAudioEnabled(message.value);
                    break;
                case REDSTONE_SOLID_PASS:
                    grill.setRedstoneEvent(TileEntityEmancipationGrill.RS_SOLID_PASS, message.value);
                    break;
                case REDSTONE_SOLID_BLOCK:
                    grill.setRedstoneEvent(TileEntityEmancipationGrill.RS_SOLID_BLOCK, message.value);
                    break;
                case REDSTONE_LETHAL_KILL:
                    grill.setRedstoneEvent(TileEntityEmancipationGrill.RS_LETHAL_KILL, message.value);
                    break;
                case REDSTONE_MATCH_USER_PASS:
                    grill.setRedstoneEvent(TileEntityEmancipationGrill.RS_MATCH_USER_PASS, message.value);
                    break;
                case REDSTONE_MATCH_USER_FAIL:
                    grill.setRedstoneEvent(TileEntityEmancipationGrill.RS_MATCH_USER_FAIL, message.value);
                    break;
                default:
                    break;
            }
        }
    }
}
