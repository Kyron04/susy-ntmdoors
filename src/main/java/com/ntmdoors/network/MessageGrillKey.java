// SPDX-License-Identifier: LGPL-3.0-only
package com.ntmdoors.network;

import com.ntmdoors.item.ItemOperatorKeycard;
import com.ntmdoors.tile.TileEntityEmancipationGrill;
import com.ntmdoors.util.Biometrics;
import com.ntmdoors.util.HeldItems;
import com.ntmdoors.util.PrivateKeys;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import io.netty.buffer.ByteBuf;

/**
 * Client to server: one row on the Emancipation Grill assignment page. The server checks the
 * grill's security core, the relay key and the Operator Keycard before moving an entry.
 */
public class MessageGrillKey implements IMessage {

    private BlockPos pos;
    private String value;
    private boolean assign;
    private boolean biometric;

    public MessageGrillKey() {
    }

    public MessageGrillKey(BlockPos pos, String value, boolean assign, boolean biometric) {
        this.pos = pos;
        this.value = value;
        this.assign = assign;
        this.biometric = biometric;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = NetworkUtil.readPos(buf);
        value = ByteBufUtils.readUTF8String(buf);
        assign = buf.readBoolean();
        biometric = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        NetworkUtil.writePos(buf, pos);
        ByteBufUtils.writeUTF8String(buf, value == null ? "" : value);
        buf.writeBoolean(assign);
        buf.writeBoolean(biometric);
    }

    public static class Handler implements IMessageHandler<MessageGrillKey, IMessage> {

        @Override
        public IMessage onMessage(MessageGrillKey message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> apply(player, message));
            return null;
        }

        private static void apply(EntityPlayerMP player, MessageGrillKey message) {
            if (message.pos == null || message.value == null || message.value.isEmpty()) return;
            if (!NetworkUtil.isWithinReach(player, message.pos)) return;

            TileEntity te = player.world.getTileEntity(message.pos);
            if (!(te instanceof TileEntityEmancipationGrill)) return;
            TileEntityEmancipationGrill grill = ((TileEntityEmancipationGrill) te).getSecurityCore();
            if (grill == null || !grill.hasConsoleAccess(player)) return;

            if (!message.assign) {
                if (message.biometric) grill.unassignId(message.value);
                else grill.unassignKey(message.value);
                return;
            }

            // The relay key occupies the hand, so the source card may be anywhere in the inventory.
            ItemStack card = HeldItems.findInInventory(player, ItemOperatorKeycard.class);
            if (card.isEmpty()) return;

            if (message.biometric) {
                if (!Biometrics.isBound(card) || !Biometrics.ownerUuid(card).equals(message.value)) return;
                grill.assignId(new TileEntityEmancipationGrill.BiometricId(
                        message.value, Biometrics.ownerName(card)));
                return;
            }

            for (PrivateKeys.Key key : PrivateKeys.get(card)) {
                if (key.serial.equals(message.value)) {
                    grill.assignKey(key);
                    return;
                }
            }
        }
    }
}
