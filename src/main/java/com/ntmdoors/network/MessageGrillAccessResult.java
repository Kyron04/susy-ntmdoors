// SPDX-License-Identifier: LGPL-3.0-only
package com.ntmdoors.network;

import com.ntmdoors.NTMDoors;

import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import io.netty.buffer.ByteBuf;

/**
 * Server to client: the grill console may open for this player, or the player is not the owner.
 */
public class MessageGrillAccessResult implements IMessage {

    private BlockPos pos;
    private boolean allowed;

    public MessageGrillAccessResult() {
    }

    public MessageGrillAccessResult(BlockPos pos, boolean allowed) {
        this.pos = pos;
        this.allowed = allowed;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = NetworkUtil.readPos(buf);
        allowed = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        NetworkUtil.writePos(buf, pos);
        buf.writeBoolean(allowed);
    }

    public static class Handler implements IMessageHandler<MessageGrillAccessResult, IMessage> {

        @Override
        public IMessage onMessage(MessageGrillAccessResult message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                Minecraft mc = Minecraft.getMinecraft();
                if (message.allowed && message.pos != null) {
                    NTMDoors.proxy.openEmancipationGrillGui(message.pos);
                } else if (mc.player != null) {
                    mc.player.sendStatusMessage(new TextComponentTranslation(
                            "ntmdoors.msg.emancipation_owner_only"), true);
                }
            });
            return null;
        }
    }
}
