package com.ntmdoors.client;

import com.ntmdoors.NTMDoors;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

/** Clears the terminal-return context if a device screen was closed without Escape. */
@Mod.EventBusSubscriber(modid = NTMDoors.MODID, value = Side.CLIENT)
public final class MonitorReturnEvents {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            MonitorReturnContext.clearIfNoScreen();
        }
    }

    private MonitorReturnEvents() {
    }
}
