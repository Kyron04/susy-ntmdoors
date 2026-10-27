package com.ntmdoors.door;

public enum DoorState {
    CLOSED, OPEN, OPENING, CLOSING;

    public boolean isMoving() {
        return this == OPENING || this == CLOSING;
    }

    public static DoorState byId(int id) {
        DoorState[] v = values();
        return v[Math.floorMod(id, v.length)];
    }
}
