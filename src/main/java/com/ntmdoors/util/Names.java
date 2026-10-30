package com.ntmdoors.util;

import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;

import java.util.List;

/** Display-name helpers. Keeps raw translation keys out of tooltips and the creative tab. */
public final class Names {

    /** The translation for {@code key}, or {@code fallback} when no language file defines it. */
    public static String translationOr(String key, String fallback) {
        return I18n.canTranslate(key) ? I18n.translateToLocal(key) : fallback;
    }

    /** True when a language file defines {@code key}. */
    public static boolean canTranslate(String key) {
        return I18n.canTranslate(key);
    }

    /**
     * Adds item description lines in the standard grey, so they read as supporting text under the
     * coloured item name and any white data lines.
     */
    public static void addDescription(List<String> tooltip, String... lines) {
        for (String line : lines) {
            tooltip.add(TextFormatting.GRAY + line);
        }
    }

    private Names() {
    }
}
