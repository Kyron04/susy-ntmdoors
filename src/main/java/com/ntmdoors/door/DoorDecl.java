package com.ntmdoors.door;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.util.Facings;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Describes one door type: footprint, collision for each state, timings, sounds and render animation.
 *
 * Local coordinate system (door facing NORTH, i.e. its front looks towards -Z):
 *   origin = bottom centre of the CORE cell, +X = right, +Y = up, +Z = behind the door.
 *   Footprint values are whole cells relative to the core and must contain (0,0,0).
 *   Collision boxes are in the same space, in blocks (a cell spans -0.5..0.5 on X/Z, 0..1 on Y).
 * Everything is rotated automatically for the other three facings.
 */
public class DoorDecl {

    public static final List<SoundEvent> ALL_SOUNDS = new ArrayList<>();
    private static final Map<String, SoundEvent> SOUND_CACHE = new HashMap<>();

    public final String name;
    /** Human-readable name used when the language file has no entry for this door. */
    public String displayName;
    public int minX, maxX, minY, maxY, minZ, maxZ;
    public int openTicks = 40, closeTicks = 40;
    public float hardness = 10F, resistance = 1000F;

    /*
     * HBM's door sounds are split into four roles per direction:
     *   start  - one-shot played when the movement begins
     *   loop   - repeating sound played for as long as the door moves
     *   loop2  - optional second repeating layer (the fire door's alarm)
     *   end    - one-shot played when the movement finishes
     * A null entry simply means that role is not used. Closing reuses the opening set unless a
     * separate closing set was configured.
     */
    private Sounds openSounds = Sounds.NONE;
    private Sounds closeSounds;
    /** Extra one-shot sounds fired at a fixed tick of the movement (the vault door's thuds). */
    public final List<SoundCue> soundCues = new ArrayList<>();

    /** The four sound roles for one movement direction. */
    public static final class Sounds {
        public static final Sounds NONE = new Sounds(null, null, null, null);
        public final SoundEvent start, loop, loop2, end;

        private Sounds(SoundEvent start, SoundEvent loop, SoundEvent loop2, SoundEvent end) {
            this.start = start;
            this.loop = loop;
            this.loop2 = loop2;
            this.end = end;
        }

        private Sounds withLoop2(SoundEvent loop2) {
            return new Sounds(start, loop, loop2, end);
        }
    }

    /** HBM's large doors all use a volume of two so they carry across a base. */
    public float soundVolume = 2F;

    /** One delayed one-shot sound for a door movement. */
    public static final class SoundCue {
        public final int tick;
        public final SoundEvent sound;
        public final boolean opening, closing;

        SoundCue(int tick, SoundEvent sound, boolean opening, boolean closing) {
            this.tick = Math.max(0, tick);
            this.sound = sound;
            this.opening = opening;
            this.closing = closing;
        }
    }

    public ResourceLocation model;
    public ResourceLocation texture;
    /** Optional client-side keyframe animation for models baked from an animated Collada scene. */
    public ResourceLocation keyframeAnim;
    public double modelX, modelY, modelZ;
    /** Extra yaw added after the door facing. HBM's pheo doors are authored facing EAST. */
    public float modelYaw;
    /** Optional animated-part clipping bounds in model-local X/Y/Z. Infinite means no clip. */
    public double clipMinX = Double.NEGATIVE_INFINITY, clipMaxX = Double.POSITIVE_INFINITY;
    public double clipMinY = Double.NEGATIVE_INFINITY, clipMaxY = Double.POSITIVE_INFINITY;
    public double clipMinZ = Double.NEGATIVE_INFINITY, clipMaxZ = Double.POSITIVE_INFINITY;
    /** Extra render-culling margin for doors whose moving parts leave their nominal footprint. */
    public double renderRadius;

    /** When open, the central cells report air so falling blocks and entities can pass through. */
    public boolean passFallingWhenOpen;
    /** Radius (in cells) of the pass-through square. One gives the original 3x3 silo centre. */
    public int passRadius = 1;
    /** Adjacent doors of this type along their width axis share a single open/close request. */
    public boolean linkAdjacent;

