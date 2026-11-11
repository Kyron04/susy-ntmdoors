package com.ntmdoors.test.door;

import com.ntmdoors.block.door.DoorVoxelShapes;
import com.ntmdoors.door.DoorDecl;
import com.ntmdoors.door.DoorDecls;
import com.ntmdoors.door.DoorGeometry;
import com.ntmdoors.door.PartAnim;
import net.minecraft.Bootstrap;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DoorBoundingBoxSlicingTest {

    @BeforeAll
    public static void setup() {
        try {
            java.lang.reflect.Field field = Bootstrap.class.getDeclaredField("initialized");
            field.setAccessible(true);
            field.setBoolean(null, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        net.minecraft.registry.Registries.REGISTRIES.getIds();
    }

    @Test
    @DisplayName("Verify clipBoxesToCell handles bounds clamping and empty cells")
    public void testClipBoxesToCell() {
        // 1. Box completely inside cell
        Box insideBox = new Box(0.1, 0.2, 0.3, 0.8, 0.9, 0.7);
        VoxelShape shapeInside = DoorVoxelShapes.clipBoxesToCell(new Box[]{insideBox}, 0, 0, 0);
        assertFalse(shapeInside.isEmpty(), "Shape inside cell must not be empty");
        Box bounding = shapeInside.getBoundingBox();
        assertEquals(0.1, bounding.minX, 1e-4);
        assertEquals(0.8, bounding.maxX, 1e-4);
        assertEquals(0.2, bounding.minY, 1e-4);
        assertEquals(0.9, bounding.maxY, 1e-4);

        // 2. Box completely outside cell
        Box outsideBox = new Box(1.5, 0.0, 0.0, 2.5, 1.0, 1.0);
        VoxelShape shapeOutside = DoorVoxelShapes.clipBoxesToCell(new Box[]{outsideBox}, 0, 0, 0);
        assertTrue(shapeOutside.isEmpty(), "Box outside cell bounds [0..1] must produce empty shape");

        // 3. Box overlapping cell boundary
        Box spanningBox = new Box(-0.5, 0.0, -0.5, 1.5, 1.0, 1.5);
        VoxelShape shapeClamped = DoorVoxelShapes.clipBoxesToCell(new Box[]{spanningBox}, 0, 0, 0);
        assertFalse(shapeClamped.isEmpty());
        Box clampedBounds = shapeClamped.getBoundingBox();
        assertEquals(0.0, clampedBounds.minX, 1e-4);
        assertEquals(1.0, clampedBounds.maxX, 1e-4);
        assertEquals(0.0, clampedBounds.minY, 1e-4);
        assertEquals(1.0, clampedBounds.maxY, 1e-4);
        assertEquals(0.0, clampedBounds.minZ, 1e-4);
        assertEquals(1.0, clampedBounds.maxZ, 1e-4);

        // 4. Degenerate box (width < EPS)
        Box degenerateBox = new Box(0.5, 0.0, 0.0, 0.5 + 1e-6, 1.0, 1.0);
        VoxelShape shapeDegen = DoorVoxelShapes.clipBoxesToCell(new Box[]{degenerateBox}, 0, 0, 0);
        assertTrue(shapeDegen.isEmpty(), "Degenerate box below EPS must be discarded");

        // 5. Null or empty boxes array
        assertTrue(DoorVoxelShapes.clipBoxesToCell(null, 0, 0, 0).isEmpty());
        assertTrue(DoorVoxelShapes.clipBoxesToCell(new Box[0], 0, 0, 0).isEmpty());
    }

    @Test
    @DisplayName("Verify DoorGeometry.transform translation and rotation slicing")
    public void testDoorGeometryTransform() {
        Box modelBox = new Box(-0.5, 0.0, -0.1, 0.5, 2.0, 0.1);

        // 1. Pure translation
        List<PartAnim> transAnim = Collections.singletonList(
                PartAnim.translate(2.0, 0.0, 0.0, 0.0f, 1.0f)
        );
        Box[] boxesTranslated = DoorGeometry.transform(
                transAnim, 1.0f, modelBox, 1, 0.0f, 0.0, 0.0, 0.0, null
        );
        assertEquals(1, boxesTranslated.length);
        assertEquals(1.5, boxesTranslated[0].minX, 1e-3);
        assertEquals(2.5, boxesTranslated[0].maxX, 1e-3);

        // 2. Pure rotation with multiple slices (prevents ballooning)
        List<PartAnim> rotAnim = Collections.singletonList(
                PartAnim.rotate(90.0, 0, 1, 0, 0, 0, 0, 0.0f, 1.0f)
        );
        Box[] boxesRotated = DoorGeometry.transform(
                rotAnim, 1.0f, modelBox, 4, 0.0f, 0.0, 0.0, 0.0, null
        );
        assertTrue(boxesRotated.length >= 1, "Rotated box should generate sliced boxes");
        for (Box b : boxesRotated) {
            assertTrue(b.maxX > b.minX && b.maxY > b.minY && b.maxZ > b.minZ, "Valid box dimensions");
        }

        // 3. Clipping plane truncation
        Box clipVolume = new Box(-0.2, 0.0, -1.0, 0.2, 2.0, 1.0);
        Box[] boxesClipped = DoorGeometry.transform(
                null, 0.0f, modelBox, 1, 0.0f, 0.0, 0.0, 0.0, clipVolume
        );
        assertEquals(1, boxesClipped.length);
        assertEquals(-0.2, boxesClipped[0].minX, 1e-3);
        assertEquals(0.2, boxesClipped[0].maxX, 1e-3);
    }

    @Test
    @DisplayName("Verify static VoxelShape generation for CLOSED vs OPEN vs BREACHED")
    public void testStaticVoxelShapeStates() {
        DoorDecl door = DoorDecls.SLIDING_BLAST_DOOR;
        Direction facing = Direction.NORTH;
        BlockPos centerPos = new BlockPos(0, 1, 0); // Center doorway cell (x=0, y=1)
        BlockPos framePos = new BlockPos(-3, 1, 0);  // Frame post cell (x=-3, y=1)

        // CLOSED state: center doorway cell must have collision
        VoxelShape closedCenter = DoorVoxelShapes.getStaticShape(
                door, facing, centerPos, DoorVoxelShapes.ShapeState.CLOSED
        );
        assertFalse(closedCenter.isEmpty(), "Closed sliding blast door center cell must be solid");

        // OPEN state: center doorway cell must be passable (empty shape)
        VoxelShape openCenter = DoorVoxelShapes.getStaticShape(
                door, facing, centerPos, DoorVoxelShapes.ShapeState.OPEN
        );
        assertTrue(openCenter.isEmpty(), "Open sliding blast door center cell must be empty/passable");

        // Frame cell must remain solid even when OPEN
        VoxelShape openFrame = DoorVoxelShapes.getStaticShape(
                door, facing, framePos, DoorVoxelShapes.ShapeState.OPEN
        );
        assertFalse(openFrame.isEmpty(), "Open sliding blast door frame post must remain solid");

        // BREACHED state: moving panel removed, center doorway is passable
        VoxelShape breachedCenter = DoorVoxelShapes.getStaticShape(
                door, facing, centerPos, DoorVoxelShapes.ShapeState.BREACHED
        );
        assertTrue(breachedCenter.isEmpty(), "Breached door center cell must be passable");

        // Frame remains solid on BREACHED state
        VoxelShape breachedFrame = DoorVoxelShapes.getStaticShape(
                door, facing, framePos, DoorVoxelShapes.ShapeState.BREACHED
        );
        assertFalse(breachedFrame.isEmpty(), "Breached door frame post must remain solid");
    }

    @Test
    @DisplayName("Verify O(1) static VoxelShape caching returns identical instances")
    public void testVoxelShapeCaching() {
        DoorDecl door = DoorDecls.FIRE_DOOR;
        Direction facing = Direction.SOUTH;
        BlockPos offset = new BlockPos(1, 1, 0);

        VoxelShape shape1 = DoorVoxelShapes.getStaticShape(door, facing, offset, DoorVoxelShapes.ShapeState.CLOSED);
        VoxelShape shape2 = DoorVoxelShapes.getStaticShape(door, facing, offset, DoorVoxelShapes.ShapeState.CLOSED);

        assertSame(shape1, shape2, "Subsequent calls with identical key must return exact cached reference");

        DoorVoxelShapes.clearCache();
        VoxelShape shape3 = DoorVoxelShapes.getStaticShape(door, facing, offset, DoorVoxelShapes.ShapeState.CLOSED);
        assertNotSame(shape1, shape3, "After clearCache, a new instance should be generated");
        assertEquals(shape1.getBoundingBox(), shape3.getBoundingBox(), "Content must be identical");
    }
}
