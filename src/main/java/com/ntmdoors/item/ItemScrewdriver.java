package com.ntmdoors.item;

import com.ntmdoors.block.door.BlockDoorGeneric;
import com.ntmdoors.block.entity.DoorBlockEntity;
import com.ntmdoors.block.entity.DoorPartBlockEntity;
import com.ntmdoors.block.entity.KeycardScannerBlockEntity;
import com.ntmdoors.block.entity.KeypadBlockEntity;
import com.ntmdoors.door.DoorDecl;
import com.ntmdoors.sound.ModSounds;
import com.ntmdoors.util.Names;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Screwdriver. Right click a keypad to start its code-reset sequence; sneak + right click a keypad
 * to enter a binding mode, then sneak + right click closed doors to link them. Sneak + right click
 * a keycard scanner to enter its binding mode, sneak + right click closed doors to collect them and
 * right click the scanner again to commit. Sneak + right click a door with alternate skins cycles
 * through them. Every interaction the tool handles clicks once.
 */
public class ItemScrewdriver extends Item {

    /** Short tool click; quiet enough to sit under the door and keypad audio. */
    private static final float SOUND_VOLUME = 0.8F;

    /** Item NBT key holding the keypad that is waiting for doors to be bound at range. */
    public static final String TAG_BIND_KEYPAD = "bindKeypad";
    public static final String TAG_BIND_DIM = "bindKeypadDim";
    /** Item NBT key holding the keycard scanner that is waiting for doors to be bound at range. */
    public static final String TAG_BIND_SCANNER = "bindScanner";
    public static final String TAG_BIND_SCANNER_DIM = "bindScannerDim";

    public ItemScrewdriver() {
        super(new FabricItemSettings().maxCount(1));
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        PlayerEntity player = context.getPlayer();
        if (player == null) return ActionResult.PASS;

        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        Hand hand = context.getHand();
        float hitX = (float) (context.getHitPos().x - pos.getX());
        float hitY = (float) (context.getHitPos().y - pos.getY());
        float hitZ = (float) (context.getHitPos().z - pos.getZ());

        ActionResult result = useTool(player, world, pos, hitX, hitY, hitZ, hand);
        if (!world.isClient && result == ActionResult.SUCCESS) {
            world.playSound(null, pos, ModSounds.BUTTON9, SoundCategory.BLOCKS, SOUND_VOLUME, 1.0F);
        }
        return result;
    }

    private ActionResult useTool(PlayerEntity player, World world, BlockPos pos,
                                 float hitX, float hitY, float hitZ, Hand hand) {
        BlockEntity be = world.getBlockEntity(pos);

        if (be instanceof KeycardScannerBlockEntity scanner) {
            if (!world.isClient) {
                if (player.isSneaking()) scanner.toggleBinding(player);
                else scanner.confirm(player);
                updateScannerTag(player, hand, scanner);
            }
            return ActionResult.success(world.isClient);
        }

        if (be instanceof KeypadBlockEntity keypad) {
            if (player.isSneaking()) {
                if (!world.isClient) {
                    keypad.onScrewdriverSneak(player);
                    ItemStack stack = player.getStackInHand(hand);
                    NbtCompound tag = stack.getNbt();
                    boolean armed = keypad.isLinking() && keypad.getBindMode() != KeypadBlockEntity.BindMode.NONE;
                    if (armed) {
                        tag = stack.getOrCreateNbt();
                        tag.putLong(TAG_BIND_KEYPAD, keypad.getPos().asLong());
                        tag.putString(TAG_BIND_DIM, world.getRegistryKey().getValue().toString());
                    } else if (tag != null) {
                        tag.remove(TAG_BIND_KEYPAD);
                        tag.remove(TAG_BIND_DIM);
                    }
                }
                return ActionResult.success(world.isClient);
            }

            int button = keypad.buttonAt(hitX, hitY, hitZ);
            boolean typingReset = keypad.getMode() == KeypadBlockEntity.Mode.RESET_ENTER;
            boolean bindingMode = keypad.getBindMode() != KeypadBlockEntity.BindMode.NONE;
            if (button >= 0 && (button == KeypadBlockEntity.BUTTON_RESET || typingReset || bindingMode)) {
                return ActionResult.PASS;
            }
            if (!keypad.hasStoredCode()) return ActionResult.PASS;
            if (!world.isClient) keypad.onScrewdriverUse();
            return ActionResult.success(world.isClient);
        }

        if (!player.isSneaking()) return ActionResult.PASS;
        DoorBlockEntity core = findCore(world, pos);
        if (core == null) return ActionResult.PASS;

        KeycardScannerBlockEntity scanner = bindScanner(world, player, hand);
        if (scanner != null) {
            if (!world.isClient) scanner.selectDoor(core, player);
            return ActionResult.success(world.isClient);
        }

        KeypadBlockEntity binder = bindKeypad(world, player, hand);
        if (binder != null) {
            if (!world.isClient) binder.bindDoor(core, player);
            return ActionResult.success(world.isClient);
        }

        DoorDecl decl = core.getDecl();
        if (decl == null || !decl.hasSkinVariants()) return ActionResult.PASS;

        if (!world.isClient) core.cycleSkin();
        return ActionResult.success(world.isClient);
    }

