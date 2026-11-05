package com.ntmdoors.client;

import com.ntmdoors.block.BlockMonitorDesk;
import com.ntmdoors.block.BlockMonitorSetup;
import com.ntmdoors.block.MonitorDesk;
import com.ntmdoors.monitor.MonitorDisplay;
import com.ntmdoors.monitor.MonitorWallpaper;
import com.ntmdoors.registry.ModContent;
import com.ntmdoors.tile.TileEntityMonitorDesk;
import com.ntmdoors.util.Facings;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws the physical monitor screens from the core desk tile entity.
 *
 * <p>Each screen renders the real terminal GUI, using the desk's last live page. The GUI is drawn
 * directly into the panel plane, so text is rendered at the panel's native resolution instead of
 * being a blurry screenshot. Interaction still opens the normal terminal GUI.</p>
 */
@SideOnly(Side.CLIENT)
public class RenderMonitorDesk extends TileEntitySpecialRenderer<TileEntityMonitorDesk> {

    private static final int GUI_WIDTH = 512;
    private static final int GUI_HEIGHT = 384;
    private static final Map<BlockPos, GuiMonitor[]> DISPLAY_GUIS = boundedCache();
    private static final Map<BlockPos, GuiNotepad[]> NOTEPAD_DISPLAY_GUIS = boundedCache();
    private static final Map<BlockPos, int[]> DISPLAY_REVISIONS = boundedCache();
    private static final Map<BlockPos, int[]> NOTEPAD_REVISIONS = boundedCache();
    private static final Map<BlockPos, CachedScreens> SCREEN_CACHE = boundedCache();
    private static World displayWorld;

