package com.ntmdoors.item;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.block.BlockEmancipationGrill;
import com.ntmdoors.network.MessageGrillAccess;
import com.ntmdoors.network.ModNetwork;
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
 * Emancipation Relay Key. Sneak right-clicking an Emancipation Grill opens its console. The key
 * itself carries no credentials; the console reads assignable keys and biometric IDs from an
 * Operator Keycard in the player's inventory, exactly like the Motion Sensor console.
 */
public class ItemEmancipationRelayKey extends Item {

    public ItemEmancipationRelayKey() {
        setRegistryName(NTMDoors.MODID, "emancipation_relay_key");
        setTranslationKey(NTMDoors.MODID + ".emancipation_relay_key");
        setCreativeTab(NTMDoors.TAB);
        setMaxStackSize(1);
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return TextFormatting.DARK_AQUA + super.getItemStackDisplayName(stack);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                      EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!player.isSneaking()) return EnumActionResult.PASS;

        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockEmancipationGrill)) return EnumActionResult.PASS;

        BlockEmancipationGrill block = (BlockEmancipationGrill) state.getBlock();
        BlockPos corePos = pos.down(block.layer().ordinal());
        if (world.isRemote) {
            ModNetwork.INSTANCE.sendToServer(new MessageGrillAccess(corePos));
        }
        return EnumActionResult.SUCCESS;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip,
                               ITooltipFlag flag) {
        tooltip.add(TextFormatting.GRAY
                + "Allows you to access the Grill GUI via Sneak+ right click");
    }
}
