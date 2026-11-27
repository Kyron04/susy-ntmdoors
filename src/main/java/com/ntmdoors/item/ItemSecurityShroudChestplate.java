// SPDX-License-Identifier: LGPL-3.0-only
package com.ntmdoors.item;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.client.ModelSecurityShroudChestplate;

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
 * The Security Shroud chestplate. Cosmetic only: no protection, durability, effects or other
 * behaviour.
 */
public class ItemSecurityShroudChestplate extends ItemArmor {

    public ItemSecurityShroudChestplate() {
        super(SecurityShroudArmor.MATERIAL, 1, EntityEquipmentSlot.CHEST);
        setRegistryName(NTMDoors.MODID, "security_shroud_chestplate");
        setTranslationKey(NTMDoors.MODID + ".security_shroud_chestplate");
        setCreativeTab(NTMDoors.TAB);
    }

    @Nullable
    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EntityEquipmentSlot slot, String type) {
        return type == null
                ? NTMDoors.MODID + ":textures/models/armor/security_shroud_chestplate_layer_1.png"
                : null;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ModelBiped getArmorModel(EntityLivingBase entityLiving, ItemStack itemStack,
                                    EntityEquipmentSlot armorSlot, ModelBiped defaultModel) {
        return ModelSecurityShroudChestplate.INSTANCE;
    }
}
