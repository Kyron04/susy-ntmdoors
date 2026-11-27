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

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;

/** Tooltip lines for the four built-in Security Shroud pieces. */
public final class SecurityShroudTooltips {

    /** Vanilla caps the armor bar and damage reduction at 20 points. */
    private static final int VANILLA_ARMOR_CAP = 20;

    public static void add(@Nullable ItemStack stack, @Nullable List<String> tooltip) {
        if (stack == null || tooltip == null) return;
        if (!SecurityShroudConfig.showTooltips()) return;

        Item item = stack.getItem();
        int protection;
        if (item instanceof ItemSecurityShroudHelmet) {
            protection = SecurityShroudConfig.helmetProtection();
            if (SecurityShroudConfig.hideNameTags()
                    && SecurityShroudLogic.isConfiguredHelmet(stack)) {
                description(tooltip,
                        "item.ntmdoors.security_shroud_helmet.tooltip1",
                        "Shroud your identity from the enemy.",
                        "item.ntmdoors.security_shroud_helmet.tooltip2",
                        "Obstructs player tags from other players");
            }
        } else if (item instanceof ItemSecurityShroudChestplate) {
            protection = SecurityShroudConfig.chestplateProtection();
            if (SecurityShroudConfig.bypassWeaponsBindings()
                    && SecurityShroudLogic.isConfiguredChestplate(stack)) {
                description(tooltip,
                        "item.ntmdoors.security_shroud_chestplate.tooltip1",
                        "Nullifies weapons relay key bindings",
                        "item.ntmdoors.security_shroud_chestplate.tooltip2",
                        "when interacting with bound explosives");
            }
        } else if (item instanceof ItemSecurityShroudLeggings) {
            protection = SecurityShroudConfig.leggingsProtection();
            if (SecurityShroudConfig.movementSpeedBonus() != 0.0F
                    && SecurityShroudLogic.isConfiguredLeggings(stack)) {
                formattedDescription(tooltip,
                        "item.ntmdoors.security_shroud_leggings.tooltip1",
                        "%sx movement speed",
                        formatNumber(1.0F + SecurityShroudConfig.movementSpeedBonus()));
            }
        } else if (item instanceof ItemSecurityShroudBoots) {
            protection = SecurityShroudConfig.bootsProtection();
            if (SecurityShroudConfig.preventLethalFallDamage()
                    && SecurityShroudLogic.isConfiguredBoots(stack)) {
                formattedDescription(tooltip,
                        "item.ntmdoors.security_shroud_boots.tooltip1",
                        "Down but not out. Prevents a lethal fall from");
                formattedDescription(tooltip,
                        "item.ntmdoors.security_shroud_boots.tooltip2",
                        "reducing health below %s.",
                        formatHearts(SecurityShroudConfig.minimumHealthAfterFall()));
            }
        } else {
            return;
        }

        int maxDamage = stack.getMaxDamage();
        int remaining = Math.max(0, maxDamage - stack.getItemDamage());
        int fullSet = Math.min(VANILLA_ARMOR_CAP, SecurityShroudConfig.totalProtection());
        boolean completeSet = SecurityShroudConfig.hasAllItemLists();
        tooltip.add(TextFormatting.GRAY + "Durability: " + remaining + " / " + maxDamage);
        tooltip.add(TextFormatting.GRAY + "Damage resistance: " + protection
                + (completeSet ? " (" + fullSet + " full set)" : ""));

        boolean sensors = SecurityShroudConfig.ignoreMotionSensors();
        boolean grills = SecurityShroudConfig.ignoreEmancipationGrills();
        if (SecurityShroudConfig.enabled()
                && SecurityShroudConfig.showUnityTooltip()
                && SecurityShroudConfig.unityEnabled()
                && completeSet
                && (sensors || grills)) {
            String key;
            String fallback;
            if (sensors && grills) {
                key = "item.ntmdoors.security_shroud.unity";
                fallback = "Unity: Motion Sensors and Emancipation Grills ignore you.";
            } else if (sensors) {
                key = "item.ntmdoors.security_shroud.unity.sensors";
                fallback = "Unity: Motion Sensors ignore you.";
            } else {
                key = "item.ntmdoors.security_shroud.unity.grills";
                fallback = "Unity: Emancipation Grills ignore you.";
            }
            tooltip.add(TextFormatting.AQUA + Names.translationOr(key, fallback));
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

    /** Adds one formatted description line while effects are enabled. */
    private static void formattedDescription(List<String> tooltip, String key, String fallback,
                                             Object... args) {
        if (!SecurityShroudConfig.enabled()) return;
        tooltip.add(TextFormatting.GRAY + Names.translationOrFormatted(key, fallback, args));
    }

    /** Formats a multiplier or health value without trailing zeroes. */
    private static String formatNumber(float value) {
        if (Math.abs(value - Math.round(value)) < 0.001F) {
            return Integer.toString(Math.round(value));
        }
        String text = String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "");
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }

    private static String formatHearts(float health) {
        float hearts = health / 2.0F;
        String number = formatNumber(hearts);
        return number + (Math.abs(hearts - 1.0F) < 0.001F ? " heart" : " hearts");
    }

    private SecurityShroudTooltips() {
    }
}
