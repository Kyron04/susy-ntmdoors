package com.ntmdoors.block;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.tile.TileEntityThermiteCharge;
import com.ntmdoors.util.Facings;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.Random;

/**
 * Framed thermite breaching charge. The charge mounts flush against the face it was clicked on and
 * points away from that surface. On doors it is inset by the hit point so it sits on the door's
 * hitbox rather than floating at the cell boundary; the inset is stored in
 * {@link TileEntityThermiteCharge} and selected by the {@code inset} blockstate property.
 *
 * <p>Flint and steel lights the fuse. While it burns the attached door ignores its controls, and
 * detonation removes the door's moving panel and collision while leaving the static frame.</p>
 */
public class BlockThermiteCharge extends Block {

    public static final PropertyDirection FACING = PropertyDirection.create("facing");
    /** 0..15 sixteenths of a block, from the cell face toward the mounted surface. */
    public static final PropertyInteger INSET = PropertyInteger.create("inset", 0, 15);

    /** One texture pixel. */
    private static final double P = 1.0 / 16.0;
    /** Panel depth in model pixels: the supplied model spans z 6.76..9.176. */
    private static final double DEPTH = 2.416;

    public BlockThermiteCharge() {
        super(Material.IRON);
        setRegistryName(NTMDoors.MODID, "thermite_charge");
        setTranslationKey(NTMDoors.MODID + ".thermite_charge");
        setCreativeTab(NTMDoors.TAB);
        setHardness(1.5F);
        setResistance(10F);
        setSoundType(SoundType.METAL);
        setHarvestLevel("pickaxe", 0);
        // The panel is thin and has transparent pixels in its front sprite; it must not block
        // light or force neighbouring faces to be culled.
        setLightOpacity(0);
        setDefaultState(blockState.getBaseState()
                .withProperty(FACING, EnumFacing.NORTH)
                .withProperty(INSET, 0));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, INSET);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        EnumFacing facing = EnumFacing.byIndex(meta & 7);
        if (facing == null) facing = EnumFacing.NORTH;
        return getDefaultState().withProperty(FACING, facing).withProperty(INSET, 0);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getIndex();
    }

    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        return state.withProperty(INSET, insetAt(world, pos));
    }

    private static int insetAt(IBlockAccess world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        return te instanceof TileEntityThermiteCharge
                ? ((TileEntityThermiteCharge) te).getInset() : 0;
    }

    /**
     * The clicked face is the direction the charge points. The block is placed in the cell in
     * front of that face, so the charge's back sits against the clicked surface. The inset is
     * applied by the item after placement, once it knows which door hitbox was clicked.
     */
    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer, EnumHand hand) {
        return getDefaultState().withProperty(FACING, facing == null ? EnumFacing.NORTH : facing);
    }

    /** World-space box for the mounted panel. */
    public static AxisAlignedBB shape(EnumFacing facing, int inset) {
        int pixels = Math.max(0, Math.min(15, inset));
        switch (facing) {
            case UP:
                // Base z becomes -y under the x=270 model rotation; the back sits at -inset.
                return new AxisAlignedBB(
                        1 * P, -pixels * P, 0,
                        15 * P, (DEPTH - pixels) * P, 1);
            case DOWN:
                // Base z becomes +y under the x=90 model rotation.
                return new AxisAlignedBB(
                        1 * P, (16 - DEPTH + pixels) * P, 0,
                        15 * P, (16 + pixels) * P, 1);
            default:
                AxisAlignedBB local = new AxisAlignedBB(
                        1 * P - 0.5, 0, (16 - DEPTH + pixels) * P - 0.5,
                        15 * P - 0.5, 1, (16 + pixels) * P - 0.5);
                return Facings.rotate(local, facing).offset(0.5, 0, 0.5);
        }
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return shape(state.getValue(FACING), insetAt(source, pos));
    }

    @Nullable
    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return shape(state.getValue(FACING), insetAt(world, pos));
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileEntityThermiteCharge();
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
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos,
                                            EnumFacing face) {
        return BlockFaceShape.UNDEFINED;
    }

    /** The front sprite has transparent corners, so cut them out instead of rendering them. */
    @SideOnly(Side.CLIENT)
    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    @Override
    public EnumPushReaction getPushReaction(IBlockState state) {
        return EnumPushReaction.BLOCK;
    }

    /** Flint and steel lights the fuse. */
    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileEntityThermiteCharge)) return false;
        ItemStack stack = player.getHeldItem(hand);
        if (stack.getItem() != Items.FLINT_AND_STEEL) return false;
        if (world.isRemote) return true;
        TileEntityThermiteCharge charge = (TileEntityThermiteCharge) te;
        if (charge.isLit()) return true;
        if (!charge.light()) return true;
        world.playSound(null, pos, SoundEvents.ITEM_FLINTANDSTEEL_USE, SoundCategory.BLOCKS, 1F, 0.8F);
        if (!player.capabilities.isCreativeMode) stack.damageItem(1, player);
        player.swingArm(hand);
        return true;
    }

    /**
     * The charge's tile entity schedules this when its door starts moving or the fuse ends.
     * Running the removal here keeps it outside the tile-entity tick loop.
     */
    @Override
    public void updateTick(World world, BlockPos pos, IBlockState state, Random rand) {
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileEntityThermiteCharge)) return;
        TileEntityThermiteCharge charge = (TileEntityThermiteCharge) te;
        if (charge.isPendingExplosion()) {
            charge.explode();
        } else if (charge.isPendingBreak()) {
            world.destroyBlock(pos, true);
        }
    }

    /** Drop or detonate the charge when the surface it is mounted on disappears. */
    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos fromPos) {
        if (world.isRemote) return;
        EnumFacing facing = state.getValue(FACING);
        BlockPos support = pos.offset(facing.getOpposite());
        IBlockState supportState = world.getBlockState(support);
        if (world.isAirBlock(support) || supportState.getBlock().isReplaceable(world, support)) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileEntityThermiteCharge
                    && ((TileEntityThermiteCharge) te).isLit()) {
                ((TileEntityThermiteCharge) te).scheduleExplosion();
            } else {
                dropBlockAsItem(world, pos, state, 0);
                world.setBlockToAir(pos);
            }
        }
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileEntityThermiteCharge) {
            ((TileEntityThermiteCharge) te).onRemoved();
        }
        super.breakBlock(world, pos, state);
    }
}
