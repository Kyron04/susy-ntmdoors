package com.ntmdoors.block.entity;

import com.ntmdoors.util.RedstoneBinding;
import net.minecraft.entity.player.PlayerEntity;

/**
 * A block entity that can own a wireless Redstone Gate link.
 */
public interface RedstoneBindingHost {

    RedstoneBinding getRedstoneBinding();

    /** Re-sends the device's synced state after a link count changes. */
    default void syncRedstoneState() {}

    /** Called after a gate is successfully linked or unlinked from this device. */
    void onRedstoneBindingChanged();

    /** Status message used by the binding session. */
    default void sendRedstoneMessage(PlayerEntity player, String key) {
        if (player != null && key != null) {
            player.sendMessage(net.minecraft.text.Text.translatable(key), true);
        }
    }
}
