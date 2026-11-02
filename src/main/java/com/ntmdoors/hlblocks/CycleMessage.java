package com.ntmdoors.hlblocks;

import io.netty.buffer.ByteBuf;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/** Client-to-server request to cycle the held HL or concrete block by one variant. */
public class CycleMessage implements IMessage {

    private EnumHand hand = EnumHand.MAIN_HAND;
    private int offset;
    private int slot = -1;

    public CycleMessage() {
    }

    public CycleMessage(EnumHand hand, int offset, int slot) {
        this.hand = hand;
        this.offset = offset;
        this.slot = slot;
    }

    public EnumHand getHand() {
        return this.hand;
    }

    public int getOffset() {
        return this.offset;
    }

    /** Hotbar slot that held the item when it was scrolled, or -1 for the off hand. */
    public int getSlot() {
        return this.slot;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        EnumHand[] hands = EnumHand.values();
        this.hand = hands[Math.floorMod(buf.readUnsignedByte(), hands.length)];
        this.offset = buf.readByte();
        this.slot = buf.readShort();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(this.hand.ordinal());
        buf.writeByte(this.offset);
        buf.writeShort(this.slot);
    }
}
