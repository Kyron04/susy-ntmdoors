package com.ntmdoors.event;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.block.BlockDoorGeneric;
import com.ntmdoors.block.BlockDoorKeypad;
import com.ntmdoors.block.BlockKeycardProgrammer;
import com.ntmdoors.block.BlockKeycardScanner;
import com.ntmdoors.door.DoorPlacementGuard;
import com.ntmdoors.item.ItemKeycardHolder;
import com.ntmdoors.item.ItemRedstoneMonocle;
import com.ntmdoors.item.ItemRedstoneRelayKey;
import com.ntmdoors.item.ItemRelayKey;
import com.ntmdoors.item.ItemRfidCard;
import com.ntmdoors.item.ItemSafeBindingKey;
import com.ntmdoors.item.ItemScrewdriver;
import com.ntmdoors.item.ItemTerminalRelayKey;
import com.ntmdoors.tile.TileEntityDoorGeneric;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Cross-mod interaction guards for the mod's blocks.
 *
 * <p>Only our own tools may right-click a door, scanner, keypad or programmer. A wrench from
 * another mod is refused before it can reach its own use or the block's activation, which keeps it
 * from re-orienting a door or driving one through a side effect we never accounted for. Normal
 * block placement still works, because every {@link ItemBlock} is allowed through.</p>
 *
 * <p>The mount guard closes the boat hole: a player can sit in a boat, right-click a second boat
 * on the far side of a closed door and be teleported through it. Mounting is refused whenever a
 * solid door panel lies between the player and the seat.</p>
 */
@Mod.EventBusSubscriber(modid = NTMDoors.MODID)
public final class GuardEvents {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled()) return;
        World world = event.getWorld();
        BlockPos pos = event.getPos();
        if (!isGuardedBlock(world.getBlockState(pos).getBlock())) return;

        ItemStack held = event.getItemStack();
        if (held.isEmpty()) return;
        Item item = held.getItem();
        if (item instanceof ItemScrewdriver
                || item instanceof ItemRfidCard
                || item instanceof ItemRelayKey
                || item instanceof ItemSafeBindingKey
                || item instanceof ItemRedstoneRelayKey
                || item instanceof ItemRedstoneMonocle
                || item instanceof ItemTerminalRelayKey
                || item instanceof ItemKeycardHolder
                || item instanceof ItemBlock) {
            return;
        }
        event.setCanceled(true);
    }

    /**
     * Refuses placement into the space a door's moving panel occupies when open. The door block
     * itself is non-replaceable, but its panel can park outside the footprint (above a roller
     * shutter, in a wall pocket, or sideways), so without this check a block could be placed inside
     * the visible open door. Forge restores any captured block snapshots when this event is
     * cancelled.
     */
    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getWorld().isRemote) return;
        if (event instanceof BlockEvent.MultiPlaceEvent) {
            for (BlockSnapshot snapshot : ((BlockEvent.MultiPlaceEvent) event).getReplacedBlockSnapshots()) {
                if (DoorPlacementGuard.blocksPlacement(event.getWorld(), snapshot.getPos())) {
                    event.setCanceled(true);
                    return;
                }
            }
            return;
        }
        if (DoorPlacementGuard.blocksPlacement(event.getWorld(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityMount(EntityMountEvent event) {
        if (!event.isMounting()) return;
        Entity rider = event.getEntityMounting();
        Entity mount = event.getEntityBeingMounted();
        if (!(rider instanceof EntityPlayer) || mount == null || rider.world == null) return;
        if (mount.world != rider.world) return;

        Vec3d from = rider.getPositionEyes(1F);
        Vec3d to = new Vec3d(mount.posX, mount.posY + mount.height * 0.5D, mount.posZ);
        RayTraceResult hit = rider.world.rayTraceBlocks(from, to, false, true, false);
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) return;
        if (isSolidDoor(rider.world, hit.getBlockPos())) event.setCanceled(true);
    }

    private static boolean isGuardedBlock(Block block) {
        return block instanceof BlockDoorGeneric
                || block instanceof BlockDoorKeypad
                || block instanceof BlockKeycardScanner
                || block instanceof BlockKeycardProgrammer;
    }

    /**
     * True when the door cell at {@code pos} currently has collision. A fully open door has none,
     * and walking or mounting through it is allowed; the raytrace itself can still report the
     * closed shape because that shape is kept as the click target for open doors.
     */
    private static boolean isSolidDoor(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockDoorGeneric)) return false;
        TileEntityDoorGeneric core = ((BlockDoorGeneric) state.getBlock()).getCore(world, pos, state);
        if (core == null) return true;
        return core.getCollisionBoxes(state.getValue(BlockDoorGeneric.FACING)).length > 0;
    }

    private GuardEvents() {
    }
}
