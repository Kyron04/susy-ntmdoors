package com.ntmdoors.item;

import com.ntmdoors.block.door.BlockDoorGeneric;
import com.ntmdoors.block.entity.DoorBlockEntity;
import com.ntmdoors.block.entity.SafeBlockEntity;
import com.ntmdoors.util.Names;
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

public class ItemSafeBindingKey extends Item {

    public ItemSafeBindingKey(Settings settings) {
        super(settings.maxCount(1));
    }

    public ItemSafeBindingKey() {
        this(new Settings());
    }

    @Override
    public Text getName(ItemStack stack) {
        return super.getName(stack).copy().formatted(Formatting.GREEN);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        PlayerEntity player = context.getPlayer();
        if (player == null || !player.isSneaking()) return ActionResult.PASS;

        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        BlockEntity be = world.getBlockEntity(pos);

        if (be instanceof SafeBlockEntity safe) {
            if (!world.isClient) {
                safe.toggleBinding(player);
            }
            return ActionResult.success(world.isClient);
        }

        DoorBlockEntity door = null;
        if (be instanceof DoorBlockEntity d) {
            door = d;
        } else {
            BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof BlockDoorGeneric doorBlock) {
                door = doorBlock.getCore(world, pos, state);
            }
        }

        if (door != null) {
            if (!world.isClient) {
                SafeBlockEntity safe = SafeBlockEntity.findBindingSafe(world, player);
                if (safe == null) {
                    player.sendMessage(Text.translatable("ntmdoors.msg.safe_binding_required"), true);
                } else {
                    safe.bindDoor(player, door);
                }
            }
            return ActionResult.success(world.isClient);
        }

        return ActionResult.PASS;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        Names.addDescription(tooltip,
                "Binds safes to the security infrastructure",
                "to protect your stuff.",
                "Sneak + Right Click a safe to start, then",
                "Sneak + Right Click doors to link them.");
    }
}
