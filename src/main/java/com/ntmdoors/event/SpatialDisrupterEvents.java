package com.ntmdoors.event;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.block.BlockSpatialDisrupter;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * The Spatial Disrupter is bedrock-hard. Hardness -1 already stops survival mining, and this
 * listener also refuses creative or fake-player breaks. Only the machine's own self-destruct
 * (a direct setBlockToAir from the tile entity) removes it.
 */
@Mod.EventBusSubscriber(modid = NTMDoors.MODID)
public final class SpatialDisrupterEvents {

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getState().getBlock() instanceof BlockSpatialDisrupter) {
            event.setCanceled(true);
        }
    }

    private SpatialDisrupterEvents() {
    }
}
