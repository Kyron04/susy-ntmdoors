package com.ntmdoors.item;

import com.ntmdoors.block.MotionSensorBlock;
import com.ntmdoors.client.MotionSensorOverlay;
import com.ntmdoors.client.screen.MotionSensorScreen;
import com.ntmdoors.util.Names;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ItemMotionRelayKey extends Item {

    public ItemMotionRelayKey(Settings settings) {
        super(settings.maxCount(1));
    }

    public ItemMotionRelayKey() {
        this(new Settings());
    }

    @Override
    public Text getName(ItemStack stack) {
        return super.getName(stack).copy().formatted(Formatting.BLUE);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        PlayerEntity player = context.getPlayer();
        if (player == null) return ActionResult.PASS;

        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        BlockState state = world.getBlockState(pos);

        if (state.getBlock() instanceof MotionSensorBlock) {
            if (player.isSneaking()) {
                if (world.isClient) {
                    MotionSensorOverlay.toggle(pos);
                }
                return ActionResult.success(world.isClient);
            } else {
                if (world.isClient) {
                    MinecraftClient.getInstance().setScreen(new MotionSensorScreen(pos));
                }
                return ActionResult.success(world.isClient);
            }
        }

        return ActionResult.PASS;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        Names.addDescription(tooltip,
                "Right Click a Motion Sensor to configure",
                "its detection area and credentials.",
                "Sneak + Right Click to show or hide the",
                "client blue detection footprint.");
    }
}
