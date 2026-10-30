package com.ntmdoors.client;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.gui.ContainerKeycardProgrammer;
import com.ntmdoors.tile.TileEntityKeycardProgrammer;
import com.ntmdoors.util.ModSounds;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Three-page keycard programmer GUI, styled after the in-game mockups: a light metal frame around a
 * dark panel, outlined item slots and a paged footer. Page one binds cards, page two re-colours an
 * RFID card and page three is the legacy backup console.
 */
@SideOnly(Side.CLIENT)
public class GuiKeycardProgrammer extends GuiContainer {

    private static final int PAGE_COUNT = 3;
    private static final int COLOUR = 0xFFE8E8E8;
    private static final int MUTED = 0xFFAFAFAF;
    private static final int DARK_TEXT = 0xFF2B2B2E;
    private static final float BUTTON_VOLUME = 0.8F;
    private static final int BACKUP_FRAME_WIDTH = 876;
    private static final int BACKUP_FRAME_HEIGHT = 64;

    private static final ResourceLocation[] BACKUP_FRAMES = {
            new ResourceLocation(NTMDoors.MODID, "textures/gui/legacy_backup_progress_000.png"),
            new ResourceLocation(NTMDoors.MODID, "textures/gui/legacy_backup_progress_025.png"),
            new ResourceLocation(NTMDoors.MODID, "textures/gui/legacy_backup_progress_050.png"),
            new ResourceLocation(NTMDoors.MODID, "textures/gui/legacy_backup_progress_075.png"),
            new ResourceLocation(NTMDoors.MODID, "textures/gui/legacy_backup_progress_100.png")
    };

    private final TileEntityKeycardProgrammer tile;
    private int page;
    private String status = "";
    private int statusTicks;
    private final int[] originalX;
    private final int[] originalY;

    public GuiKeycardProgrammer(InventoryPlayer playerInventory, TileEntityKeycardProgrammer tile) {
        super(new ContainerKeycardProgrammer(playerInventory, tile));
        this.tile = tile;
        this.xSize = ContainerKeycardProgrammer.GUI_WIDTH;
        this.ySize = ContainerKeycardProgrammer.GUI_HEIGHT;

        List<Slot> slots = this.inventorySlots.inventorySlots;
        this.originalX = new int[slots.size()];
        this.originalY = new int[slots.size()];
        for (int i = 0; i < slots.size(); i++) {
            this.originalX[i] = slots.get(i).xPos;
            this.originalY[i] = slots.get(i).yPos;
        }
        updateSlotPositions();
    }

    /**
     * Minecraft draws every slot in the container. Hidden page slots are parked far outside the
     * screen, which also stops them being hovered or clicked; the server container never moves.
     */
    private void updateSlotPositions() {
        for (Slot slot : this.inventorySlots.inventorySlots) {
            int index = slot.slotNumber;
            if (index >= 0 && index < this.originalX.length) {
                if (isSlotVisible(slot)) {
                    slot.xPos = this.originalX[index];
                    slot.yPos = this.originalY[index];
                } else {
                    slot.xPos = -10000 - index;
                    slot.yPos = -10000;
                }
            }
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (statusTicks > 0) statusTicks--;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // Standard container GUIs darken the world first. Drawing the tooltip after super also
        // puts it above JEI/HEI overlays, matching normal container behaviour.
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawSlotTooltip(mouseX, mouseY);
    }

    // ------------------------------------------------------------ background

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        // GuiContainer calls this before it applies the guiLeft/guiTop translation, so the
        // background has to enter GUI space itself or it lands in the screen's top-left corner.
        GlStateManager.pushMatrix();
        GlStateManager.translate(guiLeft, guiTop, 0F);
        GlStateManager.color(1F, 1F, 1F, 1F);

        // outer shadow and the light metal casing
        drawRect(0, 0, xSize, ySize, 0xFF101012);
        drawRect(2, 2, xSize - 2, ySize - 2, 0xFFC8C8C8);
        drawRect(3, 3, xSize - 3, 4, 0xFFECECEC);
        drawRect(3, 3, 4, ySize - 3, 0xFFECECEC);
        drawRect(3, ySize - 4, xSize - 3, ySize - 3, 0xFF8A8A8A);
        drawRect(xSize - 4, 3, xSize - 3, ySize - 3, 0xFF8A8A8A);

        // title strip
        drawRect(4, 4, xSize - 4, 20, 0xFFD4D4D4);
        drawRect(4, 19, xSize - 4, 20, 0xFF9A9A9A);

        // dark machine face and the player-inventory well below it
        drawRect(4, 20, xSize - 4, 138, 0xFF0B0B0D);
        drawRect(5, 21, xSize - 5, 137, 0xFF1B1B1E);
        drawRect(4, 140, xSize - 4, 228, 0xFF0B0B0D);
        drawRect(5, 141, xSize - 5, 227, 0xFF1B1B1E);

        if (page == 0) drawPageOne(mouseX, mouseY);
        else if (page == 1) drawPageTwo(mouseX, mouseY);
        else drawPageThree(mouseX, mouseY);

        drawSlotFrames();
        // Draw the ghosts in the same pass as the frames, directly over them. Keeping this out
        // of the foreground avoids other GUI overlays changing the blend/alpha state first.
        drawGhosts();
        GlStateManager.popMatrix();
    }

