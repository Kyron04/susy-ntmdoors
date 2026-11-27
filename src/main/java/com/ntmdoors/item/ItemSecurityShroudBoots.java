// SPDX-License-Identifier: LGPL-3.0-only
package com.ntmdoors.item;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.armor.SecurityShroudTooltips;
import com.ntmdoors.client.ModelSecurityShroudBoots;
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
 * The Security Shroud boots. They prevent a lethal fall from taking the player below one heart and
 * are the feet piece of the full-set Unity effect; both behaviours are tunable through
 * {@link SecurityShroudConfig}.
 */
public class ItemSecurityShroudBoots extends ItemArmor {

    public ItemSecurityShroudBoots() {
        super(SecurityShroudArmor.MATERIAL, 3, EntityEquipmentSlot.FEET);
        setMaxDamage(SecurityShroudConfig.durability());
        setRegistryName(NTMDoors.MODID, "security_shroud_boots");
        setTranslationKey(NTMDoors.MODID + ".security_shroud_boots");
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
