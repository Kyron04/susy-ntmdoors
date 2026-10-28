package com.ntmdoors.door;

import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * All door types. Adding a door = adding one more entry here (plus a lang line and assets).
 * Every entry is exposed as two blocks by ModContent: the normal door and a redstone-only
 * {@code <name>_locked} twin.
 *
 * Footprints, collision boxes, animations and sounds follow the original HBM definitions. The OBJ
 * models and textures in assets/ntmdoors are the HBM assets, used with the LGPL-3.0 licence
 * included in the project root.
 *
 * Local space (door facing NORTH):
 *   origin = bottom centre of the CORE cell, +X = right, +Y = up, +Z = behind the door.
 */
public final class DoorDecls {

    private static final List<DoorDecl> LIST = new ArrayList<>();
    public static final List<DoorDecl> ALL = Collections.unmodifiableList(LIST);

    /*
     * The shared HBM audio library. Several doors use the same recording, so these events are
     * resolved through DoorDecl.sound() and registered only once.
     */
    private static final SoundEvent SND_VAULT_SCRAPE = DoorDecl.sound("door_vault_scrape");
    private static final SoundEvent SND_VAULT_THUD = DoorDecl.sound("door_vault_thud");
    private static final SoundEvent SND_SLIDING_OPENING = DoorDecl.sound("door_sliding_opening");
    private static final SoundEvent SND_SLIDING_OPENED = DoorDecl.sound("door_sliding_opened");
    private static final SoundEvent SND_SLIDING_SHUT = DoorDecl.sound("door_sliding_shut");
    private static final SoundEvent SND_WGH_START = DoorDecl.sound("door_wgh_start");
    private static final SoundEvent SND_WGH_STOP = DoorDecl.sound("door_wgh_stop");
    private static final SoundEvent SND_WGH_BIG_START = DoorDecl.sound("door_wgh_big_start");
    private static final SoundEvent SND_WGH_BIG_STOP = DoorDecl.sound("door_wgh_big_stop");
    private static final SoundEvent SND_GARAGE_MOVE = DoorDecl.sound("door_garage_move");
    private static final SoundEvent SND_GARAGE_STOP = DoorDecl.sound("door_garage_stop");
    private static final SoundEvent SND_SEAL_OPEN = DoorDecl.sound("door_seal_open");
    private static final SoundEvent SND_SEAL_STOP = DoorDecl.sound("door_seal_stop");
    private static final SoundEvent SND_TRANSITION_SEAL = DoorDecl.sound("door_transition_seal");
    private static final SoundEvent SND_ALARM = DoorDecl.sound("door_alarm");
    private static final SoundEvent SND_LEVER = DoorDecl.sound("door_lever");

    private static DoorDecl reg(DoorDecl d) {
        LIST.add(d);
        return d;
    }

    private static AxisAlignedBB box(double x1, double y1, double z1, double x2, double y2, double z2) {
        return new AxisAlignedBB(x1, y1, z1, x2, y2, z2);
    }

