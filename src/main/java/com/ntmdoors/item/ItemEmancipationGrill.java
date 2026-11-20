package com.ntmdoors.item;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.block.BlockEmancipationGrill;
import com.ntmdoors.door.DoorPlacementGuard;
import com.ntmdoors.registry.ModContent;
import com.ntmdoors.tile.TileEntityEmancipationGrill;
import net.minecraft.block.SoundType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Places a complete 1x3x1 vertical Emancipation Grill or a 3x1x1 horizontal bar. If an
 * opposite-facing grill of the same mount is already within ten blocks, the new unit snaps to its
 * line along the wall and faces it.
 */
public class ItemEmancipationGrill extends ItemBlock {

    private static final int MAX_COUPLING_DISTANCE = 10;
    private static final int MAX_LINE_OFFSET = 3;
    private static final int MAX_Y_OFFSET = 2;

    private final BlockEmancipationGrill.Mount mount;

    public ItemEmancipationGrill(BlockEmancipationGrill core) {
        this(core, core.mount());
    }

    public ItemEmancipationGrill(BlockEmancipationGrill core, BlockEmancipationGrill.Mount mount) {
        super(core);
        this.mount = mount;
        setCreativeTab(NTMDoors.TAB);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                      EnumFacing side, float hitX, float hitY, float hitZ) {
        ItemStack stack = player.getHeldItem(hand);
        if (stack.isEmpty()) return EnumActionResult.FAIL;

        // The grill is wall-mounted; ceiling/floor placement is not supported.
        if (side.getAxis() == EnumFacing.Axis.Y) return EnumActionResult.FAIL;

        IBlockState clicked = world.getBlockState(pos);
        if (clicked.getBlock().isReplaceable(world, pos)) return EnumActionResult.FAIL;
        if (!clicked.getBlock().isSideSolid(clicked, world, pos, side)) return EnumActionResult.FAIL;

        BlockPos clickedCore = pos.offset(side);
        EnumFacing front = side;
        BlockPos core = clickedCore;
        boolean mirrored = false;

        Snap snap = findSnap(world, clickedCore, front, mount);
        if (snap != null && canPlace(world, snap.pos, side, player, stack, mount)) {
            core = snap.pos;
            mirrored = snap.mirrored;
        } else {
            snap = null;
            if (!canPlace(world, clickedCore, side, player, stack, mount)) {
                return EnumActionResult.FAIL;
            }
        }

        if (!world.isRemote) {
            BlockEmancipationGrill[] parts = parts(mount);
            for (BlockEmancipationGrill.Layer layer : BlockEmancipationGrill.Layer.values()) {
                BlockEmancipationGrill part = parts[layer.ordinal()];
                BlockPos p = BlockEmancipationGrill.cellPos(core, front, mount, layer);
                world.setBlockState(p, part.getDefaultState()
                        .withProperty(BlockEmancipationGrill.FACING, front)
                        .withProperty(BlockEmancipationGrill.MIRRORED, mirrored), 2);
            }
            applyPairOwnership(world, player, core, snap);
        }

        BlockEmancipationGrill coreBlock = parts(mount)[BlockEmancipationGrill.Layer.MIDDLE.ordinal()];
        SoundType sound = coreBlock.getSoundType(
                coreBlock.getDefaultState(), world, core, player);
        world.playSound(player, core, sound.getPlaceSound(), SoundCategory.BLOCKS,
                (sound.getVolume() + 1F) / 2F, sound.getPitch() * 0.8F);
        if (!player.capabilities.isCreativeMode) stack.shrink(1);
        return EnumActionResult.SUCCESS;
    }

    private static BlockEmancipationGrill[] parts(BlockEmancipationGrill.Mount mount) {
        if (mount == BlockEmancipationGrill.Mount.HORIZONTAL) {
            return new BlockEmancipationGrill[]{
                    ModContent.EMANCIPATION_GRILL_HORIZONTAL_LEFT,
                    ModContent.EMANCIPATION_GRILL_HORIZONTAL,
                    ModContent.EMANCIPATION_GRILL_HORIZONTAL_RIGHT
            };
        }
        return new BlockEmancipationGrill[]{
                ModContent.EMANCIPATION_GRILL,
                ModContent.EMANCIPATION_GRILL_MIDDLE,
                ModContent.EMANCIPATION_GRILL_TOP
        };
    }

    /** First grill owns the pair; a snapped second grill adopts that owner. */
    private static void applyPairOwnership(World world, EntityPlayer player, BlockPos core,
                                           @Nullable Snap snap) {
        TileEntity placed = world.getTileEntity(core);
        if (!(placed instanceof TileEntityEmancipationGrill)) return;
        TileEntityEmancipationGrill grill = (TileEntityEmancipationGrill) placed;

        TileEntityEmancipationGrill first = null;
        if (snap != null) {
            TileEntity existing = world.getTileEntity(snap.partner);
            if (existing instanceof TileEntityEmancipationGrill) {
                first = (TileEntityEmancipationGrill) existing;
            }
        }

        if (first == null) {
            grill.setPairOwner(true);
            grill.setOwner(player);
            return;
        }

        first.setPairOwner(true);
        if (first.getOwnerId() == null) first.setOwner(player);
        grill.setPairOwner(false);
        grill.copyOwnerFrom(first);
    }

