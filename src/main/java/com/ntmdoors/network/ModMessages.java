package com.ntmdoors.network;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.block.entity.KeycardProgrammerBlockEntity;
import com.ntmdoors.block.entity.KeycardScannerBlockEntity;
import com.ntmdoors.block.entity.KeypadBlockEntity;
import com.ntmdoors.block.entity.MotionSensorBlockEntity;
import com.ntmdoors.block.entity.RedstoneGateBlockEntity;
import com.ntmdoors.block.entity.SafeBlockEntity;
import com.ntmdoors.block.entity.SpeakerBlockEntity;
import com.ntmdoors.client.screen.KeypadConfigScreen;
import com.ntmdoors.client.screen.SafeCodeScreen;
import com.ntmdoors.client.screen.ScannerKeysScreen;
import com.ntmdoors.item.ItemOperatorKeycard;
import com.ntmdoors.item.ItemRelayKey;
import com.ntmdoors.screen.KeycardProgrammerScreenHandler;
import com.ntmdoors.screen.SafeScreenHandler;
import com.ntmdoors.block.entity.DoorBlockEntity;
import com.ntmdoors.block.entity.MonitorDeskBlockEntity;
import com.ntmdoors.client.screen.MonitorScreen;
import com.ntmdoors.client.screen.NotepadScreen;
import com.ntmdoors.item.ItemTerminalRelayKey;
import com.ntmdoors.monitor.MonitorDevice;
import com.ntmdoors.monitor.NotepadDocument;
import com.ntmdoors.monitor.SecurityNet;
import com.ntmdoors.monitor.SecurityNetSummary;
import com.ntmdoors.monitor.TerminalKeyData;
import com.ntmdoors.block.entity.SpatialDisrupterBlockEntity;
import com.ntmdoors.item.ItemDocumentsFolder;
import com.ntmdoors.item.ItemWeaponsRelayKey;
import com.ntmdoors.weapons.WeaponsKeyData;
import com.ntmdoors.weapons.WeaponsMode;
import com.ntmdoors.sound.ModSounds;
import com.ntmdoors.util.HeldItems;
import com.ntmdoors.util.PrivateKeys;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Networking registry and packet codecs for Fabric 1.20.1 security hardware.
 */
public final class ModMessages {

    public static final Identifier RELAY_KEY_NAME_ID = Identifier.of(NTMDoors.MOD_ID, "relay_key_name");
    public static final Identifier SCANNER_CODE_ID = Identifier.of(NTMDoors.MOD_ID, "scanner_code");
    public static final Identifier SCANNER_CODE_RESULT_ID = Identifier.of(NTMDoors.MOD_ID, "scanner_code_result");
    public static final Identifier SCANNER_KEY_ID = Identifier.of(NTMDoors.MOD_ID, "scanner_key");
    public static final Identifier SCANNER_OPTION_ID = Identifier.of(NTMDoors.MOD_ID, "scanner_option");
    public static final Identifier PROGRAMMER_PAGE_ID = Identifier.of(NTMDoors.MOD_ID, "programmer_page");
    public static final Identifier PROGRAMMER_ACTION_ID = Identifier.of(NTMDoors.MOD_ID, "programmer_action");
    public static final Identifier KEYPAD_CODE_ID = Identifier.of(NTMDoors.MOD_ID, "keypad_code");
    public static final Identifier KEYPAD_OPTION_ID = Identifier.of(NTMDoors.MOD_ID, "keypad_option");
    public static final Identifier KEYPAD_SECURITY_CODE_ID = Identifier.of(NTMDoors.MOD_ID, "keypad_security_code");
    public static final Identifier KEYPAD_SECURITY_CODE_RESULT_ID = Identifier.of(NTMDoors.MOD_ID, "keypad_security_code_result");
    public static final Identifier GATE_OPTION_ID = Identifier.of(NTMDoors.MOD_ID, "gate_option");
    public static final Identifier SPEAKER_OPTION_ID = Identifier.of(NTMDoors.MOD_ID, "speaker_option");
    public static final Identifier SAFE_CODE_ID = Identifier.of(NTMDoors.MOD_ID, "safe_code");
    public static final Identifier SAFE_RESULT_ID = Identifier.of(NTMDoors.MOD_ID, "safe_result");
    public static final Identifier SAFE_OPTION_ID = Identifier.of(NTMDoors.MOD_ID, "safe_option");
    public static final Identifier SAFE_PAGE_ID = Identifier.of(NTMDoors.MOD_ID, "safe_page");
    public static final Identifier MOTION_SENSOR_OPTION_ID = Identifier.of(NTMDoors.MOD_ID, "motion_sensor_option");
    public static final Identifier MOTION_SENSOR_AREA_ID = Identifier.of(NTMDoors.MOD_ID, "motion_sensor_area");
    public static final Identifier MOTION_SENSOR_KEY_ID = Identifier.of(NTMDoors.MOD_ID, "motion_sensor_key");

    // --- Monitor & Terminal OS Packets (Milestone M5) ---
    public static final Identifier MONITOR_ACTION_ID = Identifier.of(NTMDoors.MOD_ID, "monitor_action");
    public static final Identifier MONITOR_STATE_ID = Identifier.of(NTMDoors.MOD_ID, "monitor_state");
    public static final Identifier MONITOR_DISPLAY_ID = Identifier.of(NTMDoors.MOD_ID, "monitor_display");
    public static final Identifier MONITOR_DEVICE_CONTROL_ID = Identifier.of(NTMDoors.MOD_ID, "monitor_device_control");
    public static final Identifier MONITOR_NET_REQUEST_ID = Identifier.of(NTMDoors.MOD_ID, "monitor_net_request");
    public static final Identifier MONITOR_NETS_ID = Identifier.of(NTMDoors.MOD_ID, "monitor_nets");
    public static final Identifier MONITOR_DEVICES_ID = Identifier.of(NTMDoors.MOD_ID, "monitor_devices");
    public static final Identifier MONITOR_WALLPAPER_ID = Identifier.of(NTMDoors.MOD_ID, "monitor_wallpaper");
    public static final Identifier NOTEPAD_ACTION_ID = Identifier.of(NTMDoors.MOD_ID, "notepad_action");
    public static final Identifier NOTEPAD_DATA_ID = Identifier.of(NTMDoors.MOD_ID, "notepad_data");
    public static final Identifier MONITOR_BACKUP_OPEN_ID = Identifier.of(NTMDoors.MOD_ID, "monitor_backup_open");
    public static final Identifier TERMINAL_BIND_ID = Identifier.of(NTMDoors.MOD_ID, "terminal_bind");

