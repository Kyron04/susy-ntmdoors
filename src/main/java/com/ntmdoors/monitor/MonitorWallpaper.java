package com.ntmdoors.monitor;

import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared description of the cosmetic wallpapers a monitor screen can show.
 *
 * <p>A stored wallpaper is either empty (the normal terminal) or {@code builtin:<id>} for one of
 * the packaged display images. Custom/remote URLs are deliberately not supported: the monitor
 * never makes outbound network requests on behalf of a player.</p>
 */
public final class MonitorWallpaper {

    public static final String BUILTIN_PREFIX = "builtin:";

    public static final class Builtin {
        public final String id;
        public final String label;
        public final ResourceLocation texture;

        private Builtin(String id, String label) {
            this.id = id;
            this.label = label;
            this.texture = new ResourceLocation("ntmdoors", "textures/display/" + id + ".png");
        }
    }

    private static final List<Builtin> BUILTINS;
    private static final Map<String, Builtin> BUILTIN_BY_ID;

    static {
        List<Builtin> builtins = new ArrayList<>();
        builtins.add(new Builtin("supersymmetry", "SUPERSYMMETRY"));
        builtins.add(new Builtin("black_mesa", "BLACK MESA"));
        builtins.add(new Builtin("goog", "GOOG..."));
        builtins.add(new Builtin("world_map", "WORLD MAP"));
        builtins.add(new Builtin("windows_error", "WINDOWS ERROR"));
        BUILTINS = Collections.unmodifiableList(builtins);
        Map<String, Builtin> byId = new HashMap<>();
        for (Builtin builtin : builtins) byId.put(builtin.id, builtin);
        BUILTIN_BY_ID = Collections.unmodifiableMap(byId);
    }

    public static List<Builtin> builtins() {
        return BUILTINS;
    }

    @Nullable
    public static Builtin builtinById(String id) {
        return id == null ? null : BUILTIN_BY_ID.get(id);
    }

    public static String builtin(String id) {
        return BUILTIN_PREFIX + id;
    }

    public static boolean isBuiltin(@Nullable String value) {
        return value != null && value.startsWith(BUILTIN_PREFIX);
    }

    @Nullable
    public static String builtinId(@Nullable String value) {
        if (!isBuiltin(value)) return null;
        String id = value.substring(BUILTIN_PREFIX.length());
        return builtinById(id) == null ? null : id;
    }

    @Nullable
    public static ResourceLocation texture(@Nullable String value) {
        String id = builtinId(value);
        if (id == null) return null;
        Builtin builtin = BUILTIN_BY_ID.get(id);
        return builtin == null ? null : builtin.texture;
    }

    public static String label(@Nullable String value) {
        String id = builtinId(value);
        Builtin builtin = builtinById(id);
        return builtin != null ? builtin.label : "TERMINAL";
    }

    /** Empty values are valid and mean "show the normal terminal page". */
    public static boolean isValid(@Nullable String value) {
        if (value == null || value.isEmpty()) return true;
        return isBuiltin(value) && builtinId(value) != null;
    }

    /** Returns a clean stored value, or an empty string when the input is not supported. */
    public static String normalize(@Nullable String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return "";
        if (!isBuiltin(trimmed)) return "";
        String id = builtinId(trimmed);
        return id == null ? "" : builtin(id);
    }

    private MonitorWallpaper() {
    }
}
