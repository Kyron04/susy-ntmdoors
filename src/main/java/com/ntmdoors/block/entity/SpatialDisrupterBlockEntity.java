package com.ntmdoors.block.entity;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.sound.ModSounds;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;

import java.util.UUID;

/**
 * Spatial Disrupter BlockEntity for Fabric 1.20.1.
 * Scans a 16x16 chunk area centered on itself (4 chunk sections per tick) and weakens
 * blast-resistant blocks (Obsidian, Crying Obsidian, Reinforced Deepslate, Ancient Debris).
 * When complete, plays enderman teleport sound, portal particles, and self-destructs.
 */
public class SpatialDisrupterBlockEntity extends BlockEntity {

    public static final int AREA_CHUNKS = 16;
    public static final int SECTIONS_PER_TICK = 4;

    private boolean activated = false;
    private UUID activatorId = null;
    private int chunkIndex = 0;
    private int sectionIndex = 0;
    private int converted = 0;
    private long startedTick = 0;

    public SpatialDisrupterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SPATIAL_DISRUPTER, pos, state);
    }

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

    public boolean activate(PlayerEntity player) {
        if (world == null || world.isClient()) return false;
        if (activated) {
            if (player != null) {
                player.sendMessage(Text.translatable("ntmdoors.msg.spatial_disrupter_already"), true);
            }
            return false;
        }

        activated = true;
        activatorId = player == null ? null : player.getUuid();
        chunkIndex = 0;
        sectionIndex = 0;
        converted = 0;
        startedTick = world.getTime();
        markDirty();

        if (player != null) {
            player.sendMessage(Text.translatable("ntmdoors.msg.spatial_disrupter_active"), true);
        }
        world.playSound(null, pos, ModSounds.BUTTON9, SoundCategory.BLOCKS, 1.0F, 0.8F);
        return true;
    }

    public static void tick(World world, BlockPos pos, BlockState state, SpatialDisrupterBlockEntity be) {
        if (!be.activated || world == null || world.isClient()) return;

        int budget = SECTIONS_PER_TICK;
        while (budget-- > 0) {
            if (!be.processSection(world)) {
                be.finish(world);
                return;
            }
        }
        be.markDirty();
    }

    private boolean processSection(World world) {
        if (chunkIndex >= AREA_CHUNKS * AREA_CHUNKS) return false;

        int chunkX = minChunkX() + (chunkIndex % AREA_CHUNKS);
        int chunkZ = minChunkZ() + (chunkIndex / AREA_CHUNKS);

        // Skip unloaded chunks without loading or generating them
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            advanceChunk();
            return chunkIndex < AREA_CHUNKS * AREA_CHUNKS;
        }

        Chunk chunk = world.getChunk(chunkX, chunkZ);
        ChunkSection[] sections = chunk.getSectionArray();
        if (sectionIndex >= sections.length) {
            advanceChunk();
            return chunkIndex < AREA_CHUNKS * AREA_CHUNKS;
        }

        ChunkSection section = sections[sectionIndex];
        if (section != null && !section.isEmpty()) {
            int baseY = chunk.sectionIndexToCoord(sectionIndex) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState blockState = section.getBlockState(x, y, z);
                        BlockPos target = new BlockPos((chunkX << 4) + x, baseY + y, (chunkZ << 4) + z);

                        if (blockState.isOf(Blocks.OBSIDIAN)) {
                            world.setBlockState(target, Blocks.CRYING_OBSIDIAN.getDefaultState(), Block.NOTIFY_LISTENERS);
                            converted++;
                        } else if (blockState.isOf(Blocks.CRYING_OBSIDIAN)) {
                            world.setBlockState(target, Blocks.COBBLESTONE.getDefaultState(), Block.NOTIFY_LISTENERS);
                            converted++;
                        } else if (blockState.isOf(Blocks.REINFORCED_DEEPSLATE)) {
                            world.setBlockState(target, Blocks.POLISHED_DEEPSLATE.getDefaultState(), Block.NOTIFY_LISTENERS);
                            converted++;
                        } else if (blockState.isOf(Blocks.ANCIENT_DEBRIS)) {
                            world.setBlockState(target, Blocks.BASALT.getDefaultState(), Block.NOTIFY_LISTENERS);
                            converted++;
                        }
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

        long elapsed = world.getTime() - startedTick;
        NTMDoors.LOGGER.info("Spatial Disrupter at {} finished: {} blast-resistant blocks converted in {} ticks (~{} s)",
                pos, converted, elapsed, elapsed / 20.0);

        if (world instanceof ServerWorld serverWorld) {
            double x = pos.getX() + 0.5;
            double y = pos.getY() + 0.5;
            double z = pos.getZ() + 0.5;
            for (int i = 0; i < 48; i++) {
                serverWorld.spawnParticles(ParticleTypes.PORTAL,
                        x + (world.random.nextDouble() - 0.5) * 1.5,
                        y + world.random.nextDouble() * 1.5,
                        z + (world.random.nextDouble() - 0.5) * 1.5,
                        1,
                        (world.random.nextDouble() - 0.5) * 0.4,
                        world.random.nextDouble() * 0.4,
                        (world.random.nextDouble() - 0.5) * 0.4,
                        0.1);
            }
        }

        world.playSound(null, pos, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.BLOCKS, 1.0F, 0.8F);

        if (activatorId != null) {
            PlayerEntity player = world.getPlayerByUuid(activatorId);
            if (player != null) {
                player.sendMessage(Text.translatable("ntmdoors.msg.spatial_disrupter_done"), true);
            }
        }

        // Self-destruct and schedule removal
        world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
    }

    @Override
    public void writeNbt(NbtCompound tag) {
        super.writeNbt(tag);
        tag.putBoolean("activated", activated);
        tag.putInt("chunkIndex", chunkIndex);
        tag.putInt("sectionIndex", sectionIndex);
        tag.putInt("converted", converted);
        tag.putLong("startedTick", startedTick);
        if (activatorId != null) tag.putString("activator", activatorId.toString());
    }

    @Override
    public void readNbt(NbtCompound tag) {
        super.readNbt(tag);
        activated = tag.getBoolean("activated");
        chunkIndex = tag.getInt("chunkIndex");
        sectionIndex = tag.getInt("sectionIndex");
        converted = tag.getInt("converted");
        startedTick = tag.getLong("startedTick");
        activatorId = tag.contains("activator") ? UUID.fromString(tag.getString("activator")) : null;
    }
}