    /** 5x5 roll-up vault door. The frame track deliberately extends to one side, just like HBM. */
    public static final DoorDecl VAULT_DOOR = reg(new DoorDecl("vault_door")
            .footprint(-2, 2, 0, 4, 0, 0).times(120, 120).strength(30F, 5000F)
            // The panel is a round gear plate. A single rotated AABB balloons into a diamond, so the
            // collision follows the disc's silhouette as horizontal bands. The rotation is about the
            // disc's centre, which does not change that silhouette, so the collision only needs the
            // pull-out and the sideways roll. "VaultPanel" is an animation key only; the box bounds
            // are read from the model's Door group.
            .anim("VaultPanel",
                    PartAnim.translate(-1.0, 0, 0, 0F, 1F / 3F),
                    PartAnim.translate(0, 0, 5.0, 1F / 3F, 1F))
            .movingDisc("Door", "VaultPanel", -0.5, 0.4375, 0.396711,
                    0.728, 0.50,
                    1.20, 0.91,
                    1.40, 1.21,
                    2.60, 1.70,
                    3.15, 2.08,
                    4.05, 1.41,
                    4.35, 1.20,
                    4.64728, 0.26)
            // The frame is a 5x5 slab with a gear-shaped opening. Its track is a low rail: two
            // cells deep under the frame, then one cell deep as it runs 4.5 blocks to the right
            // (door-local +X). The front row past the frame is open air, so it gets no collision.
            .open(box(-2.5, 0, -0.5, 2.5, 0.728, 1.5))
            .open(box(2.5, 0, 0.5, 6.5, 0.728, 1.5))
            // The frame stays solid while the panel rolls away. Each band follows the opening's
            // silhouette (measured from the OBJ), so the thick upper corners are not left hollow.
            .open(box(-2.5, 0.728, -0.5, -0.91, 1.20, 0.5), box(0.91, 0.728, -0.5, 2.5, 1.20, 0.5))
            .open(box(-2.5, 1.20, -0.5, -1.21, 1.40, 0.5), box(1.21, 1.20, -0.5, 2.5, 1.40, 0.5))
            .open(box(-2.5, 1.40, -0.5, -1.70, 2.60, 0.5), box(1.70, 1.40, -0.5, 2.5, 2.60, 0.5))
            .open(box(-2.5, 2.60, -0.5, -2.08, 3.15, 0.5), box(2.08, 2.60, -0.5, 2.5, 3.15, 0.5))
            .open(box(-2.5, 3.15, -0.5, -1.41, 4.05, 0.5), box(1.41, 3.15, -0.5, 2.5, 4.05, 0.5))
            .open(box(-2.5, 4.05, -0.5, -1.20, 4.35, 0.5), box(1.20, 4.05, -0.5, 2.5, 4.35, 0.5))
            .open(box(-2.5, 4.35, -0.5, -0.26, 4.65, 0.5), box(0.26, 4.35, -0.5, 2.5, 4.65, 0.5))
            // The lintel closes the top of the frame above the opening.
            .open(box(-2.5, 4.65, -0.5, 2.5, 5, 0.5))
            // The rear rail needs its own cells so blocks cannot be placed inside it and the whole
            // structure is removed together. The front row only exists under the frame, so it stays
            // part of the normal footprint.
            .extraCellsRow(-2, 6, 0, 1)
            .modelPath("models/doors/vault_door.obj", "textures/models/doors/vault_door.png")
            .modelYaw(90F)
            // HBM's seven Vault-Tec skins: three door plates and four label variants. The label
            // group has its own list so the plate and label can change independently.
            .skins("textures/models/doors/vault_door.png",
                    "textures/models/doors/vault_door.png",
                    "textures/models/doors/vault_door.png",
                    "textures/models/doors/vault_door_4.png",
                    "textures/models/doors/vault_door_4.png",
                    "textures/models/doors/vault_door_s.png",
                    "textures/models/doors/vault_door_s.png")
            .partSkin("Label",
                    "textures/models/doors/vault_door_label.png",
                    "textures/models/doors/vault_door_label_87.png",
                    "textures/models/doors/vault_door_label_106.png",
                    "textures/models/doors/vault_door_label_81.png",
                    "textures/models/doors/vault_door_label_111.png",
                    "textures/models/doors/vault_door_label_2.png",
                    "textures/models/doors/vault_door_label_99.png")
            .itemGroups("Door", "Label")
            // HBM pulls the door one block out of its seat, rolls it five blocks sideways
            // along the extended track, and spins it by 360 * 5 / (4.25 * pi) degrees.
            .anim("Door",
                    PartAnim.translate(-1.0, 0, 0, 0F, 1F / 3F),
                    PartAnim.translate(0, 0, 5.0, 1F / 3F, 1F),
                    PartAnim.rotate(134.8, 1, 0, 0, 0, 2.5, 0, 1F / 3F, 1F))
            .anim("Label",
                    PartAnim.translate(-1.0, 0, 0, 0F, 1F / 3F),
                    PartAnim.translate(0, 0, 5.0, 1F / 3F, 1F),
                    PartAnim.rotate(134.8, 1, 0, 0, 0, 2.5, 0, 1F / 3F, 1F))
            // HBM plays a scrape at the start of each movement and a thud every ten ticks
            // while the panel is rolling (open and close alike). There is no loop or end sound.
            .soundCue(0, SND_VAULT_SCRAPE, true, false)
            .soundCue(30, SND_VAULT_SCRAPE, false, true)
            .soundCue(45, SND_VAULT_THUD, true, true)
            .soundCue(55, SND_VAULT_THUD, true, true)
            .soundCue(65, SND_VAULT_THUD, true, true)
            .soundCue(75, SND_VAULT_THUD, true, true)
            .soundCue(85, SND_VAULT_THUD, true, true)
            .soundCue(95, SND_VAULT_THUD, true, true)
            .soundCue(105, SND_VAULT_THUD, true, true)
            .soundCue(115, SND_VAULT_THUD, true, true));

