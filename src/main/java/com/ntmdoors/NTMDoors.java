package com.ntmdoors;

import com.ntmdoors.block.ModBlocks;
import com.ntmdoors.block.door.ModDoors;
import com.ntmdoors.block.entity.ModBlockEntities;
import com.ntmdoors.item.ModItemGroups;
import com.ntmdoors.item.ModItems;
import com.ntmdoors.network.ModMessages;
import com.ntmdoors.screen.ModScreenHandlerTypes;
import com.ntmdoors.sound.ModSounds;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entrypoint for NTM Doors on Fabric 1.20.1.
 */
public class NTMDoors implements ModInitializer {

    public static final String MOD_ID = "ntmdoors";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing NTM Doors (Fabric 1.20.1)");

        // 1. Register SoundEvents
        ModSounds.registerModSounds();

        // 2. Register Creative Tab
        ModItemGroups.registerItemGroups();

        // 3. Register Security Hardware Blocks & Entities
        ModBlocks.registerModBlocks();
        ModBlockEntities.registerModBlockEntities();

        // 4. Register Access Cards, Tools, and Block Items
        ModItems.registerModItems();

        // 5. Register Multiblock Doors & BlockEntities
        ModDoors.registerModDoors();

        // 6. Register ScreenHandlers
        ModScreenHandlerTypes.registerScreenHandlers();

        // 7. Register C2S Networking Packets
        ModMessages.registerC2SPackets();

        // 8. Register Fabric API Interaction Events & Anti-Tamper Callbacks
        com.ntmdoors.weapons.WeaponsBindingEvents.register();
    }
}
