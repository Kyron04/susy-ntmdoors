package com.ntmdoors.client;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.block.door.DoorVoxelShapes;
import com.ntmdoors.block.door.ModDoors;
import com.ntmdoors.block.entity.ModBlockEntities;
import com.ntmdoors.client.render.DoorBlockEntityRenderer;
import com.ntmdoors.client.render.DoorItemRenderer;
import com.ntmdoors.client.render.KeycardProgrammerBlockEntityRenderer;
import com.ntmdoors.client.render.KeycardScannerBlockEntityRenderer;
import com.ntmdoors.client.render.KeyframeAnimation;
import com.ntmdoors.client.render.KeypadBlockEntityRenderer;
import com.ntmdoors.client.render.ObjModel;
import com.ntmdoors.client.screen.KeycardProgrammerScreen;
import com.ntmdoors.network.ModMessages;
import com.ntmdoors.screen.ModScreenHandlerTypes;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

/**
 * Client entrypoint for NTM Doors on Fabric 1.20.1.
 */
@Environment(EnvType.CLIENT)
public class NTMDoorsClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        NTMDoors.LOGGER.info("Initializing NTM Doors Client (Fabric 1.20.1)");

        // Register BlockEntityRenderers
        BlockEntityRendererRegistry.register(ModDoors.DOOR_BLOCK_ENTITY, DoorBlockEntityRenderer::new);
        BlockEntityRendererRegistry.register(ModBlockEntities.KEYPAD, KeypadBlockEntityRenderer::new);
        BlockEntityRendererRegistry.register(ModBlockEntities.KEYCARD_SCANNER, KeycardScannerBlockEntityRenderer::new);
        BlockEntityRendererRegistry.register(ModBlockEntities.KEYCARD_PROGRAMMER, KeycardProgrammerBlockEntityRenderer::new);

        // Register Screen Handlers
        HandledScreens.register(ModScreenHandlerTypes.KEYCARD_PROGRAMMER, KeycardProgrammerScreen::new);

        // Register S2C Packets
        ModMessages.registerS2CPackets();

        // Register resource reload listener to flush model, animation, and bounds caches on F3+T
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public Identifier getFabricId() {
                        return Identifier.of(NTMDoors.MOD_ID, "model_cache_reloader");
                    }

                    @Override
                    public void reload(ResourceManager manager) {
                        ObjModel.clearCache();
                        KeyframeAnimation.clearCache();
                        DoorItemRenderer.clearCache();
                        DoorVoxelShapes.clearCache();
                    }
                }
        );
    }
}
