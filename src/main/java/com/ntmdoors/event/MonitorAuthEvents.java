package com.ntmdoors.event;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.monitor.MonitorAuth;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

/** Clears monitor terminal sessions when a player leaves the server. */
@Mod.EventBusSubscriber(modid = NTMDoors.MODID)
public final class MonitorAuthEvents {

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player != null) MonitorAuth.clear(event.player);
    }

    private MonitorAuthEvents() {
    }
}
