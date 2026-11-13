package com.ntmdoors.item;

import com.ntmdoors.block.door.BlockDoorGeneric;
import com.ntmdoors.block.entity.DoorBlockEntity;
import com.ntmdoors.block.entity.KeypadBlockEntity;
import com.ntmdoors.block.entity.RedstoneBindingHost;
import com.ntmdoors.block.entity.RedstoneGateBlockEntity;
import com.ntmdoors.block.entity.SpeakerBlockEntity;
import com.ntmdoors.client.screen.KeypadSecurityCodeScreen;
import com.ntmdoors.util.HeldItems;
import com.ntmdoors.util.Names;
import com.ntmdoors.util.RedstoneBinding;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
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

public class ItemRedstoneRelayKey extends Item {

    public ItemRedstoneRelayKey(Settings settings) {
        super(settings.maxCount(1));
    }

    public ItemRedstoneRelayKey() {
        this(new Settings());
    }

    @Override
    public Text getName(ItemStack stack) {
        return super.getName(stack).copy().formatted(Formatting.RED);
    }

    public static boolean isHeldBy(PlayerEntity player) {
        return HeldItems.holds(player, ItemRedstoneRelayKey.class);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        PlayerEntity player = context.getPlayer();
        if (player == null) return ActionResult.PASS;

        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        BlockEntity be = world.getBlockEntity(pos);

        if (!player.isSneaking()) {
            if (be instanceof SpeakerBlockEntity speaker) {
                if (!world.isClient) {
                    speaker.getRedstoneBinding().toggle(player);
                }
                return ActionResult.success(world.isClient);
            }
            if (be instanceof KeypadBlockEntity keypad) {
                if (!world.isClient) {
                    keypad.getRedstoneBinding().toggle(player);
                }
                return ActionResult.success(world.isClient);
            }
            return ActionResult.PASS;
        }

        // Sneaking actions
        if (world.isClient && be instanceof KeypadBlockEntity) {
            MinecraftClient.getInstance().setScreen(new KeypadSecurityCodeScreen(pos));
            return ActionResult.SUCCESS;
        }

        if (world.isClient) return ActionResult.SUCCESS;

        if (be instanceof RedstoneGateBlockEntity gate) {
            RedstoneBinding binding = RedstoneBinding.findSession(world, player);
            if (binding == null) {
                gate.getRedstoneBinding().toggle(player);
            } else if (binding.getHost() == gate) {
                binding.stop(player);
            } else {
                binding.bindGate(player, gate);
            }
            return ActionResult.SUCCESS;
        }

        if (be instanceof RedstoneBindingHost host) {
            host.getRedstoneBinding().toggle(player);
            return ActionResult.SUCCESS;
        }

        DoorBlockEntity door = findDoor(world, pos);
        if (door != null) {
            RedstoneBinding binding = RedstoneBinding.findSession(world, player);
            if (binding == null) {
                player.sendMessage(Text.translatable("ntmdoors.msg.redstone_binding_required"), true);
            } else if (!binding.isGateHost()) {
                player.sendMessage(Text.translatable("ntmdoors.msg.redstone_door_gate_only"), true);
            } else {
                binding.bindDoor(player, door);
            }
            return ActionResult.SUCCESS;
        }

        return ActionResult.PASS;
    }

    @Nullable
    private static DoorBlockEntity findDoor(World world, BlockPos pos) {
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof DoorBlockEntity door) return door;
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof BlockDoorGeneric doorBlock) {
            return doorBlock.getCore(world, pos, state);
        }
        return null;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        Names.addDescription(tooltip,
                "Links keypads, scanners, safes, speakers",
                "and sensors to a Redstone Gate.",
                "Sneak + Right Click a gate to bind",
                "other gates and doors to its output.",
                "Right Click a Keypad or Speaker to bind it.",
                "Sneak + Right Click a Keypad to configure it.",
                "Sneak + Right Click a Motion Sensor to bind it.");
    }
}
