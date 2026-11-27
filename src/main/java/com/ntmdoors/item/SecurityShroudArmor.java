// SPDX-License-Identifier: LGPL-3.0-only
package com.ntmdoors.item;

import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemArmor.ArmorMaterial;
import net.minecraftforge.common.util.EnumHelper;

/** Shared zero-stat material for the cosmetic Security Shroud set. */
final class SecurityShroudArmor {

    static final ArmorMaterial MATERIAL = EnumHelper.addArmorMaterial(
            "SECURITY_SHROUD", "security_shroud", 0, new int[]{0, 0, 0, 0}, 0,
            SoundEvents.ITEM_ARMOR_EQUIP_GENERIC, 0.0F);

    private SecurityShroudArmor() {
    }
}
