// SPDX-License-Identifier: LGPL-3.0-only
package com.ntmdoors.event;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.armor.SecurityShroudLogic;
import com.ntmdoors.config.SecurityShroudConfig;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

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

    /**
     * Applies the leggings speed multiplier as a single attribute modifier. The fixed UUID keeps
     * the bonus from stacking when the tick handler runs repeatedly or the config amount changes.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player == null
                || event.player.world == null) {
            return;
        }

        EntityPlayer player = event.player;
        IAttributeInstance speed = player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
        if (speed == null) return;

        AttributeModifier existing = speed.getModifier(SPEED_MODIFIER_ID);
        float bonus = SecurityShroudConfig.movementSpeedBonus();
        boolean active = SecurityShroudLogic.grantsMovementSpeed(player) && bonus != 0.0F;

        if (!active) {
            if (existing != null) speed.removeModifier(existing);
            return;
        }

        if (existing == null) {
            speed.applyModifier(new AttributeModifier(
                    SPEED_MODIFIER_ID, SPEED_MODIFIER_NAME, bonus, 2));
        } else if (Double.compare(existing.getAmount(), bonus) != 0) {
            speed.removeModifier(existing);
            speed.applyModifier(new AttributeModifier(
                    SPEED_MODIFIER_ID, SPEED_MODIFIER_NAME, bonus, 2));
        }
    }

    /**
     * Caps final fall damage so a player in the boots cannot be taken below the configured health.
     * {@link LivingDamageEvent} runs after armor, potion and absorption calculations, so the cap
     * is applied to the damage that would actually reach health.
     */
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntityLiving() == null || event.getEntityLiving().world == null
                || event.getEntityLiving().world.isRemote) {
            return;
        }
        if (!(event.getEntityLiving() instanceof EntityPlayer)) return;

        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        if (!SecurityShroudLogic.preventsLethalFallDamage(player)) return;
        if (!"fall".equals(event.getSource().getDamageType())) return;

        float minimumHealth = SecurityShroudConfig.minimumHealthAfterFall();
        float maximumDamage = Math.max(0.0F, player.getHealth() - minimumHealth);
        if (event.getAmount() > maximumDamage) event.setAmount(maximumDamage);
    }

    private SecurityShroudEvents() {
    }
}
