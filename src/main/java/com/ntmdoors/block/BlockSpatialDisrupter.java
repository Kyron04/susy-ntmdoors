package com.ntmdoors.block;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.tile.TileEntitySpatialDisrupter;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.Random;

/**
 * Spatial Disrupter. Bedrock-hard: it cannot be mined or blown up, and only its own completed
 * operation removes it. Right-clicking opens its activation screen; the tile entity performs the
 * UHPC scan and destroys the machine when it finishes.
 */
public class BlockSpatialDisrupter extends Block {

    public BlockSpatialDisrupter() {
        super(Material.ROCK);
        setRegistryName(NTMDoors.MODID, "spatial_disrupter");
        setTranslationKey(NTMDoors.MODID + ".spatial_disrupter");
        setCreativeTab(NTMDoors.TAB);
        setBlockUnbreakable();
        setResistance(6000000F);
        setSoundType(SoundType.STONE);
        setHarvestLevel("pickaxe", 0);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileEntitySpatialDisrupter();
    }

    /** No explosion, including modded warheads, can remove it. */
    @Override
    public float getExplosionResistance(World world, BlockPos pos, @Nullable Entity exploder,
                                        Explosion explosion) {
        return Float.MAX_VALUE;
    }

    /** Pistons cannot move it. */
    @Override
    public EnumPushReaction getPushReaction(IBlockState state) {
        return EnumPushReaction.BLOCK;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand, EnumFacing side,
                                    float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            NTMDoors.proxy.openSpatialDisrupterGui(pos);
        }
        return true;
    }

    /**
     * The tile entity schedules this after its final scan tick. Removing the block here happens
     * outside the world's tile-entity tick loop, so the machine can safely consume itself.
     */
    @Override
    public void updateTick(World world, BlockPos pos, IBlockState state, Random rand) {
        world.setBlockToAir(pos);
    }
}
