package com.ntmdoors.network;

import com.ntmdoors.monitor.MonitorAccess;
import com.ntmdoors.monitor.MonitorDevice;
import com.ntmdoors.monitor.SecurityEvents;
import com.ntmdoors.monitor.SecurityNet;
import com.ntmdoors.monitor.SecurityNetSummary;
import com.ntmdoors.monitor.TerminalKeyData;
import com.ntmdoors.item.ItemTerminalRelayKey;
import com.ntmdoors.tile.TileEntityDoorGeneric;
import com.ntmdoors.tile.TileEntityMonitorDesk;
import com.ntmdoors.util.HeldItems;
import com.ntmdoors.util.PrivateKeys;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Client to server: Security net and key-slot actions.
 */
public class MessageMonitorNetRequest implements IMessage {

    public static final int REQUEST_NETS = 0;
    public static final int REQUEST_DEVICES = 1;
    public static final int CREATE_NET = 2;
    public static final int BIND_KEY = 3;
    public static final int UNLINK_KEY = 4;
    public static final int RENAME_KEY = 5;
    public static final int DELETE_NET = 6;
    public static final int TOGGLE_EVENT = 7;
    public static final int CLOSE_ALL_DOORS = 8;
    public static final int OPEN_ALL_DOORS = 9;
    public static final int OPEN_DOOR = 10;
    public static final int CLOSE_DOOR = 11;
    public static final int TOGGLE_DEVICE = 12;

    private BlockPos deskPos;
    private int action;
    private int netId;
    private String name;
    private int hand;
    private int eventId = -1;
    private boolean eventValue;
    private BlockPos devicePos;

    public MessageMonitorNetRequest() {
    }

    public MessageMonitorNetRequest(BlockPos deskPos, int action, int netId, String name, int hand) {
        this(deskPos, action, netId, name, hand, -1, false);
    }

    public MessageMonitorNetRequest(BlockPos deskPos, int action, int netId, String name, int hand,
                                    int eventId, boolean eventValue) {
        this.deskPos = deskPos;
        this.action = action;
        this.netId = netId;
        this.name = name;
        this.hand = hand;
        this.eventId = eventId;
        this.eventValue = eventValue;
    }

    public MessageMonitorNetRequest(BlockPos deskPos, int action, int netId, String name, int hand,
                                    BlockPos devicePos) {
        this(deskPos, action, netId, name, hand, -1, false);
        this.devicePos = devicePos;
    }

