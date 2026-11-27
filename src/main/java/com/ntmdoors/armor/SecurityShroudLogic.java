// SPDX-License-Identifier: LGPL-3.0-only
package com.ntmdoors.armor;

import com.ntmdoors.config.SecurityShroudConfig;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

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
    public static boolean wearsHelmet(EntityLivingBase entity) {
        return entity != null && isConfiguredHelmet(entity.getItemStackFromSlot(EntityEquipmentSlot.HEAD));
    }

    public static boolean wearsChestplate(EntityLivingBase entity) {
        return entity != null
                && isConfiguredChestplate(entity.getItemStackFromSlot(EntityEquipmentSlot.CHEST));
    }

    public static boolean wearsLeggings(EntityLivingBase entity) {
        return entity != null
                && isConfiguredLeggings(entity.getItemStackFromSlot(EntityEquipmentSlot.LEGS));
    }

    public static boolean wearsBoots(EntityLivingBase entity) {
        return entity != null && isConfiguredBoots(entity.getItemStackFromSlot(EntityEquipmentSlot.FEET));
    }

    public static boolean isConfiguredHelmet(ItemStack stack) {
        return matches(stack, SecurityShroudConfig.helmetItems());
    }

    public static boolean isConfiguredChestplate(ItemStack stack) {
        return matches(stack, SecurityShroudConfig.chestplateItems());
    }

    public static boolean isConfiguredLeggings(ItemStack stack) {
        return matches(stack, SecurityShroudConfig.leggingsItems());
    }

    public static boolean isConfiguredBoots(ItemStack stack) {
        return matches(stack, SecurityShroudConfig.bootsItems());
    }

    /**
     * True when the player has a configured item in every armor slot. When
     * {@code unity.RequireWorn} is false, the four pieces may instead be carried anywhere in the
     * player's inventory.
     */
    public static boolean hasFullSet(EntityPlayer player) {
        if (player == null || !isEnabled() || !SecurityShroudConfig.unityEnabled()) return false;

        if (SecurityShroudConfig.unityRequiresWorn()) {
            return wearsHelmet(player)
                    && wearsChestplate(player)
                    && wearsLeggings(player)
                    && wearsBoots(player);
        }

        return containsInInventory(player, SecurityShroudConfig.helmetItems())
                && containsInInventory(player, SecurityShroudConfig.chestplateItems())
                && containsInInventory(player, SecurityShroudConfig.leggingsItems())
                && containsInInventory(player, SecurityShroudConfig.bootsItems());
    }

    public static boolean hidesNameTag(EntityLivingBase entity) {
        return isEnabled()
                && SecurityShroudConfig.hideNameTags()
                && wearsHelmet(entity);
    }

    public static boolean bypassesWeaponsBindings(EntityPlayer player) {
        return isEnabled()
                && SecurityShroudConfig.bypassWeaponsBindings()
                && wearsChestplate(player);
    }

    public static boolean preventsLethalFallDamage(EntityPlayer player) {
        return isEnabled()
                && SecurityShroudConfig.preventLethalFallDamage()
                && wearsBoots(player);
    }

    public static boolean grantsMovementSpeed(EntityPlayer player) {
        return isEnabled() && wearsLeggings(player);
    }

    public static boolean ignoresMotionSensors(EntityPlayer player) {
        return isEnabled()
                && SecurityShroudConfig.ignoreMotionSensors()
                && hasFullSet(player);
    }

    public static boolean ignoresEmancipationGrills(EntityPlayer player) {
        return isEnabled()
                && SecurityShroudConfig.ignoreEmancipationGrills()
                && hasFullSet(player);
    }

    private static boolean containsInInventory(EntityPlayer player, Set<String> items) {
        for (ItemStack stack : player.inventory.mainInventory) {
            if (matches(stack, items)) return true;
        }
        for (ItemStack stack : player.inventory.armorInventory) {
            if (matches(stack, items)) return true;
        }
        for (ItemStack stack : player.inventory.offHandInventory) {
            if (matches(stack, items)) return true;
        }
        return false;
    }

    /**
     * Matches a registry name against the configured set. {@code modid:*} accepts any item from
     * that mod, which lets a pack enroll a whole armor set without listing every piece.
     */
    public static boolean matches(ItemStack stack, Set<String> items) {
        if (stack == null || stack.isEmpty() || items == null || items.isEmpty()) return false;
        Item item = stack.getItem();
        ResourceLocation id = Item.REGISTRY.getNameForObject(item);
        if (id == null) return false;
        String key = id.toString().toLowerCase(Locale.ROOT);
        String wildcard = id.getNamespace().toLowerCase(Locale.ROOT) + ":*";
        return items.contains(key) || items.contains(wildcard);
    }
}
