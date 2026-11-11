package com.ntmdoors.util;

import net.minecraft.util.math.BlockPos;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** NBT-friendly encoding for positions: {@code x,y,z} and {@code x,y,z;x,y,z}. */
public final class BlockPositions {

    public static String encode(@Nullable BlockPos pos) {
        return pos == null ? "" : pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    public static String encodeList(Iterable<BlockPos> positions) {
        StringBuilder out = new StringBuilder();
        for (BlockPos pos : positions) {
            if (out.length() > 0) out.append(';');
            out.append(encode(pos));
        }
        return out.toString();
    }

    @Nullable
    public static BlockPos decode(String text) {
        if (text == null || text.isEmpty()) return null;
        String[] xyz = text.split(",");
        if (xyz.length != 3) return null;
        try {
            return new BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]),
                    Integer.parseInt(xyz[2]));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static List<BlockPos> decodeList(String text) {
        if (text == null || text.isEmpty()) return Collections.emptyList();
        List<BlockPos> out = new ArrayList<>();
        for (String part : text.split(";")) {
            BlockPos pos = decode(part);
            if (pos != null) out.add(pos);
        }
        return out;
    }

    private BlockPositions() {
    }
}
