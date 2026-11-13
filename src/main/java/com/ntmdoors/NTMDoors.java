package com.ntmdoors;

import com.ntmdoors.block.BlockDoorGeneric;
import com.ntmdoors.block.BlockDesk;
import com.ntmdoors.block.BlockMonitorOnly;
import com.ntmdoors.concrete.ConcreteSet;
import com.ntmdoors.hlblocks.HLBlocksContent;
import com.ntmdoors.item.ItemRfidCard;
import com.ntmdoors.proxy.CommonProxy;
import com.ntmdoors.registry.ModContent;
import com.ntmdoors.util.Names;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

@Mod(modid = NTMDoors.MODID, name = "NTM Doors", version = "1.0.58",
        acceptedMinecraftVersions = "[1.12.2]", dependencies = "required-after:chisel")
public class NTMDoors {

    public static final String MODID = "ntmdoors";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    @Mod.Instance(NTMDoors.MODID)
    public static NTMDoors INSTANCE;

    @SidedProxy(clientSide = "com.ntmdoors.proxy.ClientProxy", serverSide = "com.ntmdoors.proxy.CommonProxy")
    public static CommonProxy proxy;

    public static final CreativeTabs TAB = new CreativeTabs(MODID) {
        @Override
        public ItemStack createIcon() {
            return ModContent.DOORS.isEmpty() ? new ItemStack(Items.IRON_DOOR)
                    : new ItemStack(Item.getItemFromBlock(ModContent.DOORS.get(0)));
        }

        /**
         * Fixed order instead of registry order: normal doors, their locked and security twins, then
         * the tools and cards. Keeping the lists separate means the order does not depend on
         * registration.
         */
        @Override
        public void displayAllRelevantItems(NonNullList<ItemStack> items) {
            for (BlockDoorGeneric door : displayOrder(ModContent.NORMAL_DOORS)) items.add(new ItemStack(door));
            for (BlockDoorGeneric door : displayOrder(ModContent.LOCKED_DOORS)) items.add(new ItemStack(door));
            for (BlockDoorGeneric door : displayOrder(ModContent.SECURITY_DOORS)) items.add(new ItemStack(door));
            items.add(new ItemStack(ModContent.SCREWDRIVER));
            items.add(new ItemStack(ModContent.KEYPAD));
            items.add(new ItemStack(ModContent.KEYCARD_SCANNER));
            items.add(new ItemStack(ModContent.KEYCARD_PROGRAMMER));
            items.add(new ItemStack(ModContent.SAFE));
            items.add(new ItemStack(ModContent.BIOMETRIC_SAFE));
            for (ItemRfidCard card : ModContent.RFID_CARDS) items.add(new ItemStack(card));
            for (ItemRfidCard card : ModContent.SCP_CARDS) items.add(new ItemStack(card));
            items.add(new ItemStack(ModContent.DEVELOPER_KEYCARD));
            for (ItemRfidCard card : ModContent.LEVEL_ACCESS_CARDS) items.add(new ItemStack(card));
            items.add(new ItemStack(ModContent.BIOMETRIC_KEYCARD));
            items.add(new ItemStack(ModContent.OPERATOR_KEYCARD));
            items.add(new ItemStack(ModContent.BIOMETRIC_OPERATOR_KEYCARD));
            items.add(new ItemStack(ModContent.CREATIVE_KEYCARD));
            items.add(new ItemStack(ModContent.RELAY_KEY));
            items.add(new ItemStack(ModContent.BIOMETRIC_RELAY_KEY));
            items.add(new ItemStack(ModContent.KEYCARD_HOLDER));
            items.add(new ItemStack(ModContent.DOCUMENTS_FOLDER));
            items.add(new ItemStack(ModContent.SAFE_BINDING_KEY));
            items.add(new ItemStack(ModContent.REDSTONE_RELAY_KEY));
            items.add(new ItemStack(ModContent.MOTION_RELAY_KEY));
            items.add(new ItemStack(ModContent.TERMINAL_RELAY_KEY));
            items.add(new ItemStack(ModContent.TERMINAL_BACKUP_KEY));
            items.add(new ItemStack(ModContent.WEAPONS_RELAY_KEY));
            items.add(new ItemStack(ModContent.UNDEFINED_RELAY_KEY));
            items.add(new ItemStack(ModContent.REDSTONE_GATE));
            items.add(new ItemStack(ModContent.SPEAKER));
            items.add(new ItemStack(ModContent.MOTION_SENSOR));
            items.add(new ItemStack(ModContent.MONITOR_SETUP));
            items.add(new ItemStack(ModContent.TRIPLE_MONITOR_SETUP));
            items.add(new ItemStack(ModContent.DUAL_MONITOR_SETUP));
            items.add(new ItemStack(ModContent.DUAL_MONITOR_SETUP_2X2));
            items.add(new ItemStack(ModContent.SINGLE_MONITOR_SETUP));
            items.add(new ItemStack(ModContent.SPATIAL_DISRUPTER));
            items.add(new ItemStack(ModContent.THERMITE_CHARGE));
            items.add(new ItemStack(ModContent.REDSTONE_MONOCLE));
            ConcreteSet.addCreativeStacks(items);
            HLBlocksContent.addCreativeStacks(items);
            // New crafting components and desk-only blocks go at the end of the tab.
            items.add(new ItemStack(ModContent.ID_CHIP));
            items.add(new ItemStack(ModContent.MONITOR_SCREEN));
            items.add(new ItemStack(ModContent.UNCASED_USB));
            items.add(new ItemStack(ModContent.CIRCUIT_BOARD));
            items.add(new ItemStack(ModContent.CARD_READER));
            items.add(new ItemStack(ModContent.RFID_CHIP));
            for (BlockDesk desk : ModContent.DESKS) items.add(new ItemStack(desk));
            for (BlockMonitorOnly monitor : ModContent.MONITORS) items.add(new ItemStack(monitor));
        }

        @Override
        public String getTranslationKey() {
            // Fallback so the tab name never shows a raw translation key.
            String key = "itemGroup." + getTabLabel();
            return Names.canTranslate(key) ? key : "NTM Doors";
        }
    };

    /** Moves each 3x3 door directly after its 2x2 counterpart for creative-tab display. */
    private static List<BlockDoorGeneric> displayOrder(List<BlockDoorGeneric> doors) {
        List<BlockDoorGeneric> out = new ArrayList<>(doors);
        for (int i = 0; i < out.size(); i++) {
            BlockDoorGeneric door = out.get(i);
            String name = door.decl.name;
            if (!name.endsWith("_3x3")) continue;
            String base = name.substring(0, name.length() - 4);
            int baseIndex = -1;
            for (int j = 0; j < out.size(); j++) {
                if (out.get(j).decl.name.equals(base)) {
                    baseIndex = j;
                    break;
                }
            }
            if (baseIndex >= 0 && baseIndex < i) {
                out.remove(i);
                out.add(baseIndex + 1, door);
                i = baseIndex + 1;
            }
        }
        return out;
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }
}
