package com.ntmdoors.client.render;

/**
 * Axis-aligned clipping bounds in model-local coordinates.
 * Coordinates at negative/positive infinity represent inactive clipping planes.
 */
public record ClipBounds(
    double minX, double maxX,
    double minY, double maxY,
    double minZ, double maxZ
) {
    public static final ClipBounds INFINITE = new ClipBounds(
        Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY,
        Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY,
        Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY
    );

    public boolean isInfinite() {
        return Double.isInfinite(minX) && Double.isInfinite(maxX) &&
               Double.isInfinite(minY) && Double.isInfinite(maxY) &&
               Double.isInfinite(minZ) && Double.isInfinite(maxZ);
    }

    public boolean hasXClip() {
        return !Double.isInfinite(minX) || !Double.isInfinite(maxX);
    }

    public boolean hasYClip() {
        return !Double.isInfinite(minY) || !Double.isInfinite(maxY);
    }

    public boolean hasZClip() {
        return !Double.isInfinite(minZ) || !Double.isInfinite(maxZ);
    }
}