    /** Keeps a long session from retaining GUI pages for every desk ever rendered. */
    private static <V> Map<BlockPos, V> boundedCache() {
        return new LinkedHashMap<BlockPos, V>(16, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<BlockPos, V> eldest) {
                return size() > 64;
            }
        };
    }

    /** The desk geometry only changes when a monitor cell is placed or broken. */
    private static final class CachedScreens {
        private final IBlockState coreState;
        private final List<Screen> screens;
        private final long checkedAt;

        private CachedScreens(IBlockState coreState, List<Screen> screens, long checkedAt) {
            this.coreState = coreState;
            this.screens = screens;
            this.checkedAt = checkedAt;
        }
    }

    private static final class Screen {
        private final BlockPos pos;
        private final EnumFacing facing;
        private final int pageIndex;
        private final float minX;
        private final float maxX;
        private final float minY;
        private final float maxY;
        private final float z;
        private final float originX;
        private final float originZ;
        private final float angle;

        private Screen(BlockPos pos, EnumFacing facing, int pageIndex,
                       float minX, float maxX, float minY, float maxY, float z,
                       float originX, float originZ, float angle) {
            this.pos = pos;
            this.facing = facing;
            this.pageIndex = pageIndex;
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
            this.z = z;
            this.originX = originX;
            this.originZ = originZ;
            this.angle = angle;
        }
    }

    @Override
    public void render(TileEntityMonitorDesk te, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        if (te.getWorld() == null) return;
        if (te.getWorld() != displayWorld) {
            clearDisplayCache();
            displayWorld = te.getWorld();
        }
        IBlockState coreState = te.getWorld().getBlockState(te.getPos());
        List<Screen> screens = screensFor(te, coreState);
        if (screens.isEmpty()) return;

        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        float brightnessX = OpenGlHelper.lastBrightnessX;
        float brightnessY = OpenGlHelper.lastBrightnessY;

        GlStateManager.pushMatrix();
        GlStateManager.disableLighting();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        GlStateManager.disableCull();
        GlStateManager.enablePolygonOffset();
        GlStateManager.doPolygonOffset(-1F, -1F);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);

        for (Screen screen : screens) {
            renderScreen(te, screen, x, y, z, partialTicks);
        }

        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,
                brightnessX, brightnessY);
        GlStateManager.disablePolygonOffset();
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.depthMask(depthWrite);
        if (!depth) GlStateManager.disableDepth();
        if (lighting) GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    /** Reuses the previous screen list until the core state changes or one second passes. */
    private static List<Screen> screensFor(TileEntityMonitorDesk te, IBlockState coreState) {
        CachedScreens cached = SCREEN_CACHE.get(te.getPos());
        long now = te.getWorld().getTotalWorldTime();
        if (cached != null && cached.coreState.equals(coreState)
                && now - cached.checkedAt < 20L) {
            return cached.screens;
        }
        List<Screen> screens = collectScreens(te, coreState);
        SCREEN_CACHE.put(te.getPos(), new CachedScreens(coreState, screens, now));
        return screens;
    }

    private static List<Screen> collectScreens(TileEntityMonitorDesk te, IBlockState coreState) {
        List<Screen> screens = new ArrayList<>();
        Block coreBlock = coreState.getBlock();

        if (coreBlock instanceof BlockMonitorSetup) {
            if (((BlockMonitorSetup) coreBlock).layer() != BlockMonitorSetup.Layer.BOTTOM) {
                return screens;
            }
            EnumFacing facing = coreState.getValue(BlockMonitorSetup.FACING);
            for (BlockMonitorSetup.Layer layer : new BlockMonitorSetup.Layer[]{
                    BlockMonitorSetup.Layer.MIDDLE, BlockMonitorSetup.Layer.TOP}) {
                for (BlockMonitorSetup.Column column : BlockMonitorSetup.Column.values()) {
                    BlockPos pos = te.getPos().add(
                            BlockMonitorSetup.offset(layer, column, facing));
                    IBlockState state = te.getWorld().getBlockState(pos);
                    if (!(state.getBlock() instanceof BlockMonitorSetup)) continue;
                    BlockMonitorSetup block = (BlockMonitorSetup) state.getBlock();
                    if (block.layer() != layer
                            || state.getValue(BlockMonitorSetup.FACING) != facing
                            || state.getValue(BlockMonitorSetup.COLUMN) != column) {
                        continue;
                    }
                    Screen screen = setupScreen(pos, facing, layer, column);
                    if (screen != null) screens.add(screen);
                }
            }
            return screens;
        }

        if (!(coreBlock instanceof BlockMonitorDesk)) return screens;
        BlockMonitorDesk core = (BlockMonitorDesk) coreBlock;
        if (core.layer() != BlockMonitorSetup.Layer.BOTTOM) return screens;

        EnumFacing facing = coreState.getValue(BlockMonitorDesk.FACING);
        MonitorDesk desk = core.desk();
        for (BlockMonitorDesk block : desk.blocks()) {
            if (block.layer() != BlockMonitorSetup.Layer.MIDDLE) continue;
            for (BlockMonitorSetup.Column column : block.columns()) {
                BlockPos pos = te.getPos().add(block.offset(column, facing));
                IBlockState state = te.getWorld().getBlockState(pos);
                if (state.getBlock() != block) continue;
                if (state.getValue(block.columnProperty()) != column) continue;
                if (state.getValue(BlockMonitorDesk.FACING) != facing) continue;
                Screen screen = smallScreen(pos, facing, block, column);
                if (screen != null) screens.add(screen);
            }
        }
        return screens;
    }

    private static Screen setupScreen(BlockPos pos, EnumFacing facing,
                                      BlockMonitorSetup.Layer layer,
                                      BlockMonitorSetup.Column column) {
        float minY;
        float maxY;
        if (layer == BlockMonitorSetup.Layer.MIDDLE) {
            minY = 3.5F;
            maxY = 12.5F;
        } else if (layer == BlockMonitorSetup.Layer.TOP) {
            minY = -0.5F;
            maxY = 8.5F;
        } else {
            return null;
        }

        float minX;
        float maxX;
        float screenZ;
        float originX;
        float originZ;
        float angle;
        switch (column) {
            case LEFT:
                minX = 4F;
                maxX = 16F;
                screenZ = 9.25F;
                originX = 10F;
                originZ = 11.55F;
                angle = -22.5F;
                break;
            case RIGHT:
                minX = 0F;
                maxX = 12F;
                screenZ = 9.25F;
                originX = 6F;
                originZ = 11.55F;
                angle = 22.5F;
                break;
            default:
                minX = 2F;
                maxX = 14F;
                screenZ = 10.25F;
                originX = 0F;
                originZ = 0F;
                angle = 0F;
                break;
        }
        int pageIndex = (layer == BlockMonitorSetup.Layer.TOP ? 3 : 0) + column.ordinal();
        return new Screen(pos, facing, pageIndex, minX, maxX, minY, maxY, screenZ,
                originX, originZ, angle);
    }

    private static Screen smallScreen(BlockPos pos, EnumFacing facing,
                                      BlockMonitorDesk block,
                                      BlockMonitorSetup.Column column) {
        if (block == ModContent.SINGLE_MONITOR_SETUP_MIDDLE) {
            if (column != BlockMonitorSetup.Column.RIGHT) return null;
            return new Screen(pos, facing, column.ordinal(),
                    -1.5F, 10.5F, 3.5F, 12.5F, 10.25F, 0F, 0F, 0F);
        }
        if (block == ModContent.DUAL_MONITOR_SETUP_2X2_MIDDLE) {
            if (column == BlockMonitorSetup.Column.LEFT) {
                return new Screen(pos, facing, column.ordinal(),
                        4F, 16F, 3.5F, 12.5F, 9.25F, 10F, 11.55F, -22.5F);
            }
            if (column == BlockMonitorSetup.Column.RIGHT) {
                return new Screen(pos, facing, column.ordinal(),
                        2F, 14F, 3.5F, 12.5F, 10.25F, 0F, 0F, 0F);
            }
            return null;
        }

        float minX;
        float maxX;
        float screenZ;
        float originX;
        float originZ;
        float angle;
        switch (column) {
            case LEFT:
                minX = 4F;
                maxX = 16F;
                screenZ = 9.25F;
                originX = 10F;
                originZ = 11.55F;
                angle = -22.5F;
                break;
            case RIGHT:
                minX = 0F;
                maxX = 12F;
                screenZ = 9.25F;
                originX = 6F;
                originZ = 11.55F;
                angle = 22.5F;
                break;
            default:
                minX = 2F;
                maxX = 14F;
                screenZ = 10.25F;
                originX = 0F;
                originZ = 0F;
                angle = 0F;
                break;
        }
        return new Screen(pos, facing, column.ordinal(),
                minX, maxX, 3.5F, 12.5F, screenZ, originX, originZ, angle);
    }

    private void renderScreen(TileEntityMonitorDesk te, Screen screen,
                              double coreX, double coreY, double coreZ,
                              float partialTicks) {
        double cellX = coreX + screen.pos.getX() - te.getPos().getX();
        double cellY = coreY + screen.pos.getY() - te.getPos().getY();
        double cellZ = coreZ + screen.pos.getZ() - te.getPos().getZ();

        GlStateManager.pushMatrix();
        GlStateManager.translate(cellX + 0.5D, cellY, cellZ + 0.5D);
        GlStateManager.rotate(Facings.yaw(screen.facing), 0F, 1F, 0F);
        if (screen.angle != 0F) {
            double originX = screen.originX / 16.0D - 0.5D;
            double originZ = screen.originZ / 16.0D - 0.5D;
            GlStateManager.translate(originX, 0D, originZ);
            GlStateManager.rotate(screen.angle, 0F, 1F, 0F);
            GlStateManager.translate(-originX, 0D, -originZ);
        }
        // The panel only exists on the front of the monitor. The GUI quads need culling disabled
        // because their negative scale reverses their winding, so an invisible depth-only quad is
        // drawn just behind the panel instead. From the back that quad is in front of the GUI and
        // hides it; from the front the GUI is nearer and passes the depth test.
        GlStateManager.enableCull();
        drawPanel(screen);
        GlStateManager.disableCull();
        drawDepthOccluder(screen);
        String wallpaper = te.getScreenWallpaper(screen.pageIndex);
        boolean showingNotepad = te.getScreenStage(screen.pageIndex)
                == MonitorDisplay.STAGE_NOTEPAD;
        if (wallpaper.isEmpty() || showingNotepad) {
            drawDisplay(te, screen, partialTicks);
        } else {
            drawWallpaper(screen, wallpaper);
        }
        GlStateManager.popMatrix();
    }

    private static void drawDepthOccluder(Screen screen) {
        float left = screen.minX / 16F - 0.5F;
        float right = screen.maxX / 16F - 0.5F;
        float bottom = screen.minY / 16F;
        float top = screen.maxY / 16F;
        float z = screen.z / 16F - 0.5F - 0.005F;

        GlStateManager.depthMask(true);
        GlStateManager.colorMask(false, false, false, false);
        GlStateManager.disableTexture2D();
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        buffer.pos(left, bottom, z).color(0F, 0F, 0F, 1F).endVertex();
        buffer.pos(right, bottom, z).color(0F, 0F, 0F, 1F).endVertex();
        buffer.pos(right, top, z).color(0F, 0F, 0F, 1F).endVertex();
        buffer.pos(left, top, z).color(0F, 0F, 0F, 1F).endVertex();
        Tessellator.getInstance().draw();
        GlStateManager.enableTexture2D();
        GlStateManager.colorMask(true, true, true, true);
    }

    private static void drawPanel(Screen screen) {
        float left = screen.minX / 16F - 0.5F;
        float right = screen.maxX / 16F - 0.5F;
        float bottom = screen.minY / 16F;
        float top = screen.maxY / 16F;
        float z = screen.z / 16F - 0.5F - 0.006F;

        GlStateManager.disableTexture2D();
        GlStateManager.color(1F, 1F, 1F, 1F);
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);

        // Screen background.
        buffer.pos(left, bottom, z).color(0.015F, 0.015F, 0.015F, 1F).endVertex();
        buffer.pos(right, bottom, z).color(0.015F, 0.015F, 0.015F, 1F).endVertex();
        buffer.pos(right, top, z).color(0.015F, 0.015F, 0.015F, 1F).endVertex();
        buffer.pos(left, top, z).color(0.015F, 0.015F, 0.015F, 1F).endVertex();

        // Keep the outer edge on the screen boundary, but lift it off the background slightly
        // so the thin frame does not z-fight with the panel fill.
        float border = 0.008F;
        float borderZ = z - 0.001F;
        drawRect(buffer, left, top - border, right, top,
                borderZ, 0.5F, 0.27F, 0F, 1F);
        drawRect(buffer, left, bottom, right, bottom + border,
                borderZ, 0.5F, 0.27F, 0F, 1F);
        drawRect(buffer, left, bottom, left + border, top,
                borderZ, 0.5F, 0.27F, 0F, 1F);
        drawRect(buffer, right - border, bottom, right, top,
                borderZ, 0.5F, 0.27F, 0F, 1F);

        Tessellator.getInstance().draw();
        GlStateManager.enableTexture2D();
    }

    private static void drawRect(BufferBuilder buffer, float x1, float y1,
                                 float x2, float y2, float z,
                                 float r, float g, float b, float a) {
        buffer.pos(x1, y1, z).color(r, g, b, a).endVertex();
        buffer.pos(x2, y1, z).color(r, g, b, a).endVertex();
        buffer.pos(x2, y2, z).color(r, g, b, a).endVertex();
        buffer.pos(x1, y2, z).color(r, g, b, a).endVertex();
    }

    /** Draws a cosmetic wallpaper, or a status card while a custom image loads or fails. */
    private static void drawWallpaper(Screen screen, String wallpaper) {
        if (!DisplayImages.isReady(wallpaper)) {
            String title;
            String subtitle;
            if (DisplayImages.isFailed(wallpaper)) {
                title = "DISPLAY UNAVAILABLE";
                subtitle = wallpaper.startsWith(MonitorWallpaper.URL_PREFIX)
                        ? wallpaper.substring(MonitorWallpaper.URL_PREFIX.length()) : "";
            } else if (DisplayImages.isLoading(wallpaper)) {
                title = "LOADING DISPLAY...";
                subtitle = "";
            } else {
                title = "IMAGE MISSING";
                subtitle = wallpaper.startsWith(MonitorWallpaper.BUILTIN_PREFIX)
                        ? wallpaper.substring(MonitorWallpaper.BUILTIN_PREFIX.length()) : "";
            }
            drawWallpaperMessage(screen, title, subtitle);
            return;
        }

        ResourceLocation texture = DisplayImages.textureFor(wallpaper);
        if (texture == null) {
            drawWallpaperMessage(screen, "DISPLAY UNAVAILABLE", "");
            return;
        }

        float left = screen.minX / 16F - 0.5F;
        float right = screen.maxX / 16F - 0.5F;
        float bottom = screen.minY / 16F;
        float top = screen.maxY / 16F;
        float z = screen.z / 16F - 0.5F - 0.009F;

        boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        GlStateManager.color(1F, 1F, 1F, 1F);
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);

        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        // Local +X points to the viewer's left (the same reason the GUI uses a negative X
        // scale), so the texture's U axis is reversed here.
        buffer.pos(left, bottom, z).tex(1F, 1F).endVertex();
        buffer.pos(right, bottom, z).tex(0F, 1F).endVertex();
        buffer.pos(right, top, z).tex(0F, 0F).endVertex();
        buffer.pos(left, top, z).tex(1F, 0F).endVertex();
        Tessellator.getInstance().draw();

        GlStateManager.depthMask(depthWrite);
        GlStateManager.color(1F, 1F, 1F, 1F);
    }

    private static void drawWallpaperMessage(Screen screen, String title, String subtitle) {
        float left = screen.minX / 16F - 0.5F;
        float right = screen.maxX / 16F - 0.5F;
        float bottom = screen.minY / 16F;
        float top = screen.maxY / 16F;
        float z = screen.z / 16F - 0.5F - 0.009F;

        GlStateManager.pushMatrix();
        GlStateManager.translate(right, top, z);
        GlStateManager.scale(-(right - left) / GUI_WIDTH, -(top - bottom) / GUI_HEIGHT, 1F);
        GlStateManager.enableDepth();
        boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        GlStateManager.color(1F, 1F, 1F, 1F);
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        int titleWidth = font.getStringWidth(title);
        font.drawString(title, (GUI_WIDTH - titleWidth) / 2, 172, 0xFFFF8A00);
        if (!subtitle.isEmpty()) {
            String shown = font.trimStringToWidth(subtitle, GUI_WIDTH - 40);
            int subtitleWidth = font.getStringWidth(shown);
            font.drawString(shown, (GUI_WIDTH - subtitleWidth) / 2, 188, 0xFF9A6A3A);
        }
        GlStateManager.depthMask(depthWrite);
        GlStateManager.popMatrix();
        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.enableTexture2D();
    }

    /** Draws the real terminal GUI into the panel's local plane. */
    private static void drawDisplay(TileEntityMonitorDesk te, Screen screen, float partialTicks) {
        int screenIndex = screen.pageIndex;
        if (screenIndex < 0 || screenIndex >= TileEntityMonitorDesk.MAX_SCREENS) return;
        if (te.getScreenStage(screenIndex) == MonitorDisplay.STAGE_NOTEPAD) {
            drawNotepadDisplay(te, screen, partialTicks);
            return;
        }

        GuiMonitor[] guis = DISPLAY_GUIS.get(te.getPos());
        if (guis == null) {
            guis = new GuiMonitor[TileEntityMonitorDesk.MAX_SCREENS];
            DISPLAY_GUIS.put(te.getPos(), guis);
        }
        GuiMonitor gui = guis[screenIndex];
        boolean created = false;
        if (gui == null) {
            gui = GuiMonitor.forDisplay(te.getPos(), screenIndex);
            guis[screenIndex] = gui;
            created = true;
        }
        gui.ensureDisplaySize(GUI_WIDTH, GUI_HEIGHT);
        int[] revisions = DISPLAY_REVISIONS.get(te.getPos());
        if (revisions == null) {
            revisions = new int[TileEntityMonitorDesk.MAX_SCREENS];
            Arrays.fill(revisions, -1);
            DISPLAY_REVISIONS.put(te.getPos(), revisions);
        }
        int revision = te.getDisplayRevision();
        if (created || revisions[screenIndex] != revision) {
            configureDisplay(te, gui, screenIndex);
            revisions[screenIndex] = revision;
        }
        gui.setDisplayTicks((int) (te.getWorld().getTotalWorldTime() & 0x7FFFFFFFL));
        drawGuiIntoPanel(screen, gui, partialTicks);
    }

    /** Draws the shared notepad page that the terminal was left on. */
    private static void drawNotepadDisplay(TileEntityMonitorDesk te, Screen screen,
                                           float partialTicks) {
        int screenIndex = screen.pageIndex;
        if (screenIndex < 0 || screenIndex >= TileEntityMonitorDesk.MAX_SCREENS) return;
        GuiNotepad[] guis = NOTEPAD_DISPLAY_GUIS.get(te.getPos());
        if (guis == null) {
            guis = new GuiNotepad[TileEntityMonitorDesk.MAX_SCREENS];
            NOTEPAD_DISPLAY_GUIS.put(te.getPos(), guis);
        }
        GuiNotepad gui = guis[screenIndex];
        boolean created = false;
        if (gui == null) {
            gui = GuiNotepad.forDisplay(te.getPos(), screenIndex);
            guis[screenIndex] = gui;
            created = true;
        }
        gui.ensureDisplaySize(GUI_WIDTH, GUI_HEIGHT);
        int[] revisions = NOTEPAD_REVISIONS.get(te.getPos());
        if (revisions == null) {
            revisions = new int[TileEntityMonitorDesk.MAX_SCREENS];
            Arrays.fill(revisions, -1);
            NOTEPAD_REVISIONS.put(te.getPos(), revisions);
        }
        int revision = te.getDisplayRevision();
        if (created || revisions[screenIndex] != revision) {
            gui.setDisplayData(te.getDisplayNotepadDocuments(),
                    te.getScreenNotepadDocId(screenIndex));
            revisions[screenIndex] = revision;
        }
        gui.setDisplayTicks((int) (te.getWorld().getTotalWorldTime() & 0x7FFFFFFFL));
        drawGuiIntoPanel(screen, gui, partialTicks);
    }

    private static void drawGuiIntoPanel(Screen screen, GuiScreen gui, float partialTicks) {
        float left = screen.minX / 16F - 0.5F;
        float right = screen.maxX / 16F - 0.5F;
        float bottom = screen.minY / 16F;
        float top = screen.maxY / 16F;
        float z = screen.z / 16F - 0.5F - 0.009F;

        boolean alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);

        GlStateManager.pushMatrix();
        // The world projection stays active. Mapping the GUI's 512x384 coordinates into the
        // panel's local plane lets the normal perspective projection draw it onto the angled
        // side screens exactly like the panel itself.
        GlStateManager.translate(right, top, z);
        GlStateManager.scale(-(right - left) / GUI_WIDTH, -(top - bottom) / GUI_HEIGHT, 1F);
        // Keep depth testing on so the display is hidden by walls and the monitor body, but do
        // not write GUI depth; the panel behind it already provides the depth plane.
        GlStateManager.enableDepth();
        boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        GlStateManager.depthMask(false);
        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        gui.drawScreen(-1, -1, partialTicks);
        GlStateManager.depthMask(depthWrite);
        if (alpha) GlStateManager.enableAlpha();
        else GlStateManager.disableAlpha();
        GlStateManager.popMatrix();
        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.enableTexture2D();
    }

    /** Feeds one screen's page and synced display data into its detached GUI. */
    private static void configureDisplay(TileEntityMonitorDesk te, GuiMonitor gui,
                                         int screenIndex) {
        gui.setDisplayPasswordState(te.hasPassword());
        int stage = te.getScreenStage(screenIndex);
        int netId = te.getScreenNetId(screenIndex);
        MonitorDisplay.NetData data = netId >= 0 ? te.getDisplayNetData(netId) : null;

        switch (stage) {
            case MonitorDisplay.STAGE_SECURITY_HOME:
                gui.showDisplayHome(te.getDisplayNets());
                break;
            case MonitorDisplay.STAGE_SECURITY:
                if (data != null) {
                    gui.showDisplayNet(data.id, data.name, data.devices, data.eventMask,
                            data.keyName, te.getScreenEventsTab(screenIndex));
                } else {
                    gui.showDisplayHome(te.getDisplayNets());
                }
                break;
            case MonitorDisplay.STAGE_DELETE_NET:
                gui.showDisplayDelete(data == null ? "" : data.name);
                break;
            case MonitorDisplay.STAGE_DISPLAY:
                gui.showDisplayWallpaper(te.getScreenWallpaper(screenIndex));
                break;
            default:
                gui.showDisplayStage(stage);
                break;
        }
        gui.setDisplayScroll(te.getScreenNetsScroll(screenIndex),
                te.getScreenSecurityScroll(screenIndex),
                te.getScreenEventsScroll(screenIndex));
    }

    /** Frees the detached GUI pages when the client leaves the world. */
    public static void clearDisplayCache() {
        DISPLAY_GUIS.clear();
        NOTEPAD_DISPLAY_GUIS.clear();
        DISPLAY_REVISIONS.clear();
        NOTEPAD_REVISIONS.clear();
        SCREEN_CACHE.clear();
        displayWorld = null;
    }
}
