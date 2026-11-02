package com.ntmdoors.tile;

import net.minecraft.entity.player.EntityPlayer;

/** A tile entity that can own a wireless Redstone Gate link. */
public interface RedstoneBindingHost {

    RedstoneBinding getRedstoneBinding();

    /** Re-sends the device's synced state after a link count changes. */
    void syncRedstoneState();

    /** Called after a gate is successfully linked or unlinked from this device. */
    void onRedstoneBindingChanged();

    /** Status message used by the binding session. */
    void sendRedstoneMessage(EntityPlayer player, String key);
}
