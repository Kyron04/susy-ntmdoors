package com.ntmdoors.block;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.tile.TileEntityDoorKeypad;
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
 * Wall-mounted keypad for the sliding blast door. Clicking a button sends no custom packet; the
 * server derives the pressed key from the exact local hit coordinates.
 */
public class BlockDoorKeypad extends Block {

    public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);

    /**
     * Model-to-block transform of the keypad plate. The renderer applies the same offset and
     * scale, and the hit test maps clicks back through it, so these are the single source of truth
     * for where the keypad sits on the block.
     */
    public static final double MODEL_OFFSET_Y = 0.29375;
    public static final double MODEL_OFFSET_Z = -0.47;
    public static final double MODEL_SCALE_X = 0.35;
    public static final double MODEL_SCALE_Y = 0.4125;
    public static final double MODEL_SCALE_Z = 0.25;
    /** The keypad OBJ spans x -0.375..0.375 and z -0.0625..0.0625 around its front face. */
    private static final double MODEL_HALF_WIDTH = 0.375;
    private static final double MODEL_HALF_DEPTH = 0.0625;

    public BlockDoorKeypad() {
        super(Material.IRON);
        setRegistryName(NTMDoors.MODID, "sliding_blast_door_keypad");
        setTranslationKey(NTMDoors.MODID + ".sliding_blast_door_keypad");
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
            // Face the player: the keypad mounts on the clicked wall side but its front looks
            // back toward the player.
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
        return new TileEntityDoorKeypad();
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileEntityDoorKeypad) ((TileEntityDoorKeypad) te).syncWithDoor();
        }
    }

    /**
     * The keypad plate in world space for the given facing. The plate is on the block face that
     * the keypad points toward; the body sits against the opposite wall side. For NORTH this is
     * the low-Z (north) side of the block.
     */
    public static AxisAlignedBB plate(EnumFacing f) {
        double halfWidth = MODEL_HALF_WIDTH * MODEL_SCALE_X;
        double halfDepth = MODEL_HALF_DEPTH * MODEL_SCALE_Z;
        AxisAlignedBB local = new AxisAlignedBB(
                -halfWidth, MODEL_OFFSET_Y, MODEL_OFFSET_Z - halfDepth,
                halfWidth, MODEL_OFFSET_Y + MODEL_SCALE_Y, MODEL_OFFSET_Z + halfDepth);
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
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileEntityDoorKeypad) {
                TileEntityDoorKeypad keypad = (TileEntityDoorKeypad) te;
                int button = keypad.buttonAt(hitX, hitY, hitZ);
                if (button >= 0) keypad.pressButton(button);
            }
        }
        return true;
    }
}
