package com.ntmdoors.client;

import com.ntmdoors.network.MessageSpatialDisrupterActivate;
import com.ntmdoors.network.ModNetwork;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/** Activation screen for the Spatial Disrupter. The operation cannot be undone. */
@SideOnly(Side.CLIENT)
public class GuiSpatialDisrupter extends GuiScreen {

    private static final int PANEL_WIDTH = 280;
    private static final int PANEL_HEIGHT = 156;
    private static final int BUTTON_WIDTH = 190;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_TOP = 82;

    private static final int COLOUR = 0xFFE8E8E8;
    private static final int MUTED = 0xFFAFAFAF;
    private static final int RULE = 0xFF4A4A4E;
    private static final int BUTTON_FILL = 0xFF3A2A2A;
    private static final int BUTTON_EDGE = 0xFF8A4A4A;
    private static final int BUTTON_HOVER = 0xFF5A2A2A;
    private static final int WARNING = 0xFFFF6A6A;

    private final BlockPos pos;
    private int left;
    private int top;

    public GuiSpatialDisrupter(BlockPos pos) {
        this.pos = pos;
    }

    @Override
    public void initGui() {
        super.initGui();
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        drawRect(left - 1, top - 1, left + PANEL_WIDTH + 1, top + PANEL_HEIGHT + 1, COLOUR);
        drawRect(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF111111);
        drawRect(left + 1, top + 1, left + PANEL_WIDTH - 1, top + PANEL_HEIGHT - 1, 0xFF2B2B2E);
        drawRect(left + 1, top + 1, left + PANEL_WIDTH - 1, top + 22, 0xFF232326);
        drawRect(left + 1, top + 22, left + PANEL_WIDTH - 1, top + 23, RULE);

        drawString(fontRenderer, "SPATIAL DISRUPTER", left + 12, top + 10, COLOUR);
        String right = "ACTIVATION";
        drawString(fontRenderer, right,
                left + PANEL_WIDTH - 12 - fontRenderer.getStringWidth(right), top + 10, MUTED);

        drawCenteredString(fontRenderer, "Weakens the structural integrity of UHPC",
                left + PANEL_WIDTH / 2, top + 38, COLOUR);

        int buttonLeft = left + (PANEL_WIDTH - BUTTON_WIDTH) / 2;
        int buttonTop = top + BUTTON_TOP;
        boolean hover = mouseX >= buttonLeft && mouseX < buttonLeft + BUTTON_WIDTH
                && mouseY >= buttonTop && mouseY < buttonTop + BUTTON_HEIGHT;
        drawRect(buttonLeft, buttonTop, buttonLeft + BUTTON_WIDTH, buttonTop + BUTTON_HEIGHT,
                hover ? BUTTON_HOVER : BUTTON_FILL);
        drawRect(buttonLeft, buttonTop, buttonLeft + BUTTON_WIDTH, buttonTop + 1, BUTTON_EDGE);
        drawRect(buttonLeft, buttonTop + BUTTON_HEIGHT - 1, buttonLeft + BUTTON_WIDTH,
                buttonTop + BUTTON_HEIGHT, 0xFF241C1C);
        drawCenteredString(fontRenderer, "ACTIVATE SPATIAL DISRUPTER",
                buttonLeft + BUTTON_WIDTH / 2, buttonTop + 6,
                hover ? 0xFFFFD0D0 : COLOUR);

        drawCenteredString(fontRenderer, "WARNING THIS OPERATION CANNOT BE REVERSED",
                left + PANEL_WIDTH / 2, top + 114, WARNING);
        drawCenteredString(fontRenderer, "16x16 chunks of UHPC will be weakened.",
                left + PANEL_WIDTH / 2, top + 128, MUTED);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0) {
            int buttonLeft = left + (PANEL_WIDTH - BUTTON_WIDTH) / 2;
            int buttonTop = top + BUTTON_TOP;
            if (mouseX >= buttonLeft && mouseX < buttonLeft + BUTTON_WIDTH
                    && mouseY >= buttonTop && mouseY < buttonTop + BUTTON_HEIGHT) {
                ModNetwork.INSTANCE.sendToServer(new MessageSpatialDisrupterActivate(pos));
                mc.displayGuiScreen(null);
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }
}