    // --- Milestone M6 Packets ---
    public static final Identifier DOCUMENTS_FOLDER_PAGES_ID = Identifier.of(NTMDoors.MOD_ID, "documents_folder_pages");
    public static final Identifier SPATIAL_DISRUPTER_ACTIVATE_ID = Identifier.of(NTMDoors.MOD_ID, "spatial_disrupter_activate");
    public static final Identifier WEAPONS_KEY_MODE_ID = Identifier.of(NTMDoors.MOD_ID, "weapons_key_mode");

    public static void registerC2SPackets() {
        // 1. Relay Key Name
        ServerPlayNetworking.registerGlobalReceiver(RELAY_KEY_NAME_ID, (server, player, handler, buf, responseSender) -> {
            String name = buf.readString(32);
            Hand hand = buf.readEnumConstant(Hand.class);

            server.execute(() -> {
                ItemStack stack = player.getStackInHand(hand);
                if (stack.getItem() instanceof ItemRelayKey && PrivateKeys.isBlank(stack)) {
                    String cleanName = PrivateKeys.sanitiseName(name);
                    String serial = PrivateKeys.createSerial();
                    PrivateKeys.add(stack, cleanName, serial);
                    HeldItems.sync(player);
                    player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                            ModSounds.SCANNER_BUTTON3, SoundCategory.PLAYERS, 0.8F, 1.0F);
                    player.sendMessage(Text.translatable("ntmdoors.msg.key_created", cleanName), true);
                }
            });
        });

