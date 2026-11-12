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
import com.ntmdoors.client.render.MonitorDeskBlockEntityRenderer;
import com.ntmdoors.client.render.MotionSensorBlockEntityRenderer;
import com.ntmdoors.client.screen.KeycardHolderScreen;
import com.ntmdoors.client.screen.KeycardProgrammerScreen;
import com.ntmdoors.client.screen.SafeScreen;
import com.ntmdoors.client.screen.TerminalBackupScreen;
import com.ntmdoors.door.DoorDecl;
import com.ntmdoors.item.ItemDoorBlock;
import com.ntmdoors.network.ModMessages;
import com.ntmdoors.screen.ModScreenHandlerTypes;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.Map;

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
        BlockEntityRendererRegistry.register(ModBlockEntities.MOTION_SENSOR, MotionSensorBlockEntityRenderer::new);
        BlockEntityRendererRegistry.register(ModBlockEntities.MONITOR_DESK, MonitorDeskBlockEntityRenderer::new);

        // Register Screen Handlers
        HandledScreens.register(ModScreenHandlerTypes.KEYCARD_PROGRAMMER, KeycardProgrammerScreen::new);
        HandledScreens.register(ModScreenHandlerTypes.SAFE, SafeScreen::new);
        HandledScreens.register(ModScreenHandlerTypes.TERMINAL_BACKUP, TerminalBackupScreen::new);
        HandledScreens.register(ModScreenHandlerTypes.KEYCARD_HOLDER, KeycardHolderScreen::new);

        // Register Dynamic Item Renderers for all Multiblock Doors
        for (Map.Entry<String, ItemDoorBlock> entry : ModDoors.DOOR_ITEMS.entrySet()) {
            ItemDoorBlock item = entry.getValue();
            DoorDecl decl = item.getDoorBlock().decl;
            DoorItemRenderer.registerProvider(item, new DoorItemRenderer.DoorItemModelProvider() {
                @Override
                public Identifier getModelId(ItemStack stack) {
                    return decl.model;
                }

                @Override
                public Identifier getTextureId(ItemStack stack) {
                    int skin = (stack.hasNbt() && stack.getNbt().contains("skin")) ? stack.getNbt().getInt("skin") : 0;
                    return decl.textureForPart(skin, null);
                }

                @Override
                public Identifier getTextureForGroup(ItemStack stack, String groupName) {
                    int skin = (stack.hasNbt() && stack.getNbt().contains("skin")) ? stack.getNbt().getInt("skin") : 0;
                    return decl.textureForPart(skin, groupName);
                }

                @Override
                public Collection<String> getItemGroups(ItemStack stack) {
                    return decl.itemGroups;
                }

                @Override
                public float getModelYaw(ItemStack stack) {
                    return decl.modelYaw;
                }
            });
            BuiltinItemRendererRegistry.INSTANCE.register(item, DoorItemRenderer.INSTANCE);
        }

        // Register Overlays
        RedstoneMonocleOverlay.init();

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
                        com.ntmdoors.client.display.DisplayImages.clear();
                    }
                }
        );

        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> com.ntmdoors.client.display.DisplayImages.clear()
        );
    }
}
