package com.ntmdoors.weapons;

/** The two binding behaviours offered by the Weapons Relay Key. */
public enum WeaponsMode {
    REGULAR(0, "Regular Bounding",
            "Bind explosives to Redstone Gates."),
    DEAD_MANS_HAND(1, "Dead Man's Hand",
            "Hard lock: only the matching key can break it.");

    public final int id;
    public final String displayName;
    public final String description;

    WeaponsMode(int id, String displayName, String description) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
    }

    public static WeaponsMode byId(int id) {
        return id == DEAD_MANS_HAND.id ? DEAD_MANS_HAND : REGULAR;
    }
}
