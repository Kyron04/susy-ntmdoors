package com.ntmdoors.client;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.tile.TileEntityEmancipationGrill;
import com.ntmdoors.util.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Starts, loops and stops the Emancipation Grill field sounds on the client. The server already
 * syncs whether a pair exists and whether its field is enabled, so the client can keep the ambient
 * loop tied to the field without extra packets.
 */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = NTMDoors.MODID, value = Side.CLIENT)
public final class EmancipationFieldSoundEvents {

    private static final Map<Long, EmancipationFieldLoopSound> LOOPS = new HashMap<>();
    private static World lastWorld;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) {
            stopAll(mc);
            lastWorld = null;
            return;
        }
        if (mc.world != lastWorld) {
            stopAll(mc);
            lastWorld = mc.world;
        }

        Set<Long> seen = new HashSet<>();
        for (TileEntity te : mc.world.loadedTileEntityList) {
            if (!(te instanceof TileEntityEmancipationGrill)) continue;
            TileEntityEmancipationGrill grill = (TileEntityEmancipationGrill) te;
            if (!grill.isPairOwner() || !grill.isFieldEnabled() || !grill.isAudioEnabled()) continue;

            BlockPos partner = grill.getPartner();
            AxisAlignedBB box = grill.getFieldBox();
            if (partner == null || box == null) continue;

            long key = Math.min(grill.getPos().toLong(), partner.toLong());
            if (!seen.add(key)) continue;
            if (LOOPS.containsKey(key)) continue;

            EmancipationFieldLoopSound loop = new EmancipationFieldLoopSound(grill.getPos());
            LOOPS.put(key, loop);
            mc.getSoundHandler().playSound(loop);
            playStart(mc, box);
        }

        Iterator<Map.Entry<Long, EmancipationFieldLoopSound>> iterator =
                LOOPS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, EmancipationFieldLoopSound> entry = iterator.next();
            if (seen.contains(entry.getKey())) continue;

            EmancipationFieldLoopSound loop = entry.getValue();
            mc.getSoundHandler().stopSound(loop);
            loop.playShutdown(mc);
            iterator.remove();
        }
    }

    private static void playStart(Minecraft mc, AxisAlignedBB box) {
        if (mc.world == null || mc.player == null) return;
        double eyeY = mc.player.posY + mc.player.getEyeHeight();
        double x = clamp(mc.player.posX, box.minX, box.maxX);
        double y = clamp(eyeY, box.minY, box.maxY);
        double z = clamp(mc.player.posZ, box.minZ, box.maxZ);
        if (mc.player.getDistanceSq(x, y, z) > 9.0D) return;
        mc.world.playSound(mc.player, x, y, z,
                ModSounds.EMANCIPATION_START, SoundCategory.BLOCKS, 0.5F, 1.0F);
    }

    private static double clamp(double value, double min, double max) {
        return value < min ? min : value > max ? max : value;
    }

    private static void stopAll(Minecraft mc) {
        for (EmancipationFieldLoopSound loop : LOOPS.values()) {
            mc.getSoundHandler().stopSound(loop);
        }
        LOOPS.clear();
    }

    private EmancipationFieldSoundEvents() {
    }
}
