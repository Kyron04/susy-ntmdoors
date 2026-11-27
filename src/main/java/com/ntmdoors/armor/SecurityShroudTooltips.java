// SPDX-License-Identifier: LGPL-3.0-only
package com.ntmdoors.armor;

import com.ntmdoors.config.SecurityShroudConfig;
import com.ntmdoors.item.ItemSecurityShroudBoots;
import com.ntmdoors.item.ItemSecurityShroudChestplate;
import com.ntmdoors.item.ItemSecurityShroudHelmet;
import com.ntmdoors.item.ItemSecurityShroudLeggings;
import com.ntmdoors.util.Names;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;

import java.util.List;

/** Tooltip lines for the four built-in Security Shroud pieces. */
public final class SecurityShroudTooltips {

    /** Vanilla caps the armor bar and damage reduction at 20 points. */
    private static final int VANILLA_ARMOR_CAP = 20;

    public static void add(ItemStack stack, List<String> tooltip) {
        if (stack == null || tooltip == null) return;
        if (!SecurityShroudConfig.showTooltips()) return;

        Item item = stack.getItem();
        int protection;
        if (item instanceof ItemSecurityShroudHelmet) {
            protection = SecurityShroudConfig.helmetProtection();
            description(tooltip,
                    "item.ntmdoors.security_shroud_helmet.tooltip1",
                    "Shroud your identity from the enemy.",
                    "item.ntmdoors.security_shroud_helmet.tooltip2",
                    "Obstructs player tags from other players");
        } else if (item instanceof ItemSecurityShroudChestplate) {
            protection = SecurityShroudConfig.chestplateProtection();
            description(tooltip,
                    "item.ntmdoors.security_shroud_chestplate.tooltip1",
                    "Nullifies weapons relay key bindings",
                    "item.ntmdoors.security_shroud_chestplate.tooltip2",
                    "when interacting with bound explosives");
        } else if (item instanceof ItemSecurityShroudLeggings) {
            protection = SecurityShroudConfig.leggingsProtection();
            description(tooltip,
                    "item.ntmdoors.security_shroud_leggings.tooltip1",
                    "1.5x movement speed");
        } else if (item instanceof ItemSecurityShroudBoots) {
            protection = SecurityShroudConfig.bootsProtection();
            description(tooltip,
                    "item.ntmdoors.security_shroud_boots.tooltip1",
                    "Down but not out. Ensures that at least 1 heart",
                    "item.ntmdoors.security_shroud_boots.tooltip2",
                    "remains if the player falls from a lethal height");
        } else {
            return;
        }

        int maxDamage = stack.getMaxDamage();
        int remaining = Math.max(0, maxDamage - stack.getItemDamage());
        int fullSet = Math.min(VANILLA_ARMOR_CAP, SecurityShroudConfig.totalProtection());
        tooltip.add(TextFormatting.GRAY + "Durability: " + remaining + " / " + maxDamage);
        tooltip.add(TextFormatting.GRAY + "Damage resistance: " + protection
                + " (" + fullSet + " full set)");

        if (SecurityShroudConfig.enabled()
                && SecurityShroudConfig.showUnityTooltip()
                && SecurityShroudConfig.unityEnabled()
                && (SecurityShroudConfig.ignoreMotionSensors()
                || SecurityShroudConfig.ignoreEmancipationGrills())) {
            tooltip.add(TextFormatting.AQUA + Names.translationOr(
                    "item.ntmdoors.security_shroud.unity",
                    "Unity: Motion Sensors and Emancipation Grills ignore you."));
        }
    }

    /** Adds translated lines with English fallbacks, in the standard grey description style. */
    private static void description(List<String> tooltip, String... keyFallbackPairs) {
        if (!SecurityShroudConfig.enabled()) return;
        for (int i = 0; i + 1 < keyFallbackPairs.length; i += 2) {
            tooltip.add(TextFormatting.GRAY + Names.translationOr(
                    keyFallbackPairs[i], keyFallbackPairs[i + 1]));
        }
    }

    private SecurityShroudTooltips() {
    }
}
