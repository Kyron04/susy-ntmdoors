// SPDX-License-Identifier: LGPL-3.0-only
package com.ntmdoors.client;

import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Shared TESR wrapper for the mod's devices. It gives every device the same GL baseline and a
 * clean state afterwards, so a renderer earlier in the TESR order cannot tint or shade the device
 * and the device cannot leak its own colour, lighting or depth state into the next renderer.
 */
@SideOnly(Side.CLIENT)
abstract class DeviceRenderer<T extends TileEntity> extends TileEntitySpecialRenderer<T> {

    @Override
    public final void render(T te, double x, double y, double z, float partialTicks,
                             int destroyStage, float alpha) {
        DeviceRenderState.begin();
        try {
            renderDevice(te, x, y, z, partialTicks, destroyStage, alpha);
        } finally {
            DeviceRenderState.end();
        }
    }

    protected abstract void renderDevice(T te, double x, double y, double z,
                                         float partialTicks, int destroyStage, float alpha);
}
