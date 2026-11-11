package com.ntmdoors.item;

import com.ntmdoors.block.door.BlockDoorGeneric;
import com.ntmdoors.block.entity.DoorBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
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

/**
 * Redstone Monocle. A diagnostic item: while held in either hand it asks the client
 * overlay to draw the wireless Redstone links around the player. It has no server-side behaviour.
 */
public class ItemRedstoneMonocle extends Item {

    public ItemRedstoneMonocle(Settings settings) {
        super(settings.maxCount(1));
    }

    public ItemRedstoneMonocle() {
        this(new Settings());
    }

    @Override
    public Text getName(ItemStack stack) {
        return super.getName(stack).copy().formatted(Formatting.RED);
    }

    /**
     * Sneak + right-click a placed security door to toggle its Redstone access.
     */
    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        PlayerEntity player = context.getPlayer();
        if (player == null || !player.isSneaking()) return ActionResult.PASS;
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockDoorGeneric doorBlock)) return ActionResult.PASS;
        if (!doorBlock.isSecurity()) return ActionResult.PASS;

        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof DoorBlockEntity door) {
            if (!world.isClient()) {
                door.toggleRedstoneAccess(player);
            }
            return ActionResult.success(world.isClient());
        }
        return ActionResult.PASS;
    }

    /** True when either hand holds a Redstone Monocle. */
    public static boolean isHeldBy(@Nullable PlayerEntity player) {
        if (player == null) return false;
        return player.getMainHandStack().getItem() instanceof ItemRedstoneMonocle
                || player.getOffHandStack().getItem() instanceof ItemRedstoneMonocle;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("Reveals wireless Redstone links while held.").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Red: gate to gate").formatted(Formatting.RED));
        tooltip.add(Text.literal("Amber: gate to door").formatted(Formatting.GOLD));
        tooltip.add(Text.literal("Blue: device to gate").formatted(Formatting.AQUA));
        tooltip.add(Text.literal("Sneak + Right Click a security door").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("to toggle Redstone access (owner only).").formatted(Formatting.GRAY));
    }
}