    /** The custom background has no baked slot art, so every visible slot gets its frame here. */
    private void drawSlotFrames() {
        for (Slot slot : this.inventorySlots.inventorySlots) {
            if (!isSlotVisible(slot)) continue;
            drawSlotFrame(slot.xPos, slot.yPos);
        }
    }

    private static void drawSlotFrame(int x, int y) {
        drawRect(x - 1, y - 1, x + 17, y + 17, 0xFFB8B8B8);
        drawRect(x, y, x + 16, y + 16, 0xFF343437);
        drawRect(x, y, x + 16, y + 1, 0xFF1F1F22);
    }

    private void drawPageOne(int mouseX, int mouseY) {
        drawRect(8, 59, 168, 60, 0xFF6A6A6E);
        drawRect(8, 60, 168, 61, 0xFF08080A);
        drawRect(8, 99, 168, 100, 0xFF6A6A6E);
        drawRect(8, 100, 168, 101, 0xFF08080A);

        drawArrow(34, 45, 88);
        drawArrow(34, 85, 88);

        drawButton(122, 38, 46, 16, mouseX, mouseY);
        drawButton(122, 78, 46, 16, mouseX, mouseY);
        drawPageButtons(mouseX, mouseY);
    }

    private void drawPageTwo(int mouseX, int mouseY) {
        drawRect(8, 62, 168, 63, 0xFF6A6A6E);
        drawRect(8, 63, 168, 64, 0xFF08080A);
        drawRect(8, 100, 168, 101, 0xFF6A6A6E);
        drawRect(8, 101, 168, 102, 0xFF08080A);
        drawPageButtons(mouseX, mouseY);
    }

    private void drawPageThree(int mouseX, int mouseY) {
        drawButton(ContainerKeycardProgrammer.BACKUP_BUTTON_X, ContainerKeycardProgrammer.BACKUP_BUTTON_Y,
                ContainerKeycardProgrammer.BACKUP_BUTTON_WIDTH, ContainerKeycardProgrammer.BACKUP_BUTTON_HEIGHT,
                mouseX, mouseY);
        drawBackupFrame(tile.getBackupFrame());
        drawPageButtons(mouseX, mouseY);
    }

    /** Draws one of the five supplied progress frames at the GUI bar size. */
    private void drawBackupFrame(int frame) {
        int index = Math.max(0, Math.min(BACKUP_FRAMES.length - 1, frame));
        mc.getTextureManager().bindTexture(BACKUP_FRAMES[index]);
        GlStateManager.color(1F, 1F, 1F, 1F);
        drawScaledCustomSizeModalRect(ContainerKeycardProgrammer.BACKUP_BAR_X,
                ContainerKeycardProgrammer.BACKUP_BAR_Y, 0F, 0F,
                BACKUP_FRAME_WIDTH, BACKUP_FRAME_HEIGHT,
                ContainerKeycardProgrammer.BACKUP_BAR_WIDTH,
                ContainerKeycardProgrammer.BACKUP_BAR_HEIGHT,
                BACKUP_FRAME_WIDTH, BACKUP_FRAME_HEIGHT);
        GlStateManager.color(1F, 1F, 1F, 1F);
    }