    /**
     * Whole-door skin list. Index 0 is the default texture. A door with more than one entry can be
     * cycled with the screwdriver; indexes beyond the list wrap around.
     */
    public final List<ResourceLocation> skins = new ArrayList<>();
    /**
     * Per-group skin lists, keyed by OBJ group name (for example the vault door's label). A group
     * with an entry here ignores the whole-door skin list and uses its own list instead.
     */
    public final Map<String, List<ResourceLocation>> partSkins = new LinkedHashMap<>();
    /** Optional per-group texture overrides used when a door has no skins at all. */
    public final Map<String, ResourceLocation> partTextures = new LinkedHashMap<>();
    /** Groups shown in the inventory/hand renderer. Empty means all groups. */
    public final List<String> itemGroups = new ArrayList<>();
    /** Groups actually drawn in the world. Empty means all groups. */
    public final List<String> renderGroups = new ArrayList<>();
    /** When true the clip bounds also apply to static frame groups, not just animated ones. */
    public boolean clipAllGroups;

    /**
     * Collision/selection box with an active interval in door-open fraction. Static boxes are
     * authored in door-local space; boxes with a {@code group} are authored in model space and
     * follow that OBJ group's animation.
     */
    private static final class BoxDef {
        final AxisAlignedBB box;
        final float start, end;
        final boolean collision, selection;
        /** Animated OBJ group to follow, or null for a static door-local box. */
        final String group;
        /** Slice count for rotating groups; 0 means use the door's default. */
        final int splits;

        BoxDef(AxisAlignedBB box, float start, float end, boolean collision, boolean selection) {
            this(box, start, end, collision, selection, null, 0);
        }

        BoxDef(AxisAlignedBB box, float start, float end, boolean collision, boolean selection,
               String group, int splits) {
            this.box = box;
            this.start = start;
            this.end = end;
            this.collision = collision;
            this.selection = selection;
            this.group = group;
            this.splits = splits;
        }
    }

    private final List<BoxDef> boxDefs = new ArrayList<>();
    /** Boxes that follow an animated OBJ group; evaluated with the same PartAnim steps as the renderer. */
    private final List<BoxDef> movingDefs = new ArrayList<>();
    /** Per-group model-space bounds, loaded on first use by {@link #prepareGeometry()}. */
    private Map<String, AxisAlignedBB> modelBounds;
    /** Default slice count for rotating moving boxes. */
    public int collisionSplits = 1;
    /** Cells that exist only for a track or service platform (HBM's extra dimensions). */
    private final List<BlockPos> extraCells = new ArrayList<>();
    public final Map<String, List<PartAnim>> anims = new LinkedHashMap<>();

    @SuppressWarnings("unchecked")
    private final List<BoxDef>[] rotBoxes = new List[4];
    private final BlockPos[][] cells = new BlockPos[4][];

    public DoorDecl(String name) {
        this.name = name;
        this.displayName = prettyName(name);
        this.model = new ResourceLocation(NTMDoors.MODID, "models/doors/" + name + ".obj");
        this.texture = new ResourceLocation(NTMDoors.MODID, "textures/models/doors/" + name + ".png");
    }

