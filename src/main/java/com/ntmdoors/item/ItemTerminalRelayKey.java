package com.ntmdoors.item;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.monitor.MonitorDevice;
import com.ntmdoors.monitor.SecurityNet;
import com.ntmdoors.monitor.TerminalKeyData;
import com.ntmdoors.tile.TileEntityMonitorDesk;
import com.ntmdoors.util.HeldItems;
import com.ntmdoors.util.ModSounds;
import com.ntmdoors.util.Names;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Terminal Relay Key. Start a binding session at a monitor desk, bind devices by sneak-clicking
 * them, then return to the desk and sneak-click it again to confirm the list.
 *
 * <p>The key carries the pending list; confirming writes it to the desk's tile entity. Each device
 * gets a name prompt as soon as it is bound. Starting at a different desk discards the old pending
 * list after a warning.</p>
 */
public class ItemTerminalRelayKey extends Item {

    public ItemTerminalRelayKey() {
        setRegistryName(NTMDoors.MODID, "terminal_relay_key");
        setTranslationKey(NTMDoors.MODID + ".terminal_relay_key");
        setCreativeTab(NTMDoors.TAB);
        setMaxStackSize(1);
    }

    /**
     * Runs before block activation. Doors, keypads, scanners and speakers consume a normal click
     * on one side or the other, so the terminal key must claim its sneak-click here to be able to
     * bind those devices without changing how any other key or tool behaves.
     */
    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos,
                                           EnumFacing side, float hitX, float hitY, float hitZ,
                                           EnumHand hand) {
        if (!player.isSneaking()) return EnumActionResult.PASS;
        return useTerminalKey(player, world, pos, hand);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                      EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!player.isSneaking()) return EnumActionResult.PASS;
        return useTerminalKey(player, world, pos, hand);
    }

    /** Shared by the item hooks and the high-priority right-click event. */
    public static EnumActionResult useTerminalKey(EntityPlayer player, World world, BlockPos pos,
                                                  EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!(stack.getItem() instanceof ItemTerminalRelayKey)) return EnumActionResult.PASS;

        BlockPos desk = MonitorDevice.deskCore(world, pos);
        if (desk != null) {
            if (!world.isRemote) startOrConfirm(player, world, stack, desk);
            return EnumActionResult.SUCCESS;
        }

        MonitorDevice device = MonitorDevice.find(world, pos);
        if (device == null) return EnumActionResult.PASS;

        TerminalKeyData.Session session = TerminalKeyData.getSession(stack);
        if (world.isRemote) {
            if (session != null && session.dimension == world.provider.getDimension()) {
                MonitorDevice pending = TerminalKeyData.find(stack, device);
                String defaultName = pending == null ? device.type.displayName() : pending.name;
                NTMDoors.proxy.openDeviceNameGui(hand, device.pos, defaultName,
                        device.type.displayName());
            }
            return EnumActionResult.SUCCESS;
        }

        if (session == null) {
            player.sendStatusMessage(new TextComponentTranslation(
                    "ntmdoors.msg.terminal_binding_required"), true);
        } else if (session.dimension != world.provider.getDimension()) {
            player.sendStatusMessage(new TextComponentTranslation(
                    "ntmdoors.msg.terminal_binding_wrong_dimension"), true);
        }
        return EnumActionResult.SUCCESS;
    }

    private static void startOrConfirm(EntityPlayer player, World world, ItemStack stack,
                                       BlockPos deskPos) {
        TerminalKeyData.Session session = TerminalKeyData.getSession(stack);
        int dimension = world.provider.getDimension();
        if (session != null && session.dimension == dimension && session.desk.equals(deskPos)) {
            confirm(player, world, stack, deskPos, session);
            return;
        }

        TileEntityMonitorDesk desk = TileEntityMonitorDesk.at(world, deskPos);
        if (desk == null) {
            player.sendStatusMessage(new TextComponentTranslation(
                    "ntmdoors.msg.terminal_unavailable"), true);
            return;
        }

        TerminalKeyData.Link link = TerminalKeyData.getLink(stack);
        if (link != null) {
            if (!link.desk.equals(deskPos) || link.dimension != dimension) {
                player.sendStatusMessage(new TextComponentTranslation(
                        "ntmdoors.msg.terminal_key_linked"), true);
                return;
            }
            SecurityNet linkedNet = desk.getNet(link.netId);
            if (linkedNet == null || !link.keyId.equals(linkedNet.getLinkedKeyId())) {
                // The net was unlinked or replaced; the key is no longer attached to it.
                TerminalKeyData.clearLink(stack);
                link = null;
            }
        }

        SecurityNet target = null;
        if (link != null) {
            target = desk.getNet(link.netId);
        }
        if (target == null) {
            List<SecurityNet> nets = desk.getNets();
            target = nets.isEmpty() ? desk.createNet("") : nets.get(0);
        }
        if (target == null) {
            player.sendStatusMessage(new TextComponentTranslation(
                    "ntmdoors.msg.terminal_no_net"), true);
            return;
        }

        if (session != null) {
            player.sendStatusMessage(new TextComponentTranslation(
                    "ntmdoors.msg.terminal_binding_discarded"), true);
        }

        TerminalKeyData.start(stack, deskPos, dimension, target.id, target.getDevices());
        HeldItems.sync(player);
        player.sendStatusMessage(new TextComponentTranslation(
                "ntmdoors.msg.terminal_binding_started_net",
                target.displayName(), target.getDevices().size()), true);
        playClick(world, deskPos);
    }

    private static void confirm(EntityPlayer player, World world, ItemStack stack,
                                BlockPos deskPos, TerminalKeyData.Session session) {
        TileEntityMonitorDesk desk = TileEntityMonitorDesk.at(world, deskPos);
        if (desk == null) {
            player.sendStatusMessage(new TextComponentTranslation(
                    "ntmdoors.msg.terminal_unavailable"), true);
            return;
        }

        SecurityNet net = desk.getNet(session.netId);
        if (net == null) {
            player.sendStatusMessage(new TextComponentTranslation(
                    "ntmdoors.msg.terminal_net_missing"), true);
            TerminalKeyData.clearSession(stack);
            HeldItems.sync(player);
            return;
        }

        List<MonitorDevice> pending = new ArrayList<>(session.devices);
        pending.removeIf(device -> !MonitorDevice.stillValid(world, device));
        desk.setNetDevices(session.netId, pending);
        TerminalKeyData.clearSession(stack);
        HeldItems.sync(player);
        player.sendStatusMessage(new TextComponentTranslation(
                "ntmdoors.msg.terminal_binding_confirmed_net",
                net.displayName(), pending.size()), true);
        playClick(world, deskPos);
    }

    private static void playClick(World world, BlockPos pos) {
        world.playSound(null, pos, ModSounds.BUTTON9, SoundCategory.BLOCKS, 0.8F, 1F);
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return TextFormatting.GOLD + super.getItemStackDisplayName(stack);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        Names.addDescription(tooltip,
                "Link the key to a Security net in the terminal.",
                "Start at the linked desk with Sneak + Right Click.",
                "Sneak + Right Click devices to bind them.",
                "Name each device when prompted.",
                "Return to the desk and Sneak + Right Click",
                "again to confirm the device list.");
    }
}
