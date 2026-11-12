package com.ntmdoors.item;

import com.ntmdoors.block.entity.RedstoneGateBlockEntity;
import com.ntmdoors.client.screen.WeaponsRelayKeyScreen;
import com.ntmdoors.util.HeldItems;
import com.ntmdoors.weapons.Explosives;
import com.ntmdoors.weapons.WeaponsBindings;
import com.ntmdoors.weapons.WeaponsKeyData;
import com.ntmdoors.weapons.WeaponsMode;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Weapons Relay Key for Fabric 1.20.1.
 * Mode picker screen for Regular Binding and Dead Man's Hand anti-tamper locking.
 */
public class ItemWeaponsRelayKey extends Item {

    public ItemWeaponsRelayKey() {
        super(new Item.Settings().maxCount(1));
    }

    @Override
    public Text getName(ItemStack stack) {
        return Text.literal("").append(super.getName(stack)).formatted(Formatting.RED);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        if (player.isSneaking()) {
            if (world.isClient()) {
                openClientScreen(hand);
            }
            return TypedActionResult.success(player.getStackInHand(hand), world.isClient());
        }
        return super.use(world, player, hand);
    }

    @Environment(EnvType.CLIENT)
    private void openClientScreen(Hand hand) {
        MinecraftClient.getInstance().setScreen(new WeaponsRelayKeyScreen(hand));
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        PlayerEntity player = context.getPlayer();
        if (player == null || !player.isSneaking()) return ActionResult.PASS;

        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        Hand hand = context.getHand();
        ItemStack stack = context.getStack();
        WeaponsMode mode = WeaponsKeyData.mode(stack);

        // Gates are linked only while a Regular binding session is active
        BlockEntity te = world.getBlockEntity(pos);
        if (te instanceof RedstoneGateBlockEntity) {
            if (mode != WeaponsMode.REGULAR) {
                if (!world.isClient()) message(player, "ntmdoors.msg.weapons_gate_regular_only");
                return ActionResult.SUCCESS;
            }
            BlockPos session = WeaponsKeyData.sessionPos(stack);
            String sessionDim = WeaponsKeyData.sessionDim(stack);
            String currentDim = world.getRegistryKey().getValue().toString();
            if (session == null || !currentDim.equals(sessionDim)) {
                if (!world.isClient()) message(player, "ntmdoors.msg.weapons_binding_required");
                return ActionResult.SUCCESS;
            }
            if (!world.isClient()) {
                boolean linked = WeaponsBindings.toggleGate(world, session, pos.toImmutable());
                message(player, linked ? "ntmdoors.msg.weapons_gate_linked" : "ntmdoors.msg.weapons_gate_unlinked");
            }
            return ActionResult.SUCCESS;
        }

        if (!Explosives.isExplosive(world, pos)) {
            if (world.isClient()) {
                openClientScreen(hand);
            }
            return ActionResult.SUCCESS;
        }

        if (mode == WeaponsMode.DEAD_MANS_HAND) {
            if (!world.isClient()) toggleDeadMansHand(player, world, pos, stack);
            return ActionResult.SUCCESS;
        }

        if (!world.isClient()) toggleRegularSession(player, world, pos, stack);
        return ActionResult.SUCCESS;
    }

    private static void toggleDeadMansHand(PlayerEntity player, World world, BlockPos pos, ItemStack stack) {
        UUID keyId = WeaponsKeyData.ensureKeyId(stack);
        WeaponsBindings.Binding existing = WeaponsBindings.find(world, pos);
        if (existing != null && keyId.equals(existing.keyId)) {
            WeaponsBindings.unbind(world, pos);
            message(player, "ntmdoors.msg.weapons_unbound");
        } else if (existing != null) {
            message(player, "ntmdoors.msg.weapons_bound_other");
        } else {
            WeaponsBindings.bind(world, pos, keyId, player.getUuid(), WeaponsMode.DEAD_MANS_HAND);
            message(player, "ntmdoors.msg.weapons_bound");
        }
        HeldItems.sync(player);
    }

    private static void toggleRegularSession(PlayerEntity player, World world, BlockPos pos, ItemStack stack) {
        String dimension = world.getRegistryKey().getValue().toString();
        BlockPos session = WeaponsKeyData.sessionPos(stack);
        if (session != null && dimension.equals(WeaponsKeyData.sessionDim(stack)) && session.equals(pos)) {
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
            WeaponsBindings.bind(world, pos, keyId, player.getUuid(), WeaponsMode.REGULAR);
        }

        WeaponsKeyData.startSession(stack, dimension, pos);
        message(player, "ntmdoors.msg.weapons_binding_on");
        HeldItems.sync(player);
    }

    private static void message(PlayerEntity player, String key) {
        if (player != null) {
            player.sendMessage(Text.translatable(key), true);
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("Hard lock weapons so enemies can't disarm them").formatted(Formatting.GRAY));
        if (stack.hasNbt()) {
            tooltip.add(Text.literal("Mode: " + WeaponsKeyData.mode(stack).displayName).formatted(Formatting.GRAY));
        }
        tooltip.add(Text.literal("Sneak + Right Click an explosive to bind it.").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Regular: Sneak + Right Click gates to link them.").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Dead Man's Hand: touching it without the key sets it off.").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Sneak + Right Click elsewhere to change mode.").formatted(Formatting.GRAY));
    }
}
