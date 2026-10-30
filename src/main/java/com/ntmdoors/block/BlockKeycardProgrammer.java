package com.ntmdoors.block;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.gui.GuiHandler;
import com.ntmdoors.tile.TileEntityKeycardProgrammer;
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
 * Desk-top keycard programmer. A low console that sits on the floor like a printer: a sloped
 * screen panel on the left and a card reader with an inserted keycard on the right.
 *
 * <p>The OBJ model is authored facing NORTH with its front plate at {@link #FRONT_Z} and its base
 * at y = 0, and fits inside a single block. The screen plane, the collision box and the renderer's
 * text placement all derive from the constants here, so the model and the code cannot drift.</p>
 */
public class BlockKeycardProgrammer extends Block {

    public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);

    /** Half the console width; the model spans x -HALF_WIDTH..HALF_WIDTH. */
    public static final double HALF_WIDTH = 0.45D;
    /** Front plate (most negative Z) and back plate of the body. */
    public static final double FRONT_Z = -0.32D;
    public static final double BACK_Z = 0.30D;
    /** Height of the body underside, above the dark plinth. */
    public static final double BODY_UNDERSIDE = 0.05D;
    /** The top face is a wedge: lower at the front, higher at the back. */
    public static final double TOP_FRONT_Y = 0.26D;
    public static final double TOP_BACK_Y = 0.46D;
    public static final double TOP_SLOPE = (TOP_BACK_Y - TOP_FRONT_Y) / (BACK_Z - FRONT_Z);
    /** Collision height: the top of the console body. */
    public static final double BODY_HEIGHT = 0.48D;
    /** Tallest point of the model. */
    public static final double MODEL_HEIGHT = 0.46D;

    /** The recessed screen opening on the sloped top face. */
    public static final double SCREEN_MIN_X = -0.36D;
    public static final double SCREEN_MAX_X = 0.02D;
    public static final double SCREEN_MIN_Z = -0.20D;
    public static final double SCREEN_MAX_Z = 0.04D;
    public static final double SCREEN_INSET = 0.015D;
    public static final double SCREEN_CENTER_X = (SCREEN_MIN_X + SCREEN_MAX_X) / 2D;
    public static final double SCREEN_CENTER_Z = (SCREEN_MIN_Z + SCREEN_MAX_Z) / 2D;
    /** Tilt of the screen plane in degrees. */
    public static final float SCREEN_TILT = (float) Math.toDegrees(Math.atan(TOP_SLOPE));

    public BlockKeycardProgrammer() {
        super(Material.IRON);
        setRegistryName(NTMDoors.MODID, "keycard_programmer");
        setTranslationKey(NTMDoors.MODID + ".keycard_programmer");
        setCreativeTab(NTMDoors.TAB);
        setHardness(2F);
        setResistance(10F);
        setSoundType(SoundType.METAL);
        setHarvestLevel("pickaxe", 0);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    /** Height of the sloped top face at a model-local Z. */
    public static double topY(double z) {
        return TOP_FRONT_Y + (z - FRONT_Z) * TOP_SLOPE;
    }

    /** Height of the screen glass at a model-local Z. */
    public static double screenY(double z) {
        return topY(z) - SCREEN_INSET;
    }

    /** The console's box for the given facing, in block-local space. */
    public static AxisAlignedBB box(EnumFacing f) {
        AxisAlignedBB local = new AxisAlignedBB(-HALF_WIDTH, 0D, FRONT_Z, HALF_WIDTH, BODY_HEIGHT, BACK_Z);
        return Facings.rotate(local, f).offset(0.5D, 0D, 0.5D);
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
                                            EntityLivingBase placer, EnumHand hand) {
        EnumFacing front = placer == null ? EnumFacing.NORTH : placer.getHorizontalFacing().getOpposite();
        return getDefaultState().withProperty(FACING, front);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileEntityKeycardProgrammer();
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return box(state.getValue(FACING));
    }

    @Nullable
    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return box(state.getValue(FACING));
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            player.openGui(NTMDoors.INSTANCE, GuiHandler.KEYCARD_PROGRAMMER, world,
                    pos.getX(), pos.getY(), pos.getZ());
        }
        return true;
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileEntityKeycardProgrammer) {
            ((TileEntityKeycardProgrammer) te).dropContents(world, pos);
        }
        super.breakBlock(world, pos, state);
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
}
