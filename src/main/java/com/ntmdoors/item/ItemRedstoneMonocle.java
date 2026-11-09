package com.ntmdoors.item;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.block.BlockDoorGeneric;
import com.ntmdoors.tile.TileEntityDoorGeneric;
import com.ntmdoors.util.HeldItems;
import com.ntmdoors.util.Names;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Redstone Monocle. A creative diagnostic item: while held in either hand it asks the client
 * overlay to draw the wireless Redstone links around the player. It has no server-side behaviour.
 */
public class ItemRedstoneMonocle extends Item {

    public ItemRedstoneMonocle() {
        setRegistryName(NTMDoors.MODID, "redstone_monocle");
        setTranslationKey(NTMDoors.MODID + ".redstone_monocle");
        setCreativeTab(NTMDoors.TAB);
        setMaxStackSize(1);
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return TextFormatting.RED + super.getItemStackDisplayName(stack);
    }

    /**
     * Sneak + right-click a placed security door to toggle its Redstone access. The server checks
     * the owner; the client only prevents the click from reaching the door as a normal use.
     */
    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                      EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!player.isSneaking()) return EnumActionResult.PASS;
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockDoorGeneric)) return EnumActionResult.PASS;
        BlockDoorGeneric block = (BlockDoorGeneric) state.getBlock();
        if (!block.isSecurity()) return EnumActionResult.PASS;
        if (world.isRemote) return EnumActionResult.SUCCESS;
        TileEntityDoorGeneric door = block.getCore(world, pos, state);
        if (door != null) door.toggleRedstoneAccess(player);
        return EnumActionResult.SUCCESS;
    }

    /** True when either hand holds a Redstone Monocle. */
    public static boolean isHeldBy(EntityPlayer player) {
        return HeldItems.holds(player, ItemRedstoneMonocle.class);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        Names.addDescription(tooltip,
                "Reveals wireless Redstone links while held.",
                "Red: gate to gate",
                "Amber: gate to door",
                "Blue: device to gate",
                "Sneak + Right Click a security door",
                "to toggle Redstone access (owner only).");
    }
}