    private static DoorBlockEntity findCore(World world, BlockPos pos) {
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof DoorBlockEntity core) return core;
        if (be instanceof DoorPartBlockEntity part) {
            BlockEntity coreBe = world.getBlockEntity(part.getCorePos());
            if (coreBe instanceof DoorBlockEntity core) return core;
        }
        return null;
    }

    private static void updateScannerTag(PlayerEntity player, Hand hand, KeycardScannerBlockEntity scanner) {
        ItemStack stack = player.getStackInHand(hand);
        NbtCompound tag = stack.getNbt();
        if (scanner.isBindingFor(player)) {
            tag = stack.getOrCreateNbt();
            tag.putLong(TAG_BIND_SCANNER, scanner.getPos().asLong());
            if (scanner.getWorld() != null) {
                tag.putString(TAG_BIND_SCANNER_DIM, scanner.getWorld().getRegistryKey().getValue().toString());
            }
        } else if (tag != null) {
            tag.remove(TAG_BIND_SCANNER);
            tag.remove(TAG_BIND_SCANNER_DIM);
        }
    }

    @Nullable
    private static KeycardScannerBlockEntity bindScanner(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        NbtCompound tag = stack.getNbt();
        if (tag == null || !tag.contains(TAG_BIND_SCANNER, NbtElement.LONG_TYPE)) return null;
        if (tag.contains(TAG_BIND_SCANNER_DIM) && !tag.getString(TAG_BIND_SCANNER_DIM).equals(world.getRegistryKey().getValue().toString())) {
            tag.remove(TAG_BIND_SCANNER);
            tag.remove(TAG_BIND_SCANNER_DIM);
            return null;
        }
        BlockPos scannerPos = BlockPos.fromLong(tag.getLong(TAG_BIND_SCANNER));
        BlockEntity be = world.getBlockEntity(scannerPos);
        if (!(be instanceof KeycardScannerBlockEntity scanner) || !scanner.isBindingFor(player)) {
            tag.remove(TAG_BIND_SCANNER);
            tag.remove(TAG_BIND_SCANNER_DIM);
            return null;
        }
        return scanner;
    }

    @Nullable
    private static KeypadBlockEntity bindKeypad(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        NbtCompound tag = stack.getNbt();
        if (tag == null || !tag.contains(TAG_BIND_KEYPAD, NbtElement.LONG_TYPE)) return null;
        if (tag.contains(TAG_BIND_DIM) && !tag.getString(TAG_BIND_DIM).equals(world.getRegistryKey().getValue().toString())) {
            tag.remove(TAG_BIND_KEYPAD);
            tag.remove(TAG_BIND_DIM);
            return null;
        }
        BlockPos padPos = BlockPos.fromLong(tag.getLong(TAG_BIND_KEYPAD));
        BlockEntity be = world.getBlockEntity(padPos);
        if (!(be instanceof KeypadBlockEntity keypad)) {
            tag.remove(TAG_BIND_KEYPAD);
            tag.remove(TAG_BIND_DIM);
            return null;
        }
        if (!keypad.isLinking() || keypad.getBindMode() == KeypadBlockEntity.BindMode.NONE) {
            tag.remove(TAG_BIND_KEYPAD);
            tag.remove(TAG_BIND_DIM);
            return null;
        }
        return keypad;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        Names.addDescription(tooltip,
                "For establishing connections to doors from keypads",
                "and keycard scanners via Sneak + Right Click.");
    }
}
