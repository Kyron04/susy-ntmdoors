// SPDX-License-Identifier: LGPL-3.0-only
package com.ntmdoors.config;

import com.ntmdoors.NTMDoors;

import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Loader;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Gameplay tuning for the Security Shroud set and for other armor sets that packs want to tie
 * into the same security behaviour.
 *
 * <p>The item lists are registry names, not hard-coded item classes, so a pack can grant any
 * helmet the identity-concealment effect, any chestplate the weapons-relay bypass, and so on.
 * A full set is one configured item in each of the four armor slots (or one of each carried in the
 * inventory when {@code RequireWorn} is disabled).</p>
 *
 * <p>The Forge config file is {@code config/ntmdoors.cfg}. Values are read once during pre-init;
 * changing the file requires a game restart.</p>
 */
public final class SecurityShroudConfig {

    private static final String FILE_NAME = NTMDoors.MODID + ".cfg";

    private static final String GENERAL = "securityshroud";
    private static final String ITEMS = "securityshroud.items";
    private static final String HELMET = "securityshroud.helmet";
    private static final String CHESTPLATE = "securityshroud.chestplate";
    private static final String LEGGINGS = "securityshroud.leggings";
    private static final String BOOTS = "securityshroud.boots";
    private static final String UNITY = "securityshroud.unity";

    private static final String[] DEFAULT_HELMETS = {"ntmdoors:security_shroud_helmet"};
    private static final String[] DEFAULT_CHESTPLATES = {"ntmdoors:security_shroud_chestplate"};
    private static final String[] DEFAULT_LEGGINGS = {"ntmdoors:security_shroud_leggings"};
    private static final String[] DEFAULT_BOOTS = {"ntmdoors:security_shroud_boots"};

    private static boolean loaded;

    private static boolean enabled = true;
    private static boolean showTooltips = true;
    private static boolean showUnityTooltip = true;
    private static int durability = 2500;
    private static int helmetProtection = 3;
    private static int chestplateProtection = 8;
    private static int leggingsProtection = 6;
    private static int bootsProtection = 3;
    private static int enchantability;
    private static float toughness;

    private static Set<String> helmetItems = parse(DEFAULT_HELMETS);
    private static Set<String> chestplateItems = parse(DEFAULT_CHESTPLATES);
    private static Set<String> leggingsItems = parse(DEFAULT_LEGGINGS);
    private static Set<String> bootsItems = parse(DEFAULT_BOOTS);

    private static boolean hideNameTags = true;
    private static boolean bypassWeaponsBindings = true;
    private static float movementSpeedBonus = 0.5F;
    private static boolean preventLethalFallDamage = true;
    private static float minimumHealthAfterFall = 2.0F;

    private static boolean unityEnabled = true;
    private static boolean unityRequiresWorn = true;
    private static boolean ignoreMotionSensors = true;
    private static boolean ignoreEmancipationGrills = true;

    /** Loads the config once. Called from pre-init and lazily before the armor material is built. */
    public static synchronized void loadIfNeeded() {
        if (loaded) return;
        try {
            File directory = Loader.instance().getConfigDir();
            if (directory != null) load(new File(directory, FILE_NAME));
        } catch (Throwable ignored) {
            // Loader is not ready yet. Defaults stay in place and pre-init will load the file.
        }
    }

