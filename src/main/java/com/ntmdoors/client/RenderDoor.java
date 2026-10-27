package com.ntmdoors.client;

import com.ntmdoors.block.BlockDoorGeneric;
import com.ntmdoors.door.DoorDecl;
import com.ntmdoors.door.PartAnim;
import com.ntmdoors.tile.TileEntityDoorGeneric;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
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

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y, z + 0.5);          // bottom centre of the core cell
        GlStateManager.rotate(yaw(facing) + d.modelYaw, 0F, 1F, 0F);
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

    /** Yaw that turns the local NORTH-facing door into the given facing. GL yaw is counter-clockwise from above. */
    static float yaw(EnumFacing f) {
        switch (f) {
            case EAST: return -90F;
            case SOUTH: return 180F;
            case WEST: return 90F;
            default: return 0F;
        }
    }
}
