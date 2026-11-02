package com.ntmdoors.client;

import com.ntmdoors.gui.ContainerSafe;
import com.ntmdoors.network.MessageSafeOption;
import com.ntmdoors.network.MessageSafePage;
import com.ntmdoors.network.ModNetwork;
import com.ntmdoors.tile.TileEntitySafe;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Mouse;

import java.io.IOException;

/**
 * Safe screen. Page one is the 3x9 storage grid, page two holds the variant's normal options, and
 * page three is the red-tinted Redstone event page. The page arrows live in the footer, and hidden
 * slots are parked off-screen so they cannot be hovered or clicked.
 */
@SideOnly(Side.CLIENT)
public class GuiSafe extends GuiContainer {

    private static final int PAGE_COUNT = 3;
    private static final int COLOUR = 0xFFE8E8E8;
    private static final int MUTED = 0xFFAFAFAF;
    private static final int DIM = 0xFF8A8A8A;
    private static final int HOVER = 0xFF232326;
    private static final int RULE = 0xFF4A4A4E;
    private static final int DARK_TEXT = 0xFF2B2B2E;
    private static final int RED_PANEL = 0xFF1A0B0D;
    private static final int RED_INNER = 0xFF2A1013;
    private static final int RED_RULE = 0xFF6A2A2A;
    private static final int RED_HOVER = 0xFF3A1418;

    private static final int OPTION_TOP = 36;
    private static final int OPTION_HEIGHT = 27;
    private static final int REDSTONE_AREA = 180;
    private static final int PILL_WIDTH = 54;
    private static final int PILL_HEIGHT = 14;
    private static final int ARROW_SIZE = 16;
    private static final int ARROW_Y = ContainerSafe.GUI_HEIGHT - 20;

    /** The code safe has no owner restriction; the biometric safe keeps its holder check. */
    private static final int[] CODE_OPTIONS = {
            MessageSafeOption.BINDING_ACTIVITIES,
            MessageSafeOption.LOCKOUT,
            MessageSafeOption.RESET_CODE
    };
    private static final int[] BIOMETRIC_OPTIONS = {
            MessageSafeOption.MATCH_HOLDER,
            MessageSafeOption.BINDING_ACTIVITIES,
            MessageSafeOption.LOCKOUT
    };
    private static final int[] CODE_REDSTONE = {
            MessageSafeOption.REDSTONE_CODE_FAIL,
            MessageSafeOption.REDSTONE_CODE_SUCCESS,
            MessageSafeOption.REDSTONE_BINDING,
            MessageSafeOption.REDSTONE_LOCKOUT
    };
    private static final int[] BIOMETRIC_REDSTONE = {
            MessageSafeOption.REDSTONE_SCAN_FAIL,
            MessageSafeOption.REDSTONE_SCAN_SUCCESS,
            MessageSafeOption.REDSTONE_MATCH_FAIL,
            MessageSafeOption.REDSTONE_MATCH_SUCCESS,
            MessageSafeOption.REDSTONE_LOCKOUT
    };

    private final TileEntitySafe tile;
    private final ContainerSafe safeContainer;
    private final int[] originalX;
    private final int[] originalY;

    private int page;
    private int scrollRedstone;
    private int lastMouseX;
    private int lastMouseY;

    public GuiSafe(InventoryPlayer playerInventory, TileEntitySafe tile) {
        super(new ContainerSafe(playerInventory, tile));
        this.tile = tile;
        this.safeContainer = (ContainerSafe) this.inventorySlots;
        this.xSize = ContainerSafe.GUI_WIDTH;
        this.ySize = ContainerSafe.GUI_HEIGHT;

        int count = this.inventorySlots.inventorySlots.size();
        this.originalX = new int[count];
        this.originalY = new int[count];
        for (int i = 0; i < count; i++) {
            this.originalX[i] = this.inventorySlots.inventorySlots.get(i).xPos;
            this.originalY[i] = this.inventorySlots.inventorySlots.get(i).yPos;
        }
        updateSlotPositions();
    }

