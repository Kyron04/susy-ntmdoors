package com.ntmdoors.client.screen;

import com.ntmdoors.network.ModMessages;
import com.ntmdoors.screen.SafeScreenHandler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public class SafeScreen extends HandledScreen<SafeScreenHandler> {

    public static final int PAGE_COUNT = 3;
    private static final int COLOUR = 0xFFE8E8E8;
    private static final int MUTED = 0xFFAFAFAF;
    private static final int DIM = 0xFF8A8A8A;
    private static final int HOVER = 0xFF232326;
    private static final int RULE = 0xFF4A4A4E;
    private static final int RED_PANEL = 0xFF1A0B0D;
    private static final int RED_INNER = 0xFF2A1013;
    private static final int RED_RULE = 0xFF6A2A2A;
    private static final int RED_HOVER = 0xFF3A1418;

    private static final int OPTION_TOP = 36;
    private static final int OPTIONS_HEIGHT = 21;
    private static final int PILL_WIDTH = 54;
    private static final int PILL_HEIGHT = 14;
    private static final int ARROW_SIZE = 16;
    private static final int ARROW_Y = 212;

    public SafeScreen(SafeScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 232;
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int left = this.x;
        int top = this.y;
        int page = handler.getPage();

        // Main bezel and body
        context.fill(left - 1, top - 1, left + backgroundWidth + 1, top + backgroundHeight + 1, COLOUR);
        context.fill(left, top, left + backgroundWidth, top + backgroundHeight, 0xFF111111);
        context.fill(left + 1, top + 1, left + backgroundWidth - 1, top + backgroundHeight - 1, 0xFF2B2B2E);
        context.fill(left + 1, top + 1, left + backgroundWidth - 1, top + 22, 0xFF232326);
        context.fill(left + 1, top + 22, left + backgroundWidth - 1, top + 23, RULE);
        context.fill(left + 1, top + 23, left + backgroundWidth - 1, top + 208, page == 2 ? RED_PANEL : 0xFF1B1B1E);
        context.fill(left + 1, top + 1, left + backgroundWidth - 1, top + 2, 0xFF3C3C40);

        if (page == 0) {
            drawStorageSlots(context, left, top);
        } else if (page == 1) {
            drawOptionsPage(context, left, top, mouseX, mouseY);
            drawPlayerSlots(context, left, top);
        } else if (page == 2) {
            drawRedstonePage(context, left, top, mouseX, mouseY);
        }

        drawFooter(context, left, top, mouseX, mouseY);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        int page = handler.getPage();
        String titleText = handler.isBiometric() ? "BIOMETRIC SAFE" : "SAFE";
        context.drawText(textRenderer, titleText, 12, 10, COLOUR, false);

        String pageTitle = page == 0 ? "CONTENTS" : page == 1 ? "OPTIONS" : "REDSTONE";
        context.drawText(textRenderer, pageTitle, backgroundWidth - 12 - textRenderer.getWidth(pageTitle), 10, MUTED, false);
    }

    private void drawStorageSlots(DrawContext context, int left, int top) {
        // Safe 3x9 grid
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlot(context, left + 8 + col * 18, top + 36 + row * 18);
            }
        }
        drawPlayerSlots(context, left, top);
    }

    private void drawPlayerSlots(DrawContext context, int left, int top) {
        // Player inventory 3x9
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlot(context, left + 8 + col * 18, top + 128 + row * 18);
            }
        }
        // Hotbar 1x9
        for (int col = 0; col < 9; col++) {
            drawSlot(context, left + 8 + col * 18, top + 186);
        }
    }

    private void drawSlot(DrawContext context, int x, int y) {
        context.fill(x - 1, y - 1, x + 17, y + 17, 0xFF080808);
        context.fill(x, y, x + 16, y + 16, 0xFF141416);
    }

    private void drawOptionsPage(DrawContext context, int left, int top, int mouseX, int mouseY) {
        context.drawText(textRenderer, "SETTINGS", left + 12, top + 26, MUTED, false);
        context.fill(left + 12, top + 34, left + backgroundWidth - 12, top + 35, RULE);

        byte[] options = handler.isBiometric()
                ? new byte[]{4, 0, 1, 3} // MATCH_HOLDER, BINDING, LOCKOUT, VOID
                : new byte[]{0, 1, 2, 3}; // BINDING, LOCKOUT, RESET, VOID

        for (int i = 0; i < options.length; i++) {
            byte opt = options[i];
            int y = top + OPTION_TOP + i * OPTIONS_HEIGHT;
            boolean hovered = mouseX >= left + 8 && mouseX < left + backgroundWidth - 8 && mouseY >= y && mouseY < y + OPTIONS_HEIGHT;
            if (hovered) {
                context.fill(left + 8, y, left + backgroundWidth - 8, y + OPTIONS_HEIGHT, HOVER);
            }

            String label = getOptionLabel(opt);
            String hint = getOptionHint(opt);
            boolean on = getOptionValue(opt);

            context.drawText(textRenderer, label, left + 14, y + 2, COLOUR, false);
            context.drawText(textRenderer, hint, left + 14, y + 11, MUTED, false);

            drawPill(context, left + backgroundWidth - 12 - PILL_WIDTH, y + 3, opt == 2 ? "RESET" : (on ? "ON" : "OFF"), on, opt == 2);
        }
    }

    private void drawRedstonePage(DrawContext context, int left, int top, int mouseX, int mouseY) {
        context.drawText(textRenderer, "REDSTONE TRIGGERS", left + 12, top + 26, 0xFFF0C0C0, false);
        context.fill(left + 12, top + 34, left + backgroundWidth - 12, top + 35, RED_RULE);

        byte[] events = handler.isBiometric()
                ? new byte[]{5 + 4, 5 + 5, 5 + 6, 5 + 7, 5 + 3} // SCAN_FAIL, SCAN_SUCCESS, MATCH_FAIL, MATCH_SUCCESS, LOCKOUT
                : new byte[]{5 + 0, 5 + 1, 5 + 2, 5 + 3}; // CODE_FAIL, CODE_SUCCESS, BINDING, LOCKOUT

        for (int i = 0; i < events.length; i++) {
            byte opt = events[i];
            int y = top + OPTION_TOP + i * 32;
            boolean hovered = mouseX >= left + 8 && mouseX < left + backgroundWidth - 8 && mouseY >= y && mouseY < y + 32;
            if (hovered) {
                context.fill(left + 8, y, left + backgroundWidth - 8, y + 32, RED_HOVER);
            }

            String label = getOptionLabel(opt);
            String hint = getOptionHint(opt);
            boolean on = getOptionValue(opt);

            context.drawText(textRenderer, label, left + 14, y + 4, 0xFFE6C8C8, false);
            context.drawText(textRenderer, hint, left + 14, y + 15, MUTED, false);

            drawPill(context, left + backgroundWidth - 12 - PILL_WIDTH, y + 8, on ? "ON" : "OFF", on, false);
        }
    }

    private void drawPill(DrawContext context, int px, int py, String label, boolean on, boolean reset) {
        int fill = reset ? 0xFF3A3030 : on ? 0xFF344434 : 0xFF3A3030;
        int edge = reset ? 0xFF8A6A6A : on ? 0xFF7FA87F : 0xFF8A6A6A;
        int text = reset ? 0xFFE6C8C8 : on ? 0xFFD6F0D6 : 0xFFE6C8C8;
        context.fill(px, py, px + PILL_WIDTH, py + PILL_HEIGHT, fill);
        context.fill(px, py, px + PILL_WIDTH, py + 1, edge);
        context.fill(px, py + PILL_HEIGHT - 1, px + PILL_WIDTH, py + PILL_HEIGHT, on ? 0xFF223022 : 0xFF241C1C);
        context.drawCenteredTextWithShadow(textRenderer, label, px + PILL_WIDTH / 2, py + 3, text);
    }

    private void drawFooter(DrawContext context, int left, int top, int mouseX, int mouseY) {
        context.fill(left + 12, top + 209, left + backgroundWidth - 12, top + 210, RULE);

        // Left Arrow
        boolean leftHovered = mouseX >= left + 8 && mouseX < left + 8 + ARROW_SIZE && mouseY >= top + ARROW_Y && mouseY < top + ARROW_Y + ARROW_SIZE;
        context.fill(left + 8, top + ARROW_Y, left + 8 + ARROW_SIZE, top + ARROW_Y + ARROW_SIZE, leftHovered ? HOVER : 0xFF232326);
        context.drawText(textRenderer, "<", left + 13, top + ARROW_Y + 4, COLOUR, false);

        // Right Arrow
        int rx = left + backgroundWidth - 8 - ARROW_SIZE;
        boolean rightHovered = mouseX >= rx && mouseX < rx + ARROW_SIZE && mouseY >= top + ARROW_Y && mouseY < top + ARROW_Y + ARROW_SIZE;
        context.fill(rx, top + ARROW_Y, rx + ARROW_SIZE, top + ARROW_Y + ARROW_SIZE, rightHovered ? HOVER : 0xFF232326);
        context.drawText(textRenderer, ">", rx + 5, top + ARROW_Y + 4, COLOUR, false);

        context.drawCenteredTextWithShadow(textRenderer, (handler.getPage() + 1) + "/" + PAGE_COUNT, left + backgroundWidth / 2, top + ARROW_Y + 4, DIM);
    }

    private String getOptionLabel(byte option) {
        return switch (option) {
            case 0 -> "Binding Activities";
            case 1 -> "Lockout Mode";
            case 2 -> "Reset Code";
            case 3 -> "Void on Destroy";
            case 4 -> "Match Holder";
            case 5 + 0 -> "Code Failed";
            case 5 + 1 -> "Code Accepted";
            case 5 + 2 -> "Binding Action";
            case 5 + 3 -> "Lockout Triggered";
            case 5 + 4 -> "Scan Failed";
            case 5 + 5 -> "Scan Accepted";
            case 5 + 6 -> "Match Failed";
            case 5 + 7 -> "Match Accepted";
            default -> "Option";
        };
    }

    private String getOptionHint(byte option) {
        return switch (option) {
            case 0 -> "Closes bound doors on lockout.";
            case 1 -> "5-min lockout after 3 fails.";
            case 2 -> "Clears safe PIN.";
            case 3 -> "Suppresses item drops.";
            case 4 -> "Checks player matches card.";
            case 5 + 0 -> "Wrong PIN entered.";
            case 5 + 1 -> "Valid PIN entered.";
            case 5 + 2 -> "Door bound or unbound.";
            case 5 + 3 -> "Lockout starts.";
            case 5 + 4 -> "Card or person refused.";
            case 5 + 5 -> "Valid biometric scan.";
            case 5 + 6 -> "Wrong card holder.";
            case 5 + 7 -> "Holder verified.";
            default -> "";
        };
    }

    private boolean getOptionValue(byte option) {
        return switch (option) {
            case 0 -> handler.isBindingActivities();
            case 1 -> handler.isLockoutMode();
            case 2 -> false;
            case 3 -> handler.isVoidOnDestroy();
            case 4 -> handler.isMatchHolder();
            default -> {
                int event = option - 5;
                yield handler.isRedstoneEvent(event);
            }
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int left = this.x;
            int top = this.y;

            // Check Left Arrow
            if (mouseX >= left + 8 && mouseX < left + 8 + ARROW_SIZE && mouseY >= top + ARROW_Y && mouseY < top + ARROW_Y + ARROW_SIZE) {
                int prev = (handler.getPage() + PAGE_COUNT - 1) % PAGE_COUNT;
                handler.setPage(prev);
                sendPage((byte) prev);
                return true;
            }

            // Check Right Arrow
            int rx = left + backgroundWidth - 8 - ARROW_SIZE;
            if (mouseX >= rx && mouseX < rx + ARROW_SIZE && mouseY >= top + ARROW_Y && mouseY < top + ARROW_Y + ARROW_SIZE) {
                int next = (handler.getPage() + 1) % PAGE_COUNT;
                handler.setPage(next);
                sendPage((byte) next);
                return true;
            }

            int page = handler.getPage();
            if (page == 1) {
                byte[] options = handler.isBiometric() ? new byte[]{4, 0, 1, 3} : new byte[]{0, 1, 2, 3};
                for (int i = 0; i < options.length; i++) {
                    int y = top + OPTION_TOP + i * OPTIONS_HEIGHT;
                    if (mouseX >= left + 8 && mouseX < left + backgroundWidth - 8 && mouseY >= y && mouseY < y + OPTIONS_HEIGHT) {
                        byte opt = options[i];
                        boolean current = getOptionValue(opt);
                        sendOption(opt, !current);
                        return true;
                    }
                }
            } else if (page == 2) {
                byte[] events = handler.isBiometric() ? new byte[]{5 + 4, 5 + 5, 5 + 6, 5 + 7, 5 + 3} : new byte[]{5 + 0, 5 + 1, 5 + 2, 5 + 3};
                for (int i = 0; i < events.length; i++) {
                    int y = top + OPTION_TOP + i * 32;
                    if (mouseX >= left + 8 && mouseX < left + backgroundWidth - 8 && mouseY >= y && mouseY < y + 32) {
                        byte opt = events[i];
                        boolean current = getOptionValue(opt);
                        sendOption(opt, !current);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void sendPage(byte page) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(handler.getPos());
        buf.writeByte(page);
        ClientPlayNetworking.send(ModMessages.SAFE_PAGE_ID, buf);
    }

    private void sendOption(byte option, boolean value) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(handler.getPos());
        buf.writeByte(option);
        buf.writeBoolean(value);
        ClientPlayNetworking.send(ModMessages.SAFE_OPTION_ID, buf);
    }
}
