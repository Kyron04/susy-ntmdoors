// SPDX-License-Identifier: LGPL-3.0-only
package com.ntmdoors.item;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.client.ModelSecurityShroudBoots;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;

/**
 * The Security Shroud boots. Cosmetic only: no protection, durability, effects or other
 * behaviour.
 */
public class ItemSecurityShroudBoots extends ItemArmor {

    public ItemSecurityShroudBoots() {
        super(SecurityShroudArmor.MATERIAL, 3, EntityEquipmentSlot.FEET);
        setRegistryName(NTMDoors.MODID, "security_shroud_boots");
        setTranslationKey(NTMDoors.MODID + ".security_shroud_boots");
        setCreativeTab(NTMDoors.TAB);
    }

    @Nullable
    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EntityEquipmentSlot slot, String type) {
        return type == null
                ? NTMDoors.MODID + ":textures/models/armor/security_shroud_boots_layer_1.png"
                : null;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ModelBiped getArmorModel(EntityLivingBase entityLiving, ItemStack itemStack,
                                    EntityEquipmentSlot armorSlot, ModelBiped defaultModel) {
        return ModelSecurityShroudBoots.INSTANCE;
    }
}
