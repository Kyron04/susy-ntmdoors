package com.ntmdoors.event;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.block.BlockSpatialDisrupter;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * The Spatial Disrupter is bedrock-hard in survival. Hardness -1 already stops survival mining, and
 * this listener refuses non-creative breaks. Creative players may remove it; only the machine's own
 * self-destruct (a direct setBlockToAir from the tile entity) also removes it.
 */
@Mod.EventBusSubscriber(modid = NTMDoors.MODID)
public final class SpatialDisrupterEvents {

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getState().getBlock() instanceof BlockSpatialDisrupter)) return;
        EntityPlayer player = event.getPlayer();
        if (player == null || !player.capabilities.isCreativeMode) {
            event.setCanceled(true);
        }
    }

    private SpatialDisrupterEvents() {
    }
}
