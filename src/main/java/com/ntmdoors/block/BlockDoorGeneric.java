package com.ntmdoors.block;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.door.DoorDecl;
import com.ntmdoors.tile.TileEntityDoorGeneric;
import com.ntmdoors.tile.TileEntityDoorPart;
import com.ntmdoors.util.Names;
import net.minecraft.block.Block;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * One block instance per door variant. The door is a multiblock: one CORE cell (tile entity with the
 * logic) and any number of DUMMY cells (tile entity that points at the core). All cells share the same
 * block. Rendering is done by a TESR on the core, so the block itself is invisible.
 *
 * <p>Each {@link DoorDecl} is registered twice: the normal door and a {@code _locked} twin that only
 * reacts to redstone. Both variants share the same declaration; the lock is a block property, so it can
 * never drift out of sync with the door definition.</p>
 */
public class BlockDoorGeneric extends Block {

    public static final PropertyDirection FACING = BlockHorizontal.FACING;
    public static final PropertyEnum<Part> PART = PropertyEnum.create("part", Part.class);
    /**
     * True on the open centre cells of doors that become passable (the silo hatch). Changing the
     * reported material is the only way to make vanilla falling blocks treat an open hatch as air
     * while still keeping the door block itself in place.
     */
    public static final PropertyBool PASS = PropertyBool.create("pass");

    public enum Part implements IStringSerializable {
        CORE, DUMMY;

        @Override
        public String getName() {
            return name().toLowerCase();
        }
    }

    public final DoorDecl decl;
    /** Locked twins refuse every manual interaction; only redstone can move them. */
    public final boolean locked;