    /** 7x4 double blast door; two panels slide apart into the side frames. */
    public static final DoorDecl SLIDING_BLAST_DOOR = reg(new DoorDecl("sliding_blast_door")
            .footprint(-3, 3, 0, 3, 0, 0).times(24, 24).strength(25F, 4000F)
            // Both panels follow their model translations; the frame boxes stay static.
            .movingGroup("LeftDoor")
            .movingGroup("RightDoor")
            .open(box(-3.5, 0, -0.5, -2.5, 4, 0.5), box(2.5, 0, -0.5, 3.5, 4, 0.5),
                    box(-2.5, 3, -0.5, 2.5, 4, 0.5))
            .openSelectionOnly(box(-3.5, 0, -0.5, 3.5, 0.08, 0.5))
            .modelPath("models/doors/sliding_blast_door.obj", "textures/models/doors/sliding_blast_door.png")
            .modelYaw(90F)
            .clipZ(-2.5, 2.5)
            // Default light-grey paint plus black and dark-grey repaints. The hazard squares and
            // small cyan indicator keep their colour in every scheme.
            .skins("textures/models/doors/sliding_blast_door.png",
                    "textures/models/doors/sliding_blast_door_black.png",
                    "textures/models/doors/sliding_blast_door_grey.png")
            // HBM loops the opening sound for both directions and plays a different stop sound
            // once the panels have fully opened or shut. It also layers the opening recording a
            // second time (getSoundLoop2), which is why it is registered twice here.
            .soundsOpen(null, SND_SLIDING_OPENING, SND_SLIDING_OPENED)
            .soundsClose(null, SND_SLIDING_OPENING, SND_SLIDING_SHUT)
            .secondaryLoop(SND_SLIDING_OPENING)
            // HBM nests the right lock under the left panel and vice versa. The locks turn in
            // the first 200 ms of the 1.2 s cycle; the panels wait 350 ms before sliding.
            .anim("LeftDoor", PartAnim.translate(0, 0, 2.125, 7F / 24F, 1F))
            .anim("RightDoor", PartAnim.translate(0, 0, -2.125, 7F / 24F, 1F))
            .anim("RightLock",
                    PartAnim.translate(0, 0, 2.125, 7F / 24F, 1F),
                    PartAnim.constantRotate(90, 1, 0, 0, 0, 1.8125, 0),
                    PartAnim.rotate(90, 1, 0, 0, 0, 1.8125, 0, 0F, 1F / 6F))
            .anim("LeftLock",
                    PartAnim.translate(0, 0, -2.125, 7F / 24F, 1F),
                    PartAnim.constantRotate(90, 1, 0, 0, 0, 1.8125, 0),
                    PartAnim.rotate(90, 1, 0, 0, 0, 1.8125, 0, 0F, 1F / 6F)));

    /** 2x2 single-panel sliding door (HBM QE sliding door). */
    public static final DoorDecl QE_SLIDING_DOOR = reg(new DoorDecl("qe_sliding_door")
            .footprint(0, 1, 0, 1, 0, 0).times(10, 10).strength(25F, 4000F)
            // Both panels follow their model translations. The thin jambs stay as static collision.
            .movingGroup("Left")
            .movingGroup("Right")
            // Leave only thin jambs. A full-width header would sit at player height and block the
            // open doorway, while these strips keep the frame clickable and still leave a 1.75-wide gap.
            .open(box(-0.5, 0, -0.5, -0.375, 2, 0.5), box(1.375, 0, -0.5, 1.5, 2, 0.5))
            .openSelectionOnly(box(-0.5, 0, -0.5, 1.5, 0.0625, -0.25))
            .modelPath("models/doors/qe_sliding_door.obj", "textures/models/doors/qe_sliding_door.png")
            .modelYaw(90F)
            .modelOffset(0.53125, 0.001, 0.5)
            // Default grey-green paint plus a white repaint.
            .skins("textures/models/doors/qe_sliding_door.png",
                    "textures/models/doors/qe_sliding_door_white.png")
            // HBM's separate qe_sliding_* assets are byte-identical to the sliding_door_* files,
            // so the QE door shares those events instead of shipping duplicate audio.
            .soundsOpen(null, SND_SLIDING_OPENING, SND_SLIDING_OPENED)
            .soundsClose(null, SND_SLIDING_OPENING, SND_SLIDING_SHUT)
            .anim("Left", PartAnim.translate(0, 0, 0.95, 0F, 1F))
            .anim("Right", PartAnim.translate(0, 0, -0.95, 0F, 1F)));

