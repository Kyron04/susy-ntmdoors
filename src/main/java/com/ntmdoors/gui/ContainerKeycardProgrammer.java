package com.ntmdoors.gui;

import com.ntmdoors.item.ItemCreativeKeycard;
import com.ntmdoors.item.ItemOperatorKeycard;
import com.ntmdoors.item.ItemRelayKey;
import com.ntmdoors.item.ItemRfidCard;
import com.ntmdoors.registry.ModContent;
import com.ntmdoors.tile.TileEntityKeycardProgrammer;
import com.ntmdoors.util.ModSounds;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.SoundCategory;

import java.util.function.Predicate;

/**
 * Container for the keycard programmer. Page one has the two binding rows, page two has the
 * cosmetic RFID colour picker and page three is the legacy backup console. Its left and right slots
 * both accept Relay Keys and Operator Keycards. All pages live in one container; the GUI only draws
 * and accepts clicks for the slots that belong to the page currently on screen.
 */
public class ContainerKeycardProgrammer extends Container {

    public static final int GUI_WIDTH = 176;
    public static final int GUI_HEIGHT = 232;

    public static final int SLOT_RELAY_ONE_X = 11;
    public static final int SLOT_RELAY_ONE_Y = 38;
    public static final int SLOT_OPERATOR_X = 94;
    public static final int SLOT_OPERATOR_Y = 38;
    public static final int SLOT_RELAY_TWO_X = 11;
    public static final int SLOT_RELAY_TWO_Y = 78;
    public static final int SLOT_RFID_ONE_X = 94;
    public static final int SLOT_RFID_ONE_Y = 78;
    public static final int SLOT_RFID_TWO_X = 80;
    public static final int SLOT_RFID_TWO_Y = 39;
    public static final int SLOT_LEGACY_LEFT_X = 24;
    public static final int SLOT_LEGACY_LEFT_Y = 40;
    public static final int SLOT_LEGACY_RIGHT_X = 136;
    public static final int SLOT_LEGACY_RIGHT_Y = 40;
    public static final int BACKUP_BUTTON_X = 65;
    public static final int BACKUP_BUTTON_Y = 40;
    public static final int BACKUP_BUTTON_WIDTH = 46;
    public static final int BACKUP_BUTTON_HEIGHT = 16;
    public static final int BACKUP_BAR_X = 24;
    public static final int BACKUP_BAR_Y = 62;
    public static final int BACKUP_BAR_WIDTH = 128;
    public static final int BACKUP_BAR_HEIGHT = 12;
    public static final int COLOUR_FIRST_X = 11;
    public static final int COLOUR_Y = 78;
    public static final int COLOUR_STEP = 22;
    public static final int PLAYER_X = 8;
    public static final int PLAYER_Y = 148;
    public static final int HOTBAR_Y = 208;

    public static final int COLOUR_SLOT_START = 5;
    public static final int COLOUR_SLOT_COUNT = 7;
    public static final int LEGACY_SLOT_START = COLOUR_SLOT_START + COLOUR_SLOT_COUNT;
    public static final int LEGACY_SLOT_COUNT = 2;
    /** Fake slot id used to send the page-three Backup button to the server. */
    public static final int BACKUP_ACTION_SLOT = -2;

    private static final float BUTTON_VOLUME = 0.8F;

    private static final Predicate<ItemStack> RELAY_ONLY =
            stack -> stack.getItem() instanceof ItemRelayKey;
    private static final Predicate<ItemStack> OPERATOR_ONLY =
            stack -> stack.getItem() instanceof ItemOperatorKeycard;
    private static final Predicate<ItemStack> RFID_ONLY =
            stack -> stack.getItem() instanceof ItemRfidCard
                    && !(stack.getItem() instanceof ItemOperatorKeycard)
                    && !(stack.getItem() instanceof ItemCreativeKeycard);
    private static final Predicate<ItemStack> LEGACY_ONLY =
            stack -> stack.getItem() instanceof ItemRelayKey
                    || stack.getItem() instanceof ItemOperatorKeycard;

    private final TileEntityKeycardProgrammer tile;
    /** Backing inventory for the seven display-only colour slots. */
    private final IInventory colourInventory = new InventoryBasic("keycard_colours", false, COLOUR_SLOT_COUNT);

