package com.ntmdoors.item;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.tile.TileEntityRedstoneGate;
import com.ntmdoors.util.HeldItems;
import com.ntmdoors.util.Names;
import com.ntmdoors.weapons.Explosives;
import com.ntmdoors.weapons.WeaponsBindings;
import com.ntmdoors.weapons.WeaponsKeyData;
import com.ntmdoors.weapons.WeaponsMode;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Weapons Relay Key. The player picks a mode in its screen, then sneak-clicks explosive blocks.
 * Regular binding starts a session on an explosive and links Redstone Gates to it; Dead Man's Hand
 * hard-locks the explosive so only a player carrying this key can break it.
 */
public class ItemWeaponsRelayKey extends Item {

    public ItemWeaponsRelayKey() {
        setRegistryName(NTMDoors.MODID, "weapons_relay_key");
        setTranslationKey(NTMDoors.MODID + ".weapons_relay_key");
        setCreativeTab(NTMDoors.TAB);
        setMaxStackSize(1);
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return TextFormatting.RED + super.getItemStackDisplayName(stack);
    }

    /** Sneak right-click air opens the mode screen. */
    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        if (player.isSneaking()) {
            if (world.isRemote) NTMDoors.proxy.openWeaponsRelayKeyGui(hand);
            return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
        }
        return super.onItemRightClick(world, player, hand);
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos,
                                           EnumFacing side, float hitX, float hitY, float hitZ,
                                           EnumHand hand) {
        if (!player.isSneaking()) return EnumActionResult.PASS;
        return use(player, world, pos, hand);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                      EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!player.isSneaking()) return EnumActionResult.PASS;
        return use(player, world, pos, hand);
    }

    private static EnumActionResult use(EntityPlayer player, World world, BlockPos pos,
                                        EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!(stack.getItem() instanceof ItemWeaponsRelayKey)) return EnumActionResult.PASS;

        WeaponsMode mode = WeaponsKeyData.mode(stack);

        // Gates are linked only while a Regular binding session is active.
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileEntityRedstoneGate) {
            if (mode != WeaponsMode.REGULAR) {
                if (!world.isRemote) message(player, "ntmdoors.msg.weapons_gate_regular_only");
                return EnumActionResult.SUCCESS;
            }
            BlockPos session = WeaponsKeyData.sessionPos(stack);
            int dimension = world.provider.getDimension();
            if (session == null || WeaponsKeyData.sessionDim(stack) != dimension) {
                if (!world.isRemote) message(player, "ntmdoors.msg.weapons_binding_required");
                return EnumActionResult.SUCCESS;
            }
            if (!world.isRemote) {
                boolean linked = WeaponsBindings.toggleGate(world, session,
                        pos.toImmutable());
                message(player, linked
                        ? "ntmdoors.msg.weapons_gate_linked"
                        : "ntmdoors.msg.weapons_gate_unlinked");
            }
            return EnumActionResult.SUCCESS;
        }

        if (!Explosives.isExplosive(world, pos)) {
            if (world.isRemote) NTMDoors.proxy.openWeaponsRelayKeyGui(hand);
            return EnumActionResult.SUCCESS;
        }

        if (mode == WeaponsMode.DEAD_MANS_HAND) {
            if (!world.isRemote) toggleDeadMansHand(player, world, pos, stack);
            return EnumActionResult.SUCCESS;
        }

        if (!world.isRemote) toggleRegularSession(player, world, pos, stack);
        return EnumActionResult.SUCCESS;
    }

    private static void toggleDeadMansHand(EntityPlayer player, World world, BlockPos pos,
                                           ItemStack stack) {
        UUID keyId = WeaponsKeyData.ensureKeyId(stack);
        WeaponsBindings.Binding existing = WeaponsBindings.find(world, pos);
        if (existing != null && keyId.equals(existing.keyId)) {
            WeaponsBindings.unbind(world, pos);
            message(player, "ntmdoors.msg.weapons_unbound");
        } else if (existing != null) {
            message(player, "ntmdoors.msg.weapons_bound_other");
        } else {
            WeaponsBindings.bind(world, pos, keyId, player.getUniqueID(),
                    WeaponsMode.DEAD_MANS_HAND);
            message(player, "ntmdoors.msg.weapons_bound");
        }
        HeldItems.sync(player);
    }

    private static void toggleRegularSession(EntityPlayer player, World world, BlockPos pos,
                                             ItemStack stack) {
        int dimension = world.provider.getDimension();
        BlockPos session = WeaponsKeyData.sessionPos(stack);
        if (session != null && WeaponsKeyData.sessionDim(stack) == dimension
                && session.equals(pos)) {
            WeaponsKeyData.clearSession(stack);
            message(player, "ntmdoors.msg.weapons_binding_confirmed");
            HeldItems.sync(player);
            return;
        }

        UUID keyId = WeaponsKeyData.ensureKeyId(stack);
        WeaponsBindings.Binding existing = WeaponsBindings.find(world, pos);
        if (existing != null) {
            if (existing.mode != WeaponsMode.REGULAR || !keyId.equals(existing.keyId)) {
                message(player, "ntmdoors.msg.weapons_bound_other");
                return;
            }
        } else {
            WeaponsBindings.bind(world, pos, keyId, player.getUniqueID(), WeaponsMode.REGULAR);
        }

        WeaponsKeyData.startSession(stack, dimension, pos);
        message(player, "ntmdoors.msg.weapons_binding_on");
        HeldItems.sync(player);
    }

    private static void message(EntityPlayer player, String key) {
        player.sendStatusMessage(new TextComponentTranslation(key), true);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip,
                               ITooltipFlag flag) {
        Names.addDescription(tooltip, "Hard lock weapons so enemies can't disarm them");
        if (stack.hasTagCompound()) {
            tooltip.add(TextFormatting.GRAY + "Mode: " + WeaponsKeyData.mode(stack).displayName);
        }
        Names.addDescription(tooltip,
                "Sneak + Right Click an explosive to bind it.",
                "Regular: Sneak + Right Click gates to link them.",
                "Dead Man's Hand: touching it without the key sets it off.",
                "Sneak + Right Click elsewhere to change mode.");
    }
}