    /**
     * 1x2 single-column QE sliding door. It is the core cell of the normal QE door: only the frame
     * section and the right-hand panel are drawn, clipped to the one-block footprint, and that panel
     * retracts into the wall on the left like the original's right half.
     */
    public static final DoorDecl QE_SLIDING_PANEL = reg(new DoorDecl("qe_sliding_panel")
            .footprint(0, 0, 0, 1, 0, 0).times(10, 10).strength(25F, 4000F)
            // The right panel follows its model translation; the visible jamb stays static.
            .movingGroup("Right")
            // The visible frame is the original's left jamb; the right edge is the open doorway.
            .open(box(-0.5, 0, -0.5, -0.375, 2, 0.5))
            .openSelectionOnly(box(-0.5, 0, -0.5, 0.5, 0.0625, -0.25))
            .modelPath("models/doors/qe_sliding_door.obj", "textures/models/doors/qe_sliding_door.png")
            .modelYaw(90F)
            .modelOffset(0.53125, 0.001, 0.5)
            // Same two skins as the full QE door.
            .skins("textures/models/doors/qe_sliding_door.png",
                    "textures/models/doors/qe_sliding_door_white.png")
            // The normal model is two cells wide; model Z -1..0 is exactly the core cell column.
            .clipZ(-1.0, 0.0)
            // The frame is static, so it needs clipping too or it would span both cells.
            .clipAllGroups()
            .renderGroups("Frame", "Right")
            .itemGroups("Right")
            .soundsOpen(null, SND_SLIDING_OPENING, SND_SLIDING_OPENED)
            .soundsClose(null, SND_SLIDING_OPENING, SND_SLIDING_SHUT)
            .anim("Right", PartAnim.translate(0, 0, -0.95, 0F, 1F)));

    /** 4x3 roller / fire door, lifts into the lintel. */
    public static final DoorDecl FIRE_DOOR = reg(new DoorDecl("fire_door")
            .footprint(-1, 2, 0, 2, 0, 0).times(160, 160).strength(10F, 600F)
            // The panel's collision follows its vertical model transform. When fully raised only
            // the two side returns, the 0.25-high lintel lip and a thin clickable floor remain.
            .movingGroup("Door")
            .open(box(-1.5, 0, -0.5, -1.0, 3, 0.5), box(2.0, 0, -0.5, 2.5, 3, 0.5),
                    box(-1.0, 2.75, -0.5, 2.0, 3, 0.5))
            .openSelectionOnly(box(-1.5, 0, -0.5, 2.5, 0.1, 0.5))
            .modelPath("models/doors/fire_door.obj", "textures/models/doors/fire_door.png")
            .modelYaw(180F)
            .modelOffset(-0.5, 0, 0)
            // HBM's five fire-door looks: default, black, orange, yellow and trefoil.
            .skins("textures/models/doors/fire_door.png",
                    "textures/models/doors/fire_door_black.png",
                    "textures/models/doors/fire_door_orange.png",
                    "textures/models/doors/fire_door_yellow.png",
                    "textures/models/doors/fire_door_trefoil.png")
            // Hide the raised panel above the lintel so it disappears into the wall.
            .clipY(Double.NEGATIVE_INFINITY, 3)
            // HBM loops the motor and a fire alarm for the whole movement.
            .sounds(null, SND_WGH_START, SND_WGH_STOP)
            .secondaryLoop(SND_ALARM)
            .anim("Door", PartAnim.translate(0, 2.75, 0, 0F, 1F)));

    /** 5x5 vertical sliding security door. */
    public static final DoorDecl SECURE_ACCESS_DOOR = reg(new DoorDecl("secure_access_door")
            .footprint(-2, 2, 0, 4, 0, 0).times(120, 120).strength(20F, 3000F)
            // The panel's collision follows its vertical model transform. HBM keeps the full
            // bottom row and only a small header lip when the panel is raised.
            .movingGroup("Door")
            .open(box(-2.5, 0, -0.5, 2.5, 1, 0.5),
                    box(-2.5, 4.5, -0.35, 2.5, 5, 0.35))
            .openSelectionOnly(box(-2.5, 1, -0.5, 2.5, 1.0625, 0.5))
            .modelPath("models/doors/secure_access_door.obj", "textures/models/doors/secure_access_door.png")
            .modelYaw(90F)
            .modelOffset(0, 1, 0)
            // HBM's secure-door looks: default red warning paint plus recoloured yellow, green, blue,
            // grey and black variants. The black skin uses white lettering so the sign stays
            // readable; white paint uses black lettering and orange paint uses white lettering.
            .skins("textures/models/doors/secure_access_door.png",
                    "textures/models/doors/secure_access_door_yellow.png",
                    "textures/models/doors/secure_access_door_green.png",
                    "textures/models/doors/secure_access_door_blue.png",
                    "textures/models/doors/secure_access_door_grey.png",
                    "textures/models/doors/secure_access_door_black.png",
                    "textures/models/doors/secure_access_door_hbm_yellow.png",
                    "textures/models/doors/secure_access_door_white.png",
                    "textures/models/doors/secure_access_door_orange.png")
            // The frame reaches world y=5 after the model offset; clip the raised panel there.
            .clipY(Double.NEGATIVE_INFINITY, 5)
            .sounds(null, SND_GARAGE_MOVE, SND_GARAGE_STOP)
            .anim("Door", PartAnim.translate(0, 3.5, 0, 0F, 1F)));

