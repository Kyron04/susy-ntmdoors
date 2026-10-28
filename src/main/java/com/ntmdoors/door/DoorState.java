package com.ntmdoors.door;

public enum DoorState {
    CLOSED, OPEN, OPENING, CLOSING;

    private static final DoorState[] VALUES = values();

    public boolean isMoving() {
        return this == OPENING || this == CLOSING;
    }

    public static DoorState byId(int id) {
        return VALUES[Math.floorMod(id, VALUES.length)];
    }
}
