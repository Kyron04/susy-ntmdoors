package com.ntmdoors.item;

import com.ntmdoors.block.entity.MonitorDeskBlockEntity;
import com.ntmdoors.client.screen.DeviceNameScreen;
import com.ntmdoors.monitor.MonitorDevice;
import com.ntmdoors.monitor.SecurityNet;
import com.ntmdoors.monitor.TerminalKeyData;
import com.ntmdoors.sound.ModSounds;
import com.ntmdoors.util.HeldItems;
import com.ntmdoors.util.Names;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Terminal Relay Key. Start a binding session at a monitor desk, bind devices by sneak-clicking
 * them, then return to the desk and sneak-click it again to confirm the list.
 */
public class ItemTerminalRelayKey extends Item {

    public ItemTerminalRelayKey() {
        super(new FabricItemSettings().maxCount(1));
    }

    @Override
    public Text getName(ItemStack stack) {
        return super.getName(stack).copy().formatted(Formatting.GOLD);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        PlayerEntity player = context.getPlayer();
        if (player == null) return ActionResult.PASS;

        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        Hand hand = context.getHand();
        ItemStack stack = player.getStackInHand(hand);

        BlockPos deskPos = MonitorDevice.deskCore(world, pos);
        if (deskPos != null) {
            if (!world.isClient) {
                startOrConfirm(player, world, stack, deskPos);
            }
            return ActionResult.success(world.isClient);
        }

        if (!player.isSneaking()) return ActionResult.PASS;

        MonitorDevice device = MonitorDevice.find(world, pos);
        if (device != null) {
            if (world.isClient) {
                MonitorDevice pending = TerminalKeyData.find(stack, device);
                String defaultName = pending == null ? device.type.displayName() : pending.name;
                openDeviceNameGui(hand, device.pos, defaultName, device.type.displayName());
                return ActionResult.SUCCESS;
            }

            TerminalKeyData.Session session = TerminalKeyData.getSession(stack);
            if (session == null) {
                player.sendMessage(Text.translatable("ntmdoors.msg.terminal_binding_required"), true);
            }
            return ActionResult.CONSUME;
        }

        return ActionResult.PASS;
    }

    @Environment(EnvType.CLIENT)
    private void openDeviceNameGui(Hand hand, BlockPos pos, String defaultName, String typeName) {
        MinecraftClient.getInstance().setScreen(new DeviceNameScreen(hand, pos, defaultName, typeName));
    }

    private static void startOrConfirm(PlayerEntity player, World world, ItemStack stack, BlockPos deskPos) {
        TerminalKeyData.Session session = TerminalKeyData.getSession(stack);
        if (session != null && session.desk.equals(deskPos)) {
            confirm(player, world, stack, deskPos, session);
            return;
        }

        BlockEntity be = world.getBlockEntity(deskPos);
        if (!(be instanceof MonitorDeskBlockEntity desk)) {
            player.sendMessage(Text.translatable("ntmdoors.msg.terminal_unavailable"), true);
            return;
        }

        TerminalKeyData.Link link = TerminalKeyData.getLink(stack);
        if (link != null && !link.desk.equals(deskPos)) {
            player.sendMessage(Text.translatable("ntmdoors.msg.terminal_key_linked"), true);
            return;
        }

        SecurityNet target = null;
        if (link != null) {
            target = desk.getNet(link.netId);
        }
        if (target == null) {
            List<SecurityNet> nets = desk.getNets();
            target = nets.isEmpty() ? desk.createNet("Security Net 1") : nets.get(0);
        }
        if (target == null) {
            player.sendMessage(Text.translatable("ntmdoors.msg.terminal_no_net"), true);
            return;
        }

        if (session != null) {
            player.sendMessage(Text.translatable("ntmdoors.msg.terminal_binding_discarded"), true);
        }

        TerminalKeyData.start(stack, deskPos, 0, target.id, target.getDevices());
        HeldItems.sync(player);
        player.sendMessage(Text.translatable("ntmdoors.msg.terminal_binding_started_net",
                target.displayName(), target.getDevices().size()), true);
        world.playSound(null, deskPos, ModSounds.BUTTON9, SoundCategory.BLOCKS, 0.8F, 1.0F);
    }

    private static void confirm(PlayerEntity player, World world, ItemStack stack,
                                BlockPos deskPos, TerminalKeyData.Session session) {
        BlockEntity be = world.getBlockEntity(deskPos);
        if (!(be instanceof MonitorDeskBlockEntity desk)) {
            player.sendMessage(Text.translatable("ntmdoors.msg.terminal_unavailable"), true);
            return;
        }

        SecurityNet net = desk.getNet(session.netId);
        if (net == null) {
            player.sendMessage(Text.translatable("ntmdoors.msg.terminal_net_missing"), true);
            TerminalKeyData.clearSession(stack);
            HeldItems.sync(player);
            return;
        }

        List<MonitorDevice> pending = new ArrayList<>(session.devices);
        pending.removeIf(device -> !MonitorDevice.stillValid(world, device));
        desk.setNetDevices(session.netId, pending);
        TerminalKeyData.clearSession(stack);
        HeldItems.sync(player);
        player.sendMessage(Text.translatable("ntmdoors.msg.terminal_binding_confirmed_net",
                net.displayName(), pending.size()), true);
        world.playSound(null, deskPos, ModSounds.BUTTON9, SoundCategory.BLOCKS, 0.8F, 1.0F);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        Names.addDescription(tooltip,
                "Link the key to a Security net in the terminal.",
                "Start at the linked desk with Sneak + Right Click.",
                "Sneak + Right Click devices to bind them.",
                "Name each device when prompted.",
                "Return to the desk and Sneak + Right Click",
                "again to confirm the device list.");
    }
}
