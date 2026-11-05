package com.ntmdoors.client;

import com.ntmdoors.NTMDoors;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

/** Clears terminal-return state and the detached monitor GUI caches when they go stale. */
@Mod.EventBusSubscriber(modid = NTMDoors.MODID, value = Side.CLIENT)
public final class MonitorReturnEvents {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            MonitorReturnContext.clearIfNoScreen();
            TerminalBackupContext.clearIfNoScreen();
        }
    }

    @SubscribeEvent
    public static void onWorldUnload(WorldEvent.Unload event) {
        if (event.getWorld().isRemote) RenderMonitorDesk.clearDisplayCache();
    }

    private MonitorReturnEvents() {
    }
}
