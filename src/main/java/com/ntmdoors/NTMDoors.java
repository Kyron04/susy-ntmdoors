package com.ntmdoors;

import com.ntmdoors.proxy.CommonProxy;
import com.ntmdoors.registry.ModContent;
import com.ntmdoors.util.Names;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = NTMDoors.MODID, name = "NTM Doors", version = "1.0.22", acceptedMinecraftVersions = "[1.12.2]")
public class NTMDoors {

    public static final String MODID = "ntmdoors";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    @SidedProxy(clientSide = "com.ntmdoors.proxy.ClientProxy", serverSide = "com.ntmdoors.proxy.CommonProxy")
    public static CommonProxy proxy;

    public static final CreativeTabs TAB = new CreativeTabs(MODID) {
        @Override
        public ItemStack createIcon() {
            return ModContent.DOORS.isEmpty() ? new ItemStack(Items.IRON_DOOR)
                    : new ItemStack(Item.getItemFromBlock(ModContent.DOORS.get(0)));
        }

        @Override
        public String getTranslationKey() {
            // Fallback so the tab name never shows a raw translation key.
            String key = "itemGroup." + getTabLabel();
            return Names.canTranslate(key) ? key : "NTM Doors";
        }
    };

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }
}
