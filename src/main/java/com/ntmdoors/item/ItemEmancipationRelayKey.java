// SPDX-License-Identifier: LGPL-3.0-only
package com.ntmdoors.item;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.block.BlockEmancipationGrill;
import com.ntmdoors.network.MessageGrillAccess;
import com.ntmdoors.network.ModNetwork;
import com.ntmdoors.tile.TileEntityEmancipationGrill;
import com.ntmdoors.util.ContrabandContainers;
import com.ntmdoors.util.GrillContainmentData;
import com.ntmdoors.util.HeldItems;
import com.ntmdoors.util.ModSounds;
import com.ntmdoors.util.Names;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Emancipation Relay Key. Sneak right-clicking an Emancipation Grill opens its console. The key
 * itself carries no credentials; the console reads assignable keys and biometric IDs from an
 * Operator Keycard in the player's inventory, exactly like the Motion Sensor console.
 *
 * <p>The key also carries a pending contraband-container session. The final binding is written to
 * the Grill only after the player returns and confirms it there.</p>
 */
public class ItemEmancipationRelayKey extends Item {

    public ItemEmancipationRelayKey() {
        setRegistryName(NTMDoors.MODID, "emancipation_relay_key");
        setTranslationKey(NTMDoors.MODID + ".emancipation_relay_key");
        setCreativeTab(NTMDoors.TAB);
        setMaxStackSize(1);
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return TextFormatting.DARK_AQUA + super.getItemStackDisplayName(stack);
    }

