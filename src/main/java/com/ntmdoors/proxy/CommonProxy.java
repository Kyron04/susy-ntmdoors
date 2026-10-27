package com.ntmdoors.proxy;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.door.DoorDecl;
import com.ntmdoors.door.DoorDecls;
import com.ntmdoors.tile.TileEntityDoorGeneric;
import com.ntmdoors.tile.TileEntityDoorKeypad;
import com.ntmdoors.tile.TileEntityDoorPart;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        GameRegistry.registerTileEntity(TileEntityDoorGeneric.class, new ResourceLocation(NTMDoors.MODID, "door_core"));
        GameRegistry.registerTileEntity(TileEntityDoorPart.class, new ResourceLocation(NTMDoors.MODID, "door_part"));
        GameRegistry.registerTileEntity(TileEntityDoorKeypad.class, new ResourceLocation(NTMDoors.MODID, "door_keypad"));
        // Read the OBJ group bounds used by moving collision boxes now, so the first collision
        // query on a door never has to touch the filesystem.
        for (DoorDecl d : DoorDecls.ALL) d.prepareGeometry();
    }

    public void init(FMLInitializationEvent event) {
    }
}
