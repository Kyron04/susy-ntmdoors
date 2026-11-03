package com.ntmdoors.block;

import com.ntmdoors.NTMDoors;
import net.minecraft.block.Block;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * Wall-mounted PIR housing. The front bezel carries a Fresnel lens and a status light; the light
 * colour is a block state so later motion/security logic can switch it without replacing the block.
 *
 * <p>This release is assets and placement only. The four light states are wired to the model but
 * no detection logic drives them yet.</p>
 */
public class BlockMotionSensor extends Block {

    public static final PropertyDirection FACING = BlockHorizontal.FACING;
    public static final PropertyEnum<Status> STATUS = PropertyEnum.create("status", Status.class);

    /** Light colours: idle green, authorised blue, maintenance yellow, motion red. */
    public enum Status implements IStringSerializable {
        IDLE("idle"),
        AUTHORIZED("authorized"),
        MAINTENANCE("maintenance"),
        MOTION("motion");

        private final String name;

        Status(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    public BlockMotionSensor() {
        super(Material.IRON);
        setRegistryName(NTMDoors.MODID, "motion_sensor");
        setTranslationKey(NTMDoors.MODID + ".motion_sensor");
        setCreativeTab(NTMDoors.TAB);
        setHardness(1.5F);
        setResistance(6F);
        setSoundType(SoundType.METAL);
        setLightOpacity(255);
        setDefaultState(blockState.getBaseState()
                .withProperty(FACING, EnumFacing.NORTH)
                .withProperty(STATUS, Status.IDLE));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, STATUS);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        Status status = Status.values()[(meta >> 2) & 3];
        return getDefaultState()
                .withProperty(FACING, EnumFacing.byHorizontalIndex(meta & 3))
                .withProperty(STATUS, status);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex()
                | (state.getValue(STATUS).ordinal() << 2);
    }

    /** Face the player like the safe and speaker, with the supplied north-facing model. */
    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer, EnumHand hand) {
        EnumFacing face = placer == null ? EnumFacing.NORTH : placer.getHorizontalFacing().getOpposite();
        return getDefaultState().withProperty(FACING, face);
    }

    /** Changes the status light without disturbing the facing. Used by the future sensor logic. */
    public static void setStatus(World world, BlockPos pos, Status status) {
        if (world.isRemote) return;
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof BlockMotionSensor && state.getValue(STATUS) != status) {
            world.setBlockState(pos, state.withProperty(STATUS, status), 3);
        }
    }

    /**
     * The front lens and bezel sit proud of the housing, so the block must not occlude its
     * neighbours or be treated as a full cube by the model/lighting culler. Collision remains the
     * default full block.
     */
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
        return true;
    }

    @Override
    public boolean isSideSolid(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return true;
    }

    @Override
    public EnumPushReaction getPushReaction(IBlockState state) {
        return EnumPushReaction.BLOCK;
    }
}
