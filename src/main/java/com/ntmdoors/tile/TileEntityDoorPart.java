package com.ntmdoors.tile;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;

/** Non-core cell of a door: only remembers where the core is. Does not tick. */
public class TileEntityDoorPart extends TileEntitySynced {

    @Nullable
    public BlockPos corePos;

    public void setCorePos(BlockPos core) {
        this.corePos = core;
        sync();
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (corePos != null) tag.setLong("core", corePos.toLong());
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        corePos = tag.hasKey("core") ? BlockPos.fromLong(tag.getLong("core")) : null;
    }
}
