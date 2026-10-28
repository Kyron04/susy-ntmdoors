package com.ntmdoors.client;

import com.ntmdoors.block.BlockDoorGeneric;
import com.ntmdoors.door.DoorDecl;
import com.ntmdoors.door.PartAnim;
import com.ntmdoors.tile.TileEntityDoorGeneric;
import com.ntmdoors.util.Facings;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.DoubleBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Draws the OBJ model of a door at its core cell and animates the groups listed in the door's DoorDecl. */
@SideOnly(Side.CLIENT)
public class RenderDoor extends TileEntitySpecialRenderer<TileEntityDoorGeneric> {

    private static final DoubleBuffer CLIP_BUFFER = BufferUtils.createDoubleBuffer(4);
    private static final ClipPlane[] NO_PLANES = new ClipPlane[0];
    private static final Map<DoorDecl, ClipPlane[]> CLIP_PLANES = new IdentityHashMap<>();

    /**
     * Doors are multiblocks, and large ones (the transition seal is 27x24) extend far beyond the
     * chunk that owns their core. Vanilla only visits the tile entities of chunks inside the camera
     * frustum, so when the player stands next to the door and looks away from the core chunk the
     * whole model vanishes even though most of it is still on screen. Registering the core as a
     * global renderer makes Forge test the door's own (generous) render box instead of the chunk
     * it happens to be anchored in.
     */
    @Override
    public boolean isGlobalRenderer(TileEntityDoorGeneric te) {
        return true;
    }

    @Override
    public void render(TileEntityDoorGeneric te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        DoorDecl d = te.getDecl();
        if (d == null || d.model == null) return;
        ObjModel model = ObjModel.get(d.model);
        if (model == null) return;

        IBlockState state = te.getWorld().getBlockState(te.getPos());
        if (!(state.getBlock() instanceof BlockDoorGeneric)) return;
        EnumFacing facing = state.getValue(BlockDoorGeneric.FACING);

        float open = te.getOpenFraction(partialTicks);
        // Glass doors are not sealed and keep the normal per-cell lightmap. Everything else gets a
        // lightmap coordinate per vertex so the light can vary across the model.
        DoorLight light = d.sealsLight ? lightFor(te, facing, d) : null;
        ObjModel.Transform base = light == null ? null : baseTransform(te.getPos(), facing, d);

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y, z + 0.5);          // bottom centre of the core cell
        GlStateManager.rotate(Facings.yaw(facing) + d.modelYaw, 0F, 1F, 0F);
        GlStateManager.translate(d.modelX, d.modelY, d.modelZ);

        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        // HBM doors are single-sided in a few places; culling them makes them look invisible
        // from one side, which is exactly the symptom we want to avoid.
        GlStateManager.disableCull();

