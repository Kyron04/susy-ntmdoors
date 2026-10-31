package com.ntmdoors.client;

import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Draws the programmer's slot tooltip after every other screen overlay. JEI/HEI render their item
 * list and tooltips from the same event, so running last keeps our tooltip on top instead of buried
 * under their panel. Registered explicitly from ClientProxy to avoid any annotation-scan surprises.
 */
public final class ProgrammerGuiEvents {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDrawScreen(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (event.getGui() instanceof GuiKeycardProgrammer) {
            ((GuiKeycardProgrammer) event.getGui()).drawTooltip(event.getMouseX(), event.getMouseY());
        }
    }

    private ProgrammerGuiEvents() {
    }
}