    /** Page two hides the storage grid; page three hides the whole inventory. */
    private void updateSlotPositions() {
        for (Slot slot : inventorySlots.inventorySlots) {
            int index = slot.slotNumber;
            if (index < 0 || index >= originalX.length) continue;
            boolean hide = page == 2 || (page == 1 && index < ContainerSafe.SAFE_SLOT_COUNT);
            if (hide) {
                slot.xPos = -10000 - index;
                slot.yPos = -10000;
            } else {
                slot.xPos = originalX[index];
                slot.yPos = originalY[index];
            }
        }
    }

    // ------------------------------------------------------------ input

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0) {
            int localX = mouseX - guiLeft;
            int localY = mouseY - guiTop;
            if (inArrow(localX, localY, false)) {
                changePage(-1);
                return;
            }
            if (inArrow(localX, localY, true)) {
                changePage(1);
                return;
            }
            if (page == 1 || page == 2) {
                int option = optionAt(localX, localY);
                if (option >= 0) {
                    sendOption(option);
                    return;
                }
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0 || page != 2) return;
        int x = lastMouseX - guiLeft;
        int y = lastMouseY - guiTop;
        if (x < 6 || x >= xSize - 6 || y < OPTION_TOP || y >= OPTION_TOP + REDSTONE_AREA) return;
        scrollRedstone = clampRedstoneScroll(scrollRedstone + (wheel > 0 ? -OPTION_HEIGHT : OPTION_HEIGHT));
    }

    @Override
    protected void handleMouseClick(Slot slot, int slotId, int mouseButton, ClickType type) {
        if (page == 2) return;
        if (page != 0 && slot != null && slot.slotNumber < ContainerSafe.SAFE_SLOT_COUNT) return;
        super.handleMouseClick(slot, slotId, mouseButton, type);
    }

    private void sendOption(int option) {
        boolean value;
        int event = redstoneEvent(option);
        if (event >= 0) {
            value = !tile.isRedstoneEvent(event);
        } else if (option == MessageSafeOption.MATCH_HOLDER) {
            value = !tile.isMatchHolder();
        } else if (option == MessageSafeOption.LOCKOUT) {
            value = !tile.isLockoutMode();
        } else if (option == MessageSafeOption.BINDING_ACTIVITIES) {
            value = !tile.isBindingActivities();
        } else {
            value = true;
        }
        ModNetwork.INSTANCE.sendToServer(
                new MessageSafeOption(tile.getPos(), option, value));
    }

    private static int redstoneEvent(int option) {
        switch (option) {
            case MessageSafeOption.REDSTONE_CODE_FAIL: return TileEntitySafe.RS_CODE_FAIL;
            case MessageSafeOption.REDSTONE_CODE_SUCCESS: return TileEntitySafe.RS_CODE_SUCCESS;
            case MessageSafeOption.REDSTONE_BINDING: return TileEntitySafe.RS_BINDING;
            case MessageSafeOption.REDSTONE_LOCKOUT: return TileEntitySafe.RS_LOCKOUT;
            case MessageSafeOption.REDSTONE_SCAN_FAIL: return TileEntitySafe.RS_SCAN_FAIL;
            case MessageSafeOption.REDSTONE_SCAN_SUCCESS: return TileEntitySafe.RS_SCAN_SUCCESS;
            case MessageSafeOption.REDSTONE_MATCH_FAIL: return TileEntitySafe.RS_MATCH_FAIL;
            case MessageSafeOption.REDSTONE_MATCH_SUCCESS: return TileEntitySafe.RS_MATCH_SUCCESS;
            default: return -1;
        }
    }

    private static int clampRedstoneScroll(int value) {
        int max = Math.max(0, maxRedstoneRows() * OPTION_HEIGHT - REDSTONE_AREA);
        return Math.max(0, Math.min(max, value));
    }

    private static int maxRedstoneRows() {
        return 5;
    }

    private void changePage(int delta) {
        page = (page + delta + PAGE_COUNT) % PAGE_COUNT;
        safeContainer.setPage(page);
        updateSlotPositions();
        ModNetwork.INSTANCE.sendToServer(new MessageSafePage(tile.getPos(), page));
    }

    private boolean inArrow(int mouseX, int mouseY, boolean right) {
        int x = right ? xSize - 8 - ARROW_SIZE : 8;
        return mouseX >= x && mouseX < x + ARROW_SIZE
                && mouseY >= ARROW_Y && mouseY < ARROW_Y + ARROW_SIZE;
    }

    private int optionAt(int mouseX, int mouseY) {
        int[] options = options();
        int scroll = page == 2 ? scrollRedstone : 0;
        for (int i = 0; i < options.length; i++) {
            int y = OPTION_TOP + i * OPTION_HEIGHT - scroll;
            if (mouseX >= 6 && mouseX < xSize - 6 && mouseY >= y - 2 && mouseY < y + OPTION_HEIGHT - 4) {
                return options[i];
            }
        }
        return -1;
    }

    private int[] options() {
        if (page == 2) return tile.isBiometric() ? BIOMETRIC_REDSTONE : CODE_REDSTONE;
        return tile.isBiometric() ? BIOMETRIC_OPTIONS : CODE_OPTIONS;
    }

    // ------------------------------------------------------------ background

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        // Draw the full item tooltip last, on top of JEI/HEI, exactly like the programmer GUI.
        drawSlotTooltip(mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GuiContainerStyle.resetGuiState();

        GlStateManager.pushMatrix();
        GlStateManager.translate(guiLeft, guiTop, 0F);
        GlStateManager.color(1F, 1F, 1F, 1F);

        GuiContainerStyle.drawCasing(xSize, ySize);

        if (page == 2) {
            // The Redstone page uses the full panel; there is no player inventory on it.
            drawRect(4, 20, xSize - 4, 212, RED_PANEL);
            drawRect(5, 21, xSize - 5, 211, RED_INNER);
        } else {
            // dark storage/options face and the player-inventory well below it
            drawRect(4, 20, xSize - 4, 120, 0xFF0B0B0D);
            drawRect(5, 21, xSize - 5, 119, 0xFF1B1B1E);
            drawRect(4, 122, xSize - 4, 212, 0xFF0B0B0D);
            drawRect(5, 123, xSize - 5, 211, 0xFF1B1B1E);
        }

        // footer well holding the page arrows
        drawRect(4, 212, xSize - 4, 228, 0xFF2B2B2E);
        drawRect(4, 212, xSize - 4, 213, page == 2 ? RED_RULE : RULE);

        drawSlotFrames();
        GlStateManager.popMatrix();
    }

    private void drawSlotFrames() {
        if (page == 2) return;
        for (Slot slot : inventorySlots.inventorySlots) {
            if (slot.slotNumber < ContainerSafe.SAFE_SLOT_COUNT && page != 0) continue;
            GuiContainerStyle.drawSlotFrame(slot.xPos, slot.yPos);
        }
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

        String title = tile.isBiometric() ? "Biometric Safe" : "Safe";
        fontRenderer.drawString(title, 8, 7, DARK_TEXT);
        String right = page == 2 ? "REDSTONE"
                : page == 1 && tile.isLockedOut() ? "LOCKED OUT" : "27 slots";
        GuiContainerStyle.drawRightAligned(fontRenderer, right, xSize - 8, 7,
                page == 1 && tile.isLockedOut() ? 0xFF8A3A3A : 0xFF5A5A5A);

        if (page == 0) {
            drawString(fontRenderer, "CONTENTS", 8, 24, COLOUR);
        } else if (page == 1) {
            drawString(fontRenderer, "OPTIONS", 8, 24, COLOUR);
            drawOptions(mouseX - guiLeft, mouseY - guiTop);
        } else {
            drawString(fontRenderer, "REDSTONE", 8, 24, 0xFFF0C0C0);
            drawRedstoneOptions(mouseX - guiLeft, mouseY - guiTop);
        }

        drawFooter(mouseX - guiLeft, mouseY - guiTop);
        GlStateManager.popAttrib();
    }

    private void drawOptions(int mouseX, int mouseY) {
        int[] options = options();
        for (int i = 0; i < options.length; i++) {
            int option = options[i];
            int y = OPTION_TOP + i * OPTION_HEIGHT;
            boolean hovered = isOptionHovered(i, mouseX, mouseY);
            if (hovered) {
                drawRect(6, y - 2, xSize - 6, y + OPTION_HEIGHT - 4, HOVER);
            }

            drawString(fontRenderer, optionLabel(option), 12, y + 1, COLOUR);
            drawString(fontRenderer, optionHint(option), 12, y + 11, MUTED);
            drawOptionPill(option, y);
        }
    }

    private boolean isOptionHovered(int row, int mouseX, int mouseY) {
        int y = OPTION_TOP + row * OPTION_HEIGHT;
        return mouseX >= 6 && mouseX < xSize - 6
                && mouseY >= y - 2 && mouseY < y + OPTION_HEIGHT - 4;
    }

    private void drawRedstoneOptions(int mouseX, int mouseY) {
        int[] options = options();
        scrollRedstone = clampRedstoneScroll(scrollRedstone);
        GuiClip.begin(guiLeft + 6, guiTop + OPTION_TOP - 2, xSize - 12, REDSTONE_AREA);
        for (int i = 0; i < options.length; i++) {
            int option = options[i];
            int y = OPTION_TOP + i * OPTION_HEIGHT - scrollRedstone;
            boolean hovered = mouseX >= 6 && mouseX < xSize - 6
                    && mouseY >= y - 2 && mouseY < y + OPTION_HEIGHT - 4;
            if (hovered) drawRect(6, y - 2, xSize - 6, y + OPTION_HEIGHT - 4, RED_HOVER);

            drawString(fontRenderer, optionLabel(option), 12, y + 1, COLOUR);
            drawString(fontRenderer, optionHint(option), 12, y + 11, MUTED);
            drawOptionPill(option, y);
        }
        GuiClip.end();
        drawScrollbar(xSize - 8, OPTION_TOP, REDSTONE_AREA,
                options.length, OPTION_HEIGHT, scrollRedstone);
    }

    private void drawScrollbar(int x, int y, int height, int count, int rowHeight, int scroll) {
        int max = Math.max(0, count * rowHeight - height);
        if (max <= 0) return;
        drawRect(x, y, x + 3, y + height, 0xFF1B0B0D);
        int thumb = Math.max(14, height * height / (count * rowHeight));
        int travel = height - thumb;
        int offset = (int) ((long) scroll * travel / max);
        drawRect(x, y + offset, x + 3, y + offset + thumb, 0xFFB46A6A);
    }

    private String optionLabel(int option) {
        switch (option) {
            case MessageSafeOption.MATCH_HOLDER:
                return "Match user";
            case MessageSafeOption.LOCKOUT:
                return "Lockout mode";
            case MessageSafeOption.BINDING_ACTIVITIES:
                return "Binding Activities";
            case MessageSafeOption.RESET_CODE:
                return "Reset code";
            case MessageSafeOption.REDSTONE_CODE_FAIL:
                return "Code fail";
            case MessageSafeOption.REDSTONE_CODE_SUCCESS:
                return "Code success";
            case MessageSafeOption.REDSTONE_BINDING:
                return "Binding activities";
            case MessageSafeOption.REDSTONE_LOCKOUT:
                return "Lockout";
            case MessageSafeOption.REDSTONE_SCAN_FAIL:
                return "Scan fail";
            case MessageSafeOption.REDSTONE_SCAN_SUCCESS:
                return "Scan success";
            case MessageSafeOption.REDSTONE_MATCH_FAIL:
                return "Match holder fail";
            case MessageSafeOption.REDSTONE_MATCH_SUCCESS:
                return "Match holder success";
            default:
                return "";
        }
    }

    private String optionHint(int option) {
        switch (option) {
            case MessageSafeOption.MATCH_HOLDER:
                return "Owner's card.";
            case MessageSafeOption.LOCKOUT:
                return "3 fails = 5 min.";
            case MessageSafeOption.BINDING_ACTIVITIES:
                return "Linking + lockout.";
            case MessageSafeOption.RESET_CODE:
                return "Clears the code.";
            case MessageSafeOption.REDSTONE_CODE_FAIL:
                return "Wrong code.";
            case MessageSafeOption.REDSTONE_CODE_SUCCESS:
                return "Correct code.";
            case MessageSafeOption.REDSTONE_BINDING:
                return "Door or gate link.";
            case MessageSafeOption.REDSTONE_LOCKOUT:
                return "Lockout starts.";
            case MessageSafeOption.REDSTONE_SCAN_FAIL:
                return "Card or person refused.";
            case MessageSafeOption.REDSTONE_SCAN_SUCCESS:
                return "Access granted.";
            case MessageSafeOption.REDSTONE_MATCH_FAIL:
                return "Wrong holder.";
            case MessageSafeOption.REDSTONE_MATCH_SUCCESS:
                return "Holder matched.";
            default:
                return "";
        }
    }

    private void drawOptionPill(int option, int y) {
        boolean reset = option == MessageSafeOption.RESET_CODE;
        boolean on = false;
        if (option == MessageSafeOption.MATCH_HOLDER) {
            on = tile.isMatchHolder();
        } else if (option == MessageSafeOption.LOCKOUT) {
            on = tile.isLockoutMode();
        } else if (option == MessageSafeOption.BINDING_ACTIVITIES) {
            on = tile.isBindingActivities();
        } else {
            int event = redstoneEvent(option);
            if (event >= 0) on = tile.isRedstoneEvent(event);
        }

        int fill;
        int edge;
        int text;
        if (reset) {
            fill = 0xFF3A3030;
            edge = 0xFF8A6A6A;
            text = 0xFFE6C8C8;
        } else if (on) {
            fill = 0xFF344434;
            edge = 0xFF7FA87F;
            text = 0xFFD6F0D6;
        } else {
            fill = 0xFF3A3030;
            edge = 0xFF8A6A6A;
            text = 0xFFE6C8C8;
        }

        int x = xSize - 12 - PILL_WIDTH;
        int pillY = y + 4;
        drawRect(x, pillY, x + PILL_WIDTH, pillY + PILL_HEIGHT, fill);
        drawRect(x, pillY, x + PILL_WIDTH, pillY + 1, edge);
        drawRect(x, pillY + PILL_HEIGHT - 1, x + PILL_WIDTH, pillY + PILL_HEIGHT,
                reset ? 0xFF241C1C : on ? 0xFF223022 : 0xFF241C1C);
        String state = reset ? "RESET" : on ? "ON" : "OFF";
        drawCenteredString(fontRenderer, state, x + PILL_WIDTH / 2, pillY + 3, text);
    }

    private void drawFooter(int mouseX, int mouseY) {
        drawRect(12, 209, xSize - 12, 210, RULE);
        drawArrow(8, ARROW_Y, false, mouseX, mouseY);
        drawArrow(xSize - 8 - ARROW_SIZE, ARROW_Y, true, mouseX, mouseY);
        drawCenteredString(fontRenderer, (page + 1) + "/" + PAGE_COUNT,
                xSize / 2, ARROW_Y + 4, DIM);
    }

    private void drawArrow(int x, int y, boolean right, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + ARROW_SIZE
                && mouseY >= y && mouseY < y + ARROW_SIZE;
        drawRect(x, y, x + ARROW_SIZE, y + ARROW_SIZE, hovered ? HOVER : 0xFF232326);
        drawRect(x, y, x + ARROW_SIZE, y + 1, RULE);
        drawRect(x, y + ARROW_SIZE - 1, x + ARROW_SIZE, y + ARROW_SIZE, 0xFF151517);

        int centreX = x + ARROW_SIZE / 2;
        int centreY = y + ARROW_SIZE / 2;
        for (int i = 0; i < 5; i++) {
            int dx = right ? 3 - i : i - 3;
            drawRect(centreX + dx, centreY - i, centreX + dx + 1, centreY + 1 + i, COLOUR);
        }
    }

    // ------------------------------------------------------------ tooltips / GL

    /** Vanilla's own hovered-slot pass stays empty so the tooltip is only ever drawn once. */
    @Override
    protected void renderHoveredToolTip(int mouseX, int mouseY) {
    }

    /** Draws the full stack tooltip, including every NBT line, after the GUI and overlays. */
    private void drawSlotTooltip(int mouseX, int mouseY) {
        Slot slot = findSlot(mouseX, mouseY);
        if (slot == null) return;

        ItemStack stack = slot.getStack();
        if (stack.isEmpty()) return;

        this.renderToolTip(stack, mouseX, mouseY);
        GuiContainerStyle.resetGuiState();
    }

    private Slot findSlot(int mouseX, int mouseY) {
        if (page == 2) return null;
        for (Slot slot : inventorySlots.inventorySlots) {
            if (!slot.isEnabled()) continue;
            if (slot.slotNumber < ContainerSafe.SAFE_SLOT_COUNT && page != 0) continue;
            int x = guiLeft + slot.xPos;
            int y = guiTop + slot.yPos;
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                return slot;
            }
        }
        return null;
    }
}
