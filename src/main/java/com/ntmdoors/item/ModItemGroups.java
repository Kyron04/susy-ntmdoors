package com.ntmdoors.item;

import com.ntmdoors.NTMDoors;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Creative tab registration for NTM Doors.
 */
public final class ModItemGroups {

    public static final RegistryKey<ItemGroup> NTM_DOORS_KEY = RegistryKey.of(
            RegistryKeys.ITEM_GROUP,
            Identifier.of(NTMDoors.MOD_ID, "ntmdoors")
    );

    private static Supplier<ItemStack> iconSupplier = () -> new ItemStack(Items.IRON_DOOR);
    private static final List<ItemConvertible> REGISTERED_ENTRIES = new ArrayList<>();

    public static final ItemGroup NTM_DOORS = FabricItemGroup.builder()
            .icon(() -> iconSupplier.get())
            .displayName(Text.translatable("itemGroup.ntmdoors"))
            .entries((displayContext, entries) -> {
                for (ItemConvertible item : REGISTERED_ENTRIES) {
                    entries.add(item);
                }
            })
            .build();

    private ModItemGroups() {}

    public static void setIconSupplier(Supplier<ItemStack> supplier) {
        if (supplier != null) {
            iconSupplier = supplier;
        }
    }

    public static void addEntry(ItemConvertible item) {
        if (item != null) {
            REGISTERED_ENTRIES.add(item);
        }
    }

    public static void registerItemGroups() {
        Registry.register(Registries.ITEM_GROUP, NTM_DOORS_KEY, NTM_DOORS);
    }
}
