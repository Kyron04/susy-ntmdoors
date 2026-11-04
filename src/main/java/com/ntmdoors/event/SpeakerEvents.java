package com.ntmdoors.event;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.block.BlockSpeaker;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Shift-right-click opens the speaker screen even while holding an item. Vanilla skips block
 * activation for a sneaking player holding something, so the interaction guard owns that case.
 */
@Mod.EventBusSubscriber(modid = NTMDoors.MODID)
public final class SpeakerEvents {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled()) return;
        EntityPlayer player = event.getEntityPlayer();
        if (player == null || !player.isSneaking()) return;

        World world = event.getWorld();
        BlockPos pos = event.getPos();
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockSpeaker)) return;

        if (world.isRemote) {
            NTMDoors.proxy.openSpeakerGui(pos);
        } else {
            ((BlockSpeaker) state.getBlock()).ensureTileEntity(world, pos, state);
        }
        event.setCanceled(true);
    }

    private SpeakerEvents() {
    }
}
