// SPDX-License-Identifier: LGPL-3.0-only
package com.ntmdoors.armor;

import com.ntmdoors.config.SecurityShroudConfig;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.Set;

/**
 * Shared checks for the Security Shroud and for any other armor a pack adds to the config lists.
 * Nothing here is tied to the built-in item classes, so a configured helmet from another mod gets
 * the same identity concealment and counts toward the full set.
 */
public final class SecurityShroudLogic {

    private SecurityShroudLogic() {
    }

    public static boolean isEnabled() {
        return SecurityShroudConfig.enabled();
    }

    /** True when the entity wears a configured helmet. Also used for name-tagged non-players. */
    public static boolean wearsHelmet(@Nullable EntityLivingBase entity) {
        return entity != null && isConfiguredHelmet(entity.getItemStackFromSlot(EntityEquipmentSlot.HEAD));
    }

    public static boolean wearsChestplate(@Nullable EntityLivingBase entity) {
        return entity != null
                && isConfiguredChestplate(entity.getItemStackFromSlot(EntityEquipmentSlot.CHEST));
    }

    public static boolean wearsLeggings(@Nullable EntityLivingBase entity) {
        return entity != null
                && isConfiguredLeggings(entity.getItemStackFromSlot(EntityEquipmentSlot.LEGS));
    }

    public static boolean wearsBoots(@Nullable EntityLivingBase entity) {
        return entity != null && isConfiguredBoots(entity.getItemStackFromSlot(EntityEquipmentSlot.FEET));
    }

    public static boolean isConfiguredHelmet(@Nullable ItemStack stack) {
        return isArmorForSlot(stack, EntityEquipmentSlot.HEAD, SecurityShroudConfig.helmetItems());
    }

    public static boolean isConfiguredChestplate(@Nullable ItemStack stack) {
        return isArmorForSlot(stack, EntityEquipmentSlot.CHEST, SecurityShroudConfig.chestplateItems());
    }

    public static boolean isConfiguredLeggings(@Nullable ItemStack stack) {
        return isArmorForSlot(stack, EntityEquipmentSlot.LEGS, SecurityShroudConfig.leggingsItems());
    }

    public static boolean isConfiguredBoots(@Nullable ItemStack stack) {
        return isArmorForSlot(stack, EntityEquipmentSlot.FEET, SecurityShroudConfig.bootsItems());
    }

    /**
     * True when the player has a configured item in every armor slot. When
     * {@code unity.RequireWorn} is false, the four pieces may instead be carried anywhere in the
     * player's inventory.
     */
    public static boolean hasFullSet(@Nullable EntityPlayer player) {
        if (player == null || !isEnabled() || !SecurityShroudConfig.unityEnabled()) return false;

        if (SecurityShroudConfig.unityRequiresWorn()) {
            return wearsHelmet(player)
                    && wearsChestplate(player)
                    && wearsLeggings(player)
                    && wearsBoots(player);
        }

        return containsInInventory(player, SecurityShroudConfig.helmetItems(),
                EntityEquipmentSlot.HEAD)
                && containsInInventory(player, SecurityShroudConfig.chestplateItems(),
                EntityEquipmentSlot.CHEST)
                && containsInInventory(player, SecurityShroudConfig.leggingsItems(),
                EntityEquipmentSlot.LEGS)
                && containsInInventory(player, SecurityShroudConfig.bootsItems(),
                EntityEquipmentSlot.FEET);
    }

    public static boolean hidesNameTag(@Nullable EntityLivingBase entity) {
        return isEnabled()
                && SecurityShroudConfig.hideNameTags()
                && wearsHelmet(entity);
    }

    public static boolean bypassesWeaponsBindings(@Nullable EntityPlayer player) {
        return isEnabled()
                && SecurityShroudConfig.bypassWeaponsBindings()
                && wearsChestplate(player);
    }

    public static boolean preventsLethalFallDamage(@Nullable EntityPlayer player) {
        return isEnabled()
                && SecurityShroudConfig.preventLethalFallDamage()
                && wearsBoots(player);
    }

    public static boolean grantsMovementSpeed(@Nullable EntityPlayer player) {
        return isEnabled() && wearsLeggings(player);
    }

    public static boolean ignoresMotionSensors(@Nullable EntityPlayer player) {
        return isEnabled()
                && SecurityShroudConfig.ignoreMotionSensors()
                && hasFullSet(player);
    }

    public static boolean ignoresEmancipationGrills(@Nullable EntityPlayer player) {
        return isEnabled()
                && SecurityShroudConfig.ignoreEmancipationGrills()
                && hasFullSet(player);
    }

    private static boolean containsInInventory(EntityPlayer player, Set<String> items,
                                               EntityEquipmentSlot slot) {
        for (ItemStack stack : player.inventory.mainInventory) {
            if (isArmorForSlot(stack, slot, items)) return true;
        }
        for (ItemStack stack : player.inventory.armorInventory) {
            if (isArmorForSlot(stack, slot, items)) return true;
        }
        for (ItemStack stack : player.inventory.offHandInventory) {
            if (isArmorForSlot(stack, slot, items)) return true;
        }
        return false;
    }

    /**
     * True when the stack is a wearable armor piece for the requested slot and its registry name
     * is in the configured list. Slot checking keeps a helmet listed as a chestplate, or a
     * non-armor item, from satisfying the full-set check while carried.
     */
    private static boolean isArmorForSlot(@Nullable ItemStack stack, EntityEquipmentSlot slot,
                                          @Nullable Set<String> items) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof ItemArmor)) {
            return false;
        }
        return ((ItemArmor) stack.getItem()).armorType == slot && matches(stack, items);
    }

    /**
     * Matches a registry name against the configured set. {@code modid:*} accepts any item from
     * that mod, which lets a pack enroll a whole armor set without listing every piece.
     */
    public static boolean matches(@Nullable ItemStack stack, @Nullable Set<String> items) {
        if (stack == null || stack.isEmpty() || items == null || items.isEmpty()) return false;
        Item item = stack.getItem();
        ResourceLocation id = Item.REGISTRY.getNameForObject(item);
        if (id == null) return false;
        String key = id.toString().toLowerCase(Locale.ROOT);
        String wildcard = id.getNamespace().toLowerCase(Locale.ROOT) + ":*";
        return items.contains(key) || items.contains(wildcard);
    }
}
