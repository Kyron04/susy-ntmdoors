package com.ntmdoors.tile;

import com.ntmdoors.block.BlockMonitorSetup;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;

/**
 * Bookkeeping for the six-monitor desk multiblock. Every cell stores its model part so the block
 * can pick the right slice after a chunk reload, and the non-core cells store the core position
 * so breaking any cell removes the whole desk.
 */
public class TileEntityMonitorSetup extends TileEntity {

    private BlockMonitorSetup.Part part = BlockMonitorSetup.Part.BOTTOM_CENTER;
    @Nullable
    private BlockPos corePos;
    /** Set only for the duration of removing the desk, so the removed cells do not recurse. */
    private boolean dismantling;

    public void setPart(BlockMonitorSetup.Part part) {
        this.part = part == null ? BlockMonitorSetup.Part.BOTTOM_CENTER : part;
        markDirty();
    }

    public BlockMonitorSetup.Part getPart() {
        return part;
    }

    public void setCorePos(BlockPos core) {
        corePos = core.toImmutable();
        markDirty();
    }

    @Nullable
    public BlockPos getCorePos() {
        return corePos;
    }

    /** Returns true once, for the cell that started removing the desk. */
    public boolean beginDismantle() {
        if (dismantling) return false;
        dismantling = true;
        return true;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setString("part", part.getName());
        if (corePos != null) tag.setLong("core", corePos.toLong());
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        part = BlockMonitorSetup.Part.BOTTOM_CENTER;
        if (tag.hasKey("part")) {
            String name = tag.getString("part");
            for (BlockMonitorSetup.Part candidate : BlockMonitorSetup.Part.values()) {
                if (candidate.getName().equals(name)) {
                    part = candidate;
                    break;
                }
            }
        }
        corePos = tag.hasKey("core") ? BlockPos.fromLong(tag.getLong("core")) : null;
    }
}