    /**
     * Runs before block activation, so selecting a chest or other container does not open its GUI.
     * Only delegates while a binding session is active; ordinary key use stays in onItemUse.
     */
    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos,
                                           EnumFacing side, float hitX, float hitY, float hitZ,
                                           EnumHand hand) {
        if (!player.isSneaking()) return EnumActionResult.PASS;

        ItemStack stack = player.getHeldItem(hand);
        if (!(stack.getItem() instanceof ItemEmancipationRelayKey)) return EnumActionResult.PASS;

        GrillContainmentData.Session session = GrillContainmentData.getSession(stack);
        if (session == null) return EnumActionResult.PASS;

        if (session.isExpired()) {
            if (!world.isRemote) {
                GrillContainmentData.clear(stack);
                HeldItems.sync(player);
                player.sendStatusMessage(new TextComponentTranslation(
                        "ntmdoors.msg.grill_bind_expired"), true);
            }
            return EnumActionResult.SUCCESS;
        }

        if (world.isRemote) return EnumActionResult.SUCCESS;
        return useBinding(player, world, pos, side, stack, session);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                      EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!player.isSneaking()) return EnumActionResult.PASS;

        ItemStack stack = player.getHeldItem(hand);
        if (GrillContainmentData.getSession(stack) != null) {
            // The first-use hook already owns this click while a session is active.
            return EnumActionResult.SUCCESS;
        }

        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockEmancipationGrill)) return EnumActionResult.PASS;

        BlockEmancipationGrill block = (BlockEmancipationGrill) state.getBlock();
        BlockPos corePos = block.corePos(pos, state);
        if (world.isRemote) {
            ModNetwork.INSTANCE.sendToServer(new MessageGrillAccess(corePos));
        }
        return EnumActionResult.SUCCESS;
    }

    private static EnumActionResult useBinding(EntityPlayer player, World world, BlockPos pos,
                                               EnumFacing side, ItemStack stack,
                                               GrillContainmentData.Session session) {
        if (!player.getUniqueID().equals(session.owner)) {
            player.sendStatusMessage(new TextComponentTranslation(
                    "ntmdoors.msg.grill_bind_owner_only"), true);
            return EnumActionResult.SUCCESS;
        }

        int dimension = world.provider.getDimension();
        if (session.stage == GrillContainmentData.AWAIT_CONTAINER) {
            BlockPos grill = grillCore(world, pos);
            if (grill != null && grill.equals(session.grill) && dimension == session.grillDim) {
                player.sendStatusMessage(new TextComponentTranslation(
                        "ntmdoors.msg.grill_bind_select_first"), true);
                return EnumActionResult.SUCCESS;
            }
            if (dimension != session.grillDim) {
                player.sendStatusMessage(new TextComponentTranslation(
                        "ntmdoors.msg.grill_bind_wrong_dimension"), true);
                return EnumActionResult.SUCCESS;
            }
            if (!ContrabandContainers.isWithinChunks(session.grill, pos,
                    ContrabandContainers.MAX_CHUNKS)) {
                player.sendStatusMessage(new TextComponentTranslation(
                        "ntmdoors.msg.grill_bind_too_far"), true);
                return EnumActionResult.SUCCESS;
            }
            if (!ContrabandContainers.isValidTarget(player, world, pos, side, stack,
                    session.grill)) {
                player.sendStatusMessage(new TextComponentTranslation(
                        "ntmdoors.msg.grill_bind_not_container"), true);
                return EnumActionResult.SUCCESS;
            }

            String name = ContrabandContainers.displayName(world, pos);
            GrillContainmentData.setContainer(stack, pos, dimension,
                    ContrabandContainers.sideIndex(side), name);
            HeldItems.sync(player);
            player.sendStatusMessage(new TextComponentTranslation(
                    "ntmdoors.msg.grill_bind_container_selected"), true);
            world.playSound(null, pos, ModSounds.BUTTON9, SoundCategory.BLOCKS, 0.8F, 1F);
            return EnumActionResult.SUCCESS;
        }

        if (session.stage != GrillContainmentData.AWAIT_CONFIRM) {
            return EnumActionResult.SUCCESS;
        }

        BlockPos grillPos = grillCore(world, pos);
        if (grillPos == null || dimension != session.grillDim
                || !grillPos.equals(session.grill)) {
            player.sendStatusMessage(new TextComponentTranslation(
                    grillPos == null
                            ? "ntmdoors.msg.grill_bind_confirm_first"
                            : "ntmdoors.msg.grill_bind_wrong_grill"), true);
            return EnumActionResult.SUCCESS;
        }

        TileEntity te = world.getTileEntity(grillPos);
        if (!(te instanceof TileEntityEmancipationGrill)) {
            GrillContainmentData.clear(stack);
            HeldItems.sync(player);
            player.sendStatusMessage(new TextComponentTranslation(
                    "ntmdoors.msg.grill_bind_invalid"), true);
            return EnumActionResult.SUCCESS;
        }

        TileEntityEmancipationGrill grill = ((TileEntityEmancipationGrill) te).getSecurityCore();
        if (!grill.hasConsoleAccess(player)) {
            player.sendStatusMessage(new TextComponentTranslation(
                    "ntmdoors.msg.emancipation_owner_only"), true);
            return EnumActionResult.SUCCESS;
        }
        if (!grill.isContrabandEnabled()) {
            player.sendStatusMessage(new TextComponentTranslation(
                    "ntmdoors.msg.grill_bind_disabled"), true);
            return EnumActionResult.SUCCESS;
        }
        if (session.container == null
                || !ContrabandContainers.isStillValid(world, session.containerDim,
                session.container, session.containerSide, session.grill)) {
            GrillContainmentData.clear(stack);
            HeldItems.sync(player);
            player.sendStatusMessage(new TextComponentTranslation(
                    "ntmdoors.msg.grill_bind_invalid"), true);
            return EnumActionResult.SUCCESS;
        }

        grill.setContrabandBinding(session.container, session.containerDim,
                session.containerSide, session.containerName);
        GrillContainmentData.clear(stack);
        HeldItems.sync(player);
        player.sendStatusMessage(new TextComponentTranslation(
                "ntmdoors.msg.grill_bind_confirmed"), true);
        world.playSound(null, grillPos, ModSounds.BUTTON9, SoundCategory.BLOCKS, 0.8F, 1F);
        return EnumActionResult.SUCCESS;
    }

    @Nullable
    private static BlockPos grillCore(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockEmancipationGrill)) return null;
        return ((BlockEmancipationGrill) state.getBlock()).corePos(pos, state);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip,
                               ITooltipFlag flag) {
        Names.addDescription(tooltip,
                "Allows you to access the Grill GUI via Sneak+ right click",
                "Binds a contraband container from page six.");

        GrillContainmentData.Session session = GrillContainmentData.getSession(stack);
        if (session != null && !session.isExpired()) {
            Names.addDescription(tooltip, session.stage == GrillContainmentData.AWAIT_CONTAINER
                    ? "Binding: sneak + right-click a compatible container."
                    : "Binding: return to the original Grill and confirm.");
        }
    }
}
