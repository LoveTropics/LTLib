package com.lovetropics.lib.techstack;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum Crud implements StringRepresentable {
    CREATE("create"),
    READ("read"),
    UPDATE("update"),
    DELETE("delete"),
    ;

    public static final Codec<Crud> CODEC = StringRepresentable.fromEnum(Crud::values);

    private final String name;

    Crud(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
