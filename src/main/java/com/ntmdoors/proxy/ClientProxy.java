package com.ntmdoors.proxy;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.client.GuiDeviceName;
import com.ntmdoors.client.GuiDocumentsFolder;
import com.ntmdoors.client.GuiKeycardViewer;
import com.ntmdoors.client.GuiKeypadSecurityCode;
import com.ntmdoors.client.GuiMonitor;
import com.ntmdoors.client.GuiMotionSensor;
import com.ntmdoors.client.GuiNotepad;
import com.ntmdoors.client.GuiRedstoneGate;
import com.ntmdoors.client.GuiSpatialDisrupter;
import com.ntmdoors.client.GuiWeaponsRelayKey;
import com.ntmdoors.network.MessageMonitorState;
import com.ntmdoors.client.MotionSensorOverlay;
import com.ntmdoors.client.RenderMotionSensorArea;
import com.ntmdoors.client.GuiRelayKeyName;
import com.ntmdoors.client.GuiSafeBiometric;
import com.ntmdoors.client.GuiSafeCode;
import com.ntmdoors.client.GuiScannerCode;
import com.ntmdoors.client.GuiScannerKeys;
import com.ntmdoors.client.GuiSpeaker;
import com.ntmdoors.client.GuiUndefinedRelayKey;
import com.ntmdoors.client.KeyframeAnimation;
import com.ntmdoors.client.ObjModel;
import com.ntmdoors.client.RenderDoor;
import com.ntmdoors.client.RenderDoorItem;
import com.ntmdoors.client.RenderDoorKeypad;
import com.ntmdoors.client.RenderKeycardProgrammer;
import com.ntmdoors.client.RenderKeycardScanner;
import com.ntmdoors.client.RenderMonitorDesk;
import com.ntmdoors.door.DoorDecl;
import com.ntmdoors.door.DoorDecls;
import com.ntmdoors.door.ObjBounds;
import com.ntmdoors.item.ItemDocumentsFolder;
import com.ntmdoors.item.ItemUndefinedRelayKey;
import com.ntmdoors.monitor.MonitorDevice;
import com.ntmdoors.monitor.NotepadDocument;
import com.ntmdoors.monitor.SecurityNetSummary;
import com.ntmdoors.tile.TileEntityDoorGeneric;
import com.ntmdoors.tile.TileEntityDoorKeypad;
import com.ntmdoors.tile.TileEntityKeycardProgrammer;
import com.ntmdoors.tile.TileEntityKeycardScanner;
import com.ntmdoors.tile.TileEntityMonitorDesk;
import com.ntmdoors.tile.TileEntityMotionSensor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.List;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityDoorGeneric.class, new RenderDoor());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityDoorKeypad.class, new RenderDoorKeypad());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityKeycardScanner.class, new RenderKeycardScanner());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityKeycardProgrammer.class, new RenderKeycardProgrammer());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityMotionSensor.class, new RenderMotionSensorArea());
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityMonitorDesk.class, new RenderMonitorDesk());
    }

    @Override
    public void openRelayKeyNameGui(EnumHand hand) {
        Minecraft.getMinecraft().displayGuiScreen(new GuiRelayKeyName(hand));
    }

    @Override
    public void openUndefinedRelayKeyGui(EnumHand hand) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;
        ItemStack stack = mc.player.getHeldItem(hand);
        if (stack.getItem() instanceof ItemUndefinedRelayKey) {
            mc.displayGuiScreen(new GuiUndefinedRelayKey(hand));
        }
    }

    @Override
    public void openKeycardViewerGui(ItemStack stack) {
        Minecraft.getMinecraft().displayGuiScreen(new GuiKeycardViewer(stack));
    }

    @Override
    public void openScannerCodeGui(BlockPos pos) {
        Minecraft.getMinecraft().displayGuiScreen(new GuiScannerCode(pos));
    }

    @Override
    public void openScannerKeysGui(BlockPos pos) {
        Minecraft.getMinecraft().displayGuiScreen(new GuiScannerKeys(pos));
    }

    @Override
    public void openRedstoneGateGui(BlockPos pos) {
        Minecraft.getMinecraft().displayGuiScreen(new GuiRedstoneGate(pos));
    }

    @Override
    public void openKeypadSecurityCodeGui(BlockPos pos) {
        Minecraft.getMinecraft().displayGuiScreen(new GuiKeypadSecurityCode(pos));
    }

    @Override
    public void openDocumentsFolderGui(EnumHand hand) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;
        ItemStack stack = mc.player.getHeldItem(hand);
        if (stack.getItem() instanceof ItemDocumentsFolder) {
            mc.displayGuiScreen(new GuiDocumentsFolder(hand, stack));
        }
    }

    @Override
    public void openSafeCodeGui(BlockPos pos) {
        Minecraft.getMinecraft().displayGuiScreen(new GuiSafeCode(pos));
    }

    @Override
    public void openSafeBiometricGui(BlockPos pos) {
        Minecraft.getMinecraft().displayGuiScreen(new GuiSafeBiometric(pos));
    }

    @Override
    public void openSpeakerGui(BlockPos pos) {
        Minecraft.getMinecraft().displayGuiScreen(new GuiSpeaker(pos));
    }

    @Override
    public void openMotionSensorGui(BlockPos pos) {
        Minecraft.getMinecraft().displayGuiScreen(new GuiMotionSensor(pos));
    }

    @Override
    public void openMonitorGui(BlockPos pos, boolean hasPassword, boolean passwordSkipped,
                               int screenIndex) {
        Minecraft.getMinecraft().displayGuiScreen(
                new GuiMonitor(pos, hasPassword, passwordSkipped, screenIndex));
    }

    @Override
    public void handleMonitorState(BlockPos pos, int result, boolean hasPassword,
                                   boolean passwordSkipped, int screenIndex) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(() -> {
            if (result == MessageMonitorState.OPEN) {
                openMonitorGui(pos, hasPassword, passwordSkipped, screenIndex);
            } else if (mc.currentScreen instanceof GuiMonitor) {
                ((GuiMonitor) mc.currentScreen).handleServerResult(result, hasPassword);
            }
        });
    }

    @Override
    public void handleMonitorDevices(BlockPos pos, int netId, String netName,
                                     String linkedKeyName, int eventMask,
                                     List<MonitorDevice> devices) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(() -> {
            if (mc.currentScreen instanceof GuiMonitor) {
                ((GuiMonitor) mc.currentScreen).handleDevices(
                        pos, netId, netName, linkedKeyName, eventMask, devices);
            }
        });
    }

    @Override
    public void handleNotepadData(BlockPos pos, int focusId,
                                  List<NotepadDocument> documents) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(() -> {
            if (mc.currentScreen instanceof GuiNotepad) {
                ((GuiNotepad) mc.currentScreen).handleData(pos, focusId, documents);
            }
        });
    }

    @Override
    public void handleMonitorNets(BlockPos pos, int createdNetId, List<SecurityNetSummary> nets) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(() -> {
            if (mc.currentScreen instanceof GuiMonitor) {
                ((GuiMonitor) mc.currentScreen).handleNets(pos, createdNetId, nets);
            }
        });
    }

    @Override
    public void handleMonitorKeyResult(BlockPos pos, int netId, int result, String keyName) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(() -> {
            if (mc.currentScreen instanceof GuiMonitor) {
                ((GuiMonitor) mc.currentScreen).handleKeyResult(pos, netId, result, keyName);
            }
        });
    }

    @Override
    public void openDeviceNameGui(EnumHand hand, BlockPos pos, String defaultName, String typeName) {
        Minecraft.getMinecraft().displayGuiScreen(
                new GuiDeviceName(hand, pos, defaultName, typeName));
    }

    @Override
    public void openLinkedDeviceGui(MonitorDevice device) {
        if (device == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        switch (device.type) {
            case KEYPAD:
                mc.displayGuiScreen(new GuiKeypadSecurityCode(device.pos));
                break;
            case SCANNER:
                mc.displayGuiScreen(new GuiScannerCode(device.pos));
                break;
            case MOTION_SENSOR:
                mc.displayGuiScreen(new GuiMotionSensor(device.pos));
                break;
            case REDSTONE_GATE:
                mc.displayGuiScreen(new GuiRedstoneGate(device.pos));
                break;
            case SPEAKER:
                mc.displayGuiScreen(new GuiSpeaker(device.pos));
                break;
            default:
                break;
        }
    }

    @Override
    public void handleMonitorDoorScannerResult(BlockPos doorPos, BlockPos scannerPos, int result) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(() -> {
            if (mc.currentScreen instanceof GuiMonitor) {
                ((GuiMonitor) mc.currentScreen).handleDoorScanner(doorPos, scannerPos, result);
            }
        });
    }

    @Override
    public void toggleMotionSensorOverlay(BlockPos pos) {
        MotionSensorOverlay.toggle(pos);
    }

    @Override
    public void openWeaponsRelayKeyGui(EnumHand hand) {
        Minecraft.getMinecraft().displayGuiScreen(new GuiWeaponsRelayKey(hand));
    }

    @Override
    public void openSpatialDisrupterGui(BlockPos pos) {
        Minecraft.getMinecraft().displayGuiScreen(new GuiSpatialDisrupter(pos));
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        // drop cached models / display lists whenever resources reload (F3+T)
        ((IReloadableResourceManager) Minecraft.getMinecraft().getResourceManager())
                .registerReloadListener(resourceManager -> {
                    ObjModel.clear();
                    KeyframeAnimation.clear();
                    ObjBounds.clear();
                    RenderDoorItem.clearCache();
                    logMissingAssets(resourceManager);
                });
        logMissingAssets(Minecraft.getMinecraft().getResourceManager());
    }

    /** Prints the exact resource paths that are absent, instead of leaving only a purple/black texture in game. */
    private static void logMissingAssets(IResourceManager resources) {
        for (DoorDecl d : DoorDecls.ALL) {
            String owner = "door '" + d.name + "'";
            check(resources, d.model, "model", owner);
            check(resources, d.texture, "texture", owner);
            check(resources, d.keyframeAnim, "keyframe animation", owner);
            for (ResourceLocation partTexture : d.partTextures.values()) {
                check(resources, partTexture, "part texture", owner);
            }
            for (List<ResourceLocation> skins : d.partSkins.values()) {
                for (ResourceLocation skin : skins) check(resources, skin, "part skin", owner);
            }
            for (ResourceLocation skin : d.skins) check(resources, skin, "skin", owner);
        }
        check(resources, RenderDoorKeypad.MODEL, "model", "the keypad");
        check(resources, RenderDoorKeypad.TEX_KEYPAD, "texture", "the keypad");
        check(resources, RenderDoorKeypad.TEX_SUCCESS, "texture", "the keypad");
        check(resources, RenderDoorKeypad.TEX_ERROR, "texture", "the keypad");
        check(resources, RenderKeycardScanner.MODEL, "model", "the keycard scanner");
        check(resources, RenderKeycardScanner.TEXTURE, "texture", "the keycard scanner");
        check(resources, RenderKeycardProgrammer.MODEL, "model", "the keycard programmer");
        check(resources, RenderKeycardProgrammer.TEXTURE, "texture", "the keycard programmer");
    }

    private static void check(IResourceManager resources, @Nullable ResourceLocation location, String kind, String owner) {
        if (location == null || exists(resources, location)) return;
        NTMDoors.LOGGER.warn("Missing {} for {}: {}", kind, owner, location);
    }

    private static boolean exists(IResourceManager resources, ResourceLocation location) {
        try {
            resources.getResource(location).getInputStream().close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
