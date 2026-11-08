package com.ntmdoors.tile;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.concrete.BlockConcreteVariant;
import com.ntmdoors.concrete.ConcreteSet;
import com.ntmdoors.util.ModSounds;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import java.util.UUID;

/**
 * Scans a 16x16 chunk square centred on itself and converts every hard Ultra High Performance
 * Concrete block into the soft UHPC Panel of the same shade. Work is spread over ticks and only
 * loaded chunks are processed; unloaded chunks are skipped. When the scan completes, the machine
 * removes itself with particles and a sound.
 */
public class TileEntitySpatialDisrupter extends net.minecraft.tileentity.TileEntity
        implements ITickable {

    /** Chunk square edge, in chunks. */
    public static final int AREA_CHUNKS = 16;
    /** Chunk sections processed per tick (each is a 16x16x16 volume). */
    private static final int SECTIONS_PER_TICK = 4;

    private boolean activated;
    private UUID activatorId;
    private int chunkIndex;
    private int sectionIndex;
    private int converted;
    private long startedTick;

    public boolean isActivated() {
        return activated;
    }

    public int getConverted() {
        return converted;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public int getSectionIndex() {
        return sectionIndex;
    }

    /** Server side: starts the irreversible operation for this player. */
    public boolean activate(EntityPlayer player) {
        World world = getWorld();
        if (activated || world == null || world.isRemote) return false;
        activated = true;
        activatorId = player == null ? null : player.getUniqueID();
        chunkIndex = 0;
        sectionIndex = 0;
        converted = 0;
        startedTick = world.getTotalWorldTime();
        markDirty();
        if (player != null) {
            player.sendStatusMessage(new TextComponentTranslation(
                    "ntmdoors.msg.spatial_disrupter_active"), true);
        }
        world.playSound(null, pos, ModSounds.BUTTON9, SoundCategory.BLOCKS, 1.0F, 0.8F);
        return true;
    }

    @Override
    public void update() {
        World world = getWorld();
        if (!activated || world == null || world.isRemote) return;

        int budget = SECTIONS_PER_TICK;
        while (budget-- > 0) {
            if (!processSection(world)) {
                finish(world);
                return;
            }
        }
        markDirty();
    }

    /** Processes one section; returns false when the whole area is complete. */
    private boolean processSection(World world) {
        if (chunkIndex >= AREA_CHUNKS * AREA_CHUNKS) return false;

        int chunkX = minChunkX() + (chunkIndex % AREA_CHUNKS);
        int chunkZ = minChunkZ() + (chunkIndex / AREA_CHUNKS);

        // Skip unloaded chunks without generating or loading them.
        if (!world.isBlockLoaded(new BlockPos(chunkX << 4, 0, chunkZ << 4))) {
            advanceChunk();
            return chunkIndex < AREA_CHUNKS * AREA_CHUNKS;
        }

        Chunk chunk = world.getChunk(chunkX, chunkZ);
        ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
        if (sectionIndex >= sections.length) {
            advanceChunk();
            return chunkIndex < AREA_CHUNKS * AREA_CHUNKS;
        }

        ExtendedBlockStorage section = sections[sectionIndex];
        if (section != null && !section.isEmpty()) {
            int baseY = sectionIndex << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        IBlockState state = section.get(x, y, z);
                        if (state.getBlock() != ConcreteSet.ULTRA_HIGH_PERFORMANCE_CONCRETE) {
                            continue;
                        }
                        int meta = state.getValue(BlockConcreteVariant.META);
                        BlockPos target = new BlockPos((chunkX << 4) + x, baseY + y,
                                (chunkZ << 4) + z);
                        world.setBlockState(target,
                                ConcreteSet.ULTRA_HIGH_PERFORMANCE_CONCRETE_PANEL
                                        .getStateFromMeta(meta), 2);
                        converted++;
                    }
                }
            }
        }

        sectionIndex++;
        if (sectionIndex >= sections.length) advanceChunk();
        return chunkIndex < AREA_CHUNKS * AREA_CHUNKS;
    }

    private void advanceChunk() {
        chunkIndex++;
        sectionIndex = 0;
    }

    private int minChunkX() {
        return (pos.getX() >> 4) - (AREA_CHUNKS / 2);
    }

    private int minChunkZ() {
        return (pos.getZ() >> 4) - (AREA_CHUNKS / 2);
    }

    private void finish(World world) {
        activated = false;
        markDirty();

        long elapsed = world.getTotalWorldTime() - startedTick;
        NTMDoors.LOGGER.info(
                "Spatial Disrupter at {} finished: {} UHPC blocks converted in {} ticks (~{} s)",
                pos, converted, elapsed, elapsed / 20.0D);

        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.5D;
        double z = pos.getZ() + 0.5D;
        for (int i = 0; i < 48; i++) {
            world.spawnParticle(EnumParticleTypes.PORTAL,
                    x + (world.rand.nextDouble() - 0.5D) * 1.5D,
                    y + world.rand.nextDouble() * 1.5D,
                    z + (world.rand.nextDouble() - 0.5D) * 1.5D,
                    (world.rand.nextDouble() - 0.5D) * 0.4D,
                    world.rand.nextDouble() * 0.4D,
                    (world.rand.nextDouble() - 0.5D) * 0.4D);
        }
        world.playSound(null, pos, SoundEvents.ENTITY_ENDERMEN_TELEPORT,
                SoundCategory.BLOCKS, 1.0F, 0.8F);

        if (activatorId != null) {
            EntityPlayer player = world.getPlayerEntityByUUID(activatorId);
            if (player != null) {
                player.sendStatusMessage(new TextComponentTranslation(
                        "ntmdoors.msg.spatial_disrupter_done"), true);
            }
        }

        // The machine consumes itself; schedule the removal for the next block tick so the
        // tile entity is not removed from the world's tickable list while it is being iterated.
        world.scheduleUpdate(pos, getBlockType(), 1);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setBoolean("activated", activated);
        tag.setInteger("chunkIndex", chunkIndex);
        tag.setInteger("sectionIndex", sectionIndex);
        tag.setInteger("converted", converted);
        tag.setLong("startedTick", startedTick);
        if (activatorId != null) tag.setString("activator", activatorId.toString());
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        activated = tag.getBoolean("activated");
        chunkIndex = tag.getInteger("chunkIndex");
        sectionIndex = tag.getInteger("sectionIndex");
        converted = tag.getInteger("converted");
        startedTick = tag.getLong("startedTick");
        if (tag.hasKey("activator")) {
            try {
                activatorId = UUID.fromString(tag.getString("activator"));
            } catch (IllegalArgumentException ignored) {
                activatorId = null;
            }
        }
    }
}
