package com.ntmdoors.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

/** Scissor helpers for scrolling GUI lists. */
@SideOnly(Side.CLIENT)
final class GuiClip {

    /** Restricts drawing to the given GUI-space rectangle until {@link #end()}. */
    static void begin(int x, int y, int width, int height) {
        int scale = new ScaledResolution(Minecraft.getMinecraft()).getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x * scale, Minecraft.getMinecraft().displayHeight - (y + height) * scale,
                width * scale, height * scale);
    }

    static void end() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private GuiClip() {
    }
}
