package com.ntmdoors.item;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.block.BlockDoorGeneric;
import com.ntmdoors.block.BlockEmancipationGrill;
import com.ntmdoors.network.MessageBreakerPrompt;
import com.ntmdoors.network.ModNetwork;
import com.ntmdoors.tile.TileEntityBiometricSafe;
import com.ntmdoors.tile.TileEntityDoorGeneric;
import com.ntmdoors.tile.TileEntityEmancipationGrill;
import com.ntmdoors.tile.TileEntitySafe;
import com.ntmdoors.util.Names;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Breaker Relay Key. Sneak right-clicking a security door or safe you own opens a confirmation
 * screen. The server revalidates ownership and reach before removing the target, so a modified
 * client cannot break another player's property.
 */
public class ItemBreakerRelayKey extends Item {

    private static final int TARGET_DOOR = 1;
    private static final int TARGET_SAFE = 2;
    private static final int TARGET_GRILL = 3;

    public ItemBreakerRelayKey() {
        setRegistryName(NTMDoors.MODID, "breaker_relay_key");
        setTranslationKey(NTMDoors.MODID + ".breaker_relay_key");
        setCreativeTab(NTMDoors.TAB);
        setMaxStackSize(1);
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return TextFormatting.GOLD + super.getItemStackDisplayName(stack);
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos,
                                           EnumFacing side, float hitX, float hitY, float hitZ,
                                           EnumHand hand) {
        if (!player.isSneaking()) return EnumActionResult.PASS;
        return useBreaker(player, world, pos);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                      EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!player.isSneaking()) return EnumActionResult.PASS;
        return useBreaker(player, world, pos);
    }

    public static EnumActionResult useBreaker(EntityPlayer player, World world, BlockPos pos) {
        if (world.isRemote) {
            return isBreakerTarget(world, pos) ? EnumActionResult.SUCCESS : EnumActionResult.PASS;
        }

        Target target = targetAt(world, pos);
        if (target == null) return EnumActionResult.PASS;

        if (!target.isOwnedBy(player)) {
            player.sendStatusMessage(new TextComponentTranslation(
                    "ntmdoors.msg.breaker_owner_only"), true);
            return EnumActionResult.SUCCESS;
        }

        if (player instanceof EntityPlayerMP) {
            ModNetwork.INSTANCE.sendTo(new MessageBreakerPrompt(target.promptPos, target.label),
                    (EntityPlayerMP) player);
        }
        return EnumActionResult.SUCCESS;
    }

    /**
     * Server side: validates and removes a confirmed target. Doors drop their item through the
     * normal block path; safes have Void on Destroy disabled for this break so both the safe item
     * and every stored stack drop.
     */
    public static boolean breakTarget(EntityPlayer player, World world, BlockPos pos) {
        if (world == null || world.isRemote || player == null || pos == null) return false;

        Target target = targetAt(world, pos);
        if (target == null || !target.isOwnedBy(player)) return false;

        if (target.type == TARGET_DOOR) {
            TileEntity te = world.getTileEntity(target.breakPos);
            if (!(te instanceof TileEntityDoorGeneric)) return false;
            TileEntityDoorGeneric core = (TileEntityDoorGeneric) te;
            if (!core.isSecurityDoor()) return false;
            if (core.getOwnerId() != null && !core.isOwnedBy(player)) return false;
            return world.destroyBlock(core.getPos(), true);
        }

        if (target.type == TARGET_GRILL) {
            TileEntity te = world.getTileEntity(target.breakPos);
            if (!(te instanceof TileEntityEmancipationGrill)) return false;
            TileEntityEmancipationGrill grill = (TileEntityEmancipationGrill) te;
            TileEntityEmancipationGrill security = grill.getSecurityCore();
            if (security.getOwnerId() != null && !security.isOwnedBy(player)) return false;
            return world.destroyBlock(grill.getPos(), true);
        }

        TileEntity te = world.getTileEntity(target.breakPos);
        if (!(te instanceof TileEntitySafe)) return false;
        TileEntitySafe safe = (TileEntitySafe) te;
        if (!safe.isOwnedBy(player)) return false;
        safe.setVoidOnDestroy(false);
        return world.destroyBlock(safe.getPos(), true);
    }

    /** Client-side preflight so the Breaker consumes the click only on a valid target. */
    private static boolean isBreakerTarget(World world, BlockPos pos) {
        if (world == null || pos == null) return false;
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof BlockDoorGeneric) {
            return ((BlockDoorGeneric) state.getBlock()).isSecurity();
        }
        if (state.getBlock() instanceof BlockEmancipationGrill) {
            BlockEmancipationGrill grill = (BlockEmancipationGrill) state.getBlock();
            return world.getTileEntity(grill.corePos(pos, state))
                    instanceof TileEntityEmancipationGrill;
        }
        return world.getTileEntity(pos) instanceof TileEntitySafe;
    }

    @Nullable
    private static Target targetAt(World world, BlockPos clicked) {
        if (world == null || clicked == null) return null;

        IBlockState state = world.getBlockState(clicked);
        if (state.getBlock() instanceof BlockDoorGeneric) {
            BlockDoorGeneric door = (BlockDoorGeneric) state.getBlock();
            if (!door.isSecurity()) return null;
            TileEntityDoorGeneric core = door.getCore(world, clicked, state);
            if (core == null || !core.isSecurityDoor()) return null;
            return new Target(clicked, core.getPos(), "Security Door", TARGET_DOOR);
        }

        if (state.getBlock() instanceof BlockEmancipationGrill) {
            BlockEmancipationGrill grill = (BlockEmancipationGrill) state.getBlock();
            BlockPos core = grill.corePos(clicked, state);
            TileEntity te = world.getTileEntity(core);
            if (!(te instanceof TileEntityEmancipationGrill)) return null;
            return new Target(clicked, core, "Emancipation Grill", TARGET_GRILL);
        }

        TileEntity te = world.getTileEntity(clicked);
        if (te instanceof TileEntitySafe) {
            String label = te instanceof TileEntityBiometricSafe ? "Biometric Safe" : "Safe";
            return new Target(clicked, clicked, label, TARGET_SAFE);
        }
        return null;
    }

    private static final class Target {
        final BlockPos promptPos;
        final BlockPos breakPos;
        final String label;
        final int type;

        Target(BlockPos promptPos, BlockPos breakPos, String label, int type) {
            this.promptPos = promptPos;
            this.breakPos = breakPos;
            this.label = label;
            this.type = type;
        }

        boolean isOwnedBy(EntityPlayer player) {
            if (player == null) return false;
            World world = player.world;
            if (world == null) return false;
            TileEntity te = world.getTileEntity(breakPos);
            if (type == TARGET_DOOR) {
                if (!(te instanceof TileEntityDoorGeneric)) return false;
                TileEntityDoorGeneric core = (TileEntityDoorGeneric) te;
                // An ownerless security door is public property for the Breaker; an owned one
                // remains restricted to its recorded owner.
                return core.isSecurityDoor()
                        && (core.getOwnerId() == null || core.isOwnedBy(player));
            }
            if (type == TARGET_GRILL) {
                if (!(te instanceof TileEntityEmancipationGrill)) return false;
                TileEntityEmancipationGrill security =
                        ((TileEntityEmancipationGrill) te).getSecurityCore();
                return security.getOwnerId() == null || security.isOwnedBy(player);
            }
            return te instanceof TileEntitySafe && ((TileEntitySafe) te).isOwnedBy(player);
        }
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip,
                               ITooltipFlag flag) {
        Names.addDescription(tooltip,
                "Breaks ownerless security doors and grills,",
                "and doors, safes or grills you own.",
                "Sneak + Right Click a target,",
                "then confirm the break.");
    }
}
