package com.ntmdoors.client;

import com.ntmdoors.NTMDoors;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Draws the programmer's slot tooltip after every other screen overlay. JEI/HEI render their item
 * list from the same event, so running last keeps our tooltip on top instead of buried under it.
 */
@Mod.EventBusSubscriber(modid = NTMDoors.MODID, value = Side.CLIENT)
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
