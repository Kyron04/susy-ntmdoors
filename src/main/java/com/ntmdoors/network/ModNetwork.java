package com.ntmdoors.network;

import com.ntmdoors.NTMDoors;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/** Simple networking channel used by the keycard items. */
public final class ModNetwork {

    public static final SimpleNetworkWrapper INSTANCE =
            NetworkRegistry.INSTANCE.newSimpleChannel(NTMDoors.MODID + "_main");

    public static void register() {
        INSTANCE.registerMessage(MessageRelayKeyName.Handler.class, MessageRelayKeyName.class, 0, Side.SERVER);
    }

    private ModNetwork() {
    }
}
