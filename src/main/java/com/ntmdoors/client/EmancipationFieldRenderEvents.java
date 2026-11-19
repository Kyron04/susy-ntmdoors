package com.ntmdoors.client;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.tile.TileEntityEmancipationGrill;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MovementInput;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Client-side Emancipation Grill bookkeeping: ends the per-frame render set and discards the
 * movement component that pushes an unauthorized player into the field. The server clamp remains
 * authoritative; this only stops the client from continuously trying to walk into a wall.
 */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = NTMDoors.MODID, value = Side.CLIENT)
public final class EmancipationFieldRenderEvents {

    @SubscribeEvent
    public static void onRenderWorldLast(RenderWorldLastEvent event) {
        try {
            RenderEmancipationField.drawDeferred(event.getPartialTicks());
        } finally {
            RenderEmancipationField.endFrame();
        }
    }

    @SubscribeEvent
    public static void onInputUpdate(InputUpdateEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null || mc.world == null || event.getEntityPlayer() != player) return;

        MovementInput input = event.getMovementInput();
        if (input == null || (input.moveForward == 0.0F && input.moveStrafe == 0.0F)) return;

        for (TileEntity te : mc.world.loadedTileEntityList) {
            if (!(te instanceof TileEntityEmancipationGrill)) continue;
            TileEntityEmancipationGrill grill = (TileEntityEmancipationGrill) te;
            if (!grill.isPairOwner()) continue;
            if (!grill.isFieldEnabled()) continue;
            if (!grill.isDetectPlayers()) continue;
            if (grill.getFieldMode() != TileEntityEmancipationGrill.FieldMode.SOLID) continue;
            if (grill.getPos().distanceSq(player.posX, player.posY, player.posZ) > 1024.0D) continue;

            AxisAlignedBB field = grill.getFieldBox();
            if (field == null) continue;
            if (!nearField(player, field)) continue;
            if (grill.hasClearance(player)) continue;
            if (blockTowardsField(player, field, input)) return;
        }
    }

    /** True when the player is close enough to the plane for input rejection to matter. */
    private static boolean nearField(EntityPlayerSP player, AxisAlignedBB field) {
        boolean spansX = (field.maxX - field.minX) > (field.maxZ - field.minZ);
        double plane = spansX
                ? (field.minZ + field.maxZ) * 0.5D
                : (field.minX + field.maxX) * 0.5D;
        double current = spansX ? player.posZ : player.posX;
        if (Math.abs(current - plane) > 0.40D) return false;

        double along = spansX ? player.posX : player.posZ;
        double half = player.width * 0.5D;
        double alongMin = spansX ? field.minX : field.minZ;
        double alongMax = spansX ? field.maxX : field.maxZ;
        if (along + half < alongMin || along - half > alongMax) return false;
        return player.posY + player.height >= field.minY && player.posY <= field.maxY;
    }

    /**
     * Removes only the component of the player's movement input that points into the field. Moving
     * sideways along the field and backing away are preserved.
     */
    private static boolean blockTowardsField(EntityPlayerSP player, AxisAlignedBB field,
                                             MovementInput input) {
        boolean spansX = (field.maxX - field.minX) > (field.maxZ - field.minZ);
        double plane = spansX
                ? (field.minZ + field.maxZ) * 0.5D
                : (field.minX + field.maxX) * 0.5D;
        double current = spansX ? player.posZ : player.posX;
        double distance = current - plane;
        if (Math.abs(distance) > 0.40D) return false;

        double along = spansX ? player.posX : player.posZ;
        double half = player.width * 0.5D;
        double alongMin = spansX ? field.minX : field.minZ;
        double alongMax = spansX ? field.maxX : field.maxZ;
        if (along + half < alongMin || along - half > alongMax) return false;
        if (player.posY + player.height < field.minY || player.posY > field.maxY) return false;

        double yaw = Math.toRadians(player.rotationYaw);
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        double dx = input.moveStrafe * cos - input.moveForward * sin;
        double dz = input.moveForward * cos + input.moveStrafe * sin;

        double normal = spansX ? dz : dx;
        double toward = normal * (distance >= 0.0D ? -1.0D : 1.0D);
        if (toward <= 1.0E-5D) return false;

        // Remove the normal component, then convert the along-field remainder back to the two
        // MovementInput axes. Both axes stay usable for sliding and backing out.
        if (spansX) dz = 0.0D;
        else dx = 0.0D;

        input.moveForward = (float) (-dx * sin + dz * cos);
        input.moveStrafe = (float) (dx * cos + dz * sin);
        return true;
    }

    private EmancipationFieldRenderEvents() {
    }
}
