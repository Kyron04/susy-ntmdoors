package com.ntmdoors.tile;

import com.ntmdoors.block.BlockDoorGeneric;
import com.ntmdoors.block.BlockThermiteCharge;
import com.ntmdoors.util.ModSounds;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;

/**
 * Stores the mount inset and runs the thermite fuse. The inset is quantised to 1/16 block, matching
 * the blockstate model variants; the fuse is 100 ticks and ends in a breach/explosion scheduled
 * outside the tile-entity tick loop.
 */
public class TileEntityThermiteCharge extends TileEntitySynced implements ITickable {

    public static final int FUSE_TICKS = 100;
    /** Start the 3.52 s countdown file 1.5 s after lighting so its final beep lands on the blast. */
    private static final int BEEP_START_TICKS = 70;

    /** 0..15 sixteenths of a block. */
    private int inset;
    private boolean doorTracked;
    @Nullable
    private BlockPos doorCore;
    private float doorFraction;
    /** Set when the door moved; the block removes itself on the next scheduled block tick. */
    private boolean pendingBreak;
    /** Set when the fuse ends or the support disappears; the block ticks the detonation. */
    private boolean pendingExplosion;
    private boolean lit;
    private int fuse;

    public int getInset() {
        return inset;
    }

    public boolean isDoorTracked() {
        return doorTracked;
    }

    public boolean isLit() {
        return lit;
    }

    public boolean isPendingExplosion() {
        return pendingExplosion;
    }

    public boolean isPendingBreak() {
        return pendingBreak;
    }

    /** Server and client: records the mount depth and the door state it was attached to. */
    public void setMount(int inset, @Nullable BlockPos doorCore, float doorFraction) {
        this.inset = Math.max(0, Math.min(15, inset));
        this.doorCore = doorCore == null ? null : doorCore.toImmutable();
        this.doorTracked = doorCore != null;
        this.doorFraction = doorFraction;
        sync();
    }

    /** Server side: lights the fuse and locks the attached door's controls. */
    public boolean light() {
        if (world == null || world.isRemote || lit || pendingExplosion) return false;
        lit = true;
        fuse = FUSE_TICKS;
        pendingBreak = false;
        sync();
        TileEntityDoorGeneric core = doorCore();
        if (core != null) core.addThermiteCharge(pos);
        return true;
    }

