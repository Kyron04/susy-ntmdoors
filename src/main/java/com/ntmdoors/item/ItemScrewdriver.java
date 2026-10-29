package com.ntmdoors.item;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.block.BlockDoorGeneric;
import com.ntmdoors.door.DoorDecl;
import com.ntmdoors.tile.TileEntityDoorGeneric;
import com.ntmdoors.tile.TileEntityDoorKeypad;
import com.ntmdoors.tile.TileEntityKeycardScanner;
import com.ntmdoors.util.ModSounds;
import com.ntmdoors.util.Names;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;

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
    private static final String TAG_BIND_KEYPAD = "bindKeypad";
    private static final String TAG_BIND_DIM = "bindKeypadDim";
    /** Item NBT key holding the keycard scanner that is waiting for doors to be bound at range. */
    private static final String TAG_BIND_SCANNER = "bindScanner";
    private static final String TAG_BIND_SCANNER_DIM = "bindScannerDim";

    public ItemScrewdriver() {
        setRegistryName(NTMDoors.MODID, "screwdriver");
        setTranslationKey(NTMDoors.MODID + ".screwdriver");
        setCreativeTab(NTMDoors.TAB);
        setMaxStackSize(1);
    }

    /**
     * Handles the interaction before the block is activated. This is required because Minecraft
     * skips block activation when the player is sneaking with an item in hand.
     */
    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos,
                                           EnumFacing side, float hitX, float hitY, float hitZ,
                                           EnumHand hand) {
        EnumActionResult result = useTool(player, world, pos, hitX, hitY, hitZ, hand);
        // One click per handled interaction, sent from the server so nearby players hear it.
        if (!world.isRemote && result == EnumActionResult.SUCCESS) {
            world.playSound(null, pos, ModSounds.BUTTON9, SoundCategory.BLOCKS, SOUND_VOLUME, 1F);
        }
        return result;
    }

    /** The actual interaction; the caller plays the click once when this reports a handled use. */
    private EnumActionResult useTool(EntityPlayer player, World world, BlockPos pos,
                                     float hitX, float hitY, float hitZ, EnumHand hand) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileEntityKeycardScanner) {
            // Sneak: open or close the scanner's binding session. Plain click: commit the doors
            // collected so far. Doors clicked in between are added to the session.
            if (!world.isRemote) {
                TileEntityKeycardScanner scanner = (TileEntityKeycardScanner) te;
                if (player.isSneaking()) scanner.toggleBinding(player);
                else scanner.confirm(player);
                updateScannerTag(player, hand, scanner);
            }
            return EnumActionResult.SUCCESS;
        }

        if (te instanceof TileEntityDoorKeypad) {
            TileEntityDoorKeypad keypad = (TileEntityDoorKeypad) te;
            if (player.isSneaking()) {
                // Sneak: cycle the binding mode (or reset once the pad is in use). Remember the
                // pad on the tool so the doors clicked next know where they belong.
                if (!world.isRemote) {
                    keypad.onScrewdriverSneak(player);
                    ItemStack stack = player.getHeldItem(hand);
                    NBTTagCompound tag = stack.getTagCompound();
                    boolean armed = keypad.isLinking()
                            && keypad.getBindMode() != TileEntityDoorKeypad.BindMode.NONE;
                    if (armed) {
                        if (tag == null) {
                            tag = new NBTTagCompound();
                            stack.setTagCompound(tag);
                        }
                        tag.setLong(TAG_BIND_KEYPAD, keypad.getPos().toLong());
                        tag.setInteger(TAG_BIND_DIM, world.provider.getDimension());
                    } else if (tag != null) {
                        tag.removeTag(TAG_BIND_KEYPAD);
                        tag.removeTag(TAG_BIND_DIM);
                    }
                }
                return EnumActionResult.SUCCESS;
            }
            int button = keypad.buttonAt(hitX, hitY, hitZ);
            // R is the close/clear key and must always reach the keypad, even with the tool in
            // hand. While the reset is waiting for the current code, every key must type, so the
            // entry can never be swallowed by the tool. Binding modes also need the digits, so the
            // player can pick a multi door without putting the screwdriver away.
            boolean typingReset = keypad.getMode() == TileEntityDoorKeypad.Mode.RESET_ENTER;
            boolean bindingMode = keypad.getBindMode() != TileEntityDoorKeypad.BindMode.NONE;
            if (button >= 0 && (button == TileEntityDoorKeypad.BUTTON_RESET || typingReset || bindingMode)) {
                return EnumActionResult.PASS;
            }
            if (!keypad.hasStoredCode()) return EnumActionResult.PASS;
            if (!world.isRemote) keypad.onScrewdriverUse();
            return EnumActionResult.SUCCESS;
        }

        if (!player.isSneaking()) return EnumActionResult.PASS;
        TileEntityDoorGeneric core = findCore(world, pos);
        if (core == null) return EnumActionResult.PASS;

        TileEntityKeycardScanner scanner = bindScanner(world, player, hand);
        if (scanner != null) {
            if (!world.isRemote) scanner.selectDoor(core, player);
            return EnumActionResult.SUCCESS;
        }

        TileEntityDoorKeypad binder = bindKeypad(world, player, hand);
        if (binder != null) {
            if (!world.isRemote) binder.bindDoor(core, player);
            return EnumActionResult.SUCCESS;
        }

        DoorDecl decl = core.getDecl();
        if (decl == null || !decl.hasSkinVariants()) return EnumActionResult.PASS;

        if (!world.isRemote) core.cycleSkin();
        return EnumActionResult.SUCCESS;
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return Names.translationOr(getTranslationKey() + ".name", "Screwdriver");
    }

    private static TileEntityDoorGeneric findCore(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        return state.getBlock() instanceof BlockDoorGeneric
                ? ((BlockDoorGeneric) state.getBlock()).getCore(world, pos, state)
                : null;
    }

    /** Remembers (or forgets) the scanner this tool is currently collecting doors for. */
    private static void updateScannerTag(EntityPlayer player, EnumHand hand, TileEntityKeycardScanner scanner) {
        ItemStack stack = player.getHeldItem(hand);
        NBTTagCompound tag = stack.getTagCompound();
        if (scanner.isBindingFor(player)) {
            if (tag == null) {
                tag = new NBTTagCompound();
                stack.setTagCompound(tag);
            }
            tag.setLong(TAG_BIND_SCANNER, scanner.getPos().toLong());
            tag.setInteger(TAG_BIND_SCANNER_DIM, scanner.getWorld().provider.getDimension());
        } else if (tag != null) {
            tag.removeTag(TAG_BIND_SCANNER);
            tag.removeTag(TAG_BIND_SCANNER_DIM);
        }
    }

    /**
     * The scanner this screwdriver is currently binding doors for, or null. Like the keypad link,
     * it lives on the tool so the player can walk to a door without the scanner staying in reach.
     */
    @Nullable
    private static TileEntityKeycardScanner bindScanner(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(TAG_BIND_SCANNER)) return null;
        if (tag.hasKey(TAG_BIND_SCANNER_DIM) && tag.getInteger(TAG_BIND_SCANNER_DIM) != world.provider.getDimension()) {
            tag.removeTag(TAG_BIND_SCANNER);
            tag.removeTag(TAG_BIND_SCANNER_DIM);
            return null;
        }
        BlockPos scannerPos = BlockPos.fromLong(tag.getLong(TAG_BIND_SCANNER));
        TileEntity te = world.getTileEntity(scannerPos);
        if (!(te instanceof TileEntityKeycardScanner) || !((TileEntityKeycardScanner) te).isBindingFor(player)) {
            tag.removeTag(TAG_BIND_SCANNER);
            tag.removeTag(TAG_BIND_SCANNER_DIM);
            return null;
        }
        return (TileEntityKeycardScanner) te;
    }

    /**
     * The keypad this screwdriver is currently linking doors for, or null. The link lives on the
     * tool so the player can walk to a door and link it without the keypad staying in reach.
     */
    @Nullable
    private static TileEntityDoorKeypad bindKeypad(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(TAG_BIND_KEYPAD)) return null;
        if (tag.hasKey(TAG_BIND_DIM) && tag.getInteger(TAG_BIND_DIM) != world.provider.getDimension()) {
            // The pad is in another dimension; the stored position means something else here.
            tag.removeTag(TAG_BIND_KEYPAD);
            tag.removeTag(TAG_BIND_DIM);
            return null;
        }
        BlockPos padPos = BlockPos.fromLong(tag.getLong(TAG_BIND_KEYPAD));
        TileEntity te = world.getTileEntity(padPos);
        if (!(te instanceof TileEntityDoorKeypad)) {
            tag.removeTag(TAG_BIND_KEYPAD);
            tag.removeTag(TAG_BIND_DIM);
            return null;
        }
        TileEntityDoorKeypad keypad = (TileEntityDoorKeypad) te;
        if (!keypad.isLinking() || keypad.getBindMode() == TileEntityDoorKeypad.BindMode.NONE) {
            // The pad is no longer linking; stop treating clicks as binds.
            tag.removeTag(TAG_BIND_KEYPAD);
            tag.removeTag(TAG_BIND_DIM);
            return null;
        }
        return keypad;
    }
}
