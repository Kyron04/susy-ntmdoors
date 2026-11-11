package com.ntmdoors.block;

import net.minecraft.util.StringIdentifiable;

public enum MotionSensorStatus implements StringIdentifiable {
    IDLE("idle"),
    AUTHORIZED("authorized"),
    MAINTENANCE("maintenance"),
    MOTION("motion");

    private final String name;

    MotionSensorStatus(String name) {
        this.name = name;
    }

    @Override
    public String asString() {
        return name;
    }
}
