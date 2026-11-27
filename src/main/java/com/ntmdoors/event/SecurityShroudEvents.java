// SPDX-License-Identifier: LGPL-3.0-only
package com.ntmdoors.event;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.armor.SecurityShroudLogic;
import com.ntmdoors.config.SecurityShroudConfig;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.UUID;

/**
 * Per-piece Security Shroud effects: the leggings movement bonus and the boots' non-lethal fall
 * cap. The speed modifier runs on both sides so client movement prediction sees the same bonus as
 * the server. The helmet name-tag and chestplate weapons effects live in the client renderer and
 * weapons event respectively.
 */
@Mod.EventBusSubscriber(modid = NTMDoors.MODID)
public final class SecurityShroudEvents {

    private static final UUID SPEED_MODIFIER_ID =
            UUID.fromString("7c9e2a52-4d3a-4b7a-9e7c-2d3f4a5b6c7d");
    private static final String SPEED_MODIFIER_NAME = "ntmdoors:security_shroud_speed";
    /** AttributeModifier operation 2: multiply the final attribute value. */
    private static final int MULTIPLY_TOTAL = 2;
    /** ForgeData key holding the world tick at which the next fall can be capped. */
    private static final String FALL_PROTECTION_READY_AT = "ntmdoors:fallProtectionReadyAt";

    /**
     * Applies the leggings speed multiplier as a single attribute modifier. The fixed UUID keeps
     * the bonus from stacking when the handler runs repeatedly or the config amount changes.
     */
    @SubscribeEvent
    public static void onLivingUpdate(LivingUpdateEvent event) {
        EntityLivingBase entity = event.getEntityLiving();
        if (entity == null || entity.world == null || entity instanceof EntityArmorStand) return;
        // The server updates every living entity's attribute. The client only needs the local
        // player's copy for movement prediction; world positions remain server-authoritative.
        if (entity.world.isRemote
                && (!(entity instanceof EntityPlayer) || !((EntityPlayer) entity).isUser())) {
            return;
        }

        IAttributeInstance speed = entity.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
        if (speed == null) return;

        AttributeModifier existing = speed.getModifier(SPEED_MODIFIER_ID);
        float bonus = SecurityShroudConfig.movementSpeedBonus();
        boolean active = SecurityShroudLogic.grantsMovementSpeed(entity) && bonus != 0.0F;

        if (!active) {
            if (existing != null) speed.removeModifier(existing);
            return;
        }

        if (existing == null) {
            speed.applyModifier(new AttributeModifier(
                    SPEED_MODIFIER_ID, SPEED_MODIFIER_NAME, bonus, MULTIPLY_TOTAL));
        } else if (Double.compare(existing.getAmount(), bonus) != 0) {
            speed.removeModifier(existing);
            speed.applyModifier(new AttributeModifier(
                    SPEED_MODIFIER_ID, SPEED_MODIFIER_NAME, bonus, MULTIPLY_TOTAL));
        }
    }

    /**
     * Caps final fall damage so a wearer of the boots cannot be taken below the configured
     * health, at most once per configured cooldown. {@link LivingDamageEvent} runs after armor,
     * potion and absorption calculations, so the cap is applied to the damage that would
     * actually reach health.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntityLiving() == null || event.getEntityLiving().world == null
                || event.getEntityLiving().world.isRemote) {
            return;
        }
        EntityLivingBase entity = event.getEntityLiving();
        if (!SecurityShroudLogic.preventsLethalFallDamage(entity)) return;
        if (!DamageSource.FALL.getDamageType().equals(event.getSource().getDamageType())) return;

        int cooldown = SecurityShroudConfig.fallProtectionCooldownTicks();
        long now = entity.world.getTotalWorldTime();
        if (cooldown > 0
                && entity.getEntityData().getLong(FALL_PROTECTION_READY_AT) > now) {
            return;
        }

        float minimumHealth = SecurityShroudConfig.minimumHealthAfterFall();
        float maximumDamage = Math.max(0.0F, entity.getHealth() - minimumHealth);
        if (event.getAmount() <= maximumDamage) return;

        event.setAmount(maximumDamage);
        // Only a fall the boots actually softened starts the cooldown. ForgeData is saved with
        // the entity, so the timer survives chunk unloads.
        if (cooldown > 0) {
            entity.getEntityData().setLong(FALL_PROTECTION_READY_AT, now + cooldown);
        }
    }

    private SecurityShroudEvents() {
    }
}
