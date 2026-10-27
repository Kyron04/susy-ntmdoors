package com.ntmdoors.proxy;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.client.KeyframeAnimation;
import com.ntmdoors.client.ObjModel;
import com.ntmdoors.client.RenderDoor;
import com.ntmdoors.client.RenderDoorKeypad;
import com.ntmdoors.door.DoorDecl;
import com.ntmdoors.door.DoorDecls;
import com.ntmdoors.tile.TileEntityDoorGeneric;
import com.ntmdoors.tile.TileEntityDoorKeypad;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
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
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        // drop cached models / display lists whenever resources reload (F3+T)
        ((IReloadableResourceManager) Minecraft.getMinecraft().getResourceManager())
                .registerReloadListener(resourceManager -> {
                    ObjModel.clear();
                    KeyframeAnimation.clear();
                    logMissingAssets(resourceManager);
                });
        logMissingAssets(Minecraft.getMinecraft().getResourceManager());
    }

    /** Prints the exact resource paths that are absent, instead of leaving only a purple/black texture in game. */
    private static void logMissingAssets(IResourceManager resources) {
        for (DoorDecl d : DoorDecls.ALL) {
            check(resources, d.model, "model", d);
            check(resources, d.texture, "texture", d);
            check(resources, d.keyframeAnim, "keyframe animation", d);
            for (ResourceLocation partTexture : d.partTextures.values()) {
                check(resources, partTexture, "part texture", d);
            }
            for (List<ResourceLocation> skins : d.partSkins.values()) {
                for (ResourceLocation skin : skins) check(resources, skin, "part skin", d);
            }
            for (ResourceLocation skin : d.skins) check(resources, skin, "skin", d);
        }
    }

    private static void check(IResourceManager resources, @Nullable ResourceLocation location, String kind, DoorDecl door) {
        if (location == null || exists(resources, location)) return;
        NTMDoors.LOGGER.warn("Missing {} for door '{}': {}", kind, door.name, location);
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
