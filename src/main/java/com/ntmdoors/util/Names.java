package com.ntmdoors.util;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/** Display-name helpers. Keeps raw translation keys out of tooltips and the creative tab. */
public final class Names {

    /**
     * Adds item description lines in the standard grey, so they read as supporting text under the
     * coloured item name and any white data lines.
     */
    public static void addDescription(List<Text> tooltip, String... lines) {
        for (String line : lines) {
            tooltip.add(Text.literal(line).formatted(Formatting.GRAY));
        }
    }

    private Names() {
    }
}
