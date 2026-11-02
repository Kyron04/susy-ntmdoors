package com.ntmdoors.network;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.hlblocks.CycleHandler;
import com.ntmdoors.hlblocks.CycleMessage;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/** Simple networking channel used by the keycard items. */
public final class ModNetwork {

    public static final SimpleNetworkWrapper INSTANCE =
            NetworkRegistry.INSTANCE.newSimpleChannel(NTMDoors.MODID + "_main");

    public static void register() {
        INSTANCE.registerMessage(MessageRelayKeyName.Handler.class, MessageRelayKeyName.class, 0, Side.SERVER);
        INSTANCE.registerMessage(MessageScannerCode.Handler.class, MessageScannerCode.class, 1, Side.SERVER);
        INSTANCE.registerMessage(MessageScannerCodeResult.Handler.class, MessageScannerCodeResult.class, 2,
                Side.CLIENT);
        INSTANCE.registerMessage(MessageScannerKey.Handler.class, MessageScannerKey.class, 3, Side.SERVER);
        INSTANCE.registerMessage(MessageProgrammerPage.Handler.class, MessageProgrammerPage.class, 4,
                Side.SERVER);
        INSTANCE.registerMessage(MessageScannerOption.Handler.class, MessageScannerOption.class, 5,
                Side.SERVER);
        INSTANCE.registerMessage(MessageDocumentsFolderPages.Handler.class,
                MessageDocumentsFolderPages.class, 6, Side.SERVER);
        INSTANCE.registerMessage(MessageSafeCode.Handler.class, MessageSafeCode.class, 7, Side.SERVER);
        INSTANCE.registerMessage(MessageSafeResult.Handler.class, MessageSafeResult.class, 8,
                Side.CLIENT);
        INSTANCE.registerMessage(MessageSafeOption.Handler.class, MessageSafeOption.class, 9,
                Side.SERVER);
        INSTANCE.registerMessage(MessageSafePage.Handler.class, MessageSafePage.class, 10,
                Side.SERVER);
        INSTANCE.registerMessage(CycleHandler.class, CycleMessage.class, 11, Side.SERVER);
    }

    private ModNetwork() {
    }
}
