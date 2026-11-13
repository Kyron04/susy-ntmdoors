package com.ntmdoors.client;

import com.ntmdoors.monitor.MonitorWallpaper;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Client-side lookup for the packaged monitor wallpapers.
 *
 * <p>Only built-in images are supported. The monitor never downloads a custom image or makes an
 * outbound HTTP request, so a player cannot use a wallpaper to probe another viewer's network.</p>
 */
@SideOnly(Side.CLIENT)
public final class DisplayImages {

    private static final Map<ResourceLocation, Boolean> BUILTIN_EXISTS = new HashMap<>();

    /** Clears the cached resource probes when the client leaves a world. */
    public static void clear() {
        BUILTIN_EXISTS.clear();
    }

    /** Returns the packaged texture, or null when the value is not a built-in wallpaper. */
    @Nullable
    public static ResourceLocation textureFor(String wallpaper) {
        ResourceLocation texture = MonitorWallpaper.texture(wallpaper);
        if (texture == null || !builtinExists(texture)) return null;
        return texture;
    }

    public static boolean builtinExists(String id) {
        MonitorWallpaper.Builtin builtin = MonitorWallpaper.builtinById(id);
        return builtin != null && builtinExists(builtin.texture);
    }

    private static boolean builtinExists(ResourceLocation location) {
        Boolean cached = BUILTIN_EXISTS.get(location);
        if (cached != null) return cached;
        boolean exists;
        try {
            Minecraft.getMinecraft().getResourceManager().getResource(location);
            exists = true;
        } catch (Exception ex) {
            exists = false;
        }
        BUILTIN_EXISTS.put(location, exists);
        return exists;
    }

    private DisplayImages() {
    }
}
