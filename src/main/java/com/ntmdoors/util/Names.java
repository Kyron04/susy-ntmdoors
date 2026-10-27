package com.ntmdoors.util;

import net.minecraft.util.text.translation.I18n;

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

    private Names() {
    }
}
