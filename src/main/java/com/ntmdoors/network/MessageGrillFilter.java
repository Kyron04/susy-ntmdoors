// SPDX-License-Identifier: LGPL-3.0-only
package com.ntmdoors.network;

import com.ntmdoors.monitor.MonitorAccess;
import com.ntmdoors.tile.TileEntityEmancipationGrill;

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
 * Client to server: sets or clears one Emancipation Grill inventory-filter slot. The server
 * validates reach and console access and strips the stack down to an item and metadata pattern.
 */
public class MessageGrillFilter implements IMessage {

    private BlockPos pos;
    private boolean whitelist;
    private int slot;
    private ItemStack stack = ItemStack.EMPTY;

    public MessageGrillFilter() {
    }

    public MessageGrillFilter(BlockPos pos, boolean whitelist, int slot, ItemStack stack) {
        this.pos = pos;
        this.whitelist = whitelist;
        this.slot = slot;
        this.stack = stack == null ? ItemStack.EMPTY : stack;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = NetworkUtil.readPos(buf);
        whitelist = buf.readBoolean();
        slot = buf.readUnsignedByte();
        stack = ByteBufUtils.readItemStack(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        NetworkUtil.writePos(buf, pos);
        buf.writeBoolean(whitelist);
        buf.writeByte(slot);
        ByteBufUtils.writeItemStack(buf, stack);
    }

    public static class Handler implements IMessageHandler<MessageGrillFilter, IMessage> {

        @Override
        public IMessage onMessage(MessageGrillFilter message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> apply(player, message));
            return null;
        }

        private static void apply(EntityPlayerMP player, MessageGrillFilter message) {
            if (message.pos == null || message.stack == null) return;
            if (!MonitorAccess.canAccess(player, message.pos)) return;

            TileEntity te = player.world.getTileEntity(message.pos);
            if (!(te instanceof TileEntityEmancipationGrill)) return;
            TileEntityEmancipationGrill grill = ((TileEntityEmancipationGrill) te).getSecurityCore();
            boolean remote = MonitorAccess.isRemoteAuthorized(player, message.pos);
            if (grill == null || (!remote && !grill.hasConsoleAccess(player))) return;
            if (message.slot < 0 || message.slot >= TileEntityEmancipationGrill.FILTER_SLOTS) return;

            grill.setFilterStack(message.whitelist, message.slot, message.stack);
        }
    }
}
