package com.ntmdoors.block.entity;

import com.ntmdoors.block.BlockThermiteCharge;
import com.ntmdoors.block.door.BlockDoorGeneric;
import com.ntmdoors.sound.ModSounds;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Stores the mount inset and runs the thermite fuse. The inset is quantised to 1/16 block,
 * matching the blockstate model variants; the fuse is 100 ticks and ends in a breach/explosion.
 */
public class ThermiteChargeBlockEntity extends BlockEntity {

    public static final int FUSE_TICKS = 100;
    /** Start the 3.52 s countdown sound 1.5 s after lighting so its final beep lands on the blast. */
    public static final int BEEP_START_TICKS = 70;

    private int inset = 0;
    private boolean doorTracked = false;
    @Nullable
    private BlockPos doorCore = null;
    private float doorFraction = 0.0F;

    private boolean pendingBreak = false;
    private boolean pendingExplosion = false;
    private boolean lit = false;
    private int fuse = 0;

    public ThermiteChargeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.THERMITE_CHARGE, pos, state);
    }

    public int getInset() {
        return inset;
    }

    public boolean isDoorTracked() {
        return doorTracked;
    }

    public boolean isLit() {
        return lit;
    }

    public int getFuse() {
        return fuse;
    }

    public boolean isPendingExplosion() {
        return pendingExplosion;
    }

    public boolean isPendingBreak() {
        return pendingBreak;
    }

    /** Records the mount depth and the door state it was attached to. */
    public void setMount(int inset, @Nullable BlockPos doorCore, float doorFraction) {
        this.inset = Math.max(0, Math.min(15, inset));
        this.doorCore = doorCore == null ? null : doorCore.toImmutable();
        this.doorTracked = doorCore != null;
        this.doorFraction = doorFraction;

        if (world != null && !world.isClient()) {
            BlockState state = getCachedState();
            if (state.isOf(com.ntmdoors.block.ModBlocks.THERMITE_CHARGE) && state.get(BlockThermiteCharge.INSET) != this.inset) {
                world.setBlockState(pos, state.with(BlockThermiteCharge.INSET, this.inset), Block.NOTIFY_LISTENERS);
            }
        }
        markDirty();
        sync();
    }

    /** Lights the fuse and locks the attached door's controls. */
    public boolean light() {
        if (world == null || world.isClient() || lit || pendingExplosion) return false;
        lit = true;
        fuse = FUSE_TICKS;
        pendingBreak = false;
        markDirty();
        sync();

        DoorBlockEntity core = doorCore();
        if (core != null) {
            core.addThermiteCharge(pos);
        }
        return true;
    }

    public static void tick(World world, BlockPos pos, BlockState state, ThermiteChargeBlockEntity be) {
        if (world == null || world.isClient()) return;

        if (be.pendingBreak) {
            world.breakBlock(pos, true);
            return;
        }

        if (be.pendingExplosion) {
            be.explode(world, pos, state);
            return;
        }

        if (be.lit) {
            if (be.fuse > 0) {
                be.fuse--;
                if (be.fuse == BEEP_START_TICKS) {
                    world.playSound(null, pos, ModSounds.THERMITE_BEEP, SoundCategory.BLOCKS, 2.0F, 1.0F);
                }
                if (be.fuse > 0 && be.fuse % 10 == 0) {
                    be.spawnFuseParticles(world, pos);
                }
            }
            if (be.fuse <= 0) {
                be.explode(world, pos, state);
            }
            return;
        }

        if (!be.doorTracked || be.doorCore == null) return;

        Direction facing = state.get(BlockThermiteCharge.FACING);
        BlockPos supportPos = pos.offset(facing.getOpposite());
        BlockState supportState = world.getBlockState(supportPos);
        if (!(supportState.getBlock() instanceof BlockDoorGeneric door)) {
            be.breakCharge();
            return;
        }

        DoorBlockEntity core = BlockDoorGeneric.getCore(world, supportPos, supportState);
        if (core == null) return;

        if (!core.getPos().equals(be.doorCore) || Math.abs(core.getOpenFraction(1.0F) - be.doorFraction) > 0.001F) {
            be.breakCharge();
        }
    }

    public void scheduleExplosion() {
        if (world == null || world.isClient() || pendingExplosion) return;
        pendingExplosion = true;
        markDirty();
    }

    public void explode(World world, BlockPos pos, BlockState state) {
        if (world == null || world.isClient()) return;

        Direction facing = state.get(BlockThermiteCharge.FACING);
        BlockPos support = pos.offset(facing.getOpposite());
        BlockState supportState = world.getBlockState(support);
        DoorBlockEntity core = doorCore();

        if (core != null) {
            core.removeThermiteCharge(pos);
            core.breach();
        } else if (!(supportState.getBlock() instanceof BlockDoorGeneric)
                && !supportState.isAir()
                && !supportState.isReplaceable()
                && supportState.getHardness(world, support) >= 0.0F) {
            world.breakBlock(support, false);
        }

        spawnExplosionParticles(world, pos);
        world.playSound(null, pos, ModSounds.THERMITE_EXPLOSION, SoundCategory.BLOCKS, 4.0F, 1.0F);
        world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
    }

    public void onRemoved() {
        if (world == null || world.isClient()) return;
        DoorBlockEntity core = doorCore();
        if (core != null) {
            core.removeThermiteCharge(pos);
        }
    }

    private void breakCharge() {
        if (world == null || world.isClient() || pendingBreak) return;
        pendingBreak = true;
        markDirty();
    }

    @Nullable
    public DoorBlockEntity doorCore() {
        if (world == null) return null;
        BlockState state = getCachedState();
        if (!state.isOf(com.ntmdoors.block.ModBlocks.THERMITE_CHARGE)) return null;
        Direction facing = state.get(BlockThermiteCharge.FACING);
        BlockPos support = pos.offset(facing.getOpposite());
        BlockState supportState = world.getBlockState(support);
        if (!(supportState.getBlock() instanceof BlockDoorGeneric)) return null;
        return BlockDoorGeneric.getCore(world, support, supportState);
    }

    private void spawnFuseParticles(World world, BlockPos pos) {
        if (!(world instanceof ServerWorld serverWorld)) return;
        double x = pos.getX() + 0.5 + (world.random.nextDouble() - 0.5) * 0.45;
        double y = pos.getY() + 0.5 + (world.random.nextDouble() - 0.5) * 0.45;
        double z = pos.getZ() + 0.5 + (world.random.nextDouble() - 0.5) * 0.45;
        serverWorld.spawnParticles(ParticleTypes.SMOKE, x, y, z, 1, 0, 0.02, 0, 0);
        if (world.random.nextInt(3) == 0) {
            serverWorld.spawnParticles(ParticleTypes.FLAME, x, y, z, 1, 0, 0.01, 0, 0);
        }
    }

    private void spawnExplosionParticles(World world, BlockPos pos) {
        if (!(world instanceof ServerWorld serverWorld)) return;
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;
        serverWorld.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1, 0, 0, 0, 0);
        for (int i = 0; i < 12; i++) {
            serverWorld.spawnParticles(ParticleTypes.EXPLOSION,
                    x + (world.random.nextDouble() - 0.5) * 1.5,
                    y + (world.random.nextDouble() - 0.5) * 1.5,
                    z + (world.random.nextDouble() - 0.5) * 1.5,
                    1, 0, 0, 0, 0);
        }
        for (int i = 0; i < 18; i++) {
            serverWorld.spawnParticles(ParticleTypes.LARGE_SMOKE,
                    x + (world.random.nextDouble() - 0.5) * 2.0,
                    y + (world.random.nextDouble() - 0.5) * 2.0,
                    z + (world.random.nextDouble() - 0.5) * 2.0,
                    1, 0, 0.04, 0, 0);
        }
    }

    @Override
    public void writeNbt(NbtCompound tag) {
        super.writeNbt(tag);
        tag.putInt("inset", inset);
        tag.putBoolean("doorTracked", doorTracked);
        if (doorCore != null) tag.putLong("doorCore", doorCore.asLong());
        tag.putFloat("doorFraction", doorFraction);
        tag.putBoolean("lit", lit);
        tag.putInt("fuse", fuse);
    }

    @Override
    public void readNbt(NbtCompound tag) {
        super.readNbt(tag);
        inset = Math.max(0, Math.min(15, tag.getInt("inset")));
        doorTracked = tag.getBoolean("doorTracked");
        doorCore = tag.contains("doorCore") ? BlockPos.fromLong(tag.getLong("doorCore")) : null;
        doorFraction = tag.getFloat("doorFraction");
        lit = tag.getBoolean("lit");
        fuse = tag.getInt("fuse");
        pendingBreak = false;
        pendingExplosion = false;
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        return createNbt();
    }

    public void sync() {
        if (world instanceof ServerWorld serverWorld) {
            serverWorld.getChunkManager().markForUpdate(pos);
        }
    }
}
