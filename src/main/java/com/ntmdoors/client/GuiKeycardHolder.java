package com.ntmdoors.client;

import com.ntmdoors.gui.ContainerKeycardHolder;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.util.Collections;

/**
 * Carried Keycard Holder screen: the same light casing, dark machine face and outlined slots as the
 * Keycard Programmer, with a 3x4 grid in the top half and the player inventory below it.
 *
 * <p>Slot tooltips are drawn after {@code super.drawScreen()} so JEI/HEI overlays are already in
 * place; the GUI itself stays the standard 176px width, so their item list never sits on top of
 * it.</p>
 */
@SideOnly(Side.CLIENT)
public class GuiKeycardHolder extends GuiContainer {

    private static final int COLOUR = 0xFFE8E8E8;
    private static final int MUTED = 0xFFAFAFAF;
    private static final int DARK_TEXT = 0xFF2B2B2E;

    private static final int MACHINE_TOP = 20;
    private static final int MACHINE_BOTTOM = 120;
    private static final int PLAYER_TOP = 122;

    private final ContainerKeycardHolder holderContainer;

    public GuiKeycardHolder(InventoryPlayer playerInventory, EntityPlayer player,
                            ItemStack holder, EnumHand hand) {
        super(new ContainerKeycardHolder(playerInventory, player, holder, hand));
        this.holderContainer = (ContainerKeycardHolder) this.inventorySlots;
        this.xSize = ContainerKeycardHolder.GUI_WIDTH;
        this.ySize = ContainerKeycardHolder.GUI_HEIGHT;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);

        // super.drawScreen() has drawn the background, every slot item, the player inventory and
        // any overlay hooking GuiScreenEvent.DrawScreenEvent.Post (JEI/HEI included). Our slot
        // tooltip therefore belongs after it.
        drawSlotTooltip(mouseX, mouseY);
    }

    // ------------------------------------------------------------ background

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        // A tooltip from the previous frame leaves lighting and rescale-normal enabled.
        GuiContainerStyle.resetGuiState();

        // GuiContainer calls this before applying the guiLeft/guiTop translation.
        GlStateManager.pushMatrix();
        GlStateManager.translate(guiLeft, guiTop, 0F);
        GlStateManager.color(1F, 1F, 1F, 1F);

        GuiContainerStyle.drawCasing(xSize, ySize);

        // dark machine face holding the card grid
        drawRect(4, MACHINE_TOP, xSize - 4, MACHINE_BOTTOM, 0xFF0B0B0D);
        drawRect(5, MACHINE_TOP + 1, xSize - 5, MACHINE_BOTTOM - 1, 0xFF1B1B1E);

        // player-inventory well
        drawRect(4, PLAYER_TOP, xSize - 4, ySize - 4, 0xFF0B0B0D);
        drawRect(5, PLAYER_TOP + 1, xSize - 5, ySize - 5, 0xFF1B1B1E);

        drawSlotFrames();
        drawGhosts();
        GlStateManager.popMatrix();
    }

    /** The custom background has no baked slot art, so every slot gets its frame here. */
    private void drawSlotFrames() {
        for (Slot slot : this.inventorySlots.inventorySlots) {
            GuiContainerStyle.drawSlotFrame(slot.xPos, slot.yPos);
        }
    }

    /** Draws the grey card silhouette into each empty holder slot. */
    private void drawGhosts() {
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);

        GlStateManager.disableDepth();
        GlStateManager.disableLighting();
        GlStateManager.disableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.colorMask(true, true, true, true);

        for (int i = 0; i < ContainerKeycardHolder.HOLDER_SLOT_COUNT; i++) {
            Slot slot = inventorySlots.inventorySlots.get(i);
            if (slot.getStack().isEmpty()) {
                GuiContainerStyle.drawGhostPixels(GhostIcons.RFID, slot.xPos, slot.yPos);
            }
        }

        GlStateManager.enableTexture2D();
        if (alpha) GlStateManager.enableAlpha();
        if (depth) GlStateManager.enableDepth();
        if (lighting) GlStateManager.enableLighting();
    }

    // ------------------------------------------------------------ foreground

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        GlStateManager.pushAttrib();
        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        GlStateManager.enableAlpha();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();

        fontRenderer.drawString("Keycard Holder", 8, 7, DARK_TEXT);
        drawCenteredString(fontRenderer, "Keycards", xSize / 2, 24, COLOUR);

        String count = holderContainer.getStoredCount() + "/" + ContainerKeycardHolder.HOLDER_SLOT_COUNT;
        GuiContainerStyle.drawRightAligned(fontRenderer, count, xSize - 8, 24, MUTED);

        GlStateManager.popAttrib();
    }

    // ------------------------------------------------------------ tooltips

    /**
     * Slot tooltip drawn after {@code super.drawScreen()} so it is above the GUI, its items and
     * JEI/HEI. Empty holder slots get a plain "Keycard" label instead of an ItemStack tooltip.
     */
    private void drawSlotTooltip(int mouseX, int mouseY) {
        Slot slot = findSlot(mouseX, mouseY);
        if (slot == null) return;

        ItemStack stack = slot.getStack();
        if (!stack.isEmpty()) {
            this.renderToolTip(stack, mouseX, mouseY);
            GuiContainerStyle.resetGuiState();
            return;
        }

        if (slot.slotNumber < ContainerKeycardHolder.HOLDER_SLOT_COUNT) {
            GuiContainerStyle.drawSimpleTooltip(fontRenderer, Collections.singletonList("Keycard"),
                    mouseX, mouseY, width, height);
        }
    }

    /** Vanilla's own hovered-slot pass stays empty so the tooltip is only ever drawn once. */
    @Override
    protected void renderHoveredToolTip(int mouseX, int mouseY) {
    }

    private Slot findSlot(int mouseX, int mouseY) {
        for (Slot slot : inventorySlots.inventorySlots) {
            if (!slot.isEnabled()) continue;
            int x = guiLeft + slot.xPos;
            int y = guiTop + slot.yPos;
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                return slot;
            }
        }
        return null;
    }
}
