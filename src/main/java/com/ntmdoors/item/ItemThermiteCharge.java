package com.ntmdoors.item;

import com.ntmdoors.block.door.BlockDoorGeneric;
import com.ntmdoors.block.entity.DoorBlockEntity;
import com.ntmdoors.block.entity.ThermiteChargeBlockEntity;
import com.ntmdoors.door.DoorPlacementGuard;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Item form of the Thermite Charge.
 * Intercepts door clicks to calculate hit inset (0..15) and record mount onto the block entity,
 * while preventing mounting on excluded blast/vault/hatch doors.
 */
public class ItemThermiteCharge extends BlockItem {

    public ItemThermiteCharge(Block block) {
        super(block, new Settings());
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        BlockState clicked = world.getBlockState(pos);
        PlayerEntity player = context.getPlayer();
        Direction side = context.getSide();

        BlockDoorGeneric door = clicked.getBlock() instanceof BlockDoorGeneric d ? d : null;
        if (door != null && isExcludedDoor(door)) {
            if (!world.isClient() && player != null) {
                player.sendMessage(Text.translatable("ntmdoors.msg.thermite_door_unsupported"), true);
            }
            return ActionResult.SUCCESS;
        }

        BlockPos placedPos = clicked.isReplaceable() ? pos : pos.offset(side);
        if (DoorPlacementGuard.blocksPlacement(world, placedPos)) {
            return ActionResult.FAIL;
        }

        ActionResult result = super.useOnBlock(context);

        if (result.isAccepted() && door != null) {
            Vec3d hitPos = context.getHitPos();
            double hitX = hitPos.x - pos.getX();
            double hitY = hitPos.y - pos.getY();
            double hitZ = hitPos.z - pos.getZ();
            int inset = insetFromHit(side, hitX, hitY, hitZ);

            if (world.getBlockEntity(placedPos) instanceof ThermiteChargeBlockEntity charge) {
                DoorBlockEntity core = BlockDoorGeneric.getCore(world, pos, clicked);
                BlockPos corePos = core != null ? core.getPos() : (world.getBlockEntity(pos) instanceof com.ntmdoors.block.entity.DoorPartBlockEntity part ? part.getCorePos() : pos);
                float fraction = core == null ? 0.0F : core.getOpenFraction(1.0F);
                charge.setMount(inset, corePos, fraction);
            }
        }

        return result;
    }

    public static int insetFromHit(Direction side, double hitX, double hitY, double hitZ) {
        double surface = switch (side) {
            case SOUTH -> 1.0 - hitZ;
            case NORTH -> hitZ;
            case EAST  -> 1.0 - hitX;
            case WEST  -> hitX;
            case UP    -> 1.0 - hitY;
            case DOWN  -> hitY;
        };
        return Math.max(0, Math.min(15, (int) Math.round(surface * 16.0)));
    }

    public static boolean isExcludedDoor(BlockDoorGeneric door) {
        if (door == null || door.decl == null) return false;
        String name = door.decl.name;
        return "silo_hatch".equals(name)
                || "silo_hatch_large".equals(name)
                || "transition_seal".equals(name)
                || "blast_door".equals(name)
                || "vault_door".equals(name);
    }
}
