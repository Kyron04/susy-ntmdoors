package com.ntmdoors.recipe;

import com.ntmdoors.item.ItemPadlock;
import com.ntmdoors.item.LockUtil;
import com.ntmdoors.registry.ModContent;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.registries.IForgeRegistryEntry;

/** padlock + iron ingot -> key with the same code; the padlock is returned. */
public class RecipeKeyCopy extends IForgeRegistryEntry.Impl<IRecipe> implements IRecipe {

    @Override
    public boolean matches(InventoryCrafting inv, World world) {
        int padlocks = 0, ingots = 0;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s.isEmpty()) continue;
            if (s.getItem() instanceof ItemPadlock && LockUtil.getCode(s) != 0) padlocks++;
            else if (s.getItem() == Items.IRON_INGOT) ingots++;
            else return false;
        }
        return padlocks == 1 && ingots == 1;
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting inv) {
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s.getItem() instanceof ItemPadlock) {
                return LockUtil.withCode(new ItemStack(ModContent.KEY), LockUtil.getCode(s));
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(InventoryCrafting inv) {
        NonNullList<ItemStack> rem = NonNullList.withSize(inv.getSizeInventory(), ItemStack.EMPTY);
        for (int i = 0; i < rem.size(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s.getItem() instanceof ItemPadlock) rem.set(i, s.copy());
        }
        return rem;
    }

    @Override
    public boolean canFit(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public ItemStack getRecipeOutput() {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean isDynamic() {
        return true;
    }
}