    private void drawPageButtons(int mouseX, int mouseY) {
        drawButton(10, 106, 18, 18, mouseX, mouseY);
        drawButton(148, 106, 18, 18, mouseX, mouseY);
    }

    // ------------------------------------------------------------ foreground

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRenderer.drawString("Keycard Programmer", 8, 7, DARK_TEXT);

        if (page == 0) {
            fontRenderer.drawString("Relay Key", 12, 27, COLOUR);
            // Same font size as the other labels; right-align it so the full name still fits.
            drawRightAligned("Operator Keycard", 168, 27, COLOUR);
            fontRenderer.drawString("Relay Key", 12, 67, COLOUR);
            fontRenderer.drawString("RFID Card", 94, 67, COLOUR);

            drawButtonLabel("Bind", 122, 38, 46, 16);
            drawButtonLabel("Bind", 122, 78, 46, 16);

            if (isHovering(mouseX, mouseY, 122, 38, 46, 16)
                    || isHovering(mouseX, mouseY, 122, 78, 46, 16)) {
                drawCenteredString(fontRenderer, "Binding logic coming soon", xSize / 2, 128, MUTED);
            }
        } else if (page == 1) {
            drawCenteredString(fontRenderer, "RFID Card", xSize / 2, 27, COLOUR);
        } else {
            drawCenteredString(fontRenderer, "Legacy Backup Programmer", xSize / 2, 27, COLOUR);
            drawButtonLabel("Backup", ContainerKeycardProgrammer.BACKUP_BUTTON_X,
                    ContainerKeycardProgrammer.BACKUP_BUTTON_Y,
                    ContainerKeycardProgrammer.BACKUP_BUTTON_WIDTH,
                    ContainerKeycardProgrammer.BACKUP_BUTTON_HEIGHT);
            drawCenteredString(fontRenderer, "Backing up Relay Keys and", xSize / 2, 80, MUTED);
            drawCenteredString(fontRenderer, "Operator Keycards keeps", xSize / 2, 90, MUTED);
            drawCenteredString(fontRenderer, "critical key data secure", xSize / 2, 100, MUTED);
        }

        drawCenteredString(fontRenderer, (page + 1) + "/" + PAGE_COUNT, xSize / 2, 111, COLOUR);
        drawPageArrows();

