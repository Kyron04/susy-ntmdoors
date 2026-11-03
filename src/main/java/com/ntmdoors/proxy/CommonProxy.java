package com.ntmdoors.proxy;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.compat.ChiselIntegration;
import com.ntmdoors.concrete.ConcreteOreDict;
import com.ntmdoors.door.DoorDecl;
import com.ntmdoors.hlblocks.HLBlocksOreDict;
import com.ntmdoors.door.DoorDecls;
import com.ntmdoors.gui.GuiHandler;
import com.ntmdoors.network.ModNetwork;
import com.ntmdoors.tile.TileEntityDoorGeneric;
import com.ntmdoors.tile.TileEntityDoorKeypad;
import com.ntmdoors.tile.TileEntityDoorPart;
import com.ntmdoors.tile.TileEntityBiometricSafe;
import com.ntmdoors.tile.TileEntityKeycardProgrammer;
import com.ntmdoors.tile.TileEntityKeycardScanner;
import com.ntmdoors.tile.TileEntityMonitorSetup;
import com.ntmdoors.tile.TileEntityMotionSensor;
import com.ntmdoors.tile.TileEntityRedstoneGate;
import com.ntmdoors.tile.TileEntitySafe;
import com.ntmdoors.tile.TileEntitySpeaker;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.registry.GameRegistry;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        ModNetwork.register();
        registerTileEntity(TileEntityDoorGeneric.class, "door_core");
        registerTileEntity(TileEntityDoorPart.class, "door_part");
        registerTileEntity(TileEntityDoorKeypad.class, "door_keypad");
        registerTileEntity(TileEntityKeycardScanner.class, "keycard_scanner");
        registerTileEntity(TileEntityKeycardProgrammer.class, "keycard_programmer");
        registerTileEntity(TileEntitySafe.class, "safe");
        registerTileEntity(TileEntityBiometricSafe.class, "biometric_safe");
        registerTileEntity(TileEntityRedstoneGate.class, "redstone_gate");
        registerTileEntity(TileEntitySpeaker.class, "speaker");
        registerTileEntity(TileEntityMotionSensor.class, "motion_sensor");
        registerTileEntity(TileEntityMonitorSetup.class, "monitor_setup");
        // Read the OBJ group bounds used by moving collision boxes now, so the first collision
        // query on a door never has to touch the filesystem.
        for (DoorDecl d : DoorDecls.ALL) d.prepareGeometry();
    }

    private static void registerTileEntity(Class<? extends TileEntity> type, String name) {
        GameRegistry.registerTileEntity(type, new ResourceLocation(NTMDoors.MODID, name));
    }

    public void init(FMLInitializationEvent event) {
        NetworkRegistry.INSTANCE.registerGuiHandler(NTMDoors.INSTANCE, new GuiHandler());
        // Items are registered by now; expose the concrete and HL sets to recipes without Groovy.
        ConcreteOreDict.register();
        HLBlocksOreDict.register();
    }

    public void postInit(FMLPostInitializationEvent event) {
        // Chisel is a required dependency, but the guard keeps a dev run without it from loading
        // the API class before Forge reports the missing mod.
        if (Loader.isModLoaded("chisel")) {
            ChiselIntegration.postInit(event);
        }
    }

    /** Client only: open the Relay Key naming screen. The server never needs to know about it. */
    public void openRelayKeyNameGui(EnumHand hand) {
    }

    /** Client only: open the read-only private-key viewer for a held card. */
    public void openKeycardViewerGui(ItemStack stack) {
    }

    /** Client only: open the security code screen for a scanner bound to doors. */
    public void openScannerCodeGui(BlockPos pos) {
    }

    /** Client only: open the scanner console directly, used by the Creative Keycard. */
    public void openScannerKeysGui(BlockPos pos) {
    }

    /** Client only: open the Redstone Gate screen. */
    public void openRedstoneGateGui(BlockPos pos) {
    }

    /** Client only: open the keypad security-code screen. */
    public void openKeypadSecurityCodeGui(BlockPos pos) {
    }

    /** Client only: open the twenty-page editor for a held Documents Folder. */
    public void openDocumentsFolderGui(EnumHand hand) {
    }

    /** Client only: open the Code Safe keypad. */
    public void openSafeCodeGui(BlockPos pos) {
    }

    /** Client only: open the Biometric Safe card-read screen. */
    public void openSafeBiometricGui(BlockPos pos) {
    }

    /** Client only: open the Speaker toggle screen. */
    public void openSpeakerGui(BlockPos pos) {
    }

    /** Client only: open the Motion Sensor console. */
    public void openMotionSensorGui(BlockPos pos) {
    }

    /** Client only: show or hide the blue bound-area projection for one sensor. */
    public void toggleMotionSensorOverlay(BlockPos pos) {
    }
}
