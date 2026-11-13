package com.ntmdoors.client;

import com.ntmdoors.item.ItemUndefinedRelayKey;
import com.ntmdoors.network.MessageRelayKeyType;
import com.ntmdoors.network.ModNetwork;
import com.ntmdoors.registry.ModContent;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.List;

/**
 * Key-type picker for the Undefined Relay Key. The rows are the same relay and utility keys the
 * server validates against, and a click asks the server to replace the held programming key with
 * a fresh blank key of that type.
 */
@SideOnly(Side.CLIENT)
public class GuiUndefinedRelayKey extends GuiScreen {

    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 220;
    private static final int ROW_TOP = 30;
    private static final int ROW_HEIGHT = 21;

    private static final int COLOUR = 0xFFE8E8E8;
    private static final int MUTED = 0xFFAFAFAF;
    private static final int RULE = 0xFF4A4A4E;
    private static final int HOVER = 0xFF3A3A3E;

    private final EnumHand hand;
    private int left;
    private int top;
    private List<Item> types;

    public GuiUndefinedRelayKey(EnumHand hand) {
        this.hand = hand;
    }

    @Override
    public void initGui() {
        super.initGui();
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        types = ModContent.UNDEFINED_KEY_TYPES;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        // Light metal frame over a dark panel, matching the other key screens.
        drawRect(left - 1, top - 1, left + PANEL_WIDTH + 1, top + PANEL_HEIGHT + 1, COLOUR);
        drawRect(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF111111);
        drawRect(left + 1, top + 1, left + PANEL_WIDTH - 1, top + PANEL_HEIGHT - 1, 0xFF2B2B2E);
        drawRect(left + 1, top + 1, left + PANEL_WIDTH - 1, top + 22, 0xFF232326);
        drawRect(left + 1, top + 22, left + PANEL_WIDTH - 1, top + 23, RULE);

        drawString(fontRenderer, "UNDEFINED RELAY KEY", left + 12, top + 10, COLOUR);
        String right = "SELECT KEY TYPE";
        drawString(fontRenderer, right,
                left + PANEL_WIDTH - 12 - fontRenderer.getStringWidth(right), top + 10, MUTED);

        int hovered = hoveredRow(mouseX, mouseY);
        for (int i = 0; i < types.size(); i++) {
            int y = top + ROW_TOP + i * ROW_HEIGHT;
            if (i == hovered) {
                drawRect(left + 8, y - 2, left + PANEL_WIDTH - 8, y + ROW_HEIGHT - 3, HOVER);
            }
            ItemStack stack = new ItemStack(types.get(i));
            drawKeyIcon(stack, left + 12, y);
            drawString(fontRenderer, stack.getDisplayName(), left + 34, y + 4,
                    i == hovered ? 0xFFFFFFFF : COLOUR);
        }

        drawRect(left + 12, top + PANEL_HEIGHT - 18, left + PANEL_WIDTH - 12,
                top + PANEL_HEIGHT - 17, RULE);
        String footer = hovered >= 0 && hovered < types.size()
                ? ItemUndefinedRelayKey.descriptionFor(types.get(hovered))
                : "Click a key type to program the held key.";
        drawCenteredString(fontRenderer, footer, left + PANEL_WIDTH / 2,
                top + PANEL_HEIGHT - 13, MUTED);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawKeyIcon(ItemStack stack, int x, int y) {
        // Item icons change lighting and depth state; restore it before drawing the rest of the UI.
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableDepth();
        mc.getRenderItem().renderItemAndEffectIntoGUI(stack, x, y);
        RenderHelper.disableStandardItemLighting();
        if (depth) GlStateManager.enableDepth();
        else GlStateManager.disableDepth();
        if (lighting) GlStateManager.enableLighting();
        else GlStateManager.disableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1F, 1F, 1F, 1F);
    }

    private int hoveredRow(int mouseX, int mouseY) {
        for (int i = 0; i < types.size(); i++) {
            if (inRow(i, mouseX, mouseY)) return i;
        }
        return -1;
    }

    private boolean inRow(int index, int mouseX, int mouseY) {
        int y = top + ROW_TOP + index * ROW_HEIGHT;
        return mouseX >= left + 8 && mouseX < left + PANEL_WIDTH - 8
                && mouseY >= y - 2 && mouseY < y + ROW_HEIGHT - 3;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0) {
            int row = hoveredRow(mouseX, mouseY);
            if (row >= 0) {
                ModNetwork.INSTANCE.sendToServer(new MessageRelayKeyType(hand, row));
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
