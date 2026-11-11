package com.ntmdoors.item;

import com.ntmdoors.block.entity.KeycardScannerBlockEntity;
import com.ntmdoors.client.screen.ScannerKeysScreen;
import com.ntmdoors.util.HeldItems;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Debug keycard. It opens every door regardless of locks, security, keypad bindings or redstone,
 * and its name is shown in purple like other mods' creative-only items.
 */
public class ItemCreativeKeycard extends ItemRfidCard {

    public ItemCreativeKeycard() {
        super("creative_keycard");
    }

    @Override
    public Text getName(ItemStack stack) {
        return super.getName(stack).copy().formatted(Formatting.LIGHT_PURPLE);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        PlayerEntity player = context.getPlayer();
        if (player == null || !player.isSneaking()) return ActionResult.PASS;

        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        BlockEntity be = world.getBlockEntity(pos);

        if (be instanceof KeycardScannerBlockEntity) {
            if (world.isClient) {
                openConsole(pos);
            }
            return ActionResult.success(world.isClient);
        }
        return ActionResult.PASS;
    }

    @Environment(EnvType.CLIENT)
    private void openConsole(BlockPos pos) {
        MinecraftClient.getInstance().setScreen(new ScannerKeysScreen(pos));
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        Names.addDescription(tooltip,
                "Overrides every door, scanner and safe",
                "regardless of type or binding state.",
                "Sneak + Right Click a scanner for its console,",
                "or a Redstone Gate for its screen.");
    }

    /** True when either hand holds a Creative Keycard. */
    public static boolean isHeldBy(@Nullable PlayerEntity player) {
        return HeldItems.holds(player, ItemCreativeKeycard.class);
    }
}
