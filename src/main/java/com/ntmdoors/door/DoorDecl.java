package com.ntmdoors.door;

import com.ntmdoors.NTMDoors;
import com.ntmdoors.util.Facings;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

import org.jetbrains.annotations.Nullable;
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
     * Door sounds are split into four roles per direction:
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

        public Sounds(SoundEvent start, SoundEvent loop, SoundEvent loop2, SoundEvent end) {
            this.start = start;
            this.loop = loop;
            this.loop2 = loop2;
            this.end = end;
        }

        public Sounds withLoop2(SoundEvent loop2) {
            return new Sounds(start, loop, loop2, end);
        }
    }

    /** Large doors use a volume of 2 so they carry across a base. */
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

    public Identifier model;
    public Identifier texture;
    /** Optional client-side keyframe animation for models baked from an animated Collada scene. */
    public Identifier keyframeAnim;
    public double modelX, modelY, modelZ;
    /** Extra yaw added after the door facing. Pheo doors are authored facing EAST. */
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
    public final List<Identifier> skins = new ArrayList<>();
    /**
     * Per-group skin lists, keyed by OBJ group name (for example the vault door's label). A group
     * with an entry here ignores the whole-door skin list and uses its own list instead.
     */
    public final Map<String, List<Identifier>> partSkins = new LinkedHashMap<>();
    /** Optional per-group texture overrides used when a door has no skins at all. */
    public final Map<String, Identifier> partTextures = new LinkedHashMap<>();
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
        final Box box;
        final float start, end;
        final boolean collision, selection;
        final String group;
        final String anim;
        final int splits;

        BoxDef(Box box, float start, float end, boolean collision, boolean selection) {
            this(box, start, end, collision, selection, null, null, 0);
        }

        BoxDef(Box box, float start, float end, boolean collision, boolean selection,
               String group, int splits) {
            this(box, start, end, collision, selection, group, null, splits);
        }

        BoxDef(Box box, float start, float end, boolean collision, boolean selection,
               String group, String anim, int splits) {
            this.box = box;
            this.start = start;
            this.end = end;
            this.collision = collision;
            this.selection = selection;
            this.group = group;
            this.anim = anim;
            this.splits = splits;
        }
    }

    private final List<BoxDef> boxDefs = new ArrayList<>();
    private final List<BoxDef> movingDefs = new ArrayList<>();
    private Map<String, Box> modelBounds;
    public int collisionSplits = 1;
    private final List<BlockPos> extraCells = new ArrayList<>();
    public final Map<String, List<PartAnim>> anims = new LinkedHashMap<>();

    @SuppressWarnings({"unchecked"})
    private final List<BoxDef>[] rotBoxes = new List[4];
    private final BlockPos[][] cells = new BlockPos[4][];

    public DoorDecl(String name) {
        this.name = name;
        this.displayName = prettyName(name);
        this.model = Identifier.of(NTMDoors.MOD_ID, "models/doors/" + name + ".obj");
        this.texture = Identifier.of(NTMDoors.MOD_ID, "textures/models/doors/" + name + ".png");
    }

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

    public static SoundEvent sound(String path) {
        SoundEvent cached = SOUND_CACHE.get(path);
        if (cached != null) return cached;
        Identifier rl = Identifier.of(NTMDoors.MOD_ID, path);
        SoundEvent e = SoundEvent.of(rl);
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

    public DoorDecl passFallingWhenOpen() {
        this.passFallingWhenOpen = true;
        return this;
    }

    public DoorDecl passRadius(int radius) {
        this.passRadius = Math.max(0, radius);
        return this;
    }

    public DoorDecl linkAdjacent() {
        this.linkAdjacent = true;
        return this;
    }

    public DoorDecl closedUntil(float end, Box... boxes) {
        for (Box b : boxes) addBox(b, -0.0001F, Math.min(end, 0.9999F), true, true);
        return this;
    }

    public DoorDecl open(Box... boxes) {
        for (Box b : boxes) addBox(b, 0F, 1.0001F, true, true);
        return this;
    }

    public DoorDecl openSelectionOnly(Box... boxes) {
        for (Box b : boxes) addBox(b, 0F, 1.0001F, false, true);
        return this;
    }

    public DoorDecl collisionSplits(int splits) {
        this.collisionSplits = Math.max(1, splits);
        return this;
    }

    public DoorDecl movingGroup(String group) {
        return movingGroup(group, 0);
    }

    public DoorDecl movingGroup(String group, int splits) {
        addMovingBox(group, null, null, splits);
        return this;
    }

    public DoorDecl movingBox(String group, Box box) {
        return movingBox(group, box, 0);
    }

    public DoorDecl movingBox(String group, Box box, int splits) {
        addMovingBox(group, null, box, splits);
        return this;
    }

    public DoorDecl movingBox(String group, String animGroup, Box box) {
        return movingBox(group, animGroup, box, 0);
    }

    public DoorDecl movingBox(String group, String animGroup, Box box, int splits) {
        addMovingBox(group, animGroup, box, splits);
        return this;
    }

    private void addMovingBox(String group, String animGroup, Box box, int splits) {
        movingDefs.add(new BoxDef(box, -0.0001F, 1.0001F, true, true, group, animGroup, Math.max(0, splits)));
    }

    public DoorDecl movingDisc(String group, double xMin, double xMax, double yBottom, double... profile) {
        return movingDisc(group, group, xMin, xMax, yBottom, profile);
    }

    public DoorDecl movingDisc(String boundsGroup, String animGroup, double xMin, double xMax,
                               double yBottom, double... profile) {
        double y = yBottom;
        for (int i = 0; i + 1 < profile.length; i += 2) {
            double top = profile[i];
            double halfWidth = Math.abs(profile[i + 1]);
            movingBox(boundsGroup, animGroup, new Box(xMin, y, -halfWidth, xMax, top, halfWidth));
            y = top;
        }
        return this;
    }

    public synchronized void prepareGeometry() {
        if (movingDefs.isEmpty()) return;
        if (modelBounds == null) modelBounds = ObjBounds.get(model);
        for (BoxDef b : movingDefs) {
            if (b.box == null && !modelBounds.containsKey(b.group)) {
                NTMDoors.LOGGER.warn("Door {} has no OBJ group '{}' for its moving collision", name, b.group);
            }
        }
    }

    private void addBox(Box box, float start, float end, boolean collision, boolean selection) {
        boxDefs.add(new BoxDef(box, start, end, collision, selection));
        Arrays.fill(rotBoxes, null);
    }

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

    public DoorDecl liftRows(double xMin, double xMax, int minY, int maxY,
                             double zMin, double zMax, double travel) {
        double dist = Math.max(0.001, travel);
        for (int y = minY; y <= maxY; y++) {
            float end = thresholdForDistance((y + 1) / dist);
            closedUntil(end, new Box(xMin, y, zMin, xMax, y + 1, zMax));
        }
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
        this.model = Identifier.of(NTMDoors.MOD_ID, modelPath);
        this.texture = Identifier.of(NTMDoors.MOD_ID, texturePath);
        return this;
    }

    public DoorDecl keyframeAnim(String path) {
        this.keyframeAnim = Identifier.of(NTMDoors.MOD_ID, path);
        return this;
    }

    public DoorDecl soundVolume(float volume) {
        this.soundVolume = Math.max(0.1F, volume);
        return this;
    }

    public DoorDecl sounds(SoundEvent start, SoundEvent loop, SoundEvent end) {
        this.openSounds = new Sounds(start, loop, null, end);
        this.closeSounds = null;
        return this;
    }

    public DoorDecl soundsOpen(SoundEvent start, SoundEvent loop, SoundEvent end) {
        this.openSounds = new Sounds(start, loop, null, end);
        return this;
    }

    public DoorDecl soundsClose(SoundEvent start, SoundEvent loop, SoundEvent end) {
        this.closeSounds = new Sounds(start, loop, null, end);
        return this;
    }

    public DoorDecl secondaryLoop(SoundEvent loop2) {
        this.openSounds = openSounds.withLoop2(loop2);
        if (closeSounds != null) this.closeSounds = closeSounds.withLoop2(loop2);
        return this;
    }

    public DoorDecl soundCue(int tick, SoundEvent sound, boolean opening, boolean closing) {
        this.soundCues.add(new SoundCue(tick, sound, opening, closing));
        return this;
    }

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

    public DoorDecl skins(String... texturePaths) {
        this.skins.clear();
        this.skins.addAll(locations(texturePaths));
        return this;
    }

    public DoorDecl partSkin(String part, String... texturePaths) {
        this.partSkins.put(part, locations(texturePaths));
        return this;
    }

    private static List<Identifier> locations(String... paths) {
        List<Identifier> list = new ArrayList<>(paths.length);
        for (String path : paths) list.add(Identifier.of(NTMDoors.MOD_ID, path));
        return list;
    }

    public DoorDecl partTexture(String part, String texturePath) {
        this.partTextures.put(part, Identifier.of(NTMDoors.MOD_ID, texturePath));
        return this;
    }

    public int skinCount() {
        int count = skins.size();
        for (List<Identifier> list : partSkins.values()) {
            count = Math.max(count, list.size());
        }
        return Math.max(1, count);
    }

    public boolean hasSkinVariants() {
        return skinCount() > 1;
    }

    private static Identifier skinAt(List<Identifier> list, int index, Identifier fallback) {
        if (list == null || list.isEmpty()) return fallback;
        int i = index % list.size();
        if (i < 0) i += list.size();
        return list.get(i);
    }

    public Identifier textureForPart(int skinIndex, String part) {
        List<Identifier> list = partSkins.get(part);
        if (list != null && !list.isEmpty()) return skinAt(list, skinIndex, texture);
        if (!skins.isEmpty()) return skinAt(skins, skinIndex, texture);
        Identifier override = partTextures.get(part);
        return override == null ? texture : override;
    }

    public DoorDecl itemGroups(String... groups) {
        for (String g : groups) itemGroups.add(g);
        return this;
    }

    public DoorDecl renderGroups(String... groups) {
        for (String g : groups) renderGroups.add(g);
        return this;
    }

    public DoorDecl clipAllGroups() {
        this.clipAllGroups = true;
        return this;
    }

    public DoorDecl displayName(String name) {
        this.displayName = name;
        return this;
    }

    // ------------------------------------------------------------ geometry

    public synchronized BlockPos[] getCells(Direction f) {
        int i = (f != null && f.getAxis().isHorizontal()) ? f.getHorizontal() : 0;
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

    public Box[] getBoxes(Direction f, float openFraction, boolean collision) {
        return getBoxes(f, openFraction, collision, true);
    }

    public synchronized Box[] getBoxes(Direction f, float openFraction, boolean collision,
                                       boolean includeMoving) {
        return getBoxes(f, openFraction, collision, true, includeMoving);
    }

    public synchronized Box[] getMovingBoxes(Direction f, float openFraction, boolean collision) {
        return getBoxes(f, openFraction, collision, false, true);
    }

    private synchronized Box[] getBoxes(Direction f, float openFraction, boolean collision,
                                        boolean includeStatic, boolean includeMoving) {
        int i = (f != null && f.getAxis().isHorizontal()) ? f.getHorizontal() : 0;
        if (rotBoxes[i] == null) {
            List<BoxDef> out = new ArrayList<>(boxDefs.size());
            for (BoxDef src : boxDefs) {
                out.add(new BoxDef(Facings.rotate(src.box, f), src.start, src.end, src.collision, src.selection));
            }
            rotBoxes[i] = out;
        }

        float t = Math.max(0F, Math.min(1F, openFraction));
        List<Box> active = new ArrayList<>(
                (includeStatic ? rotBoxes[i].size() : 0) + (includeMoving ? movingDefs.size() : 0));
        if (includeStatic) {
            for (BoxDef b : rotBoxes[i]) {
                if (collision ? !b.collision : !b.selection) continue;
                if (t + 0.00001F < b.start || t >= b.end) continue;
                active.add(b.box);
            }
        }

        if (includeMoving && !movingDefs.isEmpty()) {
            if (modelBounds == null) modelBounds = ObjBounds.get(model);
            float yaw = Facings.yaw(f) + modelYaw;
            Box clip = clipVolume();
            for (BoxDef b : movingDefs) {
                if (collision ? !b.collision : !b.selection) continue;
                if (t + 0.00001F < b.start || t >= b.end) continue;
                Box src = b.box != null ? b.box : modelBounds.get(b.group);
                if (src == null) continue;
                int splits = b.splits > 0 ? b.splits : collisionSplits;
                Box[] boxes = DoorGeometry.transform(
                        anims.get(b.anim != null ? b.anim : b.group), t, src, splits,
                        yaw, modelX, modelY, modelZ, clip);
                for (Box box : boxes) active.add(box);
            }
        }
        return active.toArray(new Box[0]);
    }

    @Nullable
    private Box clipVolume() {
        if (Double.isInfinite(clipMinX) && Double.isInfinite(clipMinY) && Double.isInfinite(clipMinZ)
                && Double.isInfinite(clipMaxX) && Double.isInfinite(clipMaxY) && Double.isInfinite(clipMaxZ)) {
            return null;
        }
        return new Box(clipMinX, clipMinY, clipMinZ, clipMaxX, clipMaxY, clipMaxZ);
    }
}
