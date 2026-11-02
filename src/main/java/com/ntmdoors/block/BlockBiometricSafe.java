package com.ntmdoors.block;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.item.ItemBiometricOperatorKeycard;
import com.ntmdoors.item.ItemCreativeKeycard;
import com.ntmdoors.tile.TileEntityBiometricSafe;
import com.ntmdoors.util.HeldItems;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Biometric variant of the safe. It shares the body, facing, collision, drops and full-block
 * behaviour; the tile entity owns the 27-slot inventory and accepts only the Biometric Operator
 * Keycard as a key.
 */
public class BlockBiometricSafe extends BlockSafe {

    public BlockBiometricSafe() {
        super("biometric_safe");
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileEntityBiometricSafe();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        if (handleRedstoneRelayKey(world, pos, player, hand)) return true;

        if (world.isRemote) {
            // The Creative Keycard opens the container directly; do not show the scan screen.
            if (!ItemCreativeKeycard.isHeldBy(player)
                    && HeldItems.holds(player, ItemBiometricOperatorKeycard.class)) {
                NTMDoors.proxy.openSafeBiometricGui(pos);
            }
            return true;
        }

        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileEntityBiometricSafe)) {
            ensureTileEntity(world, pos, state);
            te = world.getTileEntity(pos);
        }
        if (te instanceof TileEntityBiometricSafe) {
            TileEntityBiometricSafe safe = (TileEntityBiometricSafe) te;
            if (ItemCreativeKeycard.isHeldBy(player)) {
                safe.openContainer(player);
            } else {
                safe.tryBiometricAccess(player);
            }
        }
        return true;
    }
}
