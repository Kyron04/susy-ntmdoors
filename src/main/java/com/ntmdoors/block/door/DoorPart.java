package com.ntmdoors.block.door;

import net.minecraft.util.StringIdentifiable;

public enum DoorPart implements StringIdentifiable {
    CORE("core"),
    DUMMY("dummy");

    private final String name;

    DoorPart(String name) {
        this.name = name;
    }

    @Override
    public String asString() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