    /** 1x2 sliding seal door. The panel retracts into the wall pocket, as in HBM. */
    public static final DoorDecl SLIDING_SEAL_DOOR = reg(new DoorDecl("sliding_seal_door")
            .footprint(0, 0, 0, 1, 0, 0).times(20, 20).strength(15F, 2000F)
            // The seal follows its model translation. When open it has slid into the wall pocket,
            // so the per-cell clipping removes its collision entirely.
            .movingGroup("Door")
            // HBM removes the seal's collision entirely when open. BlockDoorGeneric falls back to
            // the closed shape for ray tracing, so the open panel can still be clicked shut.
            .open()
            // The moving panel leaves only a sliver in the cell when it is fully open, so keep a
            // static click target at the closed position.
            .openSelectionOnly(box(-0.438, 0.063, -0.5, 0.438, 1.938, -0.25))
            .modelPath("models/doors/sliding_seal_door.obj", "textures/models/doors/sliding_seal_door.png")
            .modelYaw(90F)
            .modelOffset(0.5, 0, 0)
            .clipZ(Double.NEGATIVE_INFINITY, 0.5001)
            // HBM's three seal skins (default, clean, nuclear trefoil) plus plain white, red and
            // grey repaints. The repaints have no letters, numbers, trefoil or hazard stripes.
            .skins("textures/models/doors/sliding_seal_door.png",
                    "textures/models/doors/sliding_seal_door_clean.png",
                    "textures/models/doors/sliding_seal_door_trefoil.png",
                    "textures/models/doors/sliding_seal_door_white.png",
                    "textures/models/doors/sliding_seal_door_red.png",
                    "textures/models/doors/sliding_seal_door_grey.png")
            // One-shot start and stop; the movement itself has no loop in HBM.
            .sounds(SND_SEAL_OPEN, null, SND_SEAL_STOP)
            .anim("Door", PartAnim.translate(0, 0, 0.9, 0F, 1F)));

    /** 4x4 split round airlock door. Panels slide apart into the frame. */
    public static final DoorDecl ROUND_AIRLOCK_DOOR = reg(new DoorDecl("round_airlock_door")
            .footprint(-1, 2, 0, 3, 0, 0).times(60, 60).strength(20F, 3000F)
            // Both panels follow their model translations into the frame.
            .movingGroup("Left")
            .movingGroup("Right")
            .open(box(-1.5, 0, -0.5, -1.0, 4, 0.5), box(2.0, 0, -0.5, 2.5, 4, 0.5),
                    box(-1.0, 3, -0.5, 2.0, 4, 0.5))
            .openSelectionOnly(box(-1.5, 0, -0.5, 2.5, 0.0625, 0.5))
            .modelPath("models/doors/round_airlock_door.obj", "textures/models/doors/round_airlock_door.png")
            .modelYaw(90F)
            .modelOffset(0, 0, 0.5)
            .clipZ(-1.999, 1.999)
            // HBM's three airlock looks (worn, clean, green) plus the space repaint.
            .skins("textures/models/doors/round_airlock_door.png",
                    "textures/models/doors/round_airlock_door_clean.png",
                    "textures/models/doors/round_airlock_door_green.png",
                    "textures/models/doors/round_airlock_door_space.png")
            .sounds(null, SND_GARAGE_MOVE, SND_GARAGE_STOP)
            .anim("Left", PartAnim.translate(0, 0, 1.5, 0F, 1F))
            .anim("Right", PartAnim.translate(0, 0, -1.5, 0F, 1F)));

