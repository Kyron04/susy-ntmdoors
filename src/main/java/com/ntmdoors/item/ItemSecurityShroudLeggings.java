// SPDX-License-Identifier: LGPL-3.0-only
package com.ntmdoors.item;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.armor.SecurityShroudTooltips;
import com.ntmdoors.client.ModelSecurityShroudLeggings;
import com.ntmdoors.config.SecurityShroudConfig;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The Security Shroud leggings. They grant a small movement speed bonus and are the leg piece of
 * the full-set Unity effect; both behaviours are tunable through {@link SecurityShroudConfig}.
 */
public class ItemSecurityShroudLeggings extends ItemArmor {

    public ItemSecurityShroudLeggings() {
        super(SecurityShroudArmor.MATERIAL, 2, EntityEquipmentSlot.LEGS);
        setMaxDamage(SecurityShroudConfig.durability());
        setRegistryName(NTMDoors.MODID, "security_shroud_leggings");
        setTranslationKey(NTMDoors.MODID + ".security_shroud_leggings");
        setCreativeTab(NTMDoors.TAB);
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.RARE;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip,
                               ITooltipFlag flagIn) {
        SecurityShroudTooltips.add(stack, tooltip);
    }

    @Nullable
    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EntityEquipmentSlot slot, String type) {
        return type == null
                ? NTMDoors.MODID + ":textures/models/armor/security_shroud_leggings_layer_2.png"
                : null;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ModelBiped getArmorModel(EntityLivingBase entityLiving, ItemStack itemStack,
                                    EntityEquipmentSlot armorSlot, ModelBiped defaultModel) {
        return ModelSecurityShroudLeggings.INSTANCE;
    }
}