    public ContainerKeycardProgrammer(InventoryPlayer playerInventory, TileEntityKeycardProgrammer tile) {
        this.tile = tile;

        addSlotToContainer(new SlotProgrammer(tile, TileEntityKeycardProgrammer.SLOT_RELAY_ONE,
                SLOT_RELAY_ONE_X, SLOT_RELAY_ONE_Y, RELAY_ONLY));
        addSlotToContainer(new SlotProgrammer(tile, TileEntityKeycardProgrammer.SLOT_OPERATOR,
                SLOT_OPERATOR_X, SLOT_OPERATOR_Y, OPERATOR_ONLY));
        addSlotToContainer(new SlotProgrammer(tile, TileEntityKeycardProgrammer.SLOT_RELAY_TWO,
                SLOT_RELAY_TWO_X, SLOT_RELAY_TWO_Y, RELAY_ONLY));
        addSlotToContainer(new SlotProgrammer(tile, TileEntityKeycardProgrammer.SLOT_RFID_PAGE_ONE,
                SLOT_RFID_ONE_X, SLOT_RFID_ONE_Y, RFID_ONLY));
        addSlotToContainer(new SlotProgrammer(tile, TileEntityKeycardProgrammer.SLOT_RFID_PAGE_TWO,
                SLOT_RFID_TWO_X, SLOT_RFID_TWO_Y, RFID_ONLY));

        for (int i = 0; i < COLOUR_SLOT_COUNT; i++) {
            addSlotToContainer(new SlotColourOption(colourInventory, i,
                    COLOUR_FIRST_X + i * COLOUR_STEP, COLOUR_Y,
                    new ItemStack(ModContent.COLOUR_CARDS.get(i))));
        }

        addSlotToContainer(new SlotProgrammer(tile, TileEntityKeycardProgrammer.SLOT_LEGACY_LEFT,
                SLOT_LEGACY_LEFT_X, SLOT_LEGACY_LEFT_Y, LEGACY_ONLY));
        addSlotToContainer(new SlotProgrammer(tile, TileEntityKeycardProgrammer.SLOT_LEGACY_RIGHT,
                SLOT_LEGACY_RIGHT_X, SLOT_LEGACY_RIGHT_Y, LEGACY_ONLY));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(playerInventory, col + row * 9 + 9,
                        PLAYER_X + col * 18, PLAYER_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(playerInventory, col, PLAYER_X + col * 18, HOTBAR_Y));
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return tile.isUsableByPlayer(player);
    }

    /**
     * The colour squares are buttons, not storage. Clicking one swaps the card in page two's input
     * slot to that colour while preserving any NBT the card carries.
     */
    @Override
    public ItemStack slotClick(int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player) {
        if (slotId == BACKUP_ACTION_SLOT) {
            if (!player.world.isRemote) tile.startBackup();
            // Returning the client's carried stack confirms the synthetic click without moving it.
            return player.inventory.getItemStack();
        }
        if (slotId >= COLOUR_SLOT_START && slotId < COLOUR_SLOT_START + COLOUR_SLOT_COUNT) {
            if (!player.world.isRemote) {
                applyColour(slotId - COLOUR_SLOT_START, player);
            }
            return ItemStack.EMPTY;
        }
        return super.slotClick(slotId, dragType, clickTypeIn, player);
    }

    private void applyColour(int colour, EntityPlayer player) {
        ItemStack card = tile.getStackInSlot(TileEntityKeycardProgrammer.SLOT_RFID_PAGE_TWO);
        if (card.isEmpty()
                || !(card.getItem() instanceof ItemRfidCard)
                || card.getItem() instanceof ItemOperatorKeycard
                || card.getItem() instanceof ItemCreativeKeycard) {
            playButtonClick(player);
            return;
        }

        ItemStack result = new ItemStack(ModContent.COLOUR_CARDS.get(colour), 1);
        if (card.hasTagCompound()) {
            result.setTagCompound(card.getTagCompound().copy());
        }
        tile.setInventorySlotContents(TileEntityKeycardProgrammer.SLOT_RFID_PAGE_TWO, result);
        playButtonClick(player);
        detectAndSendChanges();
    }

    /** The same short click the screwdriver plays for a handled interaction. */
    private void playButtonClick(EntityPlayer player) {
        player.world.playSound(null, tile.getPos(), ModSounds.BUTTON9,
                SoundCategory.BLOCKS, BUTTON_VOLUME, 1F);
    }

    @Override
    public void onContainerClosed(EntityPlayer player) {
        super.onContainerClosed(player);
        tile.closeInventory(player);
    }

    /** Shift-clicking is disabled: the pages share one container and must not cross-fill. */
    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        return ItemStack.EMPTY;
    }

    /** A machine slot with a hard item-type whitelist; blocks, tools and other cards are rejected. */
    private static class SlotProgrammer extends Slot {

        private final Predicate<ItemStack> allowed;

        SlotProgrammer(IInventory inventory, int index, int x, int y, Predicate<ItemStack> allowed) {
            super(inventory, index, x, y);
            this.allowed = allowed;
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return !stack.isEmpty() && allowed.test(stack);
        }

        @Override
        public int getSlotStackLimit() {
            return 1;
        }
    }

    /** A colour square that always shows a card but cannot be picked up or filled. */
    private static class SlotColourOption extends Slot {

        private final ItemStack display;

        SlotColourOption(IInventory inventory, int index, int x, int y, ItemStack display) {
            super(inventory, index, x, y);
            this.display = display;
        }

        @Override
        public ItemStack getStack() {
            return display;
        }

        @Override
        public boolean getHasStack() {
            return true;
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return false;
        }

        @Override
        public boolean canTakeStack(EntityPlayer player) {
            return false;
        }

        @Override
        public int getSlotStackLimit() {
            return 1;
        }

        @Override
        public void putStack(ItemStack stack) {
        }

        @Override
        public ItemStack decrStackSize(int amount) {
            return ItemStack.EMPTY;
        }
    }
}