        if (statusTicks > 0) {
            drawCenteredString(fontRenderer, status, xSize / 2, 128, MUTED);
        }
    }

    /** Shows the item name/NBT when hovering a slot, and the expected item name on empty ghosts. */
    private void drawSlotTooltip(int mouseX, int mouseY) {
        Slot slot = findSlot(mouseX, mouseY);
        if (slot == null) return;

        if (slot.getHasStack()) {
            drawHoveringText(getItemToolTip(slot.getStack()), mouseX, mouseY);
            return;
        }

        String ghostName = ghostNameFor(slot.slotNumber);
        if (ghostName != null) {
            drawHoveringText(Collections.singletonList(ghostName), mouseX, mouseY);
        }
    }

    private Slot findSlot(int mouseX, int mouseY) {
        for (Slot slot : inventorySlots.inventorySlots) {
            if (!isSlotVisible(slot) || !slot.isEnabled()) continue;
            int x = guiLeft + slot.xPos;
            int y = guiTop + slot.yPos;
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                return slot;
            }
        }
        return null;
    }

    private static String ghostNameFor(int slot) {
        switch (slot) {
            case 0:
            case 2:
                return "Relay Key";
            case 1:
                return "Operator Keycard";
            case 3:
            case 4:
                return "RFID Card";
            default:
                return null;
        }
    }

    @Override
    protected void renderHoveredToolTip(int mouseX, int mouseY) {
        // Drawn from drawScreen after all overlays, so the vanilla pass is intentionally empty.
    }

    private void drawPageArrows() {
        int colour = 0xFFD8D8D8;
        // left-pointing triangle in the left footer button
        for (int i = 0; i < 5; i++) {
            drawRect(18 + i, 115 - i, 19 + i, 116 + i, colour);
        }
        // right-pointing triangle in the right footer button
        for (int i = 0; i < 5; i++) {
            drawRect(153 + i, 115 - (4 - i), 154 + i, 116 + (4 - i), colour);
        }
    }

    // ------------------------------------------------------------ slots

    /** Draws the grey "requested item" silhouettes into still-empty named slots. */
    private void drawGhosts() {
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);

        // The slot frames have already been drawn; lay the flat silhouettes directly over them.
        GlStateManager.disableDepth();
        GlStateManager.disableLighting();
        GlStateManager.disableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.colorMask(true, true, true, true);

        if (page == 0) {
            drawGhostIfEmpty(0, GhostIcons.RELAY,
                    ContainerKeycardProgrammer.SLOT_RELAY_ONE_X, ContainerKeycardProgrammer.SLOT_RELAY_ONE_Y);
            drawGhostIfEmpty(1, GhostIcons.OPERATOR,
                    ContainerKeycardProgrammer.SLOT_OPERATOR_X, ContainerKeycardProgrammer.SLOT_OPERATOR_Y);
            drawGhostIfEmpty(2, GhostIcons.RELAY,
                    ContainerKeycardProgrammer.SLOT_RELAY_TWO_X, ContainerKeycardProgrammer.SLOT_RELAY_TWO_Y);
            drawGhostIfEmpty(3, GhostIcons.RFID,
                    ContainerKeycardProgrammer.SLOT_RFID_ONE_X, ContainerKeycardProgrammer.SLOT_RFID_ONE_Y);
        } else if (page == 1) {
            drawGhostIfEmpty(4, GhostIcons.RFID,
                    ContainerKeycardProgrammer.SLOT_RFID_TWO_X, ContainerKeycardProgrammer.SLOT_RFID_TWO_Y);
        }

        // Gui.drawRect disables texture 2D; restore it for the rest of the GUI pass.
        GlStateManager.enableTexture2D();
        if (alpha) GlStateManager.enableAlpha();
        if (depth) GlStateManager.enableDepth();
        if (lighting) GlStateManager.enableLighting();
    }

    private void drawGhostIfEmpty(int index, int[] pixels, int x, int y) {
        if (index < 0 || index >= inventorySlots.inventorySlots.size()) return;
        if (inventorySlots.inventorySlots.get(index).getStack().isEmpty()) {
            drawGhostPixels(pixels, x, y);
        }
    }

    /** Draws the pre-greyed silhouette as flat rectangles, with no texture or item renderer. */
    private static void drawGhostPixels(int[] pixels, int x, int y) {
        for (int row = 0; row < 16; row++) {
            int runStart = -1;
            int runColour = 0;
            for (int col = 0; col < 16; col++) {
                int colour = pixels[row * 16 + col];
                if (colour == 0) {
                    if (runStart >= 0) {
                        drawRect(x + runStart, y + row, x + col, y + row + 1, runColour);
                        runStart = -1;
                    }
                } else if (runStart < 0) {
                    runStart = col;
                    runColour = colour;
                } else if (colour != runColour) {
                    drawRect(x + runStart, y + row, x + col, y + row + 1, runColour);
                    runStart = col;
                    runColour = colour;
                }
            }
            if (runStart >= 0) {
                drawRect(x + runStart, y + row, x + 16, y + row + 1, runColour);
            }
        }
    }

    private boolean isSlotVisible(Slot slot) {
        int index = slot.slotNumber;
        if (index >= 0 && index <= 3) return page == 0;
        if (index == 4) return page == 1;
        if (index >= ContainerKeycardProgrammer.COLOUR_SLOT_START
                && index < ContainerKeycardProgrammer.COLOUR_SLOT_START + ContainerKeycardProgrammer.COLOUR_SLOT_COUNT) {
            return page == 1;
        }
        if (index >= ContainerKeycardProgrammer.LEGACY_SLOT_START
                && index < ContainerKeycardProgrammer.LEGACY_SLOT_START + ContainerKeycardProgrammer.LEGACY_SLOT_COUNT) {
            return page == 2;
        }
        return true;
    }

    // ------------------------------------------------------------ input

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0) {
            int x = mouseX - guiLeft;
            int y = mouseY - guiTop;

            if (inRect(x, y, 10, 106, 28, 124)) {
                changePage(-1);
                return;
            }
            if (inRect(x, y, 148, 106, 166, 124)) {
                changePage(1);
                return;
            }

            if (page == 0 && (inRect(x, y, 122, 38, 168, 54) || inRect(x, y, 122, 78, 168, 94))) {
                status = "Binding logic coming soon";
                statusTicks = 80;
                playClick();
                return;
            }

            if (page == 2 && inRect(x, y,
                    ContainerKeycardProgrammer.BACKUP_BUTTON_X,
                    ContainerKeycardProgrammer.BACKUP_BUTTON_Y,
                    ContainerKeycardProgrammer.BACKUP_BUTTON_X + ContainerKeycardProgrammer.BACKUP_BUTTON_WIDTH,
                    ContainerKeycardProgrammer.BACKUP_BUTTON_Y + ContainerKeycardProgrammer.BACKUP_BUTTON_HEIGHT)) {
                if (tile.isBackingUp()) {
                    status = "Backup already running";
                    statusTicks = 60;
                } else {
                    TileEntityKeycardProgrammer.BackupFailure failure = tile.getBackupFailure();
                    if (failure != TileEntityKeycardProgrammer.BackupFailure.NONE) {
                        status = backupFailureStatus(failure);
                        statusTicks = 80;
                    } else {
                        status = "";
                        statusTicks = 0;
                        mc.playerController.windowClick(inventorySlots.windowId,
                                ContainerKeycardProgrammer.BACKUP_ACTION_SLOT, 0,
                                ClickType.PICKUP, mc.player);
                    }
                }
                playClick();
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void changePage(int delta) {
        page = (page + delta + PAGE_COUNT) % PAGE_COUNT;
        statusTicks = 0;
        updateSlotPositions();
        playClick();
    }

    private static String backupFailureStatus(TileEntityKeycardProgrammer.BackupFailure failure) {
        switch (failure) {
            case EMPTY:
                return "Insert a key in both slots";
            case INVALID_ITEM:
                return "Invalid backup item";
            case INVALID_DIRECTION:
                return "Invalid backup direction";
            case NO_DATA:
                return "Left item has no NBT";
            case TARGET_NOT_FRESH:
                return "Right item must be fresh";
            default:
                return "";
        }
    }

    private void playClick() {
        if (mc.player != null) {
            mc.player.playSound(ModSounds.BUTTON9, BUTTON_VOLUME, 1F);
        }
    }

    // ------------------------------------------------------------ drawing helpers

    private void drawButton(int x, int y, int width, int height, int mouseX, int mouseY) {
        boolean hover = isHovering(mouseX, mouseY, x, y, width, height);
        drawRect(x, y, x + width, y + height, hover ? 0xFF5C5C60 : 0xFF3E3E42);
        drawRect(x, y, x + width, y + 1, 0xFFB4B4B4);
        drawRect(x, y, x + 1, y + height, 0xFFB4B4B4);
        drawRect(x, y + height - 1, x + width, y + height, 0xFF151517);
        drawRect(x + width - 1, y, x + width, y + height, 0xFF151517);
    }

    private boolean isHovering(int mouseX, int mouseY, int x, int y, int width, int height) {
        int mx = mouseX - guiLeft;
        int my = mouseY - guiTop;
        return mx >= x && mx < x + width && my >= y && my < y + height;
    }

    private static boolean inRect(int x, int y, int left, int top, int right, int bottom) {
        return x >= left && x < right && y >= top && y < bottom;
    }

    private void drawButtonLabel(String text, int x, int y, int width, int height) {
        drawCenteredString(fontRenderer, text, x + width / 2, y + (height - fontRenderer.FONT_HEIGHT) / 2, COLOUR);
    }

    private void drawRightAligned(String text, int right, int y, int colour) {
        fontRenderer.drawString(text, right - fontRenderer.getStringWidth(text), y, colour);
    }

    private static void drawArrow(int x1, int y, int x2) {
        int colour = 0xFFB4B4B4;
        drawRect(x1, y, x2 - 5, y + 1, colour);
        drawRect(x2 - 6, y - 3, x2 - 5, y + 4, colour);
        drawRect(x2 - 5, y - 2, x2 - 4, y + 3, colour);
        drawRect(x2 - 4, y - 1, x2 - 3, y + 2, colour);
        drawRect(x2 - 3, y, x2 - 2, y + 1, colour);
    }
}
