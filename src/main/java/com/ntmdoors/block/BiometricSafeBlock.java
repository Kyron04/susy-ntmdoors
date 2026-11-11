package com.ntmdoors.block;

import com.ntmdoors.block.entity.BiometricSafeBlockEntity;
import com.ntmdoors.block.entity.ModBlockEntities;
import com.ntmdoors.client.screen.SafeBiometricScreen;
import com.ntmdoors.item.ItemBiometricOperatorKeycard;
import com.ntmdoors.item.ItemCreativeKeycard;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class BiometricSafeBlock extends SafeBlock {

    public BiometricSafeBlock() {
        super();
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new BiometricSafeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return checkType(type, ModBlockEntities.BIOMETRIC_SAFE, (w, p, s, be) -> be.tick(w, p, s));
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (!world.isClient && placer instanceof PlayerEntity player) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof BiometricSafeBlockEntity safe) {
                if (safe.bindOwner(player)) {
                    player.sendMessage(Text.translatable("ntmdoors.msg.safe_biometric_bound"), true);
                }
            }
        }
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        ItemStack held = player.getStackInHand(hand);
        if (held.getItem() instanceof ItemCreativeKeycard || player.isCreative()) {
            if (!world.isClient) {
                BlockEntity be = world.getBlockEntity(pos);
                if (be instanceof BiometricSafeBlockEntity safe) {
                    player.openHandledScreen(safe);
                }
            }
            return ActionResult.success(world.isClient);
        }

        if (!(held.getItem() instanceof ItemBiometricOperatorKeycard)) {
            if (!world.isClient) {
                player.sendMessage(Text.translatable("ntmdoors.msg.safe_biometric_required"), true);
            }
            return ActionResult.CONSUME;
        }

        if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof BiometricSafeBlockEntity safe) {
                safe.tryBiometricAccess(serverPlayer);
            }
        } else if (world.isClient) {
            MinecraftClient.getInstance().setScreen(new SafeBiometricScreen(pos));
        }

        return ActionResult.success(world.isClient);
    }
}