    public MessageMonitorNetRequest(BlockPos deskPos, int action, int netId, String name, int hand,
                                    BlockPos devicePos, boolean enabled) {
        this(deskPos, action, netId, name, hand, -1, enabled);
        this.devicePos = devicePos;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        deskPos = NetworkUtil.readPos(buf);
        action = buf.readByte();
        netId = buf.readInt();
        name = ByteBufUtils.readUTF8String(buf);
        hand = buf.readByte();
        eventId = buf.readByte();
        eventValue = buf.readBoolean();
        devicePos = NetworkUtil.readPos(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        NetworkUtil.writePos(buf, deskPos);
        buf.writeByte(action);
        buf.writeInt(netId);
        ByteBufUtils.writeUTF8String(buf, name == null ? "" : name);
        buf.writeByte(hand);
        buf.writeByte(eventId);
        buf.writeBoolean(eventValue);
        NetworkUtil.writePos(buf, devicePos);
    }

    public static class Handler implements IMessageHandler<MessageMonitorNetRequest, IMessage> {

        @Override
        public IMessage onMessage(MessageMonitorNetRequest message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> apply(player, message));
            return null;
        }

        private static void apply(EntityPlayerMP player, MessageMonitorNetRequest message) {
            if (message.deskPos == null || !NetworkUtil.isWithinReach(player, message.deskPos)) return;

            TileEntityMonitorDesk desk = TileEntityMonitorDesk.at(player.world, message.deskPos);
            if (desk == null) return;

            switch (message.action) {
                case REQUEST_NETS:
                    sendNets(player, desk, -1);
                    break;
                case CREATE_NET:
                    SecurityNet created = desk.createNet(PrivateKeys.sanitiseName(message.name));
                    if (created != null) {
                        MonitorAccess.setTerminal(player, message.deskPos, created.id);
                        sendNets(player, desk, created.id);
                    }
                    break;
                case REQUEST_DEVICES:
                    SecurityNet net = desk.getNet(message.netId);
                    if (net == null) return;
                    MonitorAccess.setTerminal(player, message.deskPos, net.id);
                    sendDevices(player, desk, net);
                    break;
                case BIND_KEY:
                    bindKey(player, desk, message);
                    break;
                case UNLINK_KEY:
                    unlinkKey(player, desk, message.netId);
                    break;
                case RENAME_KEY:
                    renameKey(player, desk, message.netId, message.name);
                    break;
                case DELETE_NET:
                    deleteNet(player, desk, message.netId);
                    break;
                case TOGGLE_EVENT:
                    toggleEvent(player, desk, message.netId, message.eventId, message.eventValue);
                    break;
                case CLOSE_ALL_DOORS:
                    closeAllDoors(player, desk, message.netId);
                    break;
                case OPEN_ALL_DOORS:
                    openAllDoors(player, desk, message.netId);
                    break;
                case OPEN_DOOR:
                    setDoor(player, desk, message, true);
                    break;
                case CLOSE_DOOR:
                    setDoor(player, desk, message, false);
                    break;
                case TOGGLE_DEVICE:
                    toggleDevice(player, desk, message);
                    break;
                default:
                    break;
            }
        }

        private static void sendNets(EntityPlayerMP player, TileEntityMonitorDesk desk, int createdNetId) {
            List<SecurityNetSummary> nets = new ArrayList<>();
            for (SecurityNet net : desk.getNets()) {
                nets.add(new SecurityNetSummary(net.id, net.displayName(), net.getDevices().size()));
            }
            ModNetwork.INSTANCE.sendTo(new MessageMonitorNets(
                    desk.getPos(), createdNetId, nets), player);
        }

        private static void sendDevices(EntityPlayerMP player, TileEntityMonitorDesk desk,
                                        SecurityNet net) {
            sendDevices(player, desk, net, true);
        }

        private static void sendDevices(EntityPlayerMP player, TileEntityMonitorDesk desk,
                                        SecurityNet net, boolean prune) {
            List<com.ntmdoors.monitor.MonitorDevice> devices =
                    new ArrayList<>(net.getDevices());
            if (prune) {
                boolean changed = devices.removeIf(
                        device -> !com.ntmdoors.monitor.MonitorDevice.stillValid(
                                player.world, device));
                if (changed) desk.setNetDevices(net.id, devices);
            }

            List<MonitorDevice> views = new ArrayList<>(devices.size());
            for (MonitorDevice device : devices) {
                boolean loaded = player.world.isBlockLoaded(device.pos);
                boolean enabled = !loaded || MonitorDevice.isEnabled(player.world, device);
                views.add(device.withState(enabled, loaded));
            }

            ModNetwork.INSTANCE.sendTo(new MessageMonitorDevices(
                    desk.getPos(), net.id, net.displayName(), net.getLinkedKeyName(),
                    net.getEventMask(), views), player);
        }

        private static void toggleEvent(EntityPlayerMP player, TileEntityMonitorDesk desk,
                                        int netId, int eventId, boolean enabled) {
            if (eventId < 0 || eventId >= SecurityNet.EVENT_COUNT) return;
            SecurityNet net = desk.getNet(netId);
            if (net == null) return;
            desk.setNetEvent(netId, eventId, enabled);
            if (enabled && (eventId == SecurityNet.EVENT_SYSTEM_SHUTDOWN
                    || eventId == SecurityNet.EVENT_SYSTEM_LOCKDOWN)) {
                SecurityEvents.closeAllDoors(player.world, net);
            }
            // Never prune or rewrite the binding list as part of an event toggle.
            sendDevices(player, desk, net, false);
        }

        private static void closeAllDoors(EntityPlayerMP player, TileEntityMonitorDesk desk,
                                          int netId) {
            SecurityNet net = desk.getNet(netId);
            if (net != null) SecurityEvents.closeAllDoors(player.world, net);
        }

        private static void openAllDoors(EntityPlayerMP player, TileEntityMonitorDesk desk,
                                         int netId) {
            SecurityNet net = desk.getNet(netId);
            if (net != null) SecurityEvents.openAllDoors(player.world, net);
        }

        /** Server side: opens or closes one door that is part of the selected net. */
        private static void setDoor(EntityPlayerMP player, TileEntityMonitorDesk desk,
                                    MessageMonitorNetRequest message, boolean open) {
            SecurityNet net = desk.getNet(message.netId);
            if (net == null || message.devicePos == null) return;

            boolean known = false;
            for (MonitorDevice device : net.getDevices()) {
                if (device.type == MonitorDevice.Type.DOOR
                        && device.pos.equals(message.devicePos)) {
                    known = true;
                    break;
                }
            }
            if (!known || !player.world.isBlockLoaded(message.devicePos)) return;

            TileEntity te = player.world.getTileEntity(message.devicePos);
            if (!(te instanceof TileEntityDoorGeneric)) return;
            TileEntityDoorGeneric door = (TileEntityDoorGeneric) te;
            if (open) {
                door.openForSystem();
            } else {
                door.closeForSystem();
            }
        }

        /** Server side: enables or disables one non-door device that is part of the selected net. */
        private static void toggleDevice(EntityPlayerMP player, TileEntityMonitorDesk desk,
                                         MessageMonitorNetRequest message) {
            SecurityNet net = desk.getNet(message.netId);
            if (net == null || message.devicePos == null) return;

            MonitorDevice target = null;
            for (MonitorDevice device : net.getDevices()) {
                if (device.type != MonitorDevice.Type.DOOR
                        && device.pos.equals(message.devicePos)) {
                    target = device;
                    break;
                }
            }
            if (target == null || !player.world.isBlockLoaded(target.pos)) return;

            MonitorDevice.setEnabled(player.world, target, message.eventValue);
            // Keep the binding list untouched; the refreshed device list carries the new state.
            sendDevices(player, desk, net, false);
        }

        private static void bindKey(EntityPlayerMP player, TileEntityMonitorDesk desk,
                                    MessageMonitorNetRequest message) {
            SecurityNet net = desk.getNet(message.netId);
            if (net == null) {
                keyResult(player, desk, message.netId, MessageMonitorKeyResult.NO_NET, "");
                return;
            }

            EnumHand hand = message.hand == 1 ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND;
            ItemStack stack = player.getHeldItem(hand);
            if (!(stack.getItem() instanceof ItemTerminalRelayKey)) {
                keyResult(player, desk, net.id, MessageMonitorKeyResult.NO_KEY, net.getLinkedKeyName());
                return;
            }

            TerminalKeyData.Link existing = TerminalKeyData.getLink(stack);
            if (existing != null && !(existing.desk.equals(desk.getPos())
                    && existing.dimension == player.world.provider.getDimension()
                    && existing.netId == net.id)) {
                keyResult(player, desk, net.id, MessageMonitorKeyResult.KEY_ALREADY_LINKED,
                        net.getLinkedKeyName());
                return;
            }

            if (net.hasLinkedKey()) {
                if (existing == null || !existing.keyId.equals(net.getLinkedKeyId())) {
                    keyResult(player, desk, net.id, MessageMonitorKeyResult.NET_ALREADY_LINKED,
                            net.getLinkedKeyName());
                    return;
                }
                keyResult(player, desk, net.id, MessageMonitorKeyResult.BOUND,
                        net.getLinkedKeyName());
                return;
            }

            String keyId = existing == null ? UUID.randomUUID().toString() : existing.keyId;
            String keyName = stack.hasDisplayName() ? stack.getDisplayName() : "Terminal Relay Key";
            TerminalKeyData.setLink(stack, desk.getPos(), player.world.provider.getDimension(),
                    net.id, keyId);
            TerminalKeyData.clearSession(stack);
            desk.linkKey(net.id, keyId, keyName);
            HeldItems.sync(player);
            keyResult(player, desk, net.id, MessageMonitorKeyResult.BOUND, keyName);
        }

        private static void unlinkKey(EntityPlayerMP player, TileEntityMonitorDesk desk, int netId) {
            SecurityNet net = desk.getNet(netId);
            if (net == null) {
                keyResult(player, desk, netId, MessageMonitorKeyResult.NO_NET, "");
                return;
            }
            String keyId = net.getLinkedKeyId();
            desk.unlinkKey(netId);
            clearKeyLink(player, keyId);
            clearPendingSession(player);
            HeldItems.sync(player);
            keyResult(player, desk, netId, MessageMonitorKeyResult.UNLINKED, "");
        }

        private static void renameKey(EntityPlayerMP player, TileEntityMonitorDesk desk,
                                      int netId, String name) {
            SecurityNet net = desk.getNet(netId);
            if (net == null || !net.hasLinkedKey()) {
                keyResult(player, desk, netId, MessageMonitorKeyResult.NO_KEY,
                        net == null ? "" : net.getLinkedKeyName());
                return;
            }
            String clean = PrivateKeys.sanitiseName(name);
            if (clean.isEmpty()) clean = "Terminal Relay Key";
            desk.linkKey(netId, net.getLinkedKeyId(), clean);
            renameLinkedKey(player, net.getLinkedKeyId(), clean);
            keyResult(player, desk, netId, MessageMonitorKeyResult.RENAMED, clean);
        }

        private static void deleteNet(EntityPlayerMP player, TileEntityMonitorDesk desk, int netId) {
            SecurityNet net = desk.getNet(netId);
            if (net == null) {
                sendNets(player, desk, -1);
                return;
            }

            String keyId = net.getLinkedKeyId();
            if (!keyId.isEmpty()) {
                unlinkKeyEverywhere(player, keyId);
            }
            desk.removeNet(netId);
            sendNets(player, desk, -1);
        }

        /** Clears a stale net link from every online player's matching Terminal Relay Key. */
        private static void unlinkKeyEverywhere(EntityPlayerMP player, String keyId) {
            if (keyId == null || keyId.isEmpty()) return;
            MinecraftServer server = player.getServer();
            if (server == null) {
                clearKeyLink(player, keyId);
                return;
            }
            for (EntityPlayerMP online : server.getPlayerList().getPlayers()) {
                clearKeyLink(online, keyId);
            }
        }

        private static void clearKeyLink(EntityPlayerMP player, String keyId) {
            if (keyId == null || keyId.isEmpty()) return;
            boolean changed = false;
            for (ItemStack stack : player.inventory.mainInventory) {
                if (stack.getItem() instanceof ItemTerminalRelayKey
                        && keyId.equals(linkId(stack))) {
                    TerminalKeyData.clearLink(stack);
                    TerminalKeyData.clearSession(stack);
                    changed = true;
                }
            }
            ItemStack off = player.getHeldItemOffhand();
            if (off.getItem() instanceof ItemTerminalRelayKey && keyId.equals(linkId(off))) {
                TerminalKeyData.clearLink(off);
                TerminalKeyData.clearSession(off);
                changed = true;
            }
            if (changed) HeldItems.sync(player);
        }

        private static void renameLinkedKey(EntityPlayerMP player, String keyId, String name) {
            if (keyId == null || keyId.isEmpty()) return;
            for (ItemStack stack : player.inventory.mainInventory) {
                if (stack.getItem() instanceof ItemTerminalRelayKey
                        && keyId.equals(linkId(stack))) {
                    stack.setStackDisplayName(name);
                }
            }
            ItemStack off = player.getHeldItemOffhand();
            if (off.getItem() instanceof ItemTerminalRelayKey && keyId.equals(linkId(off))) {
                off.setStackDisplayName(name);
            }
            HeldItems.sync(player);
        }

        private static void clearPendingSession(EntityPlayerMP player) {
            TerminalKeyData.Session session = TerminalKeyData.getSession(player.getHeldItemMainhand());
            if (session != null) TerminalKeyData.clearSession(player.getHeldItemMainhand());
            TerminalKeyData.Session off = TerminalKeyData.getSession(player.getHeldItemOffhand());
            if (off != null) TerminalKeyData.clearSession(player.getHeldItemOffhand());
        }

        private static String linkId(ItemStack stack) {
            TerminalKeyData.Link link = TerminalKeyData.getLink(stack);
            return link == null ? "" : link.keyId;
        }

        private static void keyResult(EntityPlayerMP player, TileEntityMonitorDesk desk,
                                      int netId, int result, String keyName) {
            ModNetwork.INSTANCE.sendTo(new MessageMonitorKeyResult(
                    desk.getPos(), netId, result, keyName), player);
        }
    }
}
