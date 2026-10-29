package com.ntmdoors.client;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.block.BlockDoorGeneric;
import com.ntmdoors.item.ItemRfidCard;
import com.ntmdoors.registry.ModContent;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.StateMapperBase;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = NTMDoors.MODID, value = Side.CLIENT)
public class ClientModels {

    private static final ModelResourceLocation EMPTY = new ModelResourceLocation(NTMDoors.MODID + ":door_empty", "normal");

    @SubscribeEvent
    public static void onModelRegistry(ModelRegistryEvent e) {
        for (BlockDoorGeneric block : ModContent.DOORS) {
            invisible(block);
            // Like HBM's ItemRenderLibraryDoors: draw the OBJ in the inventory instead of a flat icon.
            Item item = Item.getItemFromBlock(block);
            item.setTileEntityItemStackRenderer(RenderDoorItem.INSTANCE);
            inventory(item);
        }
        invisible(ModContent.KEYPAD);
        inventory(Item.getItemFromBlock(ModContent.KEYPAD));
        // The scanner keeps a normal blockstate instead of the shared invisible mapper, so the item
        // also resolves through the standard blockstate "inventory" variant. That is a second route
        // to the same model in case the custom item location is ignored.
        Item scannerItem = Item.getItemFromBlock(ModContent.KEYCARD_SCANNER);
        scannerItem.setTileEntityItemStackRenderer(RenderKeycardScannerItem.INSTANCE);
        ModelResourceLocation scannerModel = new ModelResourceLocation(NTMDoors.MODID + ":keycard_scanner", "inventory");
        ModelLoader.setCustomMeshDefinition(scannerItem, stack -> scannerModel);
        ModelLoader.setCustomModelResourceLocation(scannerItem, 0, scannerModel);
        NTMDoors.LOGGER.info("Keycard Scanner item {} -> {}", scannerItem.getRegistryName(), scannerModel);
        inventory(ModContent.SCREWDRIVER);
        for (ItemRfidCard card : ModContent.RFID_CARDS) inventory(card);
        inventory(ModContent.CREATIVE_KEYCARD);
        inventory(ModContent.RELAY_KEY);
    }

    /**
     * Verifies the scanner's item model actually baked as a builtin/entity model. That flag is what
     * makes Forge call {@link RenderKeycardScannerItem}; when it is false the item draws nothing at
     * all, which is indistinguishable from a renderer that never ran.
     */
    @SubscribeEvent
    public static void onModelBake(ModelBakeEvent e) {
        ModelResourceLocation location = new ModelResourceLocation(NTMDoors.MODID + ":keycard_scanner", "inventory");
        IBakedModel baked = e.getModelRegistry().getObject(location);
        if (baked == null || !baked.isBuiltInRenderer()) {
            NTMDoors.LOGGER.warn("Keycard Scanner item model {} did not bake as builtin/entity ({}): "
                            + "the inventory and hand item will be blank",
                    location, baked == null ? "missing" : baked.getClass().getName());
            return;
        }
        NTMDoors.LOGGER.info("Keycard Scanner item model {} baked as builtin/entity", location);
    }

    /** Every state of the block maps to the empty model; the TESR draws the real thing. */
    private static void invisible(Block block) {
        ModelLoader.setCustomStateMapper(block, new StateMapperBase() {
            @Override
            protected ModelResourceLocation getModelResourceLocation(IBlockState state) {
                return EMPTY;
            }
        });
    }

    /** Sets up the standard item model location for an item. */
    private static void inventory(Item item) {
        if (item == null || item.getRegistryName() == null) return;
        ModelLoader.setCustomModelResourceLocation(item, 0,
                new ModelResourceLocation(item.getRegistryName(), "inventory"));
    }
}
