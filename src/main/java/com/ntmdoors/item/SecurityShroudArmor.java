// SPDX-License-Identifier: LGPL-3.0-only
package com.ntmdoors.item;

import com.ntmdoors.config.SecurityShroudConfig;

import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemArmor.ArmorMaterial;
import net.minecraftforge.common.util.EnumHelper;

/**
 * Shared material for the Security Shroud set. Durability is applied per item from the config so
 * the configured value is exact; the material supplies the configurable protection,
 * enchantability and toughness.
 */
final class SecurityShroudArmor {

    /** Per-item max damage is set from the config; the material factor is not used directly. */
    private static final int MATERIAL_DURABILITY = 0;

    static final ArmorMaterial MATERIAL = createMaterial();

    private static ArmorMaterial createMaterial() {
        SecurityShroudConfig.loadIfNeeded();
        // The material array is ordered boots, leggings, chestplate, helmet.
        return EnumHelper.addArmorMaterial(
                "SECURITY_SHROUD", "security_shroud", MATERIAL_DURABILITY,
                new int[]{
                        SecurityShroudConfig.bootsProtection(),
                        SecurityShroudConfig.leggingsProtection(),
                        SecurityShroudConfig.chestplateProtection(),
                        SecurityShroudConfig.helmetProtection()
                },
                SecurityShroudConfig.enchantability(),
                SoundEvents.ITEM_ARMOR_EQUIP_GENERIC,
                SecurityShroudConfig.toughness());
    }

    private SecurityShroudArmor() {
    }
}
