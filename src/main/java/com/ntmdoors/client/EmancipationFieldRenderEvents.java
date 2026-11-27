// SPDX-License-Identifier: LGPL-3.0-only
package com.ntmdoors.client;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.armor.SecurityShroudLogic;
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
import net.minecraftforge.fml.common.gameevent.TickEvent;
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
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START && event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || event.player != mc.player
                || !(event.player instanceof EntityPlayerSP)) {
            return;
        }
        EntityPlayerSP player = (EntityPlayerSP) event.player;
        if (player.isSpectator() || player.capabilities.isFlying || player.motionY > 0.0D) return;
        boolean jumping = player.movementInput != null && player.movementInput.jump;
        Boolean shroudIgnored = null;

        for (TileEntity te : mc.world.loadedTileEntityList) {
            if (!(te instanceof TileEntityEmancipationGrill)) continue;
            TileEntityEmancipationGrill grill = (TileEntityEmancipationGrill) te;
            if (!grill.isPairOwner() || !grill.isFieldEnabled() || !grill.isDetectPlayers()) continue;
            TileEntityEmancipationGrill.FieldMode mode = grill.getFieldMode();
            if (mode != TileEntityEmancipationGrill.FieldMode.SOLID
                    && mode != TileEntityEmancipationGrill.FieldMode.WALL) continue;
            if (shroudIgnored == null) {
                shroudIgnored = SecurityShroudLogic.ignoresEmancipationGrills(player);
            }
            if (shroudIgnored) continue;
            if (mode == TileEntityEmancipationGrill.FieldMode.SOLID && grill.hasClearance(player)) continue;

            AxisAlignedBB field = grill.getFieldBox();
            if (field == null || field.maxY - field.minY > 0.1D) continue;
            double half = player.width * 0.5D;
            if (player.posX + half < field.minX || player.posX - half > field.maxX
                    || player.posZ + half < field.minZ || player.posZ - half > field.maxZ) {
                continue;
            }

            double support = (field.minY + field.maxY) * 0.5D + 0.05D;
            if (player.posY < support - 0.2D || player.posY > support + 0.35D) continue;
            if (event.phase == TickEvent.Phase.START && jumping) continue;

            // Keep the local player planted on the horizontal sheet. At the start of the tick
            // the small upward motion cancels vanilla gravity before the movement packet is
            // sent; at the end the position is locked to the support height.
            player.setPosition(player.posX, support, player.posZ);
            player.motionY = event.phase == TickEvent.Phase.START ? 0.08D : 0.0D;
            player.onGround = true;
            player.fallDistance = 0.0F;
            return;
        }
    }

    @SubscribeEvent
    public static void onInputUpdate(InputUpdateEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null || mc.world == null || event.getEntityPlayer() != player) return;

        MovementInput input = event.getMovementInput();
        if (input == null || (input.moveForward == 0.0F && input.moveStrafe == 0.0F)) return;
        Boolean shroudIgnored = null;

        for (TileEntity te : mc.world.loadedTileEntityList) {
            if (!(te instanceof TileEntityEmancipationGrill)) continue;
            TileEntityEmancipationGrill grill = (TileEntityEmancipationGrill) te;
            if (!grill.isPairOwner()) continue;
            if (!grill.isFieldEnabled()) continue;
            if (!grill.isDetectPlayers()) continue;
            TileEntityEmancipationGrill.FieldMode mode = grill.getFieldMode();
            if (mode != TileEntityEmancipationGrill.FieldMode.SOLID
                    && mode != TileEntityEmancipationGrill.FieldMode.WALL) continue;
            if (grill.getPos().distanceSq(player.posX, player.posY, player.posZ) > 1024.0D) continue;
            if (shroudIgnored == null) {
                shroudIgnored = SecurityShroudLogic.ignoresEmancipationGrills(player);
            }
            if (shroudIgnored) continue;

            AxisAlignedBB field = grill.getFieldBox();
            if (field == null) continue;
            if (mode == TileEntityEmancipationGrill.FieldMode.SOLID && grill.hasClearance(player)) continue;
            if (field.maxY - field.minY <= 0.1D) {
                if (blockVerticalMovement(player, field, input)) return;
                continue;
            }
            if (!nearField(player, field)) continue;
            if (blockTowardsField(player, field, input)) return;
        }
    }

    /** Stops a jump or sneak that would push the player through a horizontal field. */
    private static boolean blockVerticalMovement(EntityPlayerSP player, AxisAlignedBB field,
                                                 MovementInput input) {
        double half = player.width * 0.5D;
        if (player.posX + half < field.minX || player.posX - half > field.maxX
                || player.posZ + half < field.minZ || player.posZ - half > field.maxZ) {
            return false;
        }
        double plane = (field.minY + field.maxY) * 0.5D;
        double centre = player.posY + player.height * 0.5D;
        if (Math.abs(centre - plane) > player.height * 0.5D + 0.5D) return false;
        if (centre < plane && input.jump) {
            input.jump = false;
            return true;
        }
        if (centre > plane && input.sneak) {
            input.sneak = false;
            return true;
        }
        return false;
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