    /** Loads {@code file}, falling back to the built-in defaults when it cannot be read. */
    public static synchronized void load(File file) {
        if (loaded) return;
        if (file == null) {
            loaded = true;
            return;
        }

        Configuration config = new Configuration(file);
        try {
            config.load();

            config.setCategoryComment(GENERAL,
                    "Security Shroud gameplay tuning. Item lists use registry names "
                            + "(modid:item). Append other mods' armor pieces to use their effects "
                            + "with the same security systems. Changes require a game restart.");

            enabled = config.getBoolean("Enabled", GENERAL, true,
                    "Master switch for all Security Shroud effects. The armor items still exist "
                            + "and keep their durability and protection values when this is false.");
            showTooltips = config.getBoolean("ShowTooltips", GENERAL, true,
                    "Show the per-piece Security Shroud descriptions on the four built-in pieces.");
            showUnityTooltip = config.getBoolean("ShowUnityTooltip", GENERAL, true,
                    "Show the full-set Unity line on the four built-in pieces.");

            durability = config.getInt("Durability", GENERAL, 2500, 1, 1_000_000,
                    "Exact maximum durability of each built-in Security Shroud piece. "
                            + "The default is 2500.");
            helmetProtection = config.getInt("HelmetProtection", GENERAL, 3, 0, 20,
                    "Armor points for the built-in helmet. The four protection values are stored "
                            + "on the shared material and applied to their matching armor slot. "
                            + "The defaults total 20, the vanilla armor cap.");
            chestplateProtection = config.getInt("ChestplateProtection", GENERAL, 8, 0, 20,
                    "Armor points for the built-in chestplate. Default 8.");
            leggingsProtection = config.getInt("LeggingsProtection", GENERAL, 6, 0, 20,
                    "Armor points for the built-in leggings. Default 6.");
            bootsProtection = config.getInt("BootsProtection", GENERAL, 3, 0, 20,
                    "Armor points for the built-in boots. Default 3.");
            enchantability = config.getInt("Enchantability", GENERAL, 0, 0, 100,
                    "Enchantability of the built-in armor material.");
            toughness = config.getFloat("Toughness", GENERAL, 0.0F, 0.0F, 20.0F,
                    "Armor toughness of the built-in armor material.");

            config.setCategoryComment(ITEMS,
                    "Registry names of items that count for each behavior slot. Use modid:item, "
                            + "or modid:* to accept every item from a mod. Add other mods' armor "
                            + "here to make it part of the same security systems; a full set is one "
                            + "configured item in each slot (or carried, if unity.RequireWorn is "
                            + "false).");

            helmetItems = parse(config.getStringList("HelmetItems", ITEMS, DEFAULT_HELMETS,
                    "Items that conceal identity while worn in the head slot."));
            chestplateItems = parse(config.getStringList("ChestplateItems", ITEMS, DEFAULT_CHESTPLATES,
                    "Items that bypass weapons relay bindings while worn in the chest slot."));
            leggingsItems = parse(config.getStringList("LeggingsItems", ITEMS, DEFAULT_LEGGINGS,
                    "Items that grant the movement speed bonus while worn in the leg slot."));
            bootsItems = parse(config.getStringList("BootsItems", ITEMS, DEFAULT_BOOTS,
                    "Items that prevent a lethal fall while worn in the feet slot."));

            config.setCategoryComment(HELMET,
                    "The helmet behavior hides name tags for players and for any name-tagged "
                            + "entity wearing a configured helmet.");
            hideNameTags = config.getBoolean("HideNameTags", HELMET, true,
                    "Hide the name tag of an entity wearing a configured helmet.");

            config.setCategoryComment(CHESTPLATE,
                    "The chestplate behavior ignores Dead Man's Hand weapons-relay bindings.");
            bypassWeaponsBindings = config.getBoolean("BypassWeaponsBindings", CHESTPLATE, true,
                    "Allow a player wearing a configured chestplate to interact with a bound "
                            + "explosive without the matching Weapons Relay Key.");

            config.setCategoryComment(LEGGINGS,
                    "The leggings behavior applies a movement speed multiplier.");
            movementSpeedBonus = config.getFloat("MovementSpeedBonus", LEGGINGS, 0.5F, -1.0F, 1.0F,
                    "Movement speed multiplier. 0.5 = +50% (1.5x normal speed). This is applied "
                            + "as a multiplier on the player's total movement speed while the "
                            + "leggings are worn.");

            config.setCategoryComment(BOOTS,
                    "The boots behavior caps lethal fall damage.");
            preventLethalFallDamage = config.getBoolean("PreventLethalFallDamage", BOOTS, true,
                    "Prevent fall damage from taking a player below the configured minimum health.");
            minimumHealthAfterFall = config.getFloat("MinimumHealthAfterFall", BOOTS, 2.0F, 0.0F, 40.0F,
                    "Health left after a prevented lethal fall. 2.0 = one heart.");

            config.setCategoryComment(UNITY,
                    "The Unity effect requires one configured item in every armor slot (or one of "
                            + "each carried in the inventory when RequireWorn is false).");
            unityEnabled = config.getBoolean("EnableUnity", UNITY, true,
                    "Enable the full-set Unity effect.");
            unityRequiresWorn = config.getBoolean("RequireWorn", UNITY, true,
                    "True: all four pieces must be equipped. False: the four pieces may be anywhere "
                            + "in the player's inventory.");
            ignoreMotionSensors = config.getBoolean("IgnoreMotionSensors", UNITY, true,
                    "Full-set players are not seen by Motion Sensors, including the Match User "
                            + "check and its redstone events.");
            ignoreEmancipationGrills = config.getBoolean("IgnoreEmancipationGrills", UNITY, true,
                    "Full-set players pass through Emancipation Grills without clearance checks, "
                            + "Match User signals, blocking or lethal damage.");

            config.save();
        } catch (RuntimeException ex) {
            NTMDoors.LOGGER.error("Could not read Security Shroud config; using defaults", ex);
        } finally {
            loaded = true;
        }
    }

