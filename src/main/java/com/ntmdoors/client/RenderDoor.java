package com.ntmdoors.client;

import com.ntmdoors.block.BlockDoorGeneric;
import com.ntmdoors.door.DoorDecl;
import com.ntmdoors.door.PartAnim;
import com.ntmdoors.tile.TileEntityDoorGeneric;
import com.ntmdoors.util.Facings;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.entity.Entity;
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
        // Glass doors do not seal light, so the dispatcher's per-cell lightmap is already correct.
        // Only sealed doors need the room-side override.
        int previousLight = d.sealsLight ? useRoomLight(te, facing) : -1;

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

        drawParts(d, model, open, null, true, te.getSkinIndex());

        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.disableRescaleNormal();
        GlStateManager.popMatrix();
        if (previousLight >= 0) restoreLight(previousLight);
    }

    /**
     * A light-sealed door makes its own cells opaque, so the lightmap the dispatcher picked for the
     * core cell is black. Use the light of the room the camera stands in instead: the visible face
     * of a door is always the one facing the viewer, so that is the light level the door should
     * show. Doors never inherit the brightness of the room on their far side.
     */
    private static int useRoomLight(TileEntityDoorGeneric te, EnumFacing facing) {
        World world = te.getWorld();
        BlockPos pos = te.getPos();
        int original = world.getCombinedLight(pos, 0);
        EnumFacing side = cameraSide(pos, te.getDecl(), facing);
        int packed = side == null ? 0 : roomLight(world, pos, side);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,
                (float) (packed & 0xFFFF), (float) (packed >>> 16));
        return original;
    }

    /**
     * The visible side of the door from the render camera's position. Vertical doors are split by
     * their facing plane; horizontal doors (the silo hatches) by their height.
     */
    @Nullable
    private static EnumFacing cameraSide(BlockPos pos, DoorDecl decl, EnumFacing facing) {
        Minecraft mc = Minecraft.getMinecraft();
        RenderManager rm = mc.getRenderManager();
        if (decl != null && decl.minY == decl.maxY) {
            double eye = rm.viewerPosY;
            Entity view = mc.getRenderViewEntity();
            if (view != null) eye += view.getEyeHeight();
            return eye >= pos.getY() + 0.5 ? EnumFacing.UP : EnumFacing.DOWN;
        }
        Vec3i normal = facing.getDirectionVec();
        double dot = (rm.viewerPosX - pos.getX() - 0.5) * normal.getX()
                + (rm.viewerPosZ - pos.getZ() - 0.5) * normal.getZ();
        return dot >= 0 ? facing : facing.getOpposite();
    }

    /**
     * Samples the first unblocked cell on the camera's side of the door. Stepping outwards (rather
     * than falling back to all six neighbours) is what stops a single block placed against the
     * door from blacking out the whole model; it also keeps thin doors from borrowing light that
     * wrapped around their edge from the far side.
     */
    private static int roomLight(World world, BlockPos pos, EnumFacing side) {
        Vec3i step = side.getDirectionVec();
        for (int distance = 1; distance <= 4; distance++) {
            BlockPos candidate = pos.add(step.getX() * distance, step.getY() * distance, step.getZ() * distance);
            if (!world.isBlockLoaded(candidate)) break;
            if (world.getBlockState(candidate).getLightOpacity(world, candidate) == 0) {
                return world.getCombinedLight(candidate, 0);
            }
        }
        return 0;
    }

    private static void restoreLight(int packed) {
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,
                (float) (packed & 0xFFFF), (float) (packed >>> 16));
    }

    /**
     * Draws the requested groups. When {@code clip} is true, animated groups are clipped to the
     * model-local bounds in the DoorDecl. HBM uses this to hide sliding panels inside their frame.
     */
    static void drawParts(DoorDecl d, ObjModel model, float open, Collection<String> only, boolean clip, int skin) {
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
            Minecraft.getMinecraft().getTextureManager().bindTexture(d.textureForPart(skin, group));

            // Static groups normally ignore the clip bounds, because most doors only need to hide
            // their moving panel. Cropped variants such as the single-column QE door also need to
            // clip their frame so it does not extend across the neighbouring cell.
            boolean clipGroup = steps != null || keyframe != null || d.clipAllGroups;
            if (clipGroup) {
                for (ClipPlane plane : planes) GL11.glEnable(plane.id);
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
            model.draw(group);
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
