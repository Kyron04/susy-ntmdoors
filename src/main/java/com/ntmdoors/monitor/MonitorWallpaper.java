package com.ntmdoors.monitor;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shared description of the cosmetic wallpapers a monitor screen can show.
 *
 * <p>A stored wallpaper is either empty (the normal terminal), {@code builtin:<id>} for one of
 * the packaged display images, or {@code url:<http(s) URL>} for a user-supplied static image.
 * The URL is deliberately only a string on the server; each client downloads and decodes it
 * locally, so a dedicated server never makes outbound requests for a player's wallpaper.</p>
 */
public final class MonitorWallpaper {

    public static final String BUILTIN_PREFIX = "builtin:";
    public static final String URL_PREFIX = "url:";
    public static final int MAX_URL_LENGTH = 512;

    public static final class Builtin {
        public final String id;
        public final String label;
        public final Identifier texture;

        public Builtin(String id, String label) {
            this.id = id;
            this.label = label;
            this.texture = Identifier.of("ntmdoors", "textures/display/" + id + ".png");
        }

        public Builtin(String id, String label, Identifier texture) {
            this.id = id;
            this.label = label;
            this.texture = texture;
        }

        public String id() { return id; }
        public String label() { return label; }
        public Identifier texture() { return texture; }
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

    public static String url(String url) {
        return URL_PREFIX + url;
    }

    public static boolean isBuiltin(@Nullable String value) {
        return value != null && value.startsWith(BUILTIN_PREFIX);
    }

    public static boolean isUrl(@Nullable String value) {
        return value != null && value.startsWith(URL_PREFIX);
    }

    @Nullable
    public static String builtinId(@Nullable String value) {
        if (!isBuiltin(value)) return null;
        String id = value.substring(BUILTIN_PREFIX.length());
        return builtinById(id) == null ? null : id;
    }

    @Nullable
    public static String urlOf(@Nullable String value) {
        if (!isUrl(value)) return null;
        return value.substring(URL_PREFIX.length());
    }

    @Nullable
    public static Identifier texture(@Nullable String value) {
        String id = builtinId(value);
        if (id == null) return null;
        Builtin builtin = BUILTIN_BY_ID.get(id);
        return builtin == null ? null : builtin.texture;
    }

    public static String label(@Nullable String value) {
        String id = builtinId(value);
        Builtin builtin = builtinById(id);
        if (builtin != null) return builtin.label;
        if (isUrl(value)) return "CUSTOM IMAGE";
        return "TERMINAL";
    }

    /** Empty values are valid and mean "show the normal terminal page". */
    public static boolean isValid(@Nullable String value) {
        if (value == null || value.isEmpty()) return true;
        if (isBuiltin(value)) return builtinId(value) != null;
        if (isUrl(value)) return isValidHttpUrl(urlOf(value));
        return false;
    }

    /** Returns a clean stored value, or an empty string when the input is not supported. */
    public static String normalize(@Nullable String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return "";
        if (isBuiltin(trimmed)) {
            String id = builtinId(trimmed);
            return id == null ? "" : builtin(id);
        }
        if (isUrl(trimmed)) {
            String raw = urlOf(trimmed);
            if (raw == null) return "";
            raw = raw.trim();
            return isValidHttpUrl(raw) ? url(raw) : "";
        }
        return "";
    }

    public static boolean isValidHttpUrl(@Nullable String raw) {
        if (raw == null) return false;
        String value = raw.trim();
        if (value.isEmpty() || value.length() > MAX_URL_LENGTH) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c <= 32 || c == 127) return false;
        }
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            if (scheme == null) return false;
            scheme = scheme.toLowerCase(Locale.ROOT);
            return ("http".equals(scheme) || "https".equals(scheme))
                    && uri.getHost() != null && !uri.getHost().isEmpty();
        } catch (URISyntaxException ex) {
            return false;
        }
    }

    private MonitorWallpaper() {
    }
}