    /**
     * Looks for an existing grill on the opposite wall. The scan runs along the new grill's front,
     * with a small tolerance along the wall and vertically, so the two units can be lined up.
     */
    private static Snap findSnap(World world, BlockPos clickedCore, EnumFacing front,
                                 BlockEmancipationGrill.Mount mount) {
        EnumFacing alongWall = front.rotateY();
        Snap best = null;
        int bestScore = Integer.MAX_VALUE;
        int minDy = mount == BlockEmancipationGrill.Mount.HORIZONTAL ? 0 : -MAX_Y_OFFSET;
        int maxDy = mount == BlockEmancipationGrill.Mount.HORIZONTAL ? 0 : MAX_Y_OFFSET;

        for (int step = 1; step <= MAX_COUPLING_DISTANCE; step++) {
            BlockPos line = clickedCore.offset(front, step);
            for (int offset = -MAX_LINE_OFFSET; offset <= MAX_LINE_OFFSET; offset++) {
                BlockPos base = line.offset(alongWall, offset);
                for (int dy = minDy; dy <= maxDy; dy++) {
                    BlockPos candidate = base.up(dy);
                    IBlockState state = world.getBlockState(candidate);
                    if (!(state.getBlock() instanceof BlockEmancipationGrill)) continue;
                    BlockEmancipationGrill grill = (BlockEmancipationGrill) state.getBlock();
                    if (!grill.isCore() || grill.mount() != mount) continue;
                    if (state.getValue(BlockEmancipationGrill.FACING) != front.getOpposite()) continue;
                    boolean existingMirrored = state.getValue(BlockEmancipationGrill.MIRRORED);
                    if (!hasParts(world, candidate, front.getOpposite(), existingMirrored, mount)) continue;

                    int score = step * 100 + Math.abs(offset) * 10 + Math.abs(dy);
                    if (score >= bestScore) continue;
                    bestScore = score;

                    BlockPos pos;
                    if (front.getAxis() == EnumFacing.Axis.X) {
                        pos = new BlockPos(clickedCore.getX(), candidate.getY(), candidate.getZ());
                    } else {
                        pos = new BlockPos(candidate.getX(), candidate.getY(), clickedCore.getZ());
                    }
                    best = new Snap(pos, !existingMirrored, candidate);
                }
            }
        }
        return best;
    }

    private static final class Snap {
        private final BlockPos pos;
        private final boolean mirrored;
        private final BlockPos partner;

        private Snap(BlockPos pos, boolean mirrored, BlockPos partner) {
            this.pos = pos;
            this.mirrored = mirrored;
            this.partner = partner;
        }
    }

    private static boolean hasParts(World world, BlockPos core, EnumFacing facing, boolean mirrored,
                                    BlockEmancipationGrill.Mount mount) {
        if (mount == BlockEmancipationGrill.Mount.VERTICAL) {
            return isLayer(world, core.up(), BlockEmancipationGrill.Layer.MIDDLE, facing, mirrored, mount)
                    && isLayer(world, core.up(2), BlockEmancipationGrill.Layer.TOP, facing, mirrored, mount);
        }
        EnumFacing along = BlockEmancipationGrill.alongWall(facing);
        return isLayer(world, core.offset(along, -1), BlockEmancipationGrill.Layer.BOTTOM, facing, mirrored, mount)
                && isLayer(world, core.offset(along, 1), BlockEmancipationGrill.Layer.TOP, facing, mirrored, mount);
    }

    private static boolean isLayer(IBlockState state, BlockEmancipationGrill.Layer layer, EnumFacing facing,
                                   boolean mirrored, BlockEmancipationGrill.Mount mount) {
        return state.getBlock() instanceof BlockEmancipationGrill
                && ((BlockEmancipationGrill) state.getBlock()).layer() == layer
                && ((BlockEmancipationGrill) state.getBlock()).mount() == mount
                && state.getValue(BlockEmancipationGrill.FACING) == facing
                && state.getValue(BlockEmancipationGrill.MIRRORED) == mirrored;
    }

    private static boolean isLayer(World world, BlockPos pos, BlockEmancipationGrill.Layer layer,
                                   EnumFacing facing, boolean mirrored, BlockEmancipationGrill.Mount mount) {
        return isLayer(world.getBlockState(pos), layer, facing, mirrored, mount);
    }

    private static boolean canPlace(World world, BlockPos core, EnumFacing side,
                                    EntityPlayer player, ItemStack stack,
                                    BlockEmancipationGrill.Mount mount) {
        for (BlockEmancipationGrill.Layer layer : BlockEmancipationGrill.Layer.values()) {
            BlockPos p = BlockEmancipationGrill.cellPos(core, side, mount, layer);
            if (world.isOutsideBuildHeight(p) || !world.isBlockLoaded(p)) return false;
            if (!player.canPlayerEdit(p, side, stack)) return false;
            if (DoorPlacementGuard.blocksPlacement(world, p)) return false;
            if (!world.getBlockState(p).getBlock().isReplaceable(world, p)) return false;

            BlockPos wall = p.offset(side.getOpposite());
            IBlockState wallState = world.getBlockState(wall);
            if (!wallState.getBlock().isSideSolid(wallState, world, wall, side)) return false;
        }
        return true;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip,
                               ITooltipFlag flagIn) {
        if (mount == BlockEmancipationGrill.Mount.HORIZONTAL) {
            tooltip.add(TextFormatting.GRAY + "Place two horizontal grills on opposing walls");
            tooltip.add(TextFormatting.GRAY + "to create a wide, one-block-high energy wall.");
            tooltip.add(TextFormatting.GRAY + "The three-block bar runs left/right along the wall.");
        } else {
            tooltip.add(TextFormatting.GRAY + "Place two grills on opposing walls to create");
            tooltip.add(TextFormatting.GRAY + "a wall of energy that destroys unauthorized");
            tooltip.add(TextFormatting.GRAY + "personnel or materials.");
        }
    }
}