    public BlockDoorGeneric(DoorDecl decl, boolean locked) {
        super(Material.IRON);
        this.decl = decl;
        this.locked = locked;
        String name = locked ? decl.name + "_locked" : decl.name;
        setRegistryName(NTMDoors.MODID, name);
        setTranslationKey(NTMDoors.MODID + "." + name);
        setCreativeTab(NTMDoors.TAB);
        setHardness(decl.hardness);
        setResistance(decl.resistance);
        setSoundType(SoundType.METAL);
        setHarvestLevel("pickaxe", 0);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH)
                .withProperty(PART, Part.CORE).withProperty(PASS, false));
    }

    // ------------------------------------------------------------ block state

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, PART, PASS);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState()
                .withProperty(FACING, EnumFacing.byHorizontalIndex(meta & 3))
                .withProperty(PART, (meta & 4) != 0 ? Part.DUMMY : Part.CORE)
                .withProperty(PASS, false);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex() | (state.getValue(PART) == Part.DUMMY ? 4 : 0);
    }

    // ------------------------------------------------------------ tile entities

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return state.getValue(PART) == Part.CORE ? new TileEntityDoorGeneric() : new TileEntityDoorPart();
    }

    @Nullable
    public BlockPos getCorePos(IBlockAccess world, BlockPos pos, IBlockState state) {
        if (state.getValue(PART) == Part.CORE) return pos;
        TileEntity te = world.getTileEntity(pos);
        return te instanceof TileEntityDoorPart ? ((TileEntityDoorPart) te).corePos : null;
    }

    @Nullable
    public TileEntityDoorGeneric getCore(IBlockAccess world, BlockPos pos, IBlockState state) {
        BlockPos corePos = getCorePos(world, pos, state);
        if (corePos == null) return null;
        TileEntity te = world.getTileEntity(corePos);
        return te instanceof TileEntityDoorGeneric ? (TileEntityDoorGeneric) te : null;
    }

    // ------------------------------------------------------------ shape / collision

    /**
     * Collision boxes of the CURRENT animation state, clipped to this cell and expressed relative
     * to this cell. When {@code forSelection} is true, the closed shape is used as a fallback for
     * open doors that have no boxes at all (the transition seal is pass-through when open), so they
     * can still be clicked shut.
     */
    private List<AxisAlignedBB> getLocalBoxes(IBlockState state, IBlockAccess world, BlockPos pos,
                                              boolean forSelection, float partialTicks) {
        List<AxisAlignedBB> out = new ArrayList<>(4);
        BlockPos corePos = getCorePos(world, pos, state);
        TileEntity te = corePos == null ? null : world.getTileEntity(corePos);
        if (!(te instanceof TileEntityDoorGeneric)) {
            out.add(FULL_BLOCK_AABB);
            return out;
        }
        TileEntityDoorGeneric core = (TileEntityDoorGeneric) te;
        EnumFacing facing = state.getValue(FACING);
        float open = core.getOpenFraction(partialTicks);
        // Collision follows the animation: the boxes are generated from the same state and timer as
        // the renderer, cached once per tick by the core. Selection uses partial ticks so the
        // outline hugs the panel between ticks.
        AxisAlignedBB[] boxes = forSelection
                ? decl.getBoxes(facing, open, false)
                : core.getCollisionBoxes(facing);
        if (forSelection && open >= 0.9999F && boxes.length == 0) {
            // Pass-through doors (for example the transition seal) still need a click target when open.
            boxes = decl.getBoxes(facing, 0F, false);
        }
        double ox = corePos.getX() + 0.5 - pos.getX();
        double oy = corePos.getY() - pos.getY();
        double oz = corePos.getZ() + 0.5 - pos.getZ();
        for (AxisAlignedBB b : boxes) {
            double x1 = Math.max(b.minX + ox, 0), y1 = Math.max(b.minY + oy, 0), z1 = Math.max(b.minZ + oz, 0);
            double x2 = Math.min(b.maxX + ox, 1), y2 = Math.min(b.maxY + oy, 1), z2 = Math.min(b.maxZ + oz, 1);
            if (x2 > x1 && y2 > y1 && z2 > z1) out.add(new AxisAlignedBB(x1, y1, z1, x2, y2, z2));
        }
        return out;
    }

    /** True when this cell currently holds collision geometry. */
    public boolean hasCollisionInCell(IBlockState state, IBlockAccess world, BlockPos pos) {
        return !getLocalBoxes(state, world, pos, false, 1F).isEmpty();
    }

    /**
     * Doors are light-tight while their panel or frame occupies the cell. The opacity cannot live
     * in the block state because it follows the animation, so it is read from the core tile
     * entity, which keeps a per-tick map of the solid cells.
     */
    @Override
    public int getLightOpacity(IBlockState state, IBlockAccess world, BlockPos pos) {
        // Glass doors never seal, whether their hitboxes are present or not.
        if (!decl.sealsLight) return 0;
        if (decl.passFallingWhenOpen && state.getValue(PASS)) return 0;
        TileEntityDoorGeneric core = getCore(world, pos, state);
        // No tile entity yet (worldgen, or a chunk that is still loading): keep the door solid.
        if (core == null) return 255;
        return core.blocksLightAt(pos) ? 255 : 0;
    }

    @Override
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB entityBox,
                                      List<AxisAlignedBB> collidingBoxes, @Nullable Entity entity, boolean isActualState) {
        // A fully open pass-through cell has no collision at all, including its frame cells.
        if (decl.passFallingWhenOpen && state.getValue(PASS)) return;
        for (AxisAlignedBB b : getLocalBoxes(state, world, pos, false, 1F)) {
            addCollisionBoxToList(pos, entityBox, collidingBoxes, b);
        }
    }

    @Nullable
    @Override
    public RayTraceResult collisionRayTrace(IBlockState state, World world, BlockPos pos, Vec3d start, Vec3d end) {
        // Open pass-through cells must not catch projectiles (for example ICBM missiles). The
        // surrounding frame cells are still clickable, so the hatch can be closed by hand.
        if (decl.passFallingWhenOpen && state.getValue(PASS)) return null;
        RayTraceResult best = null;
        double bestDist = Double.MAX_VALUE;
        for (AxisAlignedBB b : getLocalBoxes(state, world, pos, true, 1F)) {
            RayTraceResult r = rayTrace(pos, start, end, b);
            if (r != null) {
                double d = r.hitVec.squareDistanceTo(start);
                if (d < bestDist) {
                    best = r;
                    bestDist = d;
                }
            }
        }
        return best;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public AxisAlignedBB getSelectedBoundingBox(IBlockState state, World world, BlockPos pos) {
        List<AxisAlignedBB> boxes = getLocalBoxes(state, world, pos, true, Minecraft.getMinecraft().getRenderPartialTicks());
        if (boxes.isEmpty()) return FULL_BLOCK_AABB.offset(pos);
        AxisAlignedBB u = boxes.get(0);
        for (int i = 1; i < boxes.size(); i++) u = u.union(boxes.get(i));
        return u.offset(pos);
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return FULL_BLOCK_AABB;
    }

    /**
     * The union of the current animation boxes, so other mods that ask for a single collision box
     * see the moving panel instead of a full cube. A fully open pass-through cell reports no box.
     */
    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        if (decl.passFallingWhenOpen && state.getValue(PASS)) return NULL_AABB;
        List<AxisAlignedBB> boxes = getLocalBoxes(state, world, pos, false, 1F);
        if (boxes.isEmpty()) return NULL_AABB;
        AxisAlignedBB u = boxes.get(0);
        for (int i = 1; i < boxes.size(); i++) u = u.union(boxes.get(i));
        return u;
    }

    /** Skip open pass-through cells entirely when tracing blocks or collecting entity collision. */
    @Override
    public boolean canCollideCheck(IBlockState state, boolean hitIfLiquid) {
        if (decl.passFallingWhenOpen && state.getValue(PASS)) return false;
        return super.canCollideCheck(state, hitIfLiquid);
    }

    /** Prefer the language file, but never show a raw translation key if a line is missing. */
    @Override
    public String getLocalizedName() {
        String fallback = locked ? decl.displayName + " (Locked)" : decl.displayName;
        return Names.translationOr(getTranslationKey() + ".name", fallback);
    }

    /**
     * Open pass-through cells report air so {@code BlockFalling.canFallThrough} (and therefore
     * falling sand/gravel entities) treats the open hatch as an empty block. The block itself
     * stays in the world as the door's dummy cell.
     */
    @Override
    public Material getMaterial(IBlockState state) {
        if (decl.passFallingWhenOpen && state.getValue(PASS)) return Material.AIR;
        return super.getMaterial(state);
    }

    /** Never let players replace the open hatch cells while they are reporting air. */
    @Override
    public boolean isReplaceable(IBlockAccess worldIn, BlockPos pos) {
        return false;
    }

    /**
     * Lets vanilla mobs path through open multiblock doors. Without this, WalkNodeProcessor treats
     * the block as an unknown solid and returns BLOCKED even when the door is fully open.
     */
    @Override
    public PathNodeType getAiPathNodeType(IBlockState state, IBlockAccess world, BlockPos pos, @Nullable EntityLiving entity) {
        TileEntityDoorGeneric core = getCore(world, pos, state);
        if (core != null && core.getOpenFraction(1F) > 0.5F) {
            return PathNodeType.DOOR_OPEN;
        }
        return PathNodeType.DOOR_IRON_CLOSED;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
        return BlockFaceShape.UNDEFINED;
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.INVISIBLE;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    @SuppressWarnings("deprecation")
    @Override
    public EnumPushReaction getPushReaction(IBlockState state) {
        return EnumPushReaction.BLOCK;
    }

    // ------------------------------------------------------------ interaction

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing side, float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            TileEntityDoorGeneric core = getCore(world, pos, state);
            if (core != null) core.onPlayerUse(player);
        }
        return true;
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos fromPos) {
        if (world.isRemote) return;
        TileEntityDoorGeneric core = getCore(world, pos, state);
        if (core != null) core.checkRedstone();
    }

    /** Removing any cell removes the whole structure; only the block that was actually broken drops the item. */
    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote) {
            TileEntityDoorGeneric core = getCore(world, pos, state);
            if (core != null && core.beginDismantle()) {
                BlockPos corePos = core.getPos();
                for (BlockPos off : decl.getCells(state.getValue(FACING))) {
                    BlockPos p = corePos.add(off);
                    if (!p.equals(pos) && world.getBlockState(p).getBlock() == this) {
                        world.setBlockState(p, Blocks.AIR.getDefaultState(), 2);
                    }
                }
            }
        }
        super.breakBlock(world, pos, state);
    }
}