        try {
            drawParts(d, model, open, null, true, te.getSkinIndex(), light, base);
        } finally {
            if (light != null) light.release();
        }

        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.disableRescaleNormal();
        GlStateManager.popMatrix();
    }

    /** World-space transform of the core cell, matching the GL matrix stack in {@link #render}. */
    private static ObjModel.Transform baseTransform(BlockPos pos, EnumFacing facing, DoorDecl d) {
        ObjModel.Transform t = new ObjModel.Transform();
        t.translate(pos.getX() + 0.5F, pos.getY(), pos.getZ() + 0.5F);
        t.rotate(Facings.yaw(facing) + d.modelYaw, 0F, 1F, 0F);
        t.translate((float) d.modelX, (float) d.modelY, (float) d.modelZ);
        return t;
    }

    /**
     * Chooses the side of the door the camera is on. The visible face of a sealed door is always
     * the one facing the viewer, so both the panel and its frame should be lit by the room on that
     * side rather than by whichever side happens to be brighter.
     */
    private static DoorLight lightFor(TileEntityDoorGeneric te, EnumFacing facing, DoorDecl d) {
        BlockPos pos = te.getPos();
        RenderManager rm = Minecraft.getMinecraft().getRenderManager();
        if (d.minY == d.maxY) {
            // Horizontal hatches: sample above or below the lid.
            return new DoorLight(te.getWorld(), rm.viewerPosX, rm.viewerPosY, rm.viewerPosZ,
                    0F, rm.viewerPosY >= pos.getY() + 0.5 ? 1F : -1F, 0F);
        }
        Vec3i normal = facing.getDirectionVec();
        double dot = (rm.viewerPosX - pos.getX() - 0.5) * normal.getX()
                + (rm.viewerPosZ - pos.getZ() - 0.5) * normal.getZ();
        Vec3i side = (dot >= 0 ? facing : facing.getOpposite()).getDirectionVec();
        return new DoorLight(te.getWorld(), rm.viewerPosX, rm.viewerPosY, rm.viewerPosZ,
                side.getX(), side.getY(), side.getZ());
    }

    /**
     * Per-vertex world light for a sealed door. The door's own cells are opaque, so each vertex is
     * sampled just outside the panel on the camera's side. Interpolating those values across the
     * triangles is what makes one side of a closed door follow its room's light level while the
     * other side stays dark, with no hard seam across the texture.
     */
    static final class DoorLight implements ObjModel.VertexLight {

        private final World world;
        private final double cx, cy, cz;
        private final float ox, oy, oz;
        private final Map<Long, Integer> cache = new HashMap<>();
        private final Map<Long, Integer> normalCache = new HashMap<>();
        private final BlockPos.PooledMutableBlockPos sample = BlockPos.PooledMutableBlockPos.retain();

        private DoorLight(World world, double cx, double cy, double cz, float ox, float oy, float oz) {
            this.world = world;
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
        }

        @Override
        public int lightAt(double x, double y, double z, float nx, float ny, float nz) {
            // Step out of the panel towards the camera's room. One cell usually reaches the
            // adjacent room; a very thick part, a wall or a deep frame may need more.
            int bx = 0, by = 0, bz = 0;
            for (int attempt = 0; attempt < 3; attempt++) {
                double distance = 0.55 + attempt * 0.75;
                bx = (int) Math.floor(x + ox * distance);
                by = Math.max(0, Math.min(255, (int) Math.floor(y + oy * distance)));
                bz = (int) Math.floor(z + oz * distance);
                sample.setPos(bx, by, bz);
                if (world.getBlockState(sample).getLightOpacity(world, sample) == 0) break;
            }

            sample.setPos(bx, by, bz);
            long cellKey = sample.toLong();
            int block, sky;
            Integer cached = cache.get(cellKey);
            if (cached != null) {
                block = cached & 0xFFFF;
                sky = cached >>> 16;
            } else {
                // Start with the cell just outside the surface, then take the brightest light in
                // its immediate neighbourhood that stays on the same side of the door plane. This
                // removes the black patches caused by a single vertex landing in a wall, a door
                // cell or a shadowed gap, without ever sampling through to the other side.
                int packed = world.getCombinedLight(sample, 0);
                block = packed & 0xFFFF;
                sky = packed >>> 16;

                if (oy == 0F) {
                    // Vertical door: move vertically and along the door's width, never through it.
                    int tx = (int) -oz;
                    int tz = (int) ox;
                    block = Math.max(block, sampleComponent(bx, by - 1, bz, true));
                    sky = Math.max(sky, sampleComponent(bx, by - 1, bz, false));
                    block = Math.max(block, sampleComponent(bx, by + 1, bz, true));
                    sky = Math.max(sky, sampleComponent(bx, by + 1, bz, false));
                    block = Math.max(block, sampleComponent(bx + tx, by, bz + tz, true));
                    sky = Math.max(sky, sampleComponent(bx + tx, by, bz + tz, false));
                    block = Math.max(block, sampleComponent(bx - tx, by, bz - tz, true));
                    sky = Math.max(sky, sampleComponent(bx - tx, by, bz - tz, false));
                } else {
                    // Horizontal hatch: move across the lid, never above/below it.
                    block = Math.max(block, sampleComponent(bx - 1, by, bz, true));
                    sky = Math.max(sky, sampleComponent(bx - 1, by, bz, false));
                    block = Math.max(block, sampleComponent(bx + 1, by, bz, true));
                    sky = Math.max(sky, sampleComponent(bx + 1, by, bz, false));
                    block = Math.max(block, sampleComponent(bx, by, bz - 1, true));
                    sky = Math.max(sky, sampleComponent(bx, by, bz - 1, false));
                    block = Math.max(block, sampleComponent(bx, by, bz + 1, true));
                    sky = Math.max(sky, sampleComponent(bx, by, bz + 1, false));
                }
                cache.put(cellKey, block | (sky << 16));
            }

            // If the visible face is still almost unlit, the fixed side is not the right local
            // side for this part (deep frames and tracks do this). Try the real surface normal,
            // oriented towards the camera, and keep the brighter result. Only dark vertices take
            // this path, so lit faces keep their smooth room-side gradient.
            if ((block >>> 4) + (sky >>> 4) <= 2) {
                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (len > 1.0E-4F) {
                    float ux = nx / len;
                    float uy = ny / len;
                    float uz = nz / len;
                    if (ux * (cx - x) + uy * (cy - y) + uz * (cz - z) < 0) {
                        ux = -ux;
                        uy = -uy;
                        uz = -uz;
                    }
                    int nk = (Math.round(ux * 8F) & 0xFF)
                            | ((Math.round(uy * 8F) & 0xFF) << 8)
                            | ((Math.round(uz * 8F) & 0xFF) << 16);
                    long normalKey = cellKey * 31L + nk;
                    Integer normalCached = normalCache.get(normalKey);
                    int normalPacked;
                    if (normalCached != null) {
                        normalPacked = normalCached;
                    } else {
                        normalPacked = sampleDirection(x, y, z, ux, uy, uz);
                        normalCache.put(normalKey, normalPacked);
                    }
                    if (normalPacked >= 0) {
                        block = Math.max(block, normalPacked & 0xFFFF);
                        sky = Math.max(sky, normalPacked >>> 16);
                    }
                }
            }
            return block | (sky << 16);
        }

        /** Samples outwards along an arbitrary direction; -1 when no clear cell is found. */
        private int sampleDirection(double x, double y, double z, float dx, float dy, float dz) {
            for (int attempt = 0; attempt < 3; attempt++) {
                double distance = 0.55 + attempt * 0.75;
                sample.setPos(
                        (int) Math.floor(x + dx * distance),
                        Math.max(0, Math.min(255, (int) Math.floor(y + dy * distance))),
                        (int) Math.floor(z + dz * distance));
                if (world.getBlockState(sample).getLightOpacity(world, sample) == 0) {
                    return world.getCombinedLight(sample, 0);
                }
            }
            return -1;
        }

        /** One light component of a neighbour cell, or -1 when the cell is missing/opaque. */
        private int sampleComponent(int x, int y, int z, boolean blockLight) {
            if (y < 0 || y > 255) return -1;
            sample.setPos(x, y, z);
            IBlockState state = world.getBlockState(sample);
            if (state.getLightOpacity(world, sample) != 0) return -1;
            int packed = world.getCombinedLight(sample, 0);
            return blockLight ? packed & 0xFFFF : packed >>> 16;
        }

        private void release() {
            sample.release();
        }
    }

    /**
     * Draws the requested groups. When {@code clip} is true, animated groups are clipped to the
     * model-local bounds in the DoorDecl. HBM uses this to hide sliding panels inside their frame.
     *
     * <p>When {@code light} is non-null the model is drawn vertex by vertex instead of through its
     * display list, because a baked lightmap coordinate cannot vary across the surface.</p>
     */
    static void drawParts(DoorDecl d, ObjModel model, float open, Collection<String> only, boolean clip, int skin,
                          @Nullable DoorLight light, @Nullable ObjModel.Transform base) {
        ClipPlane[] planes = clip ? clipPlanes(d) : NO_PLANES;
        // These planes are fixed in model space. They must be specified before the animated
        // group matrix is pushed, otherwise they would follow the sliding panel instead of the frame.
        for (ClipPlane plane : planes) plane.apply();

        Map<String, KeyframeAnimation> keyframes = d.keyframeAnim == null
                ? Collections.emptyMap() : KeyframeAnimation.get(d.keyframeAnim);

        for (String group : model.groups()) {
            if (only != null) {
                if (!only.contains(group)) continue;
            } else if (!d.renderGroups.isEmpty() && !d.renderGroups.contains(group)) {
                continue;
            }

            List<PartAnim> steps = d.anims.get(group);
            KeyframeAnimation keyframe = keyframes.get(group);
            net.minecraft.client.Minecraft.getMinecraft().getTextureManager().bindTexture(d.textureForPart(skin, group));

            // Static groups normally ignore the clip bounds, because most doors only need to hide
            // their moving panel. Cropped variants such as the single-column QE door also need to
            // clip their frame so it does not extend across the neighbouring cell.
            boolean clipGroup = steps != null || keyframe != null || d.clipAllGroups;
            if (clipGroup) {
                for (ClipPlane plane : planes) GL11.glEnable(plane.id);
            }

            ObjModel.Transform transform = null;
            if (light != null && base != null) {
                transform = new ObjModel.Transform(base);
                if (keyframe != null) {
                    float[] matrix = new float[16];
                    keyframe.getMatrix(open, matrix);
                    transform.multiply(matrix);
                } else if (steps != null) {
                    for (PartAnim a : steps) {
                        float p = a.progress(open);
                        if (a.type == PartAnim.Type.TRANSLATE) {
                            transform.translate((float) (a.x * p), (float) (a.y * p), (float) (a.z * p));
                        } else {
                            transform.translate((float) a.px, (float) a.py, (float) a.pz);
                            transform.rotate((float) (a.angle * p), (float) a.x, (float) a.y, (float) a.z);
                            transform.translate((float) -a.px, (float) -a.py, (float) -a.pz);
                        }
                    }
                }
            }

            GlStateManager.pushMatrix();
            if (keyframe != null) {
                keyframe.apply(open);
            } else if (steps != null) {
                for (PartAnim a : steps) {
                    float p = a.progress(open);
                    if (a.type == PartAnim.Type.TRANSLATE) {
                        GlStateManager.translate(a.x * p, a.y * p, a.z * p);
                    } else {
                        GlStateManager.translate(a.px, a.py, a.pz);
                        GlStateManager.rotate((float) (a.angle * p), (float) a.x, (float) a.y, (float) a.z);
                        GlStateManager.translate(-a.px, -a.py, -a.pz);
                    }
                }
            }
            if (transform != null) {
                model.drawLit(group, light, transform);
            } else {
                model.draw(group);
            }
            GlStateManager.popMatrix();

            if (clipGroup) {
                for (ClipPlane plane : planes) GL11.glDisable(plane.id);
            }
        }
    }

    /** Model-local clipping planes configured by the door, cached because decls are immutable after setup. */
    private static ClipPlane[] clipPlanes(DoorDecl d) {
        ClipPlane[] cached = CLIP_PLANES.get(d);
        if (cached != null) return cached;
        List<ClipPlane> planes = new ArrayList<>(6);
        if (!Double.isInfinite(d.clipMinX)) planes.add(new ClipPlane(GL11.GL_CLIP_PLANE0, 1, 0, 0, -d.clipMinX));
        if (!Double.isInfinite(d.clipMaxX)) planes.add(new ClipPlane(GL11.GL_CLIP_PLANE1, -1, 0, 0, d.clipMaxX));
        if (!Double.isInfinite(d.clipMinY)) planes.add(new ClipPlane(GL11.GL_CLIP_PLANE2, 0, 1, 0, -d.clipMinY));
        if (!Double.isInfinite(d.clipMaxY)) planes.add(new ClipPlane(GL11.GL_CLIP_PLANE3, 0, -1, 0, d.clipMaxY));
        if (!Double.isInfinite(d.clipMinZ)) planes.add(new ClipPlane(GL11.GL_CLIP_PLANE4, 0, 0, 1, -d.clipMinZ));
        if (!Double.isInfinite(d.clipMaxZ)) planes.add(new ClipPlane(GL11.GL_CLIP_PLANE5, 0, 0, -1, d.clipMaxZ));
        ClipPlane[] result = planes.toArray(NO_PLANES);
        CLIP_PLANES.put(d, result);
        return result;
    }

    private static final class ClipPlane {
        final int id;
        final double a, b, c, d;

        ClipPlane(int id, double a, double b, double c, double d) {
            this.id = id;
            this.a = a;
            this.b = b;
            this.c = c;
            this.d = d;
        }

        void apply() {
            clipPlane(id, a, b, c, d);
        }
    }

    private static void clipPlane(int plane, double a, double b, double c, double d) {
        CLIP_BUFFER.clear();
        CLIP_BUFFER.put(a).put(b).put(c).put(d);
        CLIP_BUFFER.flip();
        GL11.glClipPlane(plane, CLIP_BUFFER);
    }
}
