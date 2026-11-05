package com.ntmdoors.client;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.monitor.MonitorWallpaper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/**
 * Client-side texture cache for the cosmetic monitor wallpapers.
 *
 * <p>Packaged images are ordinary resources. Custom URLs are downloaded by this client only,
 * with size and dimension limits, then uploaded as a dynamic texture on the client thread.
 * The server only ever stores the URL string.</p>
 */
@SideOnly(Side.CLIENT)
public final class DisplayImages {

    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final int MAX_DIMENSION = 4096;
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 15_000;

    private static final Map<String, Remote> REMOTE = new HashMap<>();
    private static final Map<ResourceLocation, Boolean> BUILTIN_EXISTS = new HashMap<>();

    private static final class Remote {
        private final ResourceLocation location;
        private boolean started;
        private volatile boolean ready;
        private volatile boolean failed;

        private Remote(ResourceLocation location) {
            this.location = location;
        }
    }

    /** Returns the texture location for a stored wallpaper, or null for the terminal page. */
    @Nullable
    public static ResourceLocation textureFor(String wallpaper) {
        ResourceLocation builtin = MonitorWallpaper.texture(wallpaper);
        if (builtin != null) return builtin;
        String url = MonitorWallpaper.urlOf(wallpaper);
        if (url == null) return null;
        Remote remote = remote(url);
        start(remote, url);
        return remote.location;
    }

    /** True when the wallpaper can be drawn instead of a loading/error card. */
    public static boolean isReady(String wallpaper) {
        ResourceLocation builtin = MonitorWallpaper.texture(wallpaper);
        if (builtin != null) return builtinExists(builtin);
        String url = MonitorWallpaper.urlOf(wallpaper);
        if (url == null) return false;
        Remote remote = remote(url);
        start(remote, url);
        return remote.ready;
    }

    public static boolean isFailed(String wallpaper) {
        String url = MonitorWallpaper.urlOf(wallpaper);
        if (url == null) return false;
        Remote remote = remote(url);
        start(remote, url);
        return remote.failed;
    }

    public static boolean isLoading(String wallpaper) {
        String url = MonitorWallpaper.urlOf(wallpaper);
        if (url == null) return false;
        Remote remote = remote(url);
        start(remote, url);
        return remote.started && !remote.ready && !remote.failed;
    }

    public static boolean builtinExists(String id) {
        MonitorWallpaper.Builtin builtin = MonitorWallpaper.builtinById(id);
        if (builtin == null) return false;
        return builtinExists(builtin.texture);
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

    private static Remote remote(String url) {
        synchronized (REMOTE) {
            Remote remote = REMOTE.get(url);
            if (remote == null) {
                remote = new Remote(new ResourceLocation("ntmdoors",
                        "remote_display/" + sha1(url)));
                REMOTE.put(url, remote);
            }
            return remote;
        }
    }

    private static void start(Remote remote, String url) {
        synchronized (REMOTE) {
            if (remote.started) return;
            remote.started = true;
        }
        Thread thread = new Thread(() -> download(remote, url),
                "NTMDoors display image loader");
        thread.setDaemon(true);
        thread.start();
    }

    private static void download(Remote remote, String url) {
        HttpURLConnection connection = null;
        try {
            URL parsed = new URL(url);
            connection = (HttpURLConnection) parsed.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "NTMDoors/1.0");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("HTTP " + status);
            }
            int declared = connection.getContentLength();
            if (declared > MAX_BYTES) throw new IOException("Image is too large");

            byte[] bytes;
            try (InputStream input = connection.getInputStream()) {
                bytes = readLimited(input, MAX_BYTES);
            }
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) throw new IOException("Unsupported image format");
            if (image.getWidth() <= 0 || image.getHeight() <= 0
                    || image.getWidth() > MAX_DIMENSION || image.getHeight() > MAX_DIMENSION) {
                throw new IOException("Image dimensions are out of range");
            }

            Minecraft.getMinecraft().addScheduledTask(() -> {
                try {
                    DynamicTexture texture = new DynamicTexture(image);
                    Minecraft.getMinecraft().getTextureManager().loadTexture(remote.location, texture);
                    remote.ready = true;
                } catch (Throwable ex) {
                    remote.failed = true;
                    NTMDoors.LOGGER.warn("Could not upload display image {}: {}",
                            url, ex.toString());
                }
            });
        } catch (Throwable ex) {
            remote.failed = true;
            NTMDoors.LOGGER.warn("Could not load display image {}: {}", url, ex.toString());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static byte[] readLimited(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > limit) throw new IOException("Image is too large");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static String sha1(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                out.append(Character.forDigit((b >>> 4) & 0xF, 16));
                out.append(Character.forDigit(b & 0xF, 16));
            }
            return out.toString();
        } catch (Exception ex) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private DisplayImages() {
    }
}
