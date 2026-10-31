package com.ntmdoors.proxy;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.client.GuiKeycardViewer;
import com.ntmdoors.client.GuiRelayKeyName;
import com.ntmdoors.client.GuiScannerCode;
import com.ntmdoors.client.KeyframeAnimation;
import com.ntmdoors.client.ObjModel;
import com.ntmdoors.client.ProgrammerGuiEvents;
import com.ntmdoors.client.RenderDoor;
import com.ntmdoors.client.RenderDoorKeypad;
import com.ntmdoors.client.RenderKeycardProgrammer;
import com.ntmdoors.client.RenderKeycardScanner;
import com.ntmdoors.door.DoorDecl;
import com.ntmdoors.door.DoorDecls;
import com.ntmdoors.door.ObjBounds;
import com.ntmdoors.tile.TileEntityDoorGeneric;
import com.ntmdoors.tile.TileEntityDoorKeypad;
import com.ntmdoors.tile.TileEntityKeycardProgrammer;
import com.ntmdoors.tile.TileEntityKeycardScanner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.common.MinecraftForge;
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
    }

    @Override
    public void openRelayKeyNameGui(EnumHand hand) {
        Minecraft.getMinecraft().displayGuiScreen(new GuiRelayKeyName(hand));
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
    public void init(FMLInitializationEvent event) {
        super.init(event);
        // Explicit registration so the programmer tooltip always draws after JEI/HEI.
        MinecraftForge.EVENT_BUS.register(ProgrammerGuiEvents.class);
        // drop cached models / display lists whenever resources reload (F3+T)
        ((IReloadableResourceManager) Minecraft.getMinecraft().getResourceManager())
                .registerReloadListener(resourceManager -> {
                    ObjModel.clear();
                    KeyframeAnimation.clear();
                    ObjBounds.clear();
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