    /** 5x5 horizontal silo hatch, lid tilts up around its rear hinge. */
    public static final DoorDecl SILO_HATCH = reg(new DoorDecl("silo_hatch")
            .footprint(-2, 2, 0, 0, -2, 2).times(60, 60).strength(30F, 5000F)
            .passFallingWhenOpen()
            // The lid's collision follows the hinge rotation. Slices keep the AABB of the tilted
            // plate close to the real lid; the surrounding frame stays solid.
            .collisionSplits(8)
            .movingGroup("Hatch")
            .open(box(-2.5, 0, -2.5, -1.5, 1, 2.5), box(1.5, 0, -2.5, 2.5, 1, 2.5),
                    box(-1.5, 0, -2.5, 1.5, 1, -1.5), box(-1.5, 0, 1.5, 1.5, 1, 2.5))
            .openSelectionOnly(box(-2.5, 0, -2.5, 2.5, 0.0625, 2.5))
            .modelPath("models/doors/silo_hatch.obj", "textures/models/doors/silo_hatch.png")
            .modelYaw(90F)
            .sounds(null, SND_WGH_BIG_START, SND_WGH_BIG_STOP)
            // Match HBM's transform order T(origin) * R * T(-origin + translation). The old
            // translate-then-rotate order pulled the lid away from its hinge.
            .anim("Hatch",
                    PartAnim.constantTranslate(0, 0.875, -1.875),
                    PartAnim.rotate(-240, 1, 0, 0, 0, 0, 0, 20F / 60F, 100F / 60F),
                    PartAnim.constantTranslate(0, -0.875, 1.875),
                    PartAnim.translate(0, 0.25, 0, 0F, 10F / 60F)));

    /** 7x7 large horizontal silo hatch, same hinge mechanism as the smaller silo hatch. */
    public static final DoorDecl SILO_HATCH_LARGE = reg(new DoorDecl("silo_hatch_large")
            .footprint(-3, 3, 0, 0, -3, 3).times(60, 60).strength(30F, 5000F)
            .passFallingWhenOpen()
            // The lid covers a 5x5 opening inside a one-block frame.
            .passRadius(2)
            .collisionSplits(8)
            .movingGroup("Hatch")
            .open(box(-3.5, 0, -3.5, -2.5, 1, 3.5), box(2.5, 0, -3.5, 3.5, 1, 3.5),
                    box(-2.5, 0, -3.5, 2.5, 1, -2.5), box(-2.5, 0, 2.5, 2.5, 1, 3.5))
            .openSelectionOnly(box(-3.5, 0, -3.5, 3.5, 0.0625, 3.5))
            .modelPath("models/doors/silo_hatch_large.obj", "textures/models/doors/silo_hatch_large.png")
            .displayName("Large Silo Hatch")
            .modelYaw(90F)
            .sounds(null, SND_WGH_BIG_START, SND_WGH_BIG_STOP)
            // Same timing and easing as the small hatch, with the hinge moved one block back.
            .anim("Hatch",
                    PartAnim.constantTranslate(0, 0.875, -2.875),
                    PartAnim.rotate(-240, 1, 0, 0, 0, 0, 0, 20F / 60F, 100F / 60F),
                    PartAnim.constantTranslate(0, -0.875, 2.875),
                    PartAnim.translate(0, 0.25, 0, 0F, 10F / 60F)));

    /**
     * 3x3 cargo door. The lower half lifts two blocks over the full cycle while the upper half
     * waits for the midpoint and lifts one block, exactly like HBM's cargo door.
     */
    public static final DoorDecl CARGO_DOOR = reg(new DoorDecl("cargo_door")
            .footprint(-1, 1, 0, 2, 0, 0).times(60, 60).strength(5F, 50F)
            // Both halves follow their own model transform. The upper panel leaves a shallow
            // lintel when open, and the floor keeps a click target for closing the door.
            .movingGroup("DoorBot")
            .movingGroup("DoorTop")
            .open(box(-1.5, 2.25, -0.125, 1.5, 3, 0.125))
            .openSelectionOnly(box(-1.5, 0, -0.125, 1.5, 0.125, 0.125))
            .modelPath("models/doors/cargo_door.obj", "textures/models/doors/cargo_door.png")
            .displayName("Cargo Door")
            // HBM authors the cargo model with its width along Z, like the other pheo doors.
            .modelYaw(90F)
            .sounds(null, SND_GARAGE_MOVE, SND_GARAGE_STOP)
            .anim("DoorBot", PartAnim.translate(0, 2, 0, 0F, 1F))
            .anim("DoorTop", PartAnim.translate(0, 1, 0, 0.5F, 1F)));

