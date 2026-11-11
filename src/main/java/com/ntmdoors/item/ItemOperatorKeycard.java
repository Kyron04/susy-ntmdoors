package com.ntmdoors.item;

import com.ntmdoors.block.entity.KeycardScannerBlockEntity;
import com.ntmdoors.client.screen.ScannerCodeScreen;
import com.ntmdoors.util.Biometrics;
import com.ntmdoors.util.Names;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Operator Keycard. A privileged card produced by the Keycard Programmer. It is recognised by the
 * scanner as a card, and sneak right-clicking a scanner that is already bound to doors opens its
 * security code screen, so the card also administers the doors it is assigned to.
 */
public class ItemOperatorKeycard extends ItemRfidCard {

    public ItemOperatorKeycard() {
        this("operator_keycard");
    }

    /** Lets the biometric variant reuse every behaviour with its own registry name. */
    protected ItemOperatorKeycard(String name) {
        super(name);
    }

    @Override
    public Text getName(ItemStack stack) {
        return super.getName(stack).copy().formatted(nameColour());
    }

    protected Formatting nameColour() {
        return Formatting.GOLD;
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        PlayerEntity player = context.getPlayer();
        if (player == null || !player.isSneaking()) return ActionResult.PASS;

        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        BlockEntity be = world.getBlockEntity(pos);

        if (be instanceof KeycardScannerBlockEntity scanner) {
            return openScannerScreen(player, world, scanner, context.getHand());
        }
        return ActionResult.PASS;
    }

    public ActionResult openScannerScreen(PlayerEntity player, World world,
                                         KeycardScannerBlockEntity scanner, Hand hand) {
        if (!scanner.hasBoundDoors()) return ActionResult.PASS;

        ItemStack stack = player.getStackInHand(hand);
        if (scanner.isMatchHolder() && Biometrics.isBound(stack)
                && !Biometrics.isOwnedBy(stack, player)) {
            if (world.isClient) {
                player.sendMessage(Text.translatable("ntmdoors.msg.scanner_denied"), true);
            }
            return ActionResult.success(world.isClient);
        }

        if (scanner.isLockedOut() && !(this instanceof ItemBiometricOperatorKeycard)) {
            if (world.isClient) {
                player.sendMessage(Text.translatable("ntmdoors.msg.scanner_locked_out"), true);
            }
            return ActionResult.success(world.isClient);
        }

        if (world.isClient) {
            openCodeScreen(scanner.getPos());
        }
        return ActionResult.success(world.isClient);
    }

    @Environment(EnvType.CLIENT)
    private void openCodeScreen(BlockPos pos) {
        MinecraftClient.getInstance().setScreen(new ScannerCodeScreen(pos));
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        Names.addDescription(tooltip,
                "The master keycard of your security network.",
                "Holds several private keys that can be assigned",
                "to scanners bound to doors.",
                "Sneak + Right Click a bound scanner to set",
                "its security code, or a Redstone Gate to",
                "open its settings.");
        Biometrics.addOwnerIfBound(tooltip, stack);
    }
}
