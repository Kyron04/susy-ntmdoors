// SPDX-License-Identifier: LGPL-3.0-only
package com.ntmdoors.network;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import io.netty.buffer.ByteBuf;

/** Server to client: whether the Grill closed its console and started a binding session. */
public class MessageGrillBindStartResult implements IMessage {

    private boolean success;

    public MessageGrillBindStartResult() {
    }

    public MessageGrillBindStartResult(boolean success) {
        this.success = success;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        success = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(success);
    }

    public static class Handler implements IMessageHandler<MessageGrillBindStartResult, IMessage> {

        @Override
        public IMessage onMessage(MessageGrillBindStartResult message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                if (message.success) Minecraft.getMinecraft().displayGuiScreen(null);
            });
            return null;
        }
    }
}
