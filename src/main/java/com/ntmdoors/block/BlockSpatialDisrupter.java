package com.ntmdoors.block;

import com.ntmdoors.block.entity.ModBlockEntities;
import com.ntmdoors.block.entity.SpatialDisrupterBlockEntity;
import com.ntmdoors.client.screen.SpatialDisrupterScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Spatial Disrupter for Fabric 1.20.1.
 * Unbreakable in survival (hardness -1.0F, blast resistance 6,000,000F).
 * Right-clicking opens its activation screen; the block entity performs a 16x16 chunk
 * block scan and self-destructs upon completion.
 */
public class BlockSpatialDisrupter extends BlockWithEntity {

    public BlockSpatialDisrupter() {
        super(FabricBlockSettings.create()
                .mapColor(MapColor.STONE_GRAY)
                .strength(-1.0F, 6000000.0F)
                .sounds(BlockSoundGroup.STONE)
                .pistonBehavior(PistonBehavior.BLOCK));
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new SpatialDisrupterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return checkType(type, ModBlockEntities.SPATIAL_DISRUPTER, SpatialDisrupterBlockEntity::tick);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient()) {
            openClientScreen(pos);
        }
        return ActionResult.SUCCESS;
    }

    @Environment(EnvType.CLIENT)
    private void openClientScreen(BlockPos pos) {
        MinecraftClient.getInstance().setScreen(new SpatialDisrupterScreen(pos));
    }
}
