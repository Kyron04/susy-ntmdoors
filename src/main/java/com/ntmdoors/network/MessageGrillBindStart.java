// SPDX-License-Identifier: LGPL-3.0-only
package com.ntmdoors.network;

import com.ntmdoors.item.ItemEmancipationRelayKey;
import com.ntmdoors.tile.TileEntityEmancipationGrill;
import com.ntmdoors.util.GrillContainmentData;
import com.ntmdoors.util.HeldItems;
import com.ntmdoors.util.ModSounds;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.SPacketHeldItemChange;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import io.netty.buffer.ByteBuf;

/**
 * Client to server: start a contraband-container binding session at a Grill. The server finds the
 * Relay Key itself, moves it to the main hand without deleting anything, and writes the pending
 * session to the key.
 */
public class MessageGrillBindStart implements IMessage {

    private BlockPos pos;

    public MessageGrillBindStart() {
    }

    public MessageGrillBindStart(BlockPos pos) {
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

    public static class Handler implements IMessageHandler<MessageGrillBindStart, IMessage> {

        @Override
        public IMessage onMessage(MessageGrillBindStart message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> apply(player, message));
            return null;
        }

        private static void apply(EntityPlayerMP player, MessageGrillBindStart message) {
            if (message.pos == null || !NetworkUtil.isWithinReach(player, message.pos)) return;

            TileEntity te = player.world.getTileEntity(message.pos);
            if (!(te instanceof TileEntityEmancipationGrill)) return;
            TileEntityEmancipationGrill grill = ((TileEntityEmancipationGrill) te).getSecurityCore();
            if (grill == null) return;

            if (!grill.hasConsoleAccess(player)) {
                fail(player, "ntmdoors.msg.emancipation_owner_only");
                return;
            }
            if (!grill.isContrabandEnabled()) {
                fail(player, "ntmdoors.msg.grill_bind_disabled");
                return;
            }
            if (grill.getFieldMode() != TileEntityEmancipationGrill.FieldMode.FILTER) {
                fail(player, "ntmdoors.msg.grill_bind_filter_required");
                return;
            }

            ItemStack key = findAndHoldKey(player);
            if (key.isEmpty()) {
                fail(player, "ntmdoors.msg.grill_bind_key_required");
                return;
            }

            GrillContainmentData.start(key, grill.getPos(),
                    player.world.provider.getDimension(), player.getUniqueID());
            HeldItems.sync(player);
            ModNetwork.INSTANCE.sendTo(new MessageGrillBindStartResult(true), player);
            player.sendStatusMessage(new TextComponentTranslation(
                    "ntmdoors.msg.grill_bind_started"), true);
            player.world.playSound(null, grill.getPos(), ModSounds.BUTTON9,
                    SoundCategory.BLOCKS, 0.8F, 1F);
        }

        /**
         * Puts the first Emancipation Relay Key into the main hand. The swap preserves both stacks:
         * the displaced main-hand stack goes to the key's old slot, or to the offhand when the key
         * came from the offhand.
         */
        private static ItemStack findAndHoldKey(EntityPlayerMP player) {
            ItemStack main = player.getHeldItemMainhand();
            if (main.getItem() instanceof ItemEmancipationRelayKey) return main;

            ItemStack off = player.getHeldItemOffhand();
            if (off.getItem() instanceof ItemEmancipationRelayKey) {
                ItemStack displaced = main.copy();
                player.setHeldItem(EnumHand.MAIN_HAND, off.copy());
                player.setHeldItem(EnumHand.OFF_HAND, displaced);
                sendHeldItemChange(player);
                return player.getHeldItemMainhand();
            }

            for (int slot = 0; slot < 9; slot++) {
                ItemStack stack = player.inventory.mainInventory.get(slot);
                if (!(stack.getItem() instanceof ItemEmancipationRelayKey)) continue;
                player.inventory.currentItem = slot;
                sendHeldItemChange(player);
                return player.getHeldItemMainhand();
            }

            int current = player.inventory.currentItem;
            for (int slot = 9; slot < 36; slot++) {
                ItemStack stack = player.inventory.mainInventory.get(slot);
                if (!(stack.getItem() instanceof ItemEmancipationRelayKey)) continue;

                ItemStack held = player.inventory.mainInventory.get(current);
                player.inventory.mainInventory.set(current, stack);
                player.inventory.mainInventory.set(slot, held);
                sendHeldItemChange(player);
                return player.getHeldItemMainhand();
            }
            return ItemStack.EMPTY;
        }

        private static void sendHeldItemChange(EntityPlayerMP player) {
            player.connection.sendPacket(new SPacketHeldItemChange(player.inventory.currentItem));
        }

        private static void fail(EntityPlayerMP player, String message) {
            ModNetwork.INSTANCE.sendTo(new MessageGrillBindStartResult(false), player);
            player.sendStatusMessage(new TextComponentTranslation(message), true);
        }
    }
}
