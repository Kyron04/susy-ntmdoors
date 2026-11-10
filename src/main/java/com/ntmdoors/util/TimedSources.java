package com.ntmdoors.util;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A set of source positions that stay active for a limited time unless refreshed. Used for
 * wireless gate/door power: a source reports powered on each refresh and may disappear without a
 * clean falling edge.
 */
public final class TimedSources {

    public enum Update {
        /** No active source was added or removed. A powered refresh only extended the expiry. */
        NONE,
        /** A powered source was seen again; membership did not change. */
        REFRESHED,
        /** A new powered source was added. */
        ADDED,
        /** A source was removed by an explicit off report or expiry. */
        REMOVED
    }

    private final Map<BlockPos, Long> sources = new HashMap<>();

    /**
     * Applies a powered/off report from {@code source}. When powered, the source is stored until
     * {@code now + timeoutTicks}; when not powered, the source is removed.
     */
    public Update apply(BlockPos source, boolean powered, long now, int timeoutTicks) {
        BlockPos key = source.toImmutable();
        if (powered) {
            return sources.put(key, now + timeoutTicks) == null ? Update.ADDED : Update.REFRESHED;
        }
        return sources.remove(key) == null ? Update.NONE : Update.REMOVED;
    }

    public boolean isActive(long now) {
        if (sources.isEmpty()) return false;
        for (Long expiry : sources.values()) {
            if (expiry >= now) return true;
        }
        return false;
    }

    /** Removes expired sources and reports whether any were dropped. */
    public boolean prune(long now) {
        if (sources.isEmpty()) return false;
        return sources.values().removeIf(expiry -> expiry < now);
    }

    public boolean remove(BlockPos source) {
        return source != null && sources.remove(source) != null;
    }

    public void clear() {
        sources.clear();
    }

    public boolean isEmpty() {
        return sources.isEmpty();
    }

    /** Current source positions, for persistence or link management. */
    public List<BlockPos> keys() {
        return new ArrayList<>(sources.keySet());
    }

    /** Restores persisted sources with a fresh expiry, e.g. after chunk load. */
    public void load(Iterable<BlockPos> loaded, long expiry) {
        sources.clear();
        for (BlockPos source : loaded) {
            if (source != null) sources.put(source.toImmutable(), expiry);
        }
    }
}