    /**
     * Logs configured entries that do not resolve after every mod has registered its items. This
     * runs late so a pack can see a misspelled registry name instead of silently losing an effect.
     */
    public static void logUnresolvedItems() {
        logUnresolved("HelmetItems", helmetItems);
        logUnresolved("ChestplateItems", chestplateItems);
        logUnresolved("LeggingsItems", leggingsItems);
        logUnresolved("BootsItems", bootsItems);
    }

    private static void logUnresolved(String listName, Set<String> items) {
        for (String name : items) {
            if (name.endsWith(":*")) {
                String modid = name.substring(0, name.length() - 2);
                if (!"minecraft".equals(modid) && !Loader.isModLoaded(modid)) {
                    NTMDoors.LOGGER.warn("Security Shroud {} entry '{}' names a mod that is not loaded",
                            listName, name);
                }
                continue;
            }

            try {
                if (!Item.REGISTRY.containsKey(new ResourceLocation(name))) {
                    NTMDoors.LOGGER.warn("Security Shroud {} entry '{}' is not a registered item",
                            listName, name);
                }
            } catch (RuntimeException ex) {
                NTMDoors.LOGGER.warn("Security Shroud {} entry '{}' is not a valid registry name",
                        listName, name);
            }
        }
    }

    /** Normalizes registry names, strips metadata suffixes, and accepts modid:* wildcards. */
    private static Set<String> parse(String[] values) {
        Set<String> out = new HashSet<>();
        if (values != null) {
            for (String value : values) {
                if (value == null) continue;
                String name = value.trim().toLowerCase(Locale.ROOT);
                if (name.isEmpty()) continue;
                if (name.indexOf(':') < 0) name = "minecraft:" + name;

                int secondColon = name.indexOf(':', name.indexOf(':') + 1);
                if (secondColon >= 0) name = name.substring(0, secondColon);
                out.add(name);
            }
        }
        if (out.isEmpty()) out.add("minecraft:barrier");
        return Collections.unmodifiableSet(out);
    }

    public static boolean enabled() {
        return enabled;
    }

    public static boolean showTooltips() {
        return showTooltips;
    }

    public static boolean showUnityTooltip() {
        return showUnityTooltip;
    }

    public static int durability() {
        return durability;
    }

    public static int helmetProtection() {
        return helmetProtection;
    }

    public static int chestplateProtection() {
        return chestplateProtection;
    }

    public static int leggingsProtection() {
        return leggingsProtection;
    }

    public static int bootsProtection() {
        return bootsProtection;
    }

    /** Sum of the four configured protection values, shown on the built-in tooltips. */
    public static int totalProtection() {
        return helmetProtection + chestplateProtection + leggingsProtection + bootsProtection;
    }

    public static int enchantability() {
        return enchantability;
    }

    public static float toughness() {
        return toughness;
    }

    public static Set<String> helmetItems() {
        return helmetItems;
    }

    public static Set<String> chestplateItems() {
        return chestplateItems;
    }

    public static Set<String> leggingsItems() {
        return leggingsItems;
    }

    public static Set<String> bootsItems() {
        return bootsItems;
    }

    public static boolean hideNameTags() {
        return hideNameTags;
    }

    public static boolean bypassWeaponsBindings() {
        return bypassWeaponsBindings;
    }

    public static float movementSpeedBonus() {
        return movementSpeedBonus;
    }

    public static boolean preventLethalFallDamage() {
        return preventLethalFallDamage;
    }

    public static float minimumHealthAfterFall() {
        return minimumHealthAfterFall;
    }

    public static boolean unityEnabled() {
        return unityEnabled;
    }

    public static boolean unityRequiresWorn() {
        return unityRequiresWorn;
    }

    public static boolean ignoreMotionSensors() {
        return ignoreMotionSensors;
    }

    public static boolean ignoreEmancipationGrills() {
        return ignoreEmancipationGrills;
    }

    private SecurityShroudConfig() {
    }
}
