package com.ntmdoors.block;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.item.ItemCreativeKeycard;
import com.ntmdoors.item.ItemRedstoneRelayKey;
import com.ntmdoors.tile.TileEntitySafe;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * 1x1x1 steel safe with a keypad. The tile entity owns the 27-slot inventory, the code and the
 * access session; the biometric variant extends this block and swaps in its own tile entity.
 */
public class BlockSafe extends Block {

    public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);

    public BlockSafe() {
        this("safe");
    }

    protected BlockSafe(String name) {
        super(Material.IRON);
        setRegistryName(NTMDoors.MODID, name);
        setTranslationKey(NTMDoors.MODID + "." + name);
        setCreativeTab(NTMDoors.TAB);
        setHardness(5F);
        setResistance(30F);
        setSoundType(SoundType.METAL);
        setHarvestLevel("pickaxe", 2);
        setLightOpacity(255);
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

    /**
     * The safe faces the player, whatever face of the neighbouring block was clicked. The block is
     * a full cube, so it can be placed on the floor, a wall or a ceiling like stone.
     */
    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer, EnumHand hand) {
        EnumFacing face = placer == null ? EnumFacing.NORTH : placer.getHorizontalFacing().getOpposite();
        return getDefaultState().withProperty(FACING, face);
    }

    /**
     * The front is recessed, so the block must not occlude its neighbours: anything visible through
     * the shallow door gap should show the block behind it instead of the sky. Light opacity stays
     * at 255 so the safe still blocks light, and the solid-face overrides keep placement, torches
     * and redstone behaving as they would on a full block.
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
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileEntitySafe();
    }

    /** A loaded safe must not be pushed away from its inventory. */
    @Override
    public EnumPushReaction getPushReaction(IBlockState state) {
        return EnumPushReaction.BLOCK;
    }

    /**
     * The code safe opens its keypad on the client. The first accepted code becomes its code; a
     * correct later code opens the storage container from the server side.
     */
    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        if (handleRedstoneRelayKey(world, pos, player, hand)) return true;

        if (world.isRemote) {
            // The Creative Keycard bypasses the keypad; the server opens the container directly.
            if (!ItemCreativeKeycard.isHeldBy(player)) {
                NTMDoors.proxy.openSafeCodeGui(pos);
            }
        } else {
            // A safe placed before the tile entity existed gets one on first use.
            ensureTileEntity(world, pos, state);
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileEntitySafe && ItemCreativeKeycard.isHeldBy(player)) {
                ((TileEntitySafe) te).openContainer(player);
            }
        }
        return true;
    }

    /** Starts or finishes the wireless Redstone Gate session without opening the safe screen. */
    protected boolean handleRedstoneRelayKey(World world, BlockPos pos, EntityPlayer player, EnumHand hand) {
        ItemStack held = player.getHeldItem(hand);
        if (!player.isSneaking() || !(held.getItem() instanceof ItemRedstoneRelayKey)) return false;
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileEntitySafe) {
                ((TileEntitySafe) te).getRedstoneBinding().toggle(player);
            }
        }
        return true;
    }

    /** Adds the tile entity to a legacy placed safe that predates the inventory. */
    protected void ensureTileEntity(World world, BlockPos pos, IBlockState state) {
        if (world.isRemote || world.getTileEntity(pos) instanceof TileEntitySafe) return;
        TileEntity tile = createTileEntity(world, state);
        if (tile != null) world.setTileEntity(pos, tile);
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileEntitySafe) {
            ((TileEntitySafe) te).unbindAllDoors();
            ((TileEntitySafe) te).getRedstoneBinding().clearGates();
            InventoryHelper.dropInventoryItems(world, pos, (TileEntitySafe) te);
        }
        super.breakBlock(world, pos, state);
    }
}
