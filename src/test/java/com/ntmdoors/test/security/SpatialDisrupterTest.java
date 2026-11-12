package com.ntmdoors.test.security;

import com.ntmdoors.block.BlockSpatialDisrupter;
import com.ntmdoors.block.ModBlocks;
import com.ntmdoors.block.entity.ModBlockEntities;
import com.ntmdoors.block.entity.SpatialDisrupterBlockEntity;
import com.ntmdoors.item.ModItems;
import com.ntmdoors.sound.ModSounds;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.Bootstrap;
import net.minecraft.block.BlockState;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class SpatialDisrupterTest {

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
        ModSounds.registerModSounds();
        ModBlocks.registerModBlocks();
        ModBlockEntities.registerModBlockEntities();
        ModItems.registerModItems();
    }

    @Test
    @DisplayName("Verify BlockSpatialDisrupter properties: unbreakable hardness, 6M blast resistance, piston blocking")
    public void testBlockProperties() {
        BlockSpatialDisrupter block = ModBlocks.SPATIAL_DISRUPTER;
        BlockState state = block.getDefaultState();

        assertEquals(-1.0F, state.getHardness(null, BlockPos.ORIGIN), "Must have bedrock-tier hardness (-1.0F)");
        assertEquals(6000000.0F, block.getBlastResistance(), "Must have 6,000,000F blast resistance");
        assertEquals(PistonBehavior.BLOCK, state.getPistonBehavior(), "Piston behavior must be BLOCK (immovable)");
    }

    @Test
    @DisplayName("Verify scanning area and section budget constants")
    public void testScanningConstants() {
        assertEquals(16, SpatialDisrupterBlockEntity.AREA_CHUNKS, "Must scan a 16x16 chunk area");
        assertEquals(4, SpatialDisrupterBlockEntity.SECTIONS_PER_TICK, "Must process 4 sections per tick");
    }

    @Test
    @DisplayName("Verify SpatialDisrupterBlockEntity NBT persistence roundtrip")
    public void testBlockEntityNbtRoundtrip() {
        BlockPos pos = new BlockPos(128, 64, -256);
        BlockState state = ModBlocks.SPATIAL_DISRUPTER.getDefaultState();
        SpatialDisrupterBlockEntity be = new SpatialDisrupterBlockEntity(pos, state);

        assertFalse(be.isActivated());
        assertEquals(0, be.getChunkIndex());
        assertEquals(0, be.getSectionIndex());
        assertEquals(0, be.getConverted());

        UUID playerUuid = UUID.randomUUID();
        NbtCompound tag = new NbtCompound();
        tag.putBoolean("activated", true);
        tag.putInt("chunkIndex", 77);
        tag.putInt("sectionIndex", 11);
        tag.putInt("converted", 35);
        tag.putLong("startedTick", 987654L);
        tag.putString("activator", playerUuid.toString());

        be.readNbt(tag);

        assertTrue(be.isActivated());
        assertEquals(77, be.getChunkIndex());
        assertEquals(11, be.getSectionIndex());
        assertEquals(35, be.getConverted());

        NbtCompound written = new NbtCompound();
        be.writeNbt(written);

        assertTrue(written.getBoolean("activated"));
        assertEquals(77, written.getInt("chunkIndex"));
        assertEquals(11, written.getInt("sectionIndex"));
        assertEquals(35, written.getInt("converted"));
        assertEquals(987654L, written.getLong("startedTick"));
        assertEquals(playerUuid.toString(), written.getString("activator"));
    }

    @Test
    @DisplayName("Verify SpatialDisrupter network packet payload roundtrip encoding/decoding")
    public void testSpatialDisrupterPacketCodec() {
        BlockPos targetPos = new BlockPos(-512, 40, 1024);
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(targetPos);

        BlockPos readPos = buf.readBlockPos();
        assertEquals(targetPos, readPos);
    }
}
