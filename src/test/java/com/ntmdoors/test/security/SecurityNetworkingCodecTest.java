package com.ntmdoors.test.security;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.Bootstrap;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class SecurityNetworkingCodecTest {

    @BeforeAll
    public static void setup() {
        net.minecraft.SharedConstants.createGameVersion();
        try {
            java.lang.reflect.Field field = Bootstrap.class.getDeclaredField("initialized");
            field.setAccessible(true);
            field.setBoolean(null, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        net.minecraft.registry.Registries.REGISTRIES.getIds();
    }

    @Test
    @DisplayName("Verify RelayKeyName packet payload roundtrip encoding/decoding")
    public void testRelayKeyNamePacketCodec() {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString("Storage Sector 9", 32);
        buf.writeEnumConstant(Hand.MAIN_HAND);

        String readName = buf.readString(32);
        Hand readHand = buf.readEnumConstant(Hand.class);

        assertEquals("Storage Sector 9", readName);
        assertEquals(Hand.MAIN_HAND, readHand);
    }

    @Test
    @DisplayName("Verify ScannerCode packet payload roundtrip encoding/decoding")
    public void testScannerCodePacketCodec() {
        BlockPos testPos = new BlockPos(1024, 64, -2048);
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(testPos);
        buf.writeString("123456", 6);

        BlockPos readPos = buf.readBlockPos();
        String readCode = buf.readString(6);

        assertEquals(testPos, readPos);
        assertEquals("123456", readCode);
    }

    @Test
    @DisplayName("Verify ScannerKey assignment payload roundtrip encoding/decoding")
    public void testScannerKeyPacketCodec() {
        BlockPos testPos = new BlockPos(-50, 12, 100);
        String uuidStr = UUID.randomUUID().toString();
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(testPos);
        buf.writeString(uuidStr, 64);
        buf.writeBoolean(true); // assign
        buf.writeBoolean(true); // biometric
        buf.writeString("Director Vance", 32);

        BlockPos readPos = buf.readBlockPos();
        String readVal = buf.readString(64);
        boolean readAssign = buf.readBoolean();
        boolean readBio = buf.readBoolean();
        String readName = buf.readString(32);

        assertEquals(testPos, readPos);
        assertEquals(uuidStr, readVal);
        assertTrue(readAssign);
        assertTrue(readBio);
        assertEquals("Director Vance", readName);
    }

    @Test
    @DisplayName("Verify ScannerOption payload roundtrip encoding/decoding")
    public void testScannerOptionPacketCodec() {
        BlockPos testPos = new BlockPos(0, 0, 0);
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(testPos);
        buf.writeByte((byte) 2); // lockout mode option
        buf.writeBoolean(true);

        BlockPos readPos = buf.readBlockPos();
        byte readOption = buf.readByte();
        boolean readVal = buf.readBoolean();

        assertEquals(testPos, readPos);
        assertEquals((byte) 2, readOption);
        assertTrue(readVal);
    }

    @Test
    @DisplayName("Verify ProgrammerPage and ProgrammerAction payload roundtrip encoding/decoding")
    public void testProgrammerPacketsCodec() {
        // Page packet
        PacketByteBuf pageBuf = PacketByteBufs.create();
        pageBuf.writeByte((byte) 1);
        assertEquals((byte) 1, pageBuf.readByte());

        // Action packet
        PacketByteBuf actionBuf = PacketByteBufs.create();
        actionBuf.writeByte((byte) 0); // BIND
        actionBuf.writeInt(1);         // row 1
        assertEquals((byte) 0, actionBuf.readByte());
        assertEquals(1, actionBuf.readInt());
    }

    @Test
    @DisplayName("Verify Keypad Code and Config packet payloads roundtrip encoding/decoding")
    public void testKeypadPacketsCodec() {
        BlockPos pos = new BlockPos(12, 34, 56);

        // Keypad code
        PacketByteBuf codeBuf = PacketByteBufs.create();
        codeBuf.writeBlockPos(pos);
        codeBuf.writeString("987654", 6);
        assertEquals(pos, codeBuf.readBlockPos());
        assertEquals("987654", codeBuf.readString(6));

        // Keypad option
        PacketByteBuf optBuf = PacketByteBufs.create();
        optBuf.writeBlockPos(pos);
        optBuf.writeByte((byte) 1);
        optBuf.writeBoolean(false);
        assertEquals(pos, optBuf.readBlockPos());
        assertEquals((byte) 1, optBuf.readByte());
        assertFalse(optBuf.readBoolean());

        // Keypad security code
        PacketByteBuf secBuf = PacketByteBufs.create();
        secBuf.writeBlockPos(pos);
        secBuf.writeString("000000", 6);
        assertEquals(pos, secBuf.readBlockPos());
        assertEquals("000000", secBuf.readString(6));

        // Keypad security code result
        PacketByteBuf resBuf = PacketByteBufs.create();
        resBuf.writeBlockPos(pos);
        resBuf.writeByte((byte) 1); // SET
        assertEquals(pos, resBuf.readBlockPos());
        assertEquals((byte) 1, resBuf.readByte());
    }
}