    /**
     * Turns an internal registry name into a readable fallback name, e.g. qe_sliding_panel -> QE Sliding Panel.
     */
    private static String prettyName(String name) {
        StringBuilder sb = new StringBuilder();
        for (String part : name.split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            if (part.length() <= 2) sb.append(part.toUpperCase(Locale.ROOT));
            else sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.length() == 0 ? name : sb.toString();
    }

    /**
     * Returns a registered sound event for a path below the mod namespace. Several doors share the
     * same HBM audio (for example garage_move), so the cache guarantees one event per asset.
     */
    public static SoundEvent sound(String path) {
        SoundEvent cached = SOUND_CACHE.get(path);
        if (cached != null) return cached;
        ResourceLocation rl = new ResourceLocation(NTMDoors.MODID, path);
        SoundEvent e = new SoundEvent(rl);
        e.setRegistryName(rl);
        SOUND_CACHE.put(path, e);
        ALL_SOUNDS.add(e);
        return e;
    }

    // ------------------------------------------------------------ builder

    public DoorDecl footprint(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        if (minX > 0 || maxX < 0 || minY > 0 || maxY < 0 || minZ > 0 || maxZ < 0)
            throw new IllegalArgumentException("Footprint of " + name + " must contain the core cell (0,0,0)");
        this.minX = minX; this.maxX = maxX;
        this.minY = minY; this.maxY = maxY;
        this.minZ = minZ; this.maxZ = maxZ;
        invalidateCells();
        return this;
    }

    public DoorDecl times(int openTicks, int closeTicks) {
        this.openTicks = openTicks;
        this.closeTicks = closeTicks;
        return this;
    }

    public DoorDecl strength(float hardness, float resistance) {
        this.hardness = hardness;
        this.resistance = resistance;
        return this;
    }

    /** Open central cells become passable for falling sand and other entities. */
    public DoorDecl passFallingWhenOpen() {
        this.passFallingWhenOpen = true;
        return this;
    }

    /** Size of the pass-through square, in cells from the core centre (1 = 3x3, 2 = 5x5). */
    public DoorDecl passRadius(int radius) {
        this.passRadius = Math.max(0, radius);
        return this;
    }

    /** Adjacent copies of this door along its width axis move as one. */
    public DoorDecl linkAdjacent() {
        this.linkAdjacent = true;
        return this;
    }

    /** A box that is part of the moving closed door. It vanishes just before the door is fully open. */
    public DoorDecl closed(AxisAlignedBB... boxes) {
        for (AxisAlignedBB b : boxes) addBox(b, -0.0001F, 0.9999F, true, true);
        return this;
    }

    /** A closed-door box that is removed once the opening animation reaches {@code end}. */
    public DoorDecl closedUntil(float end, AxisAlignedBB... boxes) {
        for (AxisAlignedBB b : boxes) addBox(b, -0.0001F, Math.min(end, 0.9999F), true, true);
        return this;
    }

    /** A residual frame box, present for the whole animation and in the open state. */
    public DoorDecl open(AxisAlignedBB... boxes) {
        for (AxisAlignedBB b : boxes) addBox(b, 0F, 1.0001F, true, true);
        return this;
    }

    /** A residual frame box that can be clicked but does not push entities around. */
    public DoorDecl openSelectionOnly(AxisAlignedBB... boxes) {
        for (AxisAlignedBB b : boxes) addBox(b, 0F, 1.0001F, false, true);
        return this;
    }

    public DoorDecl box(AxisAlignedBB box, float start, float end, boolean collision, boolean selection) {
        addBox(box, start, end, collision, selection);
        return this;
    }

    /**
     * Number of slices used to approximate a rotating part's collision box. More slices follow a
     * swinging panel more closely; 1 keeps the plain corner AABB.
     */
    public DoorDecl collisionSplits(int splits) {
        this.collisionSplits = Math.max(1, splits);
        return this;
    }

    /**
     * Adds a collision/selection box that follows an animated OBJ group. The box is the group's
     * model-space bounds, so it tracks the rendered mesh for the whole movement.
     */
    public DoorDecl movingGroup(String group) {
        return movingGroup(group, 0);
    }

    public DoorDecl movingGroup(String group, int splits) {
        addMovingBox(group, null, splits);
        return this;
    }

    /**
     * Adds a collision/selection box that follows an animated OBJ group, using explicit
     * model-space bounds instead of the group's own extents.
     */
    public DoorDecl movingBox(String group, AxisAlignedBB box) {
        return movingBox(group, box, 0);
    }

    public DoorDecl movingBox(String group, AxisAlignedBB box, int splits) {
        addMovingBox(group, box, splits);
        return this;
    }

    private void addMovingBox(String group, AxisAlignedBB box, int splits) {
        movingDefs.add(new BoxDef(box, -0.0001F, 1.0001F, true, true, group, Math.max(0, splits)));
    }

    /**
     * Adds a collision approximation of a round panel as a stack of horizontal bands. The profile
     * is read bottom-up as alternating band tops and half-widths, so a round door does not need a
     * full grid of boxes. Every band follows the same animated group.
     */
    public DoorDecl movingDisc(String group, double xMin, double xMax, double yBottom, double... profile) {
        double y = yBottom;
        for (int i = 0; i + 1 < profile.length; i += 2) {
            double top = profile[i];
            double halfWidth = Math.abs(profile[i + 1]);
            movingBox(group, new AxisAlignedBB(xMin, y, -halfWidth, xMax, top, halfWidth));
            y = top;
        }
        return this;
    }

    /**
     * Loads the model-space group bounds used by {@code movingGroup(...)}. Called once during mod
     * setup so the first collision query never has to read an OBJ file.
     */
    public synchronized void prepareGeometry() {
        if (movingDefs.isEmpty()) return;
        if (modelBounds == null) modelBounds = ObjBounds.get(model);
        for (BoxDef b : movingDefs) {
            if (b.box == null && !modelBounds.containsKey(b.group)) {
                NTMDoors.LOGGER.warn("Door {} has no OBJ group '{}' for its moving collision", name, b.group);
            }
        }
    }

    private void addBox(AxisAlignedBB box, float start, float end, boolean collision, boolean selection) {
        boxDefs.add(new BoxDef(box, start, end, collision, selection));
        Arrays.fill(rotBoxes, null);
    }

    /**
     * Adds one closed box per cell, removed bottom-to-top according to the panel travel. This is the
     * collision equivalent of a lift/roll-up door: the doorway opens at floor level first.
     */
    public DoorDecl liftCells(int minX, int maxX, int minY, int maxY,
                              double zMin, double zMax, double travel) {
        double dist = Math.max(0.001, travel);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                float end = thresholdForDistance((y + 1) / dist);
                closedUntil(end, new AxisAlignedBB(x - 0.5, y, zMin, x + 0.5, y + 1, zMax));
            }
        }
        return this;
    }

    /**
     * Converts the fraction of the travel distance at which a cell is cleared into the eased open
     * fraction. PartAnim and HBM both use smoothstep, so a linear threshold would drop collision
     * noticeably before the panel has actually left the cell.
     */
    private static float thresholdForDistance(double fraction) {
        if (fraction >= 1.0) return 0.9999F;
        double target = Math.max(0.0, fraction);
        double lo = 0.0, hi = 1.0;
        for (int i = 0; i < 24; i++) {
            double mid = (lo + hi) * 0.5;
            double e = mid * mid * (3.0 - 2.0 * mid);
            if (e < target) lo = mid;
            else hi = mid;
        }
        return (float) Math.min(0.9999, hi);
    }

    /**
     * Adds one closed box per cell, removed from the centre outwards. Used by double doors whose
     * panels retreat into the side pockets.
     */
    public DoorDecl slideCellsFromCentre(int minX, int maxX, int minY, int maxY,
                                         double zMin, double zMax, double centre, double travel,
                                         double start) {
        double dist = Math.max(0.001, travel);
        double begin = Math.max(0.0, Math.min(1.0, start));
        for (int x = minX; x <= maxX; x++) {
            // The gap grows from the centre. A cell is clear once the panel edge has travelled
            // past its far side. Any lock/hold phase before {@code start} keeps collision solid.
            double clear = Math.abs(x - centre) + 0.5;
            double progress = thresholdForDistance(clear / dist);
            float end = (float) Math.min(0.9999, begin + (1.0 - begin) * progress);
            for (int y = minY; y <= maxY; y++) {
                closedUntil(end, new AxisAlignedBB(x - 0.5, y, zMin, x + 0.5, y + 1, zMax));
            }
        }
        return this;
    }

    /**
     * Adds one closed box per cell, removed from the {@code minX} edge towards {@code maxX}.
     * {@code start} is the open fraction at which the panel begins sliding sideways; collision
     * must stay complete during any earlier pull-out phase.
     */
    public DoorDecl slideCellsFromMinX(int minX, int maxX, int minY, int maxY,
                                       double zMin, double zMax, double travel, double start) {
        double dist = Math.max(0.001, travel);
        double begin = Math.max(0.0, Math.min(1.0, start));
        for (int x = minX; x <= maxX; x++) {
            // The panel starts fully over the opening and slides towards +X. A cell is clear once
            // the panel's trailing edge has passed that cell's far side; using the centre would
            // remove collision half a block before the panel stops covering the cell.
            double clear = (x - minX) + 1.0;
            double progress = thresholdForDistance(clear / dist);
            float end = (float) Math.min(0.9999, begin + (1.0 - begin) * progress);
            for (int y = minY; y <= maxY; y++) {
                closedUntil(end, new AxisAlignedBB(x - 0.5, y, zMin, x + 0.5, y + 1, zMax));
            }
        }
        return this;
    }

    /**
     * Adds one closed box per cell, removed from the {@code maxZ} edge towards {@code minZ}.
     * The silo lid starts rotating at one third of the cycle, so the collision delay is mapped
     * into that rotation window instead of using the raw open fraction.
     */
    public DoorDecl slideCellsFromMaxZ(int minX, int maxX, int minZ, int maxZ,
                                       double yMin, double yMax) {
        double span = Math.max(1, maxZ - minZ);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                double f = (maxZ - z) / span;
                float rotationProgress = thresholdForDistance(0.12 + 0.82 * f);
                float end = (1F / 3F) + (2F / 3F) * rotationProgress;
                end = Math.min(end, 0.9999F);
                closedUntil(end, new AxisAlignedBB(x - 0.5, yMin, z - 0.5, x + 0.5, yMax, z + 0.5));
            }
        }
        return this;
    }

    /** Full-width row boxes removed bottom-to-top. Keeps huge doors cheap to collide with. */
    public DoorDecl liftRows(double xMin, double xMax, int minY, int maxY,
                             double zMin, double zMax, double travel) {
        double dist = Math.max(0.001, travel);
        for (int y = minY; y <= maxY; y++) {
            float end = thresholdForDistance((y + 1) / dist);
            closedUntil(end, new AxisAlignedBB(xMin, y, zMin, xMax, y + 1, zMax));
        }
        return this;
    }

    /** Extra dummy cells outside the rectangular footprint, such as the vault door's rear track. */
    public DoorDecl extraCell(int x, int y, int z) {
        extraCells.add(new BlockPos(x, y, z));
        invalidateCells();
        return this;
    }

    public DoorDecl extraCellsRow(int minX, int maxX, int y, int z) {
        for (int x = minX; x <= maxX; x++) extraCells.add(new BlockPos(x, y, z));
        invalidateCells();
        return this;
    }

    private void invalidateCells() {
        Arrays.fill(cells, null);
    }

    public DoorDecl anim(String part, PartAnim... steps) {
        List<PartAnim> l = anims.computeIfAbsent(part, k -> new ArrayList<>());
        for (PartAnim a : steps) l.add(a);
        return this;
    }

    public DoorDecl modelPath(String modelPath, String texturePath) {
        this.model = new ResourceLocation(NTMDoors.MODID, modelPath);
        this.texture = new ResourceLocation(NTMDoors.MODID, texturePath);
        return this;
    }

    /** Replaces only the texture, keeping the model path. Used by recoloured door variants. */
    public DoorDecl texture(String texturePath) {
        this.texture = new ResourceLocation(NTMDoors.MODID, texturePath);
        return this;
    }

    public DoorDecl keyframeAnim(String path) {
        this.keyframeAnim = new ResourceLocation(NTMDoors.MODID, path);
        return this;
    }

    public DoorDecl soundVolume(float volume) {
        this.soundVolume = Math.max(0.1F, volume);
        return this;
    }

    /** Same start/loop/end set for opening and closing. */
    public DoorDecl sounds(SoundEvent start, SoundEvent loop, SoundEvent end) {
        this.openSounds = new Sounds(start, loop, null, end);
        this.closeSounds = null;
        return this;
    }

    /** Opening-specific sounds. Closing keeps mirroring these until soundsClose is called. */
    public DoorDecl soundsOpen(SoundEvent start, SoundEvent loop, SoundEvent end) {
        this.openSounds = new Sounds(start, loop, null, end);
        return this;
    }

    /** Closing-specific sounds. */
    public DoorDecl soundsClose(SoundEvent start, SoundEvent loop, SoundEvent end) {
        this.closeSounds = new Sounds(start, loop, null, end);
        return this;
    }

    /** A second repeating layer used in both directions (HBM's fire door alarm). */
    public DoorDecl secondaryLoop(SoundEvent loop2) {
        this.openSounds = openSounds.withLoop2(loop2);
        if (closeSounds != null) this.closeSounds = closeSounds.withLoop2(loop2);
        return this;
    }

    /** Plays {@code sound} at {@code tick} of the movement, in either or both directions. */
    public DoorDecl soundCue(int tick, SoundEvent sound, boolean opening, boolean closing) {
        this.soundCues.add(new SoundCue(tick, sound, opening, closing));
        return this;
    }

    /** The sound roles for a movement direction. */
    public Sounds soundSet(boolean opening) {
        return opening || closeSounds == null ? openSounds : closeSounds;
    }

    public DoorDecl renderRadius(double radius) {
        this.renderRadius = Math.max(0, radius);
        return this;
    }

    public DoorDecl modelOffset(double x, double y, double z) {
        this.modelX = x; this.modelY = y; this.modelZ = z;
        return this;
    }

    public DoorDecl modelYaw(float degrees) {
        this.modelYaw = degrees;
        return this;
    }

    public DoorDecl clipX(double min, double max) {
        this.clipMinX = min;
        this.clipMaxX = max;
        return this;
    }

    public DoorDecl clipY(double min, double max) {
        this.clipMinY = min;
        this.clipMaxY = max;
        return this;
    }

    public DoorDecl clipZ(double min, double max) {
        this.clipMinZ = min;
        this.clipMaxZ = max;
        return this;
    }

    /** Replaces the whole-door skin list. Index 0 is the default texture. */
    public DoorDecl skins(String... texturePaths) {
        this.skins.clear();
        this.skins.addAll(locations(texturePaths));
        return this;
    }

    /** Replaces one group's skin list, independent of the whole-door skins. */
    public DoorDecl partSkin(String part, String... texturePaths) {
        this.partSkins.put(part, locations(texturePaths));
        return this;
    }

    private static List<ResourceLocation> locations(String... paths) {
        List<ResourceLocation> list = new ArrayList<>(paths.length);
        for (String path : paths) list.add(new ResourceLocation(NTMDoors.MODID, path));
        return list;
    }

    public DoorDecl partTexture(String part, String texturePath) {
        this.partTextures.put(part, new ResourceLocation(NTMDoors.MODID, texturePath));
        return this;
    }

    /** Number of selectable skins; always at least one (the default texture). */
    public int skinCount() {
        int count = skins.size();
        for (List<ResourceLocation> list : partSkins.values()) {
            count = Math.max(count, list.size());
        }
        return Math.max(1, count);
    }

    /** True when the screwdriver has an alternate skin to switch to. */
    public boolean hasSkinVariants() {
        return skinCount() > 1;
    }

    /** Wraps a skin index around a skin list; falls back to the default texture for an empty list. */
    private static ResourceLocation skinAt(List<ResourceLocation> list, int index, ResourceLocation fallback) {
        if (list == null || list.isEmpty()) return fallback;
        int i = index % list.size();
        if (i < 0) i += list.size();
        return list.get(i);
    }

    /**
     * Texture for one OBJ group. Per-group skins take priority, then the whole-door skin list,
     * then the legacy static part override, and finally the door's default texture.
     */
    public ResourceLocation textureForPart(int skinIndex, String part) {
        List<ResourceLocation> list = partSkins.get(part);
        if (list != null && !list.isEmpty()) return skinAt(list, skinIndex, texture);
        if (!skins.isEmpty()) return skinAt(skins, skinIndex, texture);
        ResourceLocation override = partTextures.get(part);
        return override == null ? texture : override;
    }

    public DoorDecl itemGroups(String... groups) {
        for (String g : groups) itemGroups.add(g);
        return this;
    }

    /** Restricts world rendering to these OBJ groups. Used by cropped door variants. */
    public DoorDecl renderGroups(String... groups) {
        for (String g : groups) renderGroups.add(g);
        return this;
    }

    /** Applies the clip bounds to static groups as well as animated ones. */
    public DoorDecl clipAllGroups() {
        this.clipAllGroups = true;
        return this;
    }

    /** Overrides the fallback display name (the language file still takes priority). */
    public DoorDecl displayName(String name) {
        this.displayName = name;
        return this;
    }

    // ------------------------------------------------------------ geometry

    /** Every cell of the door (including the core at 0,0,0 and any extra track cells), rotated for the facing. */
    public synchronized BlockPos[] getCells(EnumFacing f) {
        int i = f.getHorizontalIndex();
        if (cells[i] == null) {
            Set<BlockPos> l = new LinkedHashSet<>();
            for (int x = minX; x <= maxX; x++)
                for (int y = minY; y <= maxY; y++)
                    for (int z = minZ; z <= maxZ; z++)
                        l.add(new BlockPos((int) Math.round(Facings.worldX(x, z, f)), y,
                                (int) Math.round(Facings.worldZ(x, z, f))));
            for (BlockPos p : extraCells) {
                l.add(new BlockPos(
                        (int) Math.round(Facings.worldX(p.getX(), p.getZ(), f)), p.getY(),
                        (int) Math.round(Facings.worldZ(p.getX(), p.getZ(), f))));
            }
            cells[i] = l.toArray(new BlockPos[0]);
        }
        return cells[i];
    }

    /**
     * Collision or selection boxes active at the given open fraction, rotated for the facing and
     * relative to the core cell bottom centre. Static boxes keep their hand-authored door-local
     * shape; boxes bound to an OBJ group are transformed by the group's animation first.
     */
    public synchronized AxisAlignedBB[] getBoxes(EnumFacing f, float openFraction, boolean collision) {
        int i = f.getHorizontalIndex();
        if (rotBoxes[i] == null) {
            List<BoxDef> out = new ArrayList<>(boxDefs.size());
            for (BoxDef src : boxDefs) {
                out.add(new BoxDef(Facings.rotate(src.box, f), src.start, src.end, src.collision, src.selection));
            }
            rotBoxes[i] = out;
        }

        float t = Math.max(0F, Math.min(1F, openFraction));
        List<AxisAlignedBB> active = new ArrayList<>(rotBoxes[i].size() + movingDefs.size());
        for (BoxDef b : rotBoxes[i]) {
            if (collision ? !b.collision : !b.selection) continue;
            if (t + 0.00001F < b.start || t >= b.end) continue;
            active.add(b.box);
        }

        if (!movingDefs.isEmpty()) {
            if (modelBounds == null) modelBounds = ObjBounds.get(model);
            float yaw = Facings.yaw(f) + modelYaw;
            for (BoxDef b : movingDefs) {
                if (collision ? !b.collision : !b.selection) continue;
                if (t + 0.00001F < b.start || t >= b.end) continue;
                AxisAlignedBB src = b.box != null ? b.box : modelBounds.get(b.group);
                if (src == null) continue;
                int splits = b.splits > 0 ? b.splits : collisionSplits;
                AxisAlignedBB[] boxes = DoorGeometry.transform(anims.get(b.group), t, src, splits,
                        yaw, modelX, modelY, modelZ);
                for (AxisAlignedBB box : boxes) {
                    AxisAlignedBB clipped = clip(box);
                    if (clipped != null) active.add(clipped);
                }
            }
        }
        return active.toArray(new AxisAlignedBB[0]);
    }

    /** Intersects a transformed box with the door's clip volume; null when nothing is left. */
    @Nullable
    private AxisAlignedBB clip(AxisAlignedBB box) {
        double x1 = Math.max(box.minX, clipMinX);
        double y1 = Math.max(box.minY, clipMinY);
        double z1 = Math.max(box.minZ, clipMinZ);
        double x2 = Math.min(box.maxX, clipMaxX);
        double y2 = Math.min(box.maxY, clipMaxY);
        double z2 = Math.min(box.maxZ, clipMaxZ);
        if (x2 <= x1 || y2 <= y1 || z2 <= z1) return null;
        return new AxisAlignedBB(x1, y1, z1, x2, y2, z2);
    }
}