    /** 7x6 large vehicle door. The two halves slide into the side pillars. */
    public static final DoorDecl LARGE_VEHICLE_DOOR = reg(new DoorDecl("large_vehicle_door")
            .footprint(-3, 3, 0, 5, 0, 0).times(60, 60).strength(30F, 5000F)
            // Both halves follow their model translations into the side pillars.
            .movingGroup("Left")
            .movingGroup("Right")
            // HBM leaves only the two side pillars standing when the door is open.
            .open(box(-3.5, 0, -0.6, -2.8, 6, 0.6), box(2.8, 0, -0.6, 3.5, 6, 0.6))
            .openSelectionOnly(box(-3.5, 0, -0.6, 3.5, 0.0625, 0.6))
            .modelPath("models/doors/large_vehicle_door.obj", "textures/models/doors/large_vehicle_door.png")
            .modelYaw(180F)
            .clipX(-3.4375, 3.4375)
            .sounds(null, SND_GARAGE_MOVE, SND_GARAGE_STOP)
            .anim("Left", PartAnim.translate(-3, 0, 0, 0F, 1F))
            .anim("Right", PartAnim.translate(3, 0, 0, 0F, 1F)));

    /** 3x3 QE containment door. The shield rises into the frame. */
    public static final DoorDecl QE_CONTAINMENT = reg(new DoorDecl("qe_containment")
            .footprint(-1, 1, 0, 2, 0, 0).times(160, 160).strength(25F, 4000F)
            // The shield follows its vertical model transform.
            .movingGroup("Door")
            // HBM leaves the open doorway clear at floor level (only a thin click target) and
            // keeps the side jamb strips plus the lintel as collision.
            .open(box(-1.5, 0, -0.5, -1.25, 3, 0.0),
                    box(1.25, 0, -0.5, 1.5, 3, 0.0),
                    box(-1.25, 2.25, -0.5, 1.25, 3, 0.0))
            .openSelectionOnly(box(-1.5, 0, -0.5, 1.5, 0.125, 0.0))
            .modelPath("models/doors/qe_containment.obj", "textures/models/doors/qe_containment.png")
            .modelYaw(90F)
            .modelOffset(0.25, 0, 0)
            .clipY(Double.NEGATIVE_INFINITY, 3)
            // HBM's three containment markings: plain, trefoil and yellow trefoil.
            .skins("textures/models/doors/qe_containment.png",
                    "textures/models/doors/qe_containment_trefoil.png",
                    "textures/models/doors/qe_containment_trefoil_yellow.png")
            .sounds(null, SND_WGH_START, SND_WGH_STOP)
            .anim("Door", PartAnim.translate(0, 2.25, 0, 0F, 1F)));

    /** 1x7 roller shutter (HBM's standalone roller blast door). */
    public static final DoorDecl BLAST_DOOR = reg(new DoorDecl("blast_door")
            .footprint(0, 0, 0, 6, 0, 0).times(100, 100).strength(30F, 5000F)
            .linkAdjacent()
            // The shutter is one rigid box that follows the Tooth group's vertical transform.
            .movingBox("Tooth", box(-0.3, 0.75, -0.5, 0.3, 6.0, 0.5))
            // The base and the fixed header housing remain in place when the shutter is rolled up.
            .open(box(-0.5, 0, -0.5, 0.5, 1, 0.5), box(-0.5, 6, -0.5, 0.5, 7, 0.5))
            .modelPath("models/doors/blast_door.obj", "textures/models/doors/blast_door.png")
            .modelYaw(90F)
            .partTexture("Base", "textures/models/doors/blast_door_base.png")
            .partTexture("Block", "textures/models/doors/blast_door_block.png")
            .partTexture("Tooth", "textures/models/doors/blast_door_tooth.png")
            .itemGroups("Base", "Tooth", "Slider1", "Slider2", "Slider3", "Slider4", "Block")
            // The moving sections retract upwards behind the header. The Y clip hides the
            // portions above the frame instead of drawing them over the wall.
            .clipY(Double.NEGATIVE_INFINITY, 6)
            .sounds(null, SND_WGH_BIG_START, SND_WGH_BIG_STOP)
            .anim("Tooth", PartAnim.translate(0, 5, 0, 0F, 1F))
            .anim("Slider1", PartAnim.translate(0, 5, 0, 0F, 1F))
            .anim("Slider2", PartAnim.translate(0, 5, 0, 0F, 1F))
            .anim("Slider3", PartAnim.translate(0, 5, 0, 0F, 1F))
            .anim("Slider4", PartAnim.translate(0, 5, 0, 0F, 1F)));

