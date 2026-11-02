package com.ntmdoors.compat;

import com.ntmdoors.concrete.ConcreteGroups;
import net.minecraft.block.state.IBlockState;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import team.chisel.api.carving.CarvingUtils;
import team.chisel.api.carving.ICarvingGroup;
import team.chisel.api.carving.ICarvingRegistry;

/**
 * Registers the shared concrete families with the Chisel mod's carving registry. The family
 * definitions live in {@link ConcreteGroups}, which also drives shift-scroll cycling.
 */
public final class ChiselIntegration {

    private ChiselIntegration() {
    }

    public static void postInit(FMLPostInitializationEvent event) {
        ICarvingRegistry registry = CarvingUtils.getChiselRegistry();
        if (registry == null) return;

        for (ConcreteGroups.Group group : ConcreteGroups.all()) {
            ICarvingGroup carving = registry.getGroup(group.name());
            if (carving == null) {
                carving = CarvingUtils.getDefaultGroupFor(group.name());
                if (carving == null) continue;
                registry.addGroup(carving);
            }
            int order = 0;
            for (IBlockState state : group.states()) {
                registry.addVariation(group.name(), state, order++);
            }
        }
    }
}