        // 2. Scanner Code
        ServerPlayNetworking.registerGlobalReceiver(SCANNER_CODE_ID, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            String code = buf.readString(6);

            server.execute(() -> {
                if (player.squaredDistanceTo(pos.toCenterPos()) > 64.0) return;
                BlockEntity be = player.getWorld().getBlockEntity(pos);
                if (!(be instanceof KeycardScannerBlockEntity scanner)) return;

                byte result;
                if (scanner.getSecurityCode().isEmpty()) {
                    scanner.setSecurityCode(code);
                    result = 1; // SET
                } else if (code.equals(scanner.getSecurityCode())) {
                    result = 0; // ACCEPTED
                } else {
                    result = 2; // DENIED
                }

                PacketByteBuf resp = PacketByteBufs.create();
                resp.writeBlockPos(pos);
                resp.writeByte(result);
                ServerPlayNetworking.send(player, SCANNER_CODE_RESULT_ID, resp);
            });
        });

        // 3. Scanner Key Assignment / Unassignment
        ServerPlayNetworking.registerGlobalReceiver(SCANNER_KEY_ID, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            String value = buf.readString(64);
            boolean assign = buf.readBoolean();
            boolean biometric = buf.readBoolean();
            String name = buf.readString(32);

            server.execute(() -> {
                if (player.squaredDistanceTo(pos.toCenterPos()) > 64.0) return;
                BlockEntity be = player.getWorld().getBlockEntity(pos);
                if (!(be instanceof KeycardScannerBlockEntity scanner)) return;

                if (biometric) {
                    if (assign) scanner.assignId(value, name);
                    else scanner.unassignId(value);
                } else {
                    if (assign) scanner.assignKey(name, value);
                    else scanner.unassignKey(value);
                }
            });
        });

        // 4. Scanner Option
        ServerPlayNetworking.registerGlobalReceiver(SCANNER_OPTION_ID, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            byte option = buf.readByte();
            boolean val = buf.readBoolean();

            server.execute(() -> {
                if (player.squaredDistanceTo(pos.toCenterPos()) > 64.0) return;
                BlockEntity be = player.getWorld().getBlockEntity(pos);
                if (!(be instanceof KeycardScannerBlockEntity scanner)) return;

                switch (option) {
                    case 0 -> scanner.setMatchHolder(val);
                    case 1 -> scanner.setLockdown(val);
                    case 2 -> scanner.setLockoutMode(val);
                    case 3 -> scanner.setIgnoreKeys(val);
                    case 4 -> scanner.setIgnoreIds(val);
                    case 5 -> {
                        for (var d : scanner.getLoadedBoundDoors()) d.openNow();
                    }
                    case 6 -> {
                        for (var d : scanner.getLoadedBoundDoors()) d.closeNow();
                    }
                    case 7 -> scanner.setSecurityCode("");
                    default -> {
                        if (option >= 8 && option <= 16) {
                            scanner.setRedstoneEvent(option - 8, val);
                        }
                    }
                }
            });
        });

        // 5. Programmer Page
        ServerPlayNetworking.registerGlobalReceiver(PROGRAMMER_PAGE_ID, (server, player, handler, buf, responseSender) -> {
            byte page = buf.readByte();
            server.execute(() -> {
                if (player.currentScreenHandler instanceof KeycardProgrammerScreenHandler screenHandler) {
                    screenHandler.getBlockEntity().setPage(page);
                }
            });
        });

        // 6. Programmer Action
        ServerPlayNetworking.registerGlobalReceiver(PROGRAMMER_ACTION_ID, (server, player, handler, buf, responseSender) -> {
            byte action = buf.readByte();
            int param = buf.readInt();
            server.execute(() -> {
                if (player.currentScreenHandler instanceof KeycardProgrammerScreenHandler screenHandler) {
                    KeycardProgrammerBlockEntity be = screenHandler.getBlockEntity();
                    if (be != null) {
                        if (action == 0) {
                            KeycardProgrammerBlockEntity.BindFailure failure = be.getBindFailure(param);
                            if (failure == KeycardProgrammerBlockEntity.BindFailure.NONE) {
                                boolean changed = be.bind(param);
                                screenHandler.sendContentUpdates();
                                if (changed) {
                                    player.sendMessage(Text.literal("Key bound successfully!").formatted(Formatting.GREEN), true);
                                    player.playSound(ModSounds.SCANNER_BUTTON3, SoundCategory.PLAYERS, 0.8F, 1.0F);
                                } else {
                                    player.sendMessage(Text.literal("Key already bound to this card!").formatted(Formatting.YELLOW), true);
                                    player.playSound(ModSounds.SCANNER_BUTTON8, SoundCategory.PLAYERS, 0.8F, 1.0F);
                                }
                            } else {
                                String msg = switch (failure) {
                                    case EMPTY -> "Insert items into both slots.";
                                    case NO_KEY -> "Source key has no security key! Sneak-right-click blank Relay Key in air first.";
                                    case NO_BIOMETRIC -> "Source key has no biometric data!";
                                    case INVALID_TARGET -> (param == 0) ? "Top row requires an Operator Keycard as target!" : "Bottom row requires a Coloured RFID Card as target!";
                                    case BLANK_CARD -> "Blank RFID Card must be coloured first! (Use Palette tab)";
                                    case ALREADY_BOUND -> "Key is already bound to this card!";
                                    case BIOMETRIC_TAKEN -> "Target card already has a biometric identity!";
                                    case BIOMETRIC_ALREADY -> "Target card already has this biometric identity!";
                                    default -> "Cannot bind: invalid combination.";
                                };
                                player.sendMessage(Text.literal(msg).formatted(Formatting.RED), true);
                                player.playSound(ModSounds.SCANNER_BUTTON8, SoundCategory.PLAYERS, 0.8F, 1.0F);
                            }
                        } else if (action == 1) {
                            be.applyColor(param);
                            screenHandler.sendContentUpdates();
                            player.sendMessage(Text.literal("Card appearance updated!").formatted(Formatting.GREEN), true);
                        } else if (action == 2) {
                            be.startBackup();
                            screenHandler.sendContentUpdates();
                        }
                    }
                }
            });
        });

        // 7. Keypad Code
        ServerPlayNetworking.registerGlobalReceiver(KEYPAD_CODE_ID, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            String code = buf.readString(6);
            server.execute(() -> {
                if (player.squaredDistanceTo(pos.toCenterPos()) > 64.0) return;
                BlockEntity be = player.getWorld().getBlockEntity(pos);
                if (be instanceof KeypadBlockEntity keypad && !keypad.hasStoredCode()) {
                    keypad.setStoredCode(code);
                }
            });
        });

        // 8. Keypad Option
        ServerPlayNetworking.registerGlobalReceiver(KEYPAD_OPTION_ID, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            byte option = buf.readByte();
            boolean val = buf.readBoolean();
            server.execute(() -> {
                if (player.squaredDistanceTo(pos.toCenterPos()) > 64.0) return;
                BlockEntity be = player.getWorld().getBlockEntity(pos);
                if (be instanceof KeypadBlockEntity keypad) {
                    switch (option) {
                        case 0 -> keypad.getRedstoneBinding().setOnFail(val);
                        case 1 -> keypad.getRedstoneBinding().setOnSuccess(val);
                        case 2 -> keypad.resetCodes(keypad.getControlledDoors());
                        case 3 -> {
                            for (var d : keypad.getControlledDoors()) d.request(true, true);
                        }
                        case 4 -> {
                            for (var d : keypad.getControlledDoors()) d.closeNow();
                        }
                    }
                }
            });
        });

        // 9. Keypad Security Code
        ServerPlayNetworking.registerGlobalReceiver(KEYPAD_SECURITY_CODE_ID, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            String code = buf.readString(6);
            server.execute(() -> {
                if (player.squaredDistanceTo(pos.toCenterPos()) > 64.0) return;
                BlockEntity be = player.getWorld().getBlockEntity(pos);
                if (!(be instanceof KeypadBlockEntity keypad)) return;

                byte result;
                if (keypad.getSecurityCode().isEmpty()) {
                    keypad.setSecurityCode(code);
                    result = 1; // SET
                } else if (code.equals(keypad.getSecurityCode())) {
                    result = 0; // ACCEPTED
                } else {
                    result = 2; // DENIED
                }

                PacketByteBuf resp = PacketByteBufs.create();
                resp.writeBlockPos(pos);
                resp.writeByte(result);
                ServerPlayNetworking.send(player, KEYPAD_SECURITY_CODE_RESULT_ID, resp);
            });
        });

        // 10. Speaker Option
        ServerPlayNetworking.registerGlobalReceiver(SPEAKER_OPTION_ID, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            byte option = buf.readByte();
            int value = buf.readInt();
            server.execute(() -> {
                if (player.squaredDistanceTo(pos.toCenterPos()) > 64.0) return;
                BlockEntity be = player.getWorld().getBlockEntity(pos);
                if (be instanceof SpeakerBlockEntity speaker) {
                    switch (option) {
                        case 0 -> speaker.setEnabled(value != 0);
                        case 1 -> speaker.setSound(value);
                        case 2 -> speaker.setRange(value);
                        case 3 -> speaker.setVolume(value);
                    }
                }
            });
        });

        // 11. Redstone Gate Option
        ServerPlayNetworking.registerGlobalReceiver(GATE_OPTION_ID, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            byte option = buf.readByte();
            boolean value = buf.readBoolean();
            server.execute(() -> {
                if (player.squaredDistanceTo(pos.toCenterPos()) > 64.0) return;
                BlockEntity be = player.getWorld().getBlockEntity(pos);
                if (be instanceof RedstoneGateBlockEntity gate) {
                    switch (option) {
                        case 0 -> gate.setStayActive(value);
                        case 1 -> gate.setInverted(value);
                        case 2 -> gate.adjustActiveTicks(20);
                        case 3 -> gate.adjustActiveTicks(100);
                        case 4 -> gate.adjustActiveTicks(200);
                        case 5 -> gate.adjustActiveTicks(400);
                        case 6 -> gate.adjustActiveTicks(-20);
                        case 7 -> gate.adjustActiveTicks(-100);
                        case 8 -> gate.adjustActiveTicks(-200);
                        case 9 -> gate.adjustActiveTicks(-400);
                    }
                }
            });
        });

        // 12. Safe Code
        ServerPlayNetworking.registerGlobalReceiver(SAFE_CODE_ID, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            String code = buf.readString();
            server.execute(() -> {
                if (player.squaredDistanceTo(pos.toCenterPos()) > 64.0) return;
                BlockEntity be = player.getWorld().getBlockEntity(pos);
                if (be instanceof SafeBlockEntity safe) {
                    safe.submitCode(player, code);
                }
            });
        });

        // 13. Safe Option
        ServerPlayNetworking.registerGlobalReceiver(SAFE_OPTION_ID, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            byte option = buf.readByte();
            boolean value = buf.readBoolean();
            server.execute(() -> {
                if (player.squaredDistanceTo(pos.toCenterPos()) > 64.0) return;
                BlockEntity be = player.getWorld().getBlockEntity(pos);
                if (be instanceof SafeBlockEntity safe) {
                    safe.setOption(player, option, value);
                }
            });
        });

        // 14. Safe Page
        ServerPlayNetworking.registerGlobalReceiver(SAFE_PAGE_ID, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            byte page = buf.readByte();
            server.execute(() -> {
                if (player.currentScreenHandler instanceof SafeScreenHandler safeHandler) {
                    safeHandler.setPage(page);
                }
            });
        });

        // 15. Motion Sensor Option
        ServerPlayNetworking.registerGlobalReceiver(MOTION_SENSOR_OPTION_ID, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            int option = buf.readInt();
            boolean value = buf.readBoolean();
            server.execute(() -> {
                if (player.squaredDistanceTo(pos.toCenterPos()) > 64.0) return;
                BlockEntity be = player.getWorld().getBlockEntity(pos);
                if (be instanceof MotionSensorBlockEntity sensor) {
                    switch (option) {
                        case 0 -> sensor.setDetectPlayers(value);
                        case 1 -> sensor.setDetectHostile(value);
                        case 2 -> sensor.setDetectNeutral(value);
                        case 3 -> sensor.setPersistWithinBounds(value);
                        case 4 -> sensor.setPersistOutOfBounds(value);
                        case 5 -> sensor.setMatchHolder(value);
                        case 6 -> sensor.setIgnoreKeys(value);
                        case 7 -> sensor.setIgnoreIds(value);
                        case 8 -> sensor.setEnabled(value);
                        case 9 -> sensor.setRedstoneEvent(MotionSensorBlockEntity.RS_AUTHORIZED, value);
                        case 10 -> sensor.setRedstoneEvent(MotionSensorBlockEntity.RS_UNAUTHORIZED, value);
                        case 11 -> sensor.setRedstoneEvent(MotionSensorBlockEntity.RS_DISABLED, value);
                        case 12 -> sensor.setRedstoneEvent(MotionSensorBlockEntity.RS_ENABLED, value);
                    }
                }
            });
        });

        // 16. Motion Sensor Area
        ServerPlayNetworking.registerGlobalReceiver(MOTION_SENSOR_AREA_ID, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            boolean clear = buf.readBoolean();
            byte[] depths = buf.readByteArray();
            server.execute(() -> {
                if (player.squaredDistanceTo(pos.toCenterPos()) > 64.0) return;
                BlockEntity be = player.getWorld().getBlockEntity(pos);
                if (be instanceof MotionSensorBlockEntity sensor) {
                    if (clear) sensor.clearArea();
                    else sensor.setAreaDepths(depths);
                }
            });
        });

        // 17. Monitor Action (Password setup/skip/check, net create/remove/rename)
        ServerPlayNetworking.registerGlobalReceiver(MONITOR_ACTION_ID, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            int action = buf.readInt();
            String strParam = buf.readString(256);
            int intParam = buf.readInt();
            server.execute(() -> {
                if (player.squaredDistanceTo(pos.toCenterPos()) > 64.0) return;
                BlockEntity be = player.getWorld().getBlockEntity(pos);
                if (be instanceof MonitorDeskBlockEntity desk) {
                    switch (action) {
                        case 0 -> {
                            boolean ok = desk.setPasswordHash(strParam);
                            sendMonitorState(player, pos, (byte) (ok ? 2 : 1), desk.hasPassword(), desk.isPasswordSkipped(), 0);
                        }
                        case 1 -> {
                            boolean ok = desk.skipPasswordSetup();
                            sendMonitorState(player, pos, (byte) (ok ? 3 : 1), desk.hasPassword(), desk.isPasswordSkipped(), 0);
                        }
                        case 2 -> {
                            boolean ok = desk.checkPasswordHash(strParam);
                            sendMonitorState(player, pos, (byte) (ok ? 4 : 5), desk.hasPassword(), desk.isPasswordSkipped(), 0);
                        }
                        case 3 -> desk.createNet(strParam);
                        case 4 -> desk.removeNet(intParam);
                        case 5 -> desk.renameNet(intParam, strParam);
                    }
                }
            });
        });

        // 18. Monitor Display (Navigation & heartbeat)
        ServerPlayNetworking.registerGlobalReceiver(MONITOR_DISPLAY_ID, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            int screenIndex = buf.readInt();
            boolean viewing = buf.readBoolean();
            int stage = buf.readInt();
            int netId = buf.readInt();
            int docId = buf.readInt();
            boolean eventsTab = buf.readBoolean();
            server.execute(() -> {
                if (player.squaredDistanceTo(pos.toCenterPos()) > 64.0) return;
                BlockEntity be = player.getWorld().getBlockEntity(pos);
                if (be instanceof MonitorDeskBlockEntity desk) {
                    desk.setScreenViewing(screenIndex, viewing, player.getUuid());
                    desk.setScreenStage(screenIndex, stage);
                    desk.setScreenNetId(screenIndex, netId);
                    desk.setScreenNotepadDocId(screenIndex, docId);
                }
            });
        });

        // 19. Monitor Device Control (Door open/close, device enable/disable, net events)
        ServerPlayNetworking.registerGlobalReceiver(MONITOR_DEVICE_CONTROL_ID, (server, player, handler, buf, responseSender) -> {
            BlockPos deskPos = buf.readBlockPos();
            int netId = buf.readInt();
            int command = buf.readInt();
            BlockPos targetPos = buf.readBlockPos();
            int eventId = buf.readInt();
            boolean state = buf.readBoolean();
            server.execute(() -> {
                if (player.squaredDistanceTo(deskPos.toCenterPos()) > 64.0) return;
                BlockEntity be = player.getWorld().getBlockEntity(deskPos);
                if (be instanceof MonitorDeskBlockEntity desk) {
                    if (command == 0) {
                        BlockEntity target = player.getWorld().getBlockEntity(targetPos);
                        if (target instanceof DoorBlockEntity door) door.openForSystem();
                    } else if (command == 1) {
                        BlockEntity target = player.getWorld().getBlockEntity(targetPos);
                        if (target instanceof DoorBlockEntity door) door.closeForSystem();
                    } else if (command == 2) {
                        MonitorDevice dev = MonitorDevice.find(player.getWorld(), targetPos);
                        if (dev != null) {
                            MonitorDevice.setEnabled(player.getWorld(), dev, state);
                            SecurityNet net = desk.getNet(netId);
                            if (net != null) {
                                sendMonitorDevices(player, deskPos, net);
                            }
                        }
                    } else if (command == 3) {
                        desk.setNetEvent(netId, eventId, state);
                    } else if (command == 4) {
                        BlockEntity target = player.getWorld().getBlockEntity(targetPos);
                        if (target instanceof RedstoneGateBlockEntity gate) {
                            gate.pulse();
                        }
                    }
                }
            });
        });

        // 20. Monitor Net Request
        ServerPlayNetworking.registerGlobalReceiver(MONITOR_NET_REQUEST_ID, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            int netId = buf.readInt();
            server.execute(() -> {
                if (player.squaredDistanceTo(pos.toCenterPos()) > 64.0) return;
                BlockEntity be = player.getWorld().getBlockEntity(pos);
                if (be instanceof MonitorDeskBlockEntity desk) {
                    if (netId == -1) {
                        sendMonitorNets(player, pos, desk.getNetSummaries());
                    } else {
                        SecurityNet net = desk.getNet(netId);
                        if (net != null) {
                            sendMonitorDevices(player, pos, net);
                        }
                    }
                }
            });
        });

        // 21. Monitor Wallpaper
        ServerPlayNetworking.registerGlobalReceiver(MONITOR_WALLPAPER_ID, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            byte screenIndex = buf.readByte();
            String wallpaper = buf.readString(512);
            server.execute(() -> {
                if (player.squaredDistanceTo(pos.toCenterPos()) > 64.0) return;
                BlockEntity be = player.getWorld().getBlockEntity(pos);
                if (be instanceof MonitorDeskBlockEntity desk) {
                    desk.setScreenWallpaper(screenIndex, wallpaper);
                }
            });
        });

        // 22. Notepad Action
        ServerPlayNetworking.registerGlobalReceiver(NOTEPAD_ACTION_ID, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            byte action = buf.readByte();
            int docId = buf.readInt();
            String text = buf.readString(8192);
            server.execute(() -> {
                if (player.squaredDistanceTo(pos.toCenterPos()) > 64.0) return;
                BlockEntity be = player.getWorld().getBlockEntity(pos);
                if (be instanceof MonitorDeskBlockEntity desk) {
                    switch (action) {
                        case 0 -> sendNotepadData(player, pos, docId, desk.getNotepadDocuments());
                        case 1 -> {
                            NotepadDocument created = desk.createNotepadDocument();
                            sendNotepadData(player, pos, created != null ? created.getId() : -1, desk.getNotepadDocuments());
                        }
                        case 2 -> desk.saveNotepadText(docId, text);
                        case 3 -> desk.renameNotepadDocument(docId, text);
                        case 4 -> {
                            desk.deleteNotepadDocument(docId);
                            sendNotepadData(player, pos, -1, desk.getNotepadDocuments());
                        }
                    }
                }
            });
        });

        // 23. Monitor Backup Open
        ServerPlayNetworking.registerGlobalReceiver(MONITOR_BACKUP_OPEN_ID, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            boolean restore = buf.readBoolean();
            server.execute(() -> {
                if (player.squaredDistanceTo(pos.toCenterPos()) > 64.0) return;
                BlockEntity be = player.getWorld().getBlockEntity(pos);
                if (be instanceof MonitorDeskBlockEntity desk) {
                    player.openHandledScreen(desk);
                }
            });
        });

        // 24. Documents Folder Pages
        ServerPlayNetworking.registerGlobalReceiver(DOCUMENTS_FOLDER_PAGES_ID, (server, player, handler, buf, responseSender) -> {
            int handOrdinal = buf.readByte();
            int lastPage = buf.readByte();
            String customName = buf.readString(ItemDocumentsFolder.MAX_NAME_LENGTH);
            int count = Math.max(0, Math.min((int) buf.readByte(), ItemDocumentsFolder.PAGE_COUNT));
            List<String> pages = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                pages.add(buf.readString(ItemDocumentsFolder.MAX_PAGE_LENGTH));
            }

            server.execute(() -> {
                Hand hand = handOrdinal == 1 ? Hand.OFF_HAND : Hand.MAIN_HAND;
                ItemStack stack = player.getStackInHand(hand);
                if (stack.isEmpty() || !(stack.getItem() instanceof ItemDocumentsFolder)) return;

                List<String> cleanPages = ItemDocumentsFolder.clean(pages);
                ItemDocumentsFolder.setPages(stack, cleanPages);
                ItemDocumentsFolder.setCustomName(stack, customName);
                ItemDocumentsFolder.setLastPage(stack, cleanPages.isEmpty() ? 0 : lastPage);

                HeldItems.sync(player);
            });
        });

        // 25. Spatial Disrupter Activate
        ServerPlayNetworking.registerGlobalReceiver(SPATIAL_DISRUPTER_ACTIVATE_ID, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            server.execute(() -> {
                if (player.squaredDistanceTo(pos.toCenterPos()) > 64.0) return;
                BlockEntity be = player.getWorld().getBlockEntity(pos);
                if (be instanceof SpatialDisrupterBlockEntity disrupter) {
                    disrupter.activate(player);
                }
            });
        });

        // 26. Weapons Key Mode
        ServerPlayNetworking.registerGlobalReceiver(WEAPONS_KEY_MODE_ID, (server, player, handler, buf, responseSender) -> {
            Hand hand = buf.readEnumConstant(Hand.class);
            int modeId = buf.readByte();
            server.execute(() -> {
                ItemStack stack = player.getStackInHand(hand);
                if (!(stack.getItem() instanceof ItemWeaponsRelayKey)) return;

                WeaponsMode mode = WeaponsMode.byId(modeId);
                WeaponsKeyData.setMode(stack, mode);
                WeaponsKeyData.clearSession(stack);
                HeldItems.sync(player);
                player.sendMessage(Text.translatable("ntmdoors.msg.weapons_mode_changed", mode.displayName), true);
            });
        });

        // 27. Terminal Device Bind
        ServerPlayNetworking.registerGlobalReceiver(TERMINAL_BIND_ID, (server, player, handler, buf, responseSender) -> {
            Hand hand = buf.readEnumConstant(Hand.class);
            BlockPos pos = buf.readBlockPos();
            String name = buf.readString(64);
            server.execute(() -> {
                if (player.squaredDistanceTo(pos.toCenterPos()) > 64.0) return;
                ItemStack stack = player.getStackInHand(hand);
                if (!(stack.getItem() instanceof ItemTerminalRelayKey)) return;

                TerminalKeyData.Session session = TerminalKeyData.getSession(stack);
                if (session == null) {
                    player.sendMessage(Text.translatable("ntmdoors.msg.terminal_binding_required"), true);
                    return;
                }

                MonitorDevice device = MonitorDevice.find(player.getWorld(), pos);
                if (device == null) {
                    player.sendMessage(Text.translatable("ntmdoors.msg.terminal_device_invalid"), true);
                    return;
                }

                String cleanName = PrivateKeys.sanitiseName(name);
                if (cleanName.isEmpty()) cleanName = device.type.displayName();
                MonitorDevice bound = device.withName(cleanName);
                TerminalKeyData.addOrUpdate(stack, bound);
                HeldItems.sync(player);

                player.sendMessage(Text.translatable("ntmdoors.msg.terminal_device_bound", bound.name, bound.type.displayName()), true);
                player.getWorld().playSound(null, pos, ModSounds.BUTTON9, SoundCategory.BLOCKS, 0.8F, 1.0F);
            });
        });
    }

    @Environment(EnvType.CLIENT)
    public static void registerS2CPackets() {
        ClientPlayNetworking.registerGlobalReceiver(SCANNER_CODE_RESULT_ID, (client, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            byte result = buf.readByte();
            client.execute(() -> {
                if (result == 0 || result == 1) {
                    MinecraftClient.getInstance().setScreen(new ScannerKeysScreen(pos));
                } else {
                    MinecraftClient clientInstance = MinecraftClient.getInstance();
                    if (clientInstance.player != null) {
                        clientInstance.player.sendMessage(Text.translatable("ntmdoors.msg.scanner_code_denied"), true);
                    }
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(KEYPAD_SECURITY_CODE_RESULT_ID, (client, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            byte result = buf.readByte();
            client.execute(() -> {
                if (result == 0 || result == 1) {
                    MinecraftClient.getInstance().setScreen(new KeypadConfigScreen(pos));
                } else {
                    MinecraftClient clientInstance = MinecraftClient.getInstance();
                    if (clientInstance.player != null) {
                        clientInstance.player.sendMessage(Text.translatable("ntmdoors.msg.keypad_code_invalid"), true);
                    }
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SAFE_RESULT_ID, (client, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            byte result = buf.readByte();
            client.execute(() -> {
                if (MinecraftClient.getInstance().currentScreen instanceof SafeCodeScreen safeScreen) {
                    safeScreen.onSafeResult(result);
                }
            });
        });

        // 24. Monitor State (Authentication result)
        ClientPlayNetworking.registerGlobalReceiver(MONITOR_STATE_ID, (client, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            byte result = buf.readByte();
            boolean hasPassword = buf.readBoolean();
            boolean skipped = buf.readBoolean();
            int screenIndex = buf.readInt();
            client.execute(() -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.currentScreen instanceof MonitorScreen screen) {
                    if (result == 4 || result == 2 || result == 3) {
                        screen.onPasswordSuccess();
                    } else if (result == 5) {
                        screen.onPasswordFail();
                    }
                } else {
                    if (result == 2) {
                        mc.setScreen(new MonitorScreen(pos, screenIndex, hasPassword, !hasPassword || skipped));
                    } else if (result == 1) {
                        mc.setScreen(new MonitorScreen(pos, screenIndex, hasPassword, false));
                    } else if (result == 3 || result == 4) {
                        mc.setScreen(new MonitorScreen(pos, screenIndex, hasPassword, true));
                    } else if (result == 5) {
                        if (mc.player != null) {
                            mc.player.sendMessage(Text.translatable("ntmdoors.msg.monitor_wrong_password"), true);
                        }
                    }
                }
            });
        });

        // 25. Monitor Nets (Net list summaries)
        ClientPlayNetworking.registerGlobalReceiver(MONITOR_NETS_ID, (client, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            int count = buf.readInt();
            List<SecurityNetSummary> nets = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int id = buf.readInt();
                String name = buf.readString(64);
                int devCount = buf.readInt();
                nets.add(new SecurityNetSummary(id, name, devCount));
            }
            client.execute(() -> {
                if (MinecraftClient.getInstance().currentScreen instanceof MonitorScreen screen) {
                    screen.updateNets(nets);
                }
            });
        });

        // 26. Monitor Devices (Full device list)
        ClientPlayNetworking.registerGlobalReceiver(MONITOR_DEVICES_ID, (client, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            int netId = buf.readInt();
            String name = buf.readString(64);
            int eventMask = buf.readInt();
            String keyName = buf.readString(64);
            int count = buf.readInt();
            List<MonitorDevice> devs = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                byte type = buf.readByte();
                BlockPos devPos = buf.readBlockPos();
                String devName = buf.readString(64);
                boolean enabled = buf.readBoolean();
                boolean loaded = buf.readBoolean();
                devs.add(new MonitorDevice(MonitorDevice.Type.byId(type), devPos, devName, enabled, loaded));
            }
            client.execute(() -> {
                if (MinecraftClient.getInstance().currentScreen instanceof MonitorScreen screen) {
                    screen.updateDevices(netId, name, eventMask, keyName, devs);
                }
            });
        });

        // 27. Notepad Data (Multi-document notepad sync)
        ClientPlayNetworking.registerGlobalReceiver(NOTEPAD_DATA_ID, (client, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            int docId = buf.readInt();
            int count = buf.readInt();
            List<NotepadDocument> docs = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int id = buf.readInt();
                String name = buf.readString(48);
                String text = buf.readString(8192);
                docs.add(new NotepadDocument(id, name, text));
            }
            client.execute(() -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.currentScreen instanceof NotepadScreen notepad) {
                    notepad.updateDocuments(docs, docId);
                } else {
                    mc.setScreen(new NotepadScreen(pos, docId, docs));
                }
            });
        });
    }

    // Server send helpers
    public static void sendMonitorState(ServerPlayerEntity player, BlockPos pos, byte result, boolean hasPassword, boolean skipped, int extra) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeByte(result);
        buf.writeBoolean(hasPassword);
        buf.writeBoolean(skipped);
        buf.writeInt(extra);
        ServerPlayNetworking.send(player, MONITOR_STATE_ID, buf);
    }

    public static void sendMonitorNets(ServerPlayerEntity player, BlockPos pos, List<SecurityNetSummary> nets) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeInt(nets.size());
        for (SecurityNetSummary net : nets) {
            buf.writeInt(net.id);
            buf.writeString(net.name == null ? "" : net.name, 64);
            buf.writeInt(net.deviceCount);
        }
        ServerPlayNetworking.send(player, MONITOR_NETS_ID, buf);
    }

    public static void sendMonitorDevices(ServerPlayerEntity player, BlockPos pos, SecurityNet net) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeInt(net.id);
        buf.writeString(net.getName(), 64);
        buf.writeInt(net.getEventMask());
        buf.writeString(net.getLinkedKeyName(), 64);
        List<MonitorDevice> devs = net.getDevices();
        buf.writeInt(devs.size());
        for (MonitorDevice d : devs) {
            boolean loaded = player.getWorld().isChunkLoaded(d.pos);
            boolean enabled = !loaded || MonitorDevice.isEnabled(player.getWorld(), d);
            buf.writeByte(d.type.ordinal());
            buf.writeBlockPos(d.pos);
            buf.writeString(d.name, 64);
            buf.writeBoolean(enabled);
            buf.writeBoolean(loaded);
        }
        ServerPlayNetworking.send(player, MONITOR_DEVICES_ID, buf);
    }

    public static void sendNotepadData(ServerPlayerEntity player, BlockPos pos, int docId, List<NotepadDocument> docs) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeInt(docId);
        buf.writeInt(docs.size());
        for (NotepadDocument d : docs) {
            buf.writeInt(d.getId());
            buf.writeString(d.getName(), 48);
            buf.writeString(d.getText(), 8192);
        }
        ServerPlayNetworking.send(player, NOTEPAD_DATA_ID, buf);
    }

    // Client send helpers
    @Environment(EnvType.CLIENT)
    public static void sendRelayKeyName(String name, Hand hand) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(name, 32);
        buf.writeEnumConstant(hand);
        ClientPlayNetworking.send(RELAY_KEY_NAME_ID, buf);
    }

    @Environment(EnvType.CLIENT)
    public static void sendScannerCode(BlockPos pos, String code) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeString(code, 6);
        ClientPlayNetworking.send(SCANNER_CODE_ID, buf);
    }

    @Environment(EnvType.CLIENT)
    public static void sendScannerKey(BlockPos pos, String value, boolean assign, boolean biometric, String name) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeString(value, 64);
        buf.writeBoolean(assign);
        buf.writeBoolean(biometric);
        buf.writeString(name, 32);
        ClientPlayNetworking.send(SCANNER_KEY_ID, buf);
    }

    @Environment(EnvType.CLIENT)
    public static void sendScannerOption(BlockPos pos, byte option, boolean value) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeByte(option);
        buf.writeBoolean(value);
        ClientPlayNetworking.send(SCANNER_OPTION_ID, buf);
    }

    @Environment(EnvType.CLIENT)
    public static void sendProgrammerPage(byte page) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByte(page);
        ClientPlayNetworking.send(PROGRAMMER_PAGE_ID, buf);
    }

    @Environment(EnvType.CLIENT)
    public static void sendProgrammerAction(byte action, int param) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByte(action);
        buf.writeInt(param);
        ClientPlayNetworking.send(PROGRAMMER_ACTION_ID, buf);
    }

    @Environment(EnvType.CLIENT)
    public static void sendKeypadCode(BlockPos pos, String code) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeString(code, 6);
        ClientPlayNetworking.send(KEYPAD_CODE_ID, buf);
    }

    @Environment(EnvType.CLIENT)
    public static void sendKeypadOption(BlockPos pos, byte option, boolean value) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeByte(option);
        buf.writeBoolean(value);
        ClientPlayNetworking.send(KEYPAD_OPTION_ID, buf);
    }

    @Environment(EnvType.CLIENT)
    public static void sendKeypadSecurityCode(BlockPos pos, String code) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeString(code, 6);
        ClientPlayNetworking.send(KEYPAD_SECURITY_CODE_ID, buf);
    }

    @Environment(EnvType.CLIENT)
    public static void sendMonitorAction(BlockPos pos, int action, String strParam, int intParam) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeInt(action);
        buf.writeString(strParam == null ? "" : strParam, 256);
        buf.writeInt(intParam);
        ClientPlayNetworking.send(MONITOR_ACTION_ID, buf);
    }

    @Environment(EnvType.CLIENT)
    public static void sendMonitorDisplay(BlockPos pos, int screenIndex, boolean viewing, int stage, int netId, int docId, boolean eventsTab) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeInt(screenIndex);
        buf.writeBoolean(viewing);
        buf.writeInt(stage);
        buf.writeInt(netId);
        buf.writeInt(docId);
        buf.writeBoolean(eventsTab);
        ClientPlayNetworking.send(MONITOR_DISPLAY_ID, buf);
    }

    @Environment(EnvType.CLIENT)
    public static void sendMonitorDeviceControl(BlockPos deskPos, int netId, int command, BlockPos targetPos, int eventId, boolean state) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(deskPos);
        buf.writeInt(netId);
        buf.writeInt(command);
        buf.writeBlockPos(targetPos);
        buf.writeInt(eventId);
        buf.writeBoolean(state);
        ClientPlayNetworking.send(MONITOR_DEVICE_CONTROL_ID, buf);
    }

    @Environment(EnvType.CLIENT)
    public static void sendMonitorNetRequest(BlockPos pos, int netId) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeInt(netId);
        ClientPlayNetworking.send(MONITOR_NET_REQUEST_ID, buf);
    }

    @Environment(EnvType.CLIENT)
    public static void sendMonitorWallpaper(BlockPos pos, byte screenIndex, String wallpaper) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeByte(screenIndex);
        buf.writeString(wallpaper == null ? "" : wallpaper, 512);
        ClientPlayNetworking.send(MONITOR_WALLPAPER_ID, buf);
    }

    @Environment(EnvType.CLIENT)
    public static void sendNotepadAction(BlockPos pos, byte action, int docId, String text) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeByte(action);
        buf.writeInt(docId);
        buf.writeString(text == null ? "" : text, 8192);
        ClientPlayNetworking.send(NOTEPAD_ACTION_ID, buf);
    }

    @Environment(EnvType.CLIENT)
    public static void sendMonitorBackupOpen(BlockPos pos, boolean restore) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeBoolean(restore);
        ClientPlayNetworking.send(MONITOR_BACKUP_OPEN_ID, buf);
    }

    @Environment(EnvType.CLIENT)
    public static void sendDocumentsFolderPages(Hand hand, int lastPage, String customName, List<String> pages) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByte(hand == Hand.OFF_HAND ? 1 : 0);
        buf.writeByte(Math.max(0, Math.min(ItemDocumentsFolder.PAGE_COUNT - 1, lastPage)));
        buf.writeString(customName != null ? customName : "", ItemDocumentsFolder.MAX_NAME_LENGTH);
        int count = Math.min(pages.size(), ItemDocumentsFolder.PAGE_COUNT);
        buf.writeByte(count);
        for (int i = 0; i < count; i++) {
            String p = pages.get(i);
            buf.writeString(p != null ? p : "", ItemDocumentsFolder.MAX_PAGE_LENGTH);
        }
        ClientPlayNetworking.send(DOCUMENTS_FOLDER_PAGES_ID, buf);
    }

    @Environment(EnvType.CLIENT)
    public static void sendSpatialDisrupterActivate(BlockPos pos) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        ClientPlayNetworking.send(SPATIAL_DISRUPTER_ACTIVATE_ID, buf);
    }

    @Environment(EnvType.CLIENT)
    public static void sendWeaponsKeyMode(Hand hand, WeaponsMode mode) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeEnumConstant(hand);
        buf.writeByte(mode.id);
        ClientPlayNetworking.send(WEAPONS_KEY_MODE_ID, buf);
    }

    @Environment(EnvType.CLIENT)
    public static void sendTerminalBind(Hand hand, BlockPos pos, String name) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeEnumConstant(hand);
        buf.writeBlockPos(pos);
        buf.writeString(name == null ? "" : name, 64);
        ClientPlayNetworking.send(TERMINAL_BIND_ID, buf);
    }

    private ModMessages() {}
}