    /** 3x3 swinging water-tight door (forgotten in the original port). */
    public static final DoorDecl WATER_DOOR = reg(new DoorDecl("water_door")
            .footprint(-1, 1, 0, 2, 0, 0).times(60, 60).strength(30F, 5000F)
            // The panel follows the hinge rotation. Slices keep the swinging plate's AABB tight;
            // the jambs and header stay solid in every state.
            .collisionSplits(8)
            .movingBox("Door_Cube.003", box(-1.25, 0.0625, -0.5, 1.25, 2.9375, 0.5))
            .open(box(-1.5, 0, -0.5, -1.25, 3, 0.5), box(1.25, 0, -0.5, 1.5, 3, 0.5),
                    box(-1.25, 2.9375, -0.5, 1.25, 3, 0.5))
            .openSelectionOnly(box(-1.5, 0, -0.5, 1.5, 0.0625, 0.5))
            .modelPath("models/doors/water_door.obj", "textures/models/doors/water_door.png")
            // HBM authors this model facing north and rotates it 90 degrees explicitly. The
            // port frame is rotated another 90, hence 180 plus the transformed offset.
            .modelYaw(180F)
            .modelOffset(0, 0, 0.375)
            // HBM's two water-door looks: the original and a clean version.
            .skins("textures/models/doors/water_door.png",
                    "textures/models/doors/water_door_clean.png")
            // Lever on opening, motor loop during the swing, stop thud at the end. Closing has
            // no separate start sound and finishes with the lever again, exactly like HBM.
            .soundsOpen(SND_LEVER, SND_WGH_BIG_START, SND_WGH_BIG_STOP)
            .soundsClose(null, SND_WGH_BIG_START, SND_LEVER)
            // The bolts unlock during the first half; the door swings during the second half.
            // Reversing the open fraction automatically gives HBM's closing timings. The valve
            // groups are children of the swinging panel in HBM, so they must inherit the hinge
            // rotation as the outermost transform.
            .anim("Door_Cube.003", PartAnim.rotate(-120, 0, 1, 0, -1.1875, 0, 0, 0.5F, 1F))
            .anim("Bolts",
                    PartAnim.rotate(-120, 0, 1, 0, -1.1875, 0, 0, 0.5F, 1F),
                    PartAnim.translate(-0.4, 0, 0, 0F, 0.5F))
            .anim("Top",
                    PartAnim.rotate(-120, 0, 1, 0, -1.1875, 0, 0, 0.5F, 1F),
                    PartAnim.rotate(360, 0, 0, 1, 0.40625, 2.28125, 0, 0F, 0.5F))
            .anim("Bottom",
                    PartAnim.rotate(-120, 0, 1, 0, -1.1875, 0, 0, 0.5F, 1F),
                    PartAnim.rotate(360, 0, 0, 1, 0.40625, 0.71875, 0, 0F, 0.5F)));

    /**
     * 27x24 transition seal. The model is baked in the closed Collada frame and its original
     * 24-second action is replayed as per-group rigid matrices (translations and rotations).
     */
    public static final DoorDecl TRANSITION_SEAL = reg(new DoorDecl("transition_seal")
            // HBM's north-facing dimensions are 12 blocks west and 13 east of the core; the
            // converted DAE frame is centred 0.5 blocks east of the core for the same reason.
            .footprint(-12, 13, 0, 23, 0, 0).times(480, 480).strength(50F, 10000F)
            // The huge seal is pass-through when open. Its motion is a client-side keyframe
            // animation, so the collision keeps the row-by-row removal instead of following the
            // model matrices; every row is still cleared continuously while the mechanism lifts.
            .liftRows(-12.5, 13.5, 0, 23, -0.5, 0.5, 18)
            .open()
            .modelPath("models/doors/transition_seal.obj", "textures/models/doors/transition_seal.png")
            .modelYaw(90F)
            .modelOffset(0, 0, 0.5)
            .keyframeAnim("models/doors/transition_seal.anim")
            .soundVolume(6F)
            // HBM plays the 24-second mechanism recording once at the start of both opening and
            // closing, with no loop or end sound.
            .sounds(SND_TRANSITION_SEAL, null, null)
            // The closed model includes the arm above y=30 and the animation raises it another
            // 18 blocks. A small render box made those parts disappear from some camera angles.
            .renderRadius(64));

    private DoorDecls() {
    }
}
