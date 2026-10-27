package com.ntmdoors.block;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.door.DoorDecl;
import com.ntmdoors.tile.TileEntityDoorKeypad;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
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
 * Wall-mounted keypad for the sliding blast door. Clicking a button sends no custom packet; the
 * server derives the pressed key from the exact local hit coordinates.
 */
public class BlockDoorKeypad extends Block {

    public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);

    private static final double HALF_W = 0.13125;
    private static final double Y1 = 0.29375;
    private static final double Y2 = 0.70625;
    // Match the renderer offset (-0.47) and model thickness (±0.0625 scaled by 0.25).
    private static final double FRONT = -0.485625;
    private static final double BACK = -0.454375;

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
                                            net.minecraft.entity.EntityLivingBase placer,
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

    /**
     * The keypad plate in world space for the given facing. The plate is on the block face that
     * the keypad points toward; the body sits against the opposite wall side. For NORTH this is
     * the low-Z (north) side of the block.
     */
    public static AxisAlignedBB plate(EnumFacing f) {
        double x1 = -HALF_W, x2 = HALF_W, z1 = FRONT, z2 = BACK;
        double rx1 = DoorDecl.rotX(x1, z1, f);
        double rz1 = DoorDecl.rotZ(x1, z1, f);
        double rx2 = DoorDecl.rotX(x2, z2, f);
        double rz2 = DoorDecl.rotZ(x2, z2, f);
        return new AxisAlignedBB(
                0.5 + Math.min(rx1, rx2), Y1, 0.5 + Math.min(rz1, rz2),
                0.5 + Math.max(rx1, rx2), Y2, 0.5 + Math.max(rz1, rz2));
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

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileEntityDoorKeypad) {
                ((TileEntityDoorKeypad) te).onKeypadBroken();
            }
        }
        super.breakBlock(world, pos, state);
    }
}
