package com.ntmdoors.item;

import com.ntmdoors.client.screen.KeycardViewerScreen;
import com.ntmdoors.util.Biometrics;
import com.ntmdoors.util.InteractionGuard;
import com.ntmdoors.util.Names;
import com.ntmdoors.util.PrivateKeys;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * RFID card item. Every colour variant is identical apart from its registry name, which selects
 * the matching texture and model. Cards carry the private keys copied onto them by the programmer;
 * sneak right-clicking one opens the read-only viewer, except on the blank card. The Operator
 * Keycard inherits this item and replaces the viewer with scanner administration.
 */
public class ItemRfidCard extends Item {

    private final boolean keyViewer;

    public ItemRfidCard(String name) {
        this(name, true);
    }

    public ItemRfidCard(String name, boolean keyViewer) {
        super(new FabricItemSettings().maxCount(1));
        this.keyViewer = keyViewer;
    }

    public boolean hasKeyViewer() {
        return keyViewer;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (!keyViewer || !player.isSneaking()) {
            return TypedActionResult.pass(stack);
        }

        if (InteractionGuard.targetsInteractiveBlock(player)) {
            return TypedActionResult.pass(stack);
        }

        if (world.isClient) {
            openViewerScreen(stack);
        }
        return TypedActionResult.success(stack, world.isClient);
    }

    @Environment(EnvType.CLIENT)
    private void openViewerScreen(ItemStack stack) {
        MinecraftClient.getInstance().setScreen(new KeycardViewerScreen(stack));
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        if (!keyViewer) {
            Names.addDescription(tooltip,
                    "Use the Keycard Programmer to assign this card",
                    "a colour and private key.");
        } else if (!(this instanceof ItemOperatorKeycard)) {
            PrivateKeys.addTooltip(tooltip, stack);
        }
        Biometrics.addOwnerIfBound(tooltip, stack);
    }
}
