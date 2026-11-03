package com.ntmdoors.item;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.tile.TileEntityMotionSensor;
import com.ntmdoors.util.HeldItems;
import com.ntmdoors.util.Names;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Motion Relay Key. Reserved for defining the 5x5x5 area a Motion Sensor watches.
 *
 * <p>The area-binding behaviour is not implemented yet; the key is only registered as an item and
 * carries its description for the later pass.</p>
 */
public class ItemMotionRelayKey extends Item {

    public ItemMotionRelayKey() {
        setRegistryName(NTMDoors.MODID, "motion_relay_key");
        setTranslationKey(NTMDoors.MODID + ".motion_relay_key");
        setCreativeTab(NTMDoors.TAB);
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return TextFormatting.BLUE + super.getItemStackDisplayName(stack);
    }

    /**
     * A normal click opens the sensor console. Sneak-clicking the sensor starts or finishes an
     * area selection; while a session is active, sneak-clicking a block sets a corner.
     */
    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                      EnumFacing facing, float hitX, float hitY, float hitZ) {
        TileEntity te = world.getTileEntity(pos);

        if (player.isSneaking()) {
            if (te instanceof TileEntityMotionSensor) {
                if (world.isRemote) return EnumActionResult.SUCCESS;
                TileEntityMotionSensor sensor = (TileEntityMotionSensor) te;
                if (sensor.isAreaBindingFor(player)) sensor.cancelAreaBinding(player);
                else sensor.startAreaBinding(player);
                return EnumActionResult.SUCCESS;
            }

            if (!world.isRemote) {
                TileEntityMotionSensor sensor = TileEntityMotionSensor.findAreaBinding(world, player);
                if (sensor != null) {
                    sensor.setAreaCorner(player, pos);
                    return EnumActionResult.SUCCESS;
                }
            }
            return EnumActionResult.PASS;
        }

        if (te instanceof TileEntityMotionSensor) {
            if (world.isRemote) NTMDoors.proxy.openMotionSensorGui(pos);
            return EnumActionResult.SUCCESS;
        }
        return EnumActionResult.PASS;
    }

    /** True when either hand holds a Motion Relay Key. */
    public static boolean isHeldBy(EntityPlayer player) {
        return HeldItems.holds(player, ItemMotionRelayKey.class);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        Names.addDescription(tooltip,
                "Allows you to define an area the sensor is",
                "placed in within 5 by 5 by 5 area",
                "Sneak + Right Click");
    }
}
