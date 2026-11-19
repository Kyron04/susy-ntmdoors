package com.ntmdoors.block;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.registry.ModContent;
import com.ntmdoors.tile.RedstoneBinding;
import com.ntmdoors.tile.TileEntityEmancipationGrill;
import com.ntmdoors.util.Facings;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.Random;

/**
 * One third of the 1x3x1 Emancipation Grill. The bottom block owns the tile entity and the full
 * three-block model is baked into that block; the middle and top blocks are occupancy/collision
 * cells so the column can be placed in one go and removed as one piece.
 *
 * <p>The model is authored facing north with its back plate on the south edge. The blockstate
 * rotates it for the other three walls. The field itself is rendered by
 * {@link com.ntmdoors.client.RenderEmancipationField}; it has no collision and no fizzling logic
 * yet.</p>
 */
public class BlockEmancipationGrill extends Block {

    public enum Layer {
        BOTTOM,
        MIDDLE,
        TOP
    }

    public static final PropertyDirection FACING =
            PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);
    public static final PropertyBool MIRRORED = PropertyBool.create("mirrored");

    private static final ThreadLocal<Boolean> DISMANTLING = ThreadLocal.withInitial(() -> false);
    private static final double P = 1.0D / 16.0D;
    /** Physical footprint of the column in model space, centred for the facing rotation. */
    private static final AxisAlignedBB LOCAL_BOX =
            new AxisAlignedBB(P - 0.5D, 0, 8 * P - 0.5D, 15 * P - 0.5D, 1, 16 * P - 0.5D);

    private final Layer layer;

    public BlockEmancipationGrill(String name, Layer layer) {
        super(Material.IRON);
        this.layer = layer;
        setRegistryName(NTMDoors.MODID, name);
        setTranslationKey(NTMDoors.MODID + "." + name);
        setCreativeTab(NTMDoors.TAB);
        setHardness(3.5F);
        setResistance(15F);
        setSoundType(SoundType.METAL);
        setHarvestLevel("pickaxe", 1);
        setLightOpacity(0);
        setDefaultState(blockState.getBaseState()
                .withProperty(FACING, EnumFacing.NORTH)
                .withProperty(MIRRORED, false));
    }

    public Layer layer() {
        return layer;
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, MIRRORED);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        EnumFacing facing = EnumFacing.byHorizontalIndex(meta & 3);
        if (facing == null) facing = EnumFacing.NORTH;
        return getDefaultState().withProperty(FACING, facing)
                .withProperty(MIRRORED, (meta & 4) != 0);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex()
                | (state.getValue(MIRRORED) ? 4 : 0);
    }

    /** Fallback for direct placement; the Grill item supplies the wall-facing placement. */
    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer, EnumHand hand) {
        EnumFacing face = placer == null ? EnumFacing.NORTH : placer.getHorizontalFacing().getOpposite();
        return getDefaultState().withProperty(FACING, face).withProperty(MIRRORED, false);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return layer == Layer.BOTTOM;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return layer == Layer.BOTTOM ? new TileEntityEmancipationGrill() : null;
    }

    /**
     * Breaking any cell removes the whole column. The core is derived from the broken cell's
     * layer; the guard keeps the removed cells from recursively dismantling the rest.
     */
    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote && !DISMANTLING.get()) {
            BlockPos core = pos.down(layer.ordinal());
            TileEntity te = world.getTileEntity(core);
            if (te instanceof TileEntityEmancipationGrill) {
                ((TileEntityEmancipationGrill) te).getRedstoneBinding().clearGates();
            }
            RedstoneBinding.removeGrillLinks(world, core);
            DISMANTLING.set(true);
            try {
                for (Layer l : Layer.values()) {
                    BlockPos p = core.up(l.ordinal());
                    if (!p.equals(pos) && world.getBlockState(p).getBlock() instanceof BlockEmancipationGrill) {
                        world.setBlockState(p, Blocks.AIR.getDefaultState(), 2);
                    }
                }
            } finally {
                DISMANTLING.set(false);
            }
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return Item.getItemFromBlock(ModContent.EMANCIPATION_GRILL);
    }

    @Override
    public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world,
                                  BlockPos pos, EntityPlayer player) {
        return new ItemStack(ModContent.EMANCIPATION_GRILL);
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return Facings.rotate(LOCAL_BOX, state.getValue(FACING)).offset(0.5D, 0D, 0.5D);
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return Facings.rotate(LOCAL_BOX, state.getValue(FACING)).offset(0.5D, 0D, 0.5D);
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
    public boolean isTopSolid(IBlockState state) {
        return false;
    }

    @Override
    public boolean isSideSolid(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return false;
    }

    @Override
    public EnumPushReaction getPushReaction(IBlockState state) {
        return EnumPushReaction.BLOCK;
    }
}
