package com.ntmdoors.gui;

import com.ntmdoors.client.GuiKeycardProgrammer;
import com.ntmdoors.tile.TileEntityKeycardProgrammer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

import javax.annotation.Nullable;

/**
 * Opens the keycard programmer GUI. Only one GUI id is used at the moment.
 */
public class GuiHandler implements IGuiHandler {

    public static final int KEYCARD_PROGRAMMER = 0;

    @Nullable
    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id != KEYCARD_PROGRAMMER) return null;
        TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
        if (!(te instanceof TileEntityKeycardProgrammer)) return null;
        return new ContainerKeycardProgrammer(player.inventory, (TileEntityKeycardProgrammer) te);
    }

    @Nullable
    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id != KEYCARD_PROGRAMMER) return null;
        TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
        if (!(te instanceof TileEntityKeycardProgrammer)) return null;
        return new GuiKeycardProgrammer(player.inventory, (TileEntityKeycardProgrammer) te);
    }
}