    @Override
    public void update() {
        if (world == null || world.isRemote || pendingExplosion) return;

        if (lit) {
            if (fuse > 0) {
                fuse--;
                if (fuse == BEEP_START_TICKS) {
                    world.playSound(null, pos, ModSounds.THERMITE_BEEP, SoundCategory.BLOCKS, 2F, 1F);
                }
                if (fuse > 0 && fuse % 10 == 0) spawnFuseParticles();
            }
            if (fuse <= 0) scheduleExplosion();
            return;
        }

        if (!doorTracked || doorCore == null) return;

        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockThermiteCharge)) return;

        EnumFacing facing = state.getValue(BlockThermiteCharge.FACING);
        BlockPos supportPos = pos.offset(facing.getOpposite());
        IBlockState supportState = world.getBlockState(supportPos);
        if (!(supportState.getBlock() instanceof BlockDoorGeneric)) {
            breakCharge();
            return;
        }

        BlockDoorGeneric door = (BlockDoorGeneric) supportState.getBlock();
        TileEntityDoorGeneric core = door.getCore(world, supportPos, supportState);
        if (core == null) {
            // The door core can be in an unloaded chunk; leave the charge alone until it loads.
            return;
        }
        if (!core.getPos().equals(doorCore)) {
            breakCharge();
            return;
        }
        if (Math.abs(core.getOpenFraction(1F) - doorFraction) > 0.001F) {
            breakCharge();
        }
    }

    /** Schedules the explosion for the next block tick, outside the tile-entity loop. */
    public void scheduleExplosion() {
        if (world == null || world.isRemote || pendingExplosion) return;
        pendingExplosion = true;
        markDirty();
        world.scheduleUpdate(pos, getBlockType(), 1);
    }

    /** Block tick: performs the breach, sound, particles and self-removal. */
    public void explode() {
        if (world == null || world.isRemote) return;
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockThermiteCharge)) return;

        EnumFacing facing = state.getValue(BlockThermiteCharge.FACING);
        BlockPos support = pos.offset(facing.getOpposite());
        IBlockState supportState = world.getBlockState(support);
        TileEntityDoorGeneric core = doorCore();
        if (core != null) {
            core.removeThermiteCharge(pos);
            core.breach();
        } else if (!(supportState.getBlock() instanceof BlockDoorGeneric)
                && !world.isAirBlock(support)
                && !supportState.getBlock().isReplaceable(world, support)
                && supportState.getBlockHardness(world, support) >= 0F) {
            // Not a door: destroy the block the charge was stuck to. Unbreakable blocks
            // (bedrock, the Spatial Disrupter) are left alone.
            world.destroyBlock(support, false);
        }

        spawnExplosionParticles();
        world.playSound(null, pos, ModSounds.THERMITE_EXPLOSION, SoundCategory.BLOCKS, 4F, 1F);
        // Block ticks run outside the tile-entity list iteration, so this is safe.
        world.setBlockToAir(pos);
    }

    /** Called by the block before it is replaced, so a defused charge releases the door lock. */
    public void onRemoved() {
        if (world == null || world.isRemote) return;
        TileEntityDoorGeneric core = doorCore();
        if (core != null) core.removeThermiteCharge(pos);
    }

    private void breakCharge() {
        if (world == null || world.isRemote || pendingBreak) return;
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockThermiteCharge)) return;
        // Removing the block from inside the tile-entity tick loop would mutate the world's
        // tickable list. Schedule the removal for the next block tick instead.
        pendingBreak = true;
        markDirty();
        world.scheduleUpdate(pos, state.getBlock(), 1);
    }

    @Nullable
    private TileEntityDoorGeneric doorCore() {
        if (world == null) return null;
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockThermiteCharge)) return null;
        EnumFacing facing = state.getValue(BlockThermiteCharge.FACING);
        BlockPos support = pos.offset(facing.getOpposite());
        IBlockState supportState = world.getBlockState(support);
        if (!(supportState.getBlock() instanceof BlockDoorGeneric)) return null;
        return ((BlockDoorGeneric) supportState.getBlock()).getCore(world, support, supportState);
    }

    private void spawnFuseParticles() {
        double x = pos.getX() + 0.5D + (world.rand.nextDouble() - 0.5D) * 0.45D;
        double y = pos.getY() + 0.5D + (world.rand.nextDouble() - 0.5D) * 0.45D;
        double z = pos.getZ() + 0.5D + (world.rand.nextDouble() - 0.5D) * 0.45D;
        world.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, x, y, z, 0.0D, 0.02D, 0.0D);
        if (world.rand.nextInt(3) == 0) {
            world.spawnParticle(EnumParticleTypes.FLAME, x, y, z, 0.0D, 0.01D, 0.0D);
        }
    }

    private void spawnExplosionParticles() {
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.5D;
        double z = pos.getZ() + 0.5D;
        world.spawnParticle(EnumParticleTypes.EXPLOSION_HUGE, x, y, z, 0.0D, 0.0D, 0.0D);
        for (int i = 0; i < 12; i++) {
            world.spawnParticle(EnumParticleTypes.EXPLOSION_LARGE,
                    x + (world.rand.nextDouble() - 0.5D) * 1.5D,
                    y + (world.rand.nextDouble() - 0.5D) * 1.5D,
                    z + (world.rand.nextDouble() - 0.5D) * 1.5D, 0.0D, 0.0D, 0.0D);
        }
        for (int i = 0; i < 18; i++) {
            world.spawnParticle(EnumParticleTypes.SMOKE_LARGE,
                    x + (world.rand.nextDouble() - 0.5D) * 2.0D,
                    y + (world.rand.nextDouble() - 0.5D) * 2.0D,
                    z + (world.rand.nextDouble() - 0.5D) * 2.0D, 0.0D, 0.04D, 0.0D);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("inset", inset);
        tag.setBoolean("doorTracked", doorTracked);
        if (doorCore != null) tag.setLong("doorCore", doorCore.toLong());
        tag.setFloat("doorFraction", doorFraction);
        tag.setBoolean("lit", lit);
        tag.setInteger("fuse", fuse);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        inset = Math.max(0, Math.min(15, tag.getInteger("inset")));
        doorTracked = tag.getBoolean("doorTracked");
        doorCore = tag.hasKey("doorCore") ? BlockPos.fromLong(tag.getLong("doorCore")) : null;
        doorFraction = tag.getFloat("doorFraction");
        lit = tag.getBoolean("lit");
        fuse = tag.getInteger("fuse");
        pendingBreak = false;
        pendingExplosion = false;
    }
}
