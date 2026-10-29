package com.ntmdoors.client;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.block.BlockDoorGeneric;
import com.ntmdoors.item.ItemRfidCard;
import com.ntmdoors.registry.ModContent;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.StateMapperBase;
import net.minecraft.item.Item;
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
        invisible(ModContent.KEYCARD_SCANNER);
        // Same item path as the doors: builtin/entity plus a TESR. A JSON block model in
        // models/item/ does not reach the renderer in this pack (it bakes as the generated item
        // model), while the TESR path draws the OBJ for the inventory, hand and ground forms.
        Item scannerItem = Item.getItemFromBlock(ModContent.KEYCARD_SCANNER);
        scannerItem.setTileEntityItemStackRenderer(RenderKeycardScannerItem.INSTANCE);
        inventory(scannerItem);
        inventory(ModContent.SCREWDRIVER);
        for (ItemRfidCard card : ModContent.RFID_CARDS) inventory(card);
        inventory(ModContent.CREATIVE_KEYCARD);
        inventory(ModContent.RELAY_KEY);
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
