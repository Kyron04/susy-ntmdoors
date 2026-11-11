package com.ntmdoors.item;

import com.ntmdoors.client.screen.RelayKeyNameScreen;
import com.ntmdoors.sound.ModSounds;
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
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Relay Key. A blank key creates one private key when the player sneak right-clicks, which opens a
 * naming screen; the server then stores the chosen name and a fresh ten digit serial. The key can
 * never be reset, but the private key can be copied onto other keys and cards.
 */
public class ItemRelayKey extends Item {

    protected static final float FEEDBACK_VOLUME = 0.8F;

    public ItemRelayKey() {
        this("relay_key");
    }

    /** Lets the biometric variant reuse the naming flow with its own registry name. */
    protected ItemRelayKey(String name) {
        super(new FabricItemSettings().maxCount(1));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (!player.isSneaking() || InteractionGuard.targetsInteractiveBlock(player)) {
            return TypedActionResult.pass(stack);
        }

        if (world.isClient) {
            if (PrivateKeys.isBlank(stack)) {
                openNameScreen(hand);
            }
            return TypedActionResult.success(stack, true);
        }

        // The server owns the one-key-only rule, so it answers every attempt on a used key.
        if (!PrivateKeys.isBlank(stack)) {
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    ModSounds.SCANNER_BUTTON8, SoundCategory.PLAYERS, FEEDBACK_VOLUME, 1.0F);
            player.sendMessage(Text.translatable("ntmdoors.msg.key_exists"), true);
        }
        return TypedActionResult.success(stack, false);
    }

    @Environment(EnvType.CLIENT)
    private void openNameScreen(Hand hand) {
        MinecraftClient.getInstance().setScreen(new RelayKeyNameScreen(hand));
    }

    @Override
    public Text getName(ItemStack stack) {
        return super.getName(stack).copy().formatted(nameColour());
    }

    /** The plain key is yellow; the biometric variant overrides this with aqua. */
    protected Formatting nameColour() {
        return Formatting.YELLOW;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        Names.addDescription(tooltip,
                "Used to generate private keys via",
                "Sneak + Right Click.");
        PrivateKeys.addTooltip(tooltip, stack);
        Biometrics.addOwnerIfBound(tooltip, stack);
    }
}
