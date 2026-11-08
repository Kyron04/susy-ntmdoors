package com.ntmdoors.network;

import com.ntmdoors.tile.TileEntitySpatialDisrupter;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Client to server: the player confirmed "Activate Spatial Disrupter". */
public class MessageSpatialDisrupterActivate implements IMessage {

    private BlockPos pos;

    public MessageSpatialDisrupterActivate() {
    }

    public MessageSpatialDisrupterActivate(BlockPos pos) {
        this.pos = pos;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = NetworkUtil.readPos(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        NetworkUtil.writePos(buf, pos);
    }

    public static class Handler implements IMessageHandler<MessageSpatialDisrupterActivate, IMessage> {

        @Override
        public IMessage onMessage(MessageSpatialDisrupterActivate message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> apply(player, message));
            return null;
        }

        private static void apply(EntityPlayerMP player, MessageSpatialDisrupterActivate message) {
            if (message.pos == null || !NetworkUtil.isWithinReach(player, message.pos)) return;
            TileEntity te = player.world.getTileEntity(message.pos);
            if (!(te instanceof TileEntitySpatialDisrupter)) return;
            TileEntitySpatialDisrupter disrupter = (TileEntitySpatialDisrupter) te;
            if (!disrupter.activate(player)) {
                player.sendStatusMessage(new TextComponentTranslation(
                        "ntmdoors.msg.spatial_disrupter_already"), true);
            }
        }
    }
}
