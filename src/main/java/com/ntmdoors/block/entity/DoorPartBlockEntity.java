package com.ntmdoors.block.entity;

import com.ntmdoors.block.door.ModDoors;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;

import org.jetbrains.annotations.Nullable;

/**
 * Non-core cell of a door: only remembers where the core is. Does not tick.
 */
public class DoorPartBlockEntity extends BlockEntity {

    @Nullable
    private BlockPos corePos;

    public DoorPartBlockEntity(BlockPos pos, BlockState state) {
        super(ModDoors.DOOR_PART_BLOCK_ENTITY, pos, state);
    }

    @Nullable
    public BlockPos getCorePos() {
        return corePos;
    }

    public void setCorePos(BlockPos core) {
        this.corePos = core != null ? core.toImmutable() : null;
        markDirty();
        if (world != null && !world.isClient) {
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
    }

    @Override
    protected void writeNbt(NbtCompound tag) {
        super.writeNbt(tag);
        if (corePos != null) {
            tag.putLong("core", corePos.asLong());
        }
    }

    @Override
    public void readNbt(NbtCompound tag) {
        super.readNbt(tag);
        corePos = tag.contains("core") ? BlockPos.fromLong(tag.getLong("core")) : null;
    }

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        NbtCompound tag = new NbtCompound();
        if (corePos != null) {
            tag.putLong("core", corePos.asLong());
        }
        return tag;
    }
}
