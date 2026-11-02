package com.ntmdoors.hlblocks;

import net.minecraft.util.IStringSerializable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The included HL textures, exposed as block state values. Lab 1 Door 4D is deliberately absent.
 */
public enum Variant implements IStringSerializable {

    FREEZERC2("freezerc2"),
    FREEZERC3("freezerc3"),
    FIFTIES_CCH1("fifties_cch1"),
    FIFTIES_CEIL1("fifties_ceil1"),
    FIFTIES_CEIL5("fifties_ceil5"),
    CRETE2_WALL01("crete2_wall01"),
    CRETE3_CEIL01("crete3_ceil01"),
    CRETE3_FLR04("crete3_flr04"),
    DUCT_WALL01("duct_wall01"),
    LAB1_C4003("lab1_c4003"),
    LAB1_FLOOR4("lab1_floor4"),
    LAB1_FLR4C("lab1_flr4c"),
    TNNL_FLR4B("tnnl_flr4b"),
    TNNL_FLR6("tnnl_flr6"),
    TNNL_FLR6B("tnnl_flr6b"),
    TNNL_PLT1A("tnnl_plt1a"),
    GENERIC015T("generic015t"),
    GENERIC015V("generic015v"),
    GENERIC80("generic80"),
    ;

    private final String name;

    Variant(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return this.name;
    }

    /** Resolves texture names (in order) into variants. */
    public static List<Variant> of(String... names) {
        List<Variant> result = new ArrayList<>(names.length);
        for (String name : names) {
            Variant found = null;
            for (Variant variant : values()) {
                if (variant.name.equals(name)) {
                    found = variant;
                    break;
                }
            }
            if (found == null) {
                throw new IllegalArgumentException("Unknown HL Blocks variant: " + name);
            }
            result.add(found);
        }
        return Collections.unmodifiableList(result);
    }
}
