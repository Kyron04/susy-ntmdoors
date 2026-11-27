// SPDX-License-Identifier: LGPL-3.0-only
package com.ntmdoors.client;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.armor.SecurityShroudLogic;

import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Client-side name-tag concealment for the Security Shroud helmet. The same check applies to
 * players and to any name-tagged living entity wearing a configured helmet.
 */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = NTMDoors.MODID, value = Side.CLIENT)
public final class SecurityShroudClientEvents {

    @SubscribeEvent
    public static void onRenderNameTag(RenderLivingEvent.Specials.Pre<EntityLivingBase> event) {
        if (SecurityShroudLogic.hidesNameTag(event.getEntity())) event.setCanceled(true);
    }

    private SecurityShroudClientEvents() {
    }
}
