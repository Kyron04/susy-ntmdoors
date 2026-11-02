package com.ntmdoors.client;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.block.BlockDoorGeneric;
import com.ntmdoors.concrete.ItemConcreteSlab;
import com.ntmdoors.hlblocks.ItemBlockGroup;
import com.ntmdoors.item.ItemBlockConcreteVariant;
import com.ntmdoors.registry.ModContent;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.StateMapperBase;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = NTMDoors.MODID, value = Side.CLIENT)
public class ClientModels {

    private static final ModelResourceLocation EMPTY =
            new ModelResourceLocation(NTMDoors.MODID + ":door_empty", "normal");

    @SubscribeEvent
    public static void onModelRegistry(ModelRegistryEvent e) {
        for (BlockDoorGeneric block : ModContent.DOORS) {
            invisible(block);
            // Like HBM's ItemRenderLibraryDoors: draw the OBJ in the inventory instead of a flat icon.
            Item.getItemFromBlock(block).setTileEntityItemStackRenderer(RenderDoorItem.INSTANCE);
        }
        invisible(ModContent.KEYPAD);
        invisible(ModContent.KEYCARD_PROGRAMMER);

        // Sweep the item registry rather than listing items by hand.
        for (Item item : Item.REGISTRY) {
            ResourceLocation name = item.getRegistryName();
            if (name == null || !NTMDoors.MODID.equals(name.getNamespace())) continue;
            if (item instanceof ItemBlockGroup) {
                hlGroup((ItemBlockGroup) item);
            } else if (item instanceof ItemConcreteSlab) {
                slabVariants((ItemConcreteSlab) item);
            } else if (item instanceof ItemBlockConcreteVariant) {
                variants((ItemBlockConcreteVariant) item);
            } else {
                inventory(item);
            }
        }
    }

    /** Metadata concrete cubes use one item model per variant. */
    private static void variants(ItemBlockConcreteVariant item) {
        for (int meta = 0; meta < item.variantCount(); meta++) {
            ModelLoader.setCustomModelResourceLocation(item, meta, new ModelResourceLocation(
                    new ResourceLocation(NTMDoors.MODID, item.modelName(meta)), "inventory"));
        }
    }

    /** Metadata slab items use the existing half-slab item model for each variant. */
    private static void slabVariants(ItemConcreteSlab item) {
        for (int meta = 0; meta < item.variantCount(); meta++) {
            ModelLoader.setCustomModelResourceLocation(item, meta, new ModelResourceLocation(
                    new ResourceLocation(NTMDoors.MODID, item.itemModel(meta)), "inventory"));
        }
    }

    /** HL group items point at their blockstate variant, so the inventory icon is the 3D block. */
    private static void hlGroup(ItemBlockGroup item) {
        for (int meta = 0; meta < item.getVariantCount(); meta++) {
            ModelLoader.setCustomModelResourceLocation(item, meta,
                    new ModelResourceLocation(item.getRegistryName(),
                            "variant=" + item.getVariantName(meta)));
        }
    }

    private static void invisible(Block block) {
        ModelLoader.setCustomStateMapper(block, new StateMapperBase() {
            @Override
            protected ModelResourceLocation getModelResourceLocation(IBlockState state) {
                return EMPTY;
            }
        });
    }

    private static void inventory(Item item) {
        if (item == null || item.getRegistryName() == null) return;
        ModelLoader.setCustomModelResourceLocation(item, 0,
                new ModelResourceLocation(item.getRegistryName(), "inventory"));
    }
}
