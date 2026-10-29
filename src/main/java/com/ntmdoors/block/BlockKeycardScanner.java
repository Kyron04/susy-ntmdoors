package com.ntmdoors.block;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.item.ItemRfidCard;
import com.ntmdoors.tile.TileEntityKeycardScanner;
import com.ntmdoors.util.Facings;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;

/**
 * Wall-mounted keycard reader. The screen and the three indicator lenses are currently inert: the
 * block exists so the model, placement and item rendering can be refined before the access logic
 * is wired up.
 */
public class BlockKeycardScanner extends Block {

    public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);

    /**
     * Model-to-block transform of the scanner. The renderer applies the same offset and scale, and
     * the bounding box is derived from it, so these are the single source of truth for where the
     * scanner sits on the block.
     */
    public static final double MODEL_OFFSET_Y = 0.22;
    public static final double MODEL_OFFSET_Z = -0.5;
    public static final double MODEL_SCALE_X = 0.36;
    public static final double MODEL_SCALE_Y = 0.55;
    public static final double MODEL_SCALE_Z = 0.30;
    /** The scanner OBJ spans x -0.375..0.375 and z 0..0.30, back plate first. */
    private static final double MODEL_HALF_WIDTH = 0.375;
    private static final double MODEL_MAX_Z = 0.30;

    public BlockKeycardScanner() {
        super(Material.IRON);
        setRegistryName(NTMDoors.MODID, "keycard_scanner");
        setTranslationKey(NTMDoors.MODID + ".keycard_scanner");
        setCreativeTab(NTMDoors.TAB);
        setHardness(2F);
        setResistance(10F);
        setSoundType(SoundType.METAL);
        setHarvestLevel("pickaxe", 0);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta & 3));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex();
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer,
                                            EnumHand hand) {
        EnumFacing face = facing;
        if (face.getAxis().isHorizontal()) {
            // Mount on the clicked wall side with the screen facing back toward the player.
            face = face.getOpposite();
        } else {
            face = placer != null ? placer.getHorizontalFacing().getOpposite() : EnumFacing.NORTH;
        }
        return getDefaultState().withProperty(FACING, face);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileEntityKeycardScanner();
    }

    /**
     * The scanner's world-space box for the given facing. The body sits against the wall on the
     * block's low-Z side for NORTH and is rotated with the same helper the renderer uses.
     */
    public static AxisAlignedBB plate(EnumFacing f) {
        double halfWidth = MODEL_HALF_WIDTH * MODEL_SCALE_X;
        double depth = MODEL_MAX_Z * MODEL_SCALE_Z;
        AxisAlignedBB local = new AxisAlignedBB(
                -halfWidth, MODEL_OFFSET_Y, MODEL_OFFSET_Z,
                halfWidth, MODEL_OFFSET_Y + MODEL_SCALE_Y, MODEL_OFFSET_Z + depth);
        return Facings.rotate(local, f).offset(0.5, 0, 0.5);
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return plate(state.getValue(FACING));
    }

    @Nullable
    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return plate(state.getValue(FACING));
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

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        if (world.isRemote) return true;
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileEntityKeycardScanner)) return false;

        TileEntityKeycardScanner scanner = (TileEntityKeycardScanner) te;
        ItemStack held = player.getHeldItem(hand);
        if (held.isEmpty()) {
            // Empty hand commits the doors selected during binding.
            scanner.confirm(player);
        } else if (held.getItem() instanceof ItemRfidCard) {
            scanner.useCard(player);
        }
        return true;
    }
}
