package com.ntmdoors.network;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.block.entity.KeycardProgrammerBlockEntity;
import com.ntmdoors.block.entity.KeycardScannerBlockEntity;
import com.ntmdoors.block.entity.KeypadBlockEntity;
import com.ntmdoors.client.screen.KeypadConfigScreen;
import com.ntmdoors.client.screen.ScannerKeysScreen;
import com.ntmdoors.item.ItemOperatorKeycard;
import com.ntmdoors.item.ItemRelayKey;
import com.ntmdoors.screen.KeycardProgrammerScreenHandler;
import com.ntmdoors.sound.ModSounds;
import com.ntmdoors.util.HeldItems;
import com.ntmdoors.util.PrivateKeys;
import io.netty.buffer.Unpooled;
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
                    if (action == 0) {
                        be.bind(param);
                    } else if (action == 1) {
                        be.applyColor(param);
                    } else if (action == 2) {
                        be.startBackup();
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

    private ModMessages() {}
}
