package com.ntmdoors.item;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.util.Names;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Carried document folder. Right-clicking opens a twenty-page editor; the pages live in the stack's
 * own NBT, so they survive inventory moves, death drops and world saves. The editor saves whenever
 * it closes, and there is no signing step: a folder stays writable for its whole life.
 */
public class ItemDocumentsFolder extends Item {

    /** Pages a folder holds. */
    public static final int PAGE_COUNT = 20;

    /** Hard cap per page, shared by the editor and the network message. */
    public static final int MAX_PAGE_LENGTH = 1024;

    private static final String TAG_PAGES = "pages";

    public ItemDocumentsFolder() {
        setRegistryName(NTMDoors.MODID, "documents_folder");
        setTranslationKey(NTMDoors.MODID + ".documents_folder");
        setCreativeTab(NTMDoors.TAB);
        setMaxStackSize(1);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        // The editor is client side only; it sends the finished pages to the server when it closes.
        if (world.isRemote) {
            NTMDoors.proxy.openDocumentsFolderGui(hand);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        Names.addDescription(tooltip,
                "Effectively a glorified book with a cooler aesthetic",
                "Right-click to open and write");
        int written = writtenPageCount(stack);
        if (written > 0) {
            tooltip.add(TextFormatting.GRAY + "Pages written: " + written + " / " + PAGE_COUNT);
        }
    }

    // ------------------------------------------------------------ pages

    /** Reads the stored pages, padded with empty strings up to {@link #PAGE_COUNT}. */
    public static List<String> getPages(ItemStack stack) {
        NBTTagList list = null;
        if (!stack.isEmpty() && stack.hasTagCompound()) {
            list = stack.getTagCompound().getTagList(TAG_PAGES, Constants.NBT.TAG_STRING);
        }

        List<String> pages = new ArrayList<>(PAGE_COUNT);
        for (int i = 0; i < PAGE_COUNT; i++) {
            pages.add(list != null && i < list.tagCount() ? list.getStringTagAt(i) : "");
        }
        return pages;
    }

    /** Writes the pages back. An entirely empty folder loses the tag, keeping the item clean. */
    public static void setPages(ItemStack stack, List<String> pages) {
        if (stack.isEmpty()) return;

        List<String> clean = clean(pages);
        NBTTagCompound tag = stack.getTagCompound();
        if (clean.isEmpty()) {
            if (tag != null) {
                tag.removeTag(TAG_PAGES);
                if (tag.isEmpty()) stack.setTagCompound(null);
            }
            return;
        }

        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        NBTTagList list = new NBTTagList();
        for (String page : clean) {
            list.appendTag(new NBTTagString(page));
        }
        tag.setTag(TAG_PAGES, list);
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
}
