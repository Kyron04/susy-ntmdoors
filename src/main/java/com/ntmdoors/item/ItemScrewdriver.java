package com.ntmdoors.item;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.block.BlockDoorGeneric;
import com.ntmdoors.tile.TileEntityDoorGeneric;
import com.ntmdoors.util.Names;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Screwdriver. Sneak + right click on a door with alternate skins cycles through them.
 */
public class ItemScrewdriver extends Item {

    public ItemScrewdriver() {
        setRegistryName(NTMDoors.MODID, "screwdriver");
        setTranslationKey(NTMDoors.MODID + ".screwdriver");
        setCreativeTab(NTMDoors.TAB);
        setMaxStackSize(1);
        setMaxDamage(0);
    }

    /**
     * Handles sneak-right-click before the block is activated. This is required because Minecraft
     * skips block activation when the player is sneaking with an item in hand.
     */
    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos,
                                           EnumFacing side, float hitX, float hitY, float hitZ,
                                           EnumHand hand) {
        if (!player.isSneaking()) return EnumActionResult.PASS;
        TileEntityDoorGeneric core = findCore(world, pos);
        if (core == null || !core.getDecl().hasSkinVariants()) return EnumActionResult.PASS;

        if (!world.isRemote) {
            if (core.cycleSkin()) {
                world.playSound(null, pos, SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.BLOCKS, 0.5F, 1.5F);
            }
        }
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
}
