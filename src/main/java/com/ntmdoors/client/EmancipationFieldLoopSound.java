package com.ntmdoors.client;

import com.ntmdoors.tile.TileEntityEmancipationGrill;
import com.ntmdoors.util.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.MovingSound;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Repeating field hum for one Emancipation Grill pair. The source follows the nearest point on the
 * field plane and the volume fades manually, so a long field still sounds like it comes from the
 * part of the curtain nearest the player.
 */
@SideOnly(Side.CLIENT)
public class EmancipationFieldLoopSound extends MovingSound {

    private static final double RANGE = 3.0D;
    private static final float BASE_VOLUME = 0.35F;

    private final BlockPos core;
    private double centerX;
    private double centerY;
    private double centerZ;
    private boolean audioEnabled = true;

    public EmancipationFieldLoopSound(BlockPos core) {
        super(ModSounds.EMANCIPATION_LOOP, SoundCategory.BLOCKS);
        this.core = core;
        this.centerX = core.getX() + 0.5D;
        this.centerY = core.getY() + 1.5D;
        this.centerZ = core.getZ() + 0.5D;
        this.repeat = true;
        this.repeatDelay = 0;
        this.attenuationType = ISound.AttenuationType.NONE;
        this.volume = 0.0001F;
        this.pitch = 1.0F;
    }

    @Override
    public void update() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.player;
        if (player == null || mc.world == null) {
            donePlaying = true;
            return;
        }

        TileEntity te = mc.world.getTileEntity(core);
        if (!(te instanceof TileEntityEmancipationGrill)) {
            donePlaying = true;
            return;
        }
        TileEntityEmancipationGrill grill = (TileEntityEmancipationGrill) te;
        if (!grill.isPairOwner() || !grill.isFieldEnabled() || !grill.isAudioEnabled()
                || grill.getPartner() == null) {
            audioEnabled = false;
            donePlaying = true;
            return;
        }
        audioEnabled = true;

        AxisAlignedBB box = grill.getFieldBox();
        if (box == null) {
            donePlaying = true;
            return;
        }

        double eyeX = player.posX;
        double eyeY = player.posY + player.getEyeHeight();
        double eyeZ = player.posZ;
        centerX = clamp(eyeX, box.minX, box.maxX);
        centerY = clamp(eyeY, box.minY, box.maxY);
        centerZ = clamp(eyeZ, box.minZ, box.maxZ);

        double dx = eyeX - centerX;
        double dy = eyeY - centerY;
        double dz = eyeZ - centerZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float fade = (float) Math.max(0.0D, 1.0D - distance / RANGE);
        volume = Math.max(0.0001F, BASE_VOLUME * fade);

        xPosF = (float) centerX;
        yPosF = (float) centerY;
        zPosF = (float) centerZ;
    }

    /** Plays the shutdown one-shot at the last known field position. */
    void playShutdown(Minecraft mc) {
        if (mc.world == null || mc.player == null) return;
        TileEntity te = mc.world.getTileEntity(core);
        if (te instanceof TileEntityEmancipationGrill
                && !((TileEntityEmancipationGrill) te).isAudioEnabled()) {
            return;
        }
        if (!audioEnabled && te == null) return;
        if (mc.player.getDistanceSq(centerX, centerY, centerZ) > RANGE * RANGE) return;
        mc.world.playSound(mc.player, centerX, centerY, centerZ,
                ModSounds.EMANCIPATION_SHUTDOWN, SoundCategory.BLOCKS, 0.5F, 1.0F);
    }

    private static double clamp(double value, double min, double max) {
        return value < min ? min : value > max ? max : value;
    }
}
