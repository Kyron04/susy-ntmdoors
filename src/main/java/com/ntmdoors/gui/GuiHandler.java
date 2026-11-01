package com.ntmdoors.gui;

import com.ntmdoors.client.GuiKeycardHolder;
import com.ntmdoors.client.GuiKeycardProgrammer;
import com.ntmdoors.client.GuiSafe;
import com.ntmdoors.gui.ContainerSafe;
import com.ntmdoors.item.ItemKeycardHolder;
import com.ntmdoors.tile.TileEntityKeycardProgrammer;
import com.ntmdoors.tile.TileEntitySafe;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

import javax.annotation.Nullable;

/**
 * Opens the mod's container screens. The programmer uses a block position; the carried Keycard
 * Holder reuses the coordinate fields to pass which hand holds it.
 */
public class GuiHandler implements IGuiHandler {

    public static final int KEYCARD_PROGRAMMER = 0;
    public static final int KEYCARD_HOLDER = 2;
    public static final int SAFE = 3;

    @Nullable
    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id == KEYCARD_PROGRAMMER) {
            TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
            if (!(te instanceof TileEntityKeycardProgrammer)) return null;
            return new ContainerKeycardProgrammer(player.inventory, (TileEntityKeycardProgrammer) te);
        }
        if (id == KEYCARD_HOLDER) {
            EnumHand hand = handFrom(x);
            if (hand == null) return null;
            ItemStack held = player.getHeldItem(hand);
            if (!(held.getItem() instanceof ItemKeycardHolder)) return null;
            return new ContainerKeycardHolder(player.inventory, player, held, hand);
        }
        if (id == SAFE) {
            TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
            if (!(te instanceof TileEntitySafe)) return null;
            return new ContainerSafe(player.inventory, (TileEntitySafe) te);
        }
        return null;
    }

    @Nullable
    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id == KEYCARD_PROGRAMMER) {
            TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
            if (!(te instanceof TileEntityKeycardProgrammer)) return null;
            return new GuiKeycardProgrammer(player.inventory, (TileEntityKeycardProgrammer) te);
        }
        if (id == KEYCARD_HOLDER) {
            EnumHand hand = handFrom(x);
            if (hand == null) return null;
            ItemStack held = player.getHeldItem(hand);
            if (!(held.getItem() instanceof ItemKeycardHolder)) return null;
            return new GuiKeycardHolder(player.inventory, player, held, hand);
        }
        if (id == SAFE) {
            TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
            if (!(te instanceof TileEntitySafe)) return null;
            return new GuiSafe(player.inventory, (TileEntitySafe) te);
        }
        return null;
    }

    @Nullable
    private static EnumHand handFrom(int index) {
        if (index == EnumHand.MAIN_HAND.ordinal()) return EnumHand.MAIN_HAND;
        if (index == EnumHand.OFF_HAND.ordinal()) return EnumHand.OFF_HAND;
        return null;
    }
}
