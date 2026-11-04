package com.ntmdoors.monitor;

/** Lightweight row data for a Security net landing page. */
public final class SecurityNetSummary {

    public final int id;
    public final String name;
    public final int deviceCount;

    public SecurityNetSummary(int id, String name, int deviceCount) {
        this.id = id;
        this.name = name;
        this.deviceCount = deviceCount;
    }
}
