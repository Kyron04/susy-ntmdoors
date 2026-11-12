package com.ntmdoors.item;

import com.ntmdoors.client.screen.DocumentsFolderScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Carried document folder. Right-clicking opens a twenty-page editor; the pages live in the stack's
 * own NBT, so they survive inventory moves, death drops and world saves. The editor saves whenever
 * it closes, remembers the page and the custom name, and there is no signing step: a folder stays
 * writable for its whole life.
 */
public class ItemDocumentsFolder extends Item {

    public static final int PAGE_COUNT = 20;
    public static final int MAX_PAGE_LENGTH = 1024;
    public static final int MAX_NAME_LENGTH = 24;

    public static final String TAG_PAGES = "pages";
    public static final String TAG_PAGE = "lastPage";

    public ItemDocumentsFolder() {
        super(new Item.Settings().maxCount(1));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (world.isClient()) {
            openClientScreen(hand, stack);
            player.playSound(SoundEvents.ITEM_BOOK_PAGE_TURN, 0.8F, 1.0F);
        }
        return TypedActionResult.success(stack, world.isClient());
    }

    @Environment(EnvType.CLIENT)
    private void openClientScreen(Hand hand, ItemStack stack) {
        MinecraftClient.getInstance().setScreen(new DocumentsFolderScreen(hand, stack));
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("Effectively a glorified book with a cooler aesthetic.").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Right-click to open and write.").formatted(Formatting.GRAY));
        int written = writtenPageCount(stack);
        if (written > 0) {
            tooltip.add(Text.literal("Pages written: " + written + " / " + PAGE_COUNT).formatted(Formatting.GRAY));
        }
    }

    /** Reads the stored pages, padded with empty strings up to PAGE_COUNT. */
    public static List<String> getPages(ItemStack stack) {
        List<String> pages = new ArrayList<>(PAGE_COUNT);
        NbtList list = null;
        if (!stack.isEmpty() && stack.hasNbt()) {
            NbtCompound tag = stack.getNbt();
            if (tag != null && tag.contains(TAG_PAGES, NbtElement.LIST_TYPE)) {
                list = tag.getList(TAG_PAGES, NbtElement.STRING_TYPE);
            }
        }

        for (int i = 0; i < PAGE_COUNT; i++) {
            pages.add(list != null && i < list.size() ? list.getString(i) : "");
        }
        return pages;
    }

    /** Writes the pages back. An entirely empty folder loses the tag, keeping the item clean. */
    public static void setPages(ItemStack stack, List<String> pages) {
        if (stack.isEmpty()) return;

        List<String> clean = clean(pages);
        NbtCompound tag = stack.getNbt();
        if (clean.isEmpty()) {
            if (tag != null) {
                tag.remove(TAG_PAGES);
                if (tag.isEmpty()) stack.setNbt(null);
            }
            return;
        }

        tag = stack.getOrCreateNbt();
        NbtList list = new NbtList();
        for (String page : clean) {
            list.add(NbtString.of(page));
        }
        tag.put(TAG_PAGES, list);
    }

    /** Clamps a page list to the folder limits and drops trailing empty pages. */
    public static List<String> clean(List<String> input) {
        List<String> out = new ArrayList<>();
        if (input == null) return out;

        int size = Math.min(PAGE_COUNT, input.size());
        for (int i = 0; i < size; i++) {
            String page = input.get(i);
            if (page == null) page = "";
            if (page.length() > MAX_PAGE_LENGTH) page = page.substring(0, MAX_PAGE_LENGTH);
            out.add(page);
        }

        while (!out.isEmpty() && out.get(out.size() - 1).isEmpty()) {
            out.remove(out.size() - 1);
        }
        return out;
    }

    /** Number of pages that contain any text. */
    public static int writtenPageCount(ItemStack stack) {
        int count = 0;
        for (String page : getPages(stack)) {
            if (!page.isEmpty()) count++;
        }
        return count;
    }

    /** The page the folder was last closed on, clamped to a real page. */
    public static int getLastPage(ItemStack stack) {
        if (stack.isEmpty() || !stack.hasNbt()) return 0;
        NbtCompound tag = stack.getNbt();
        if (tag == null || !tag.contains(TAG_PAGE, NbtElement.INT_TYPE)) return 0;
        return Math.max(0, Math.min(PAGE_COUNT - 1, tag.getInt(TAG_PAGE)));
    }

    /** Remembers the current page; page zero is default, so it removes the tag instead. */
    public static void setLastPage(ItemStack stack, int page) {
        if (stack.isEmpty()) return;

        int value = Math.max(0, Math.min(PAGE_COUNT - 1, page));
        NbtCompound tag = stack.getNbt();
        if (value == 0) {
            if (tag != null) {
                tag.remove(TAG_PAGE);
                if (tag.isEmpty()) stack.setNbt(null);
            }
            return;
        }

        tag = stack.getOrCreateNbt();
        tag.putInt(TAG_PAGE, value);
    }

    /** The custom folder name, or empty string when default item name is in use. */
    public static String getCustomName(ItemStack stack) {
        if (stack.isEmpty() || !stack.hasCustomName()) return "";
        return cleanName(stack.getName().getString());
    }

    /** Sets the custom folder name; an empty name restores default item name. */
    public static void setCustomName(ItemStack stack, String name) {
        if (stack.isEmpty()) return;

        String clean = cleanName(name);
        if (clean.isEmpty()) {
            stack.removeCustomName();
        } else {
            stack.setCustomName(Text.literal(clean));
        }
    }

    /** Strips formatting and control characters and enforces the name limit. */
    public static String cleanName(String name) {
        if (name == null) return "";
        String text = Formatting.strip(name);
        if (text == null) return "";

        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length() && out.length() < MAX_NAME_LENGTH; i++) {
            char c = text.charAt(i);
            if (c >= ' ' && c != 127 && c != '§') out.append(c);
        }
        return out.toString().trim();
    }
}
