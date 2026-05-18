package at.koopro.wizardsandbeasts.spell.core;

import net.minecraft.util.StringRepresentable;

public enum SpellCategory implements StringRepresentable {
    COMBAT(0xFFFF4444),
    UTILITY(0xFF44FF44),
    DEFENSE(0xFF4488FF),
    DARK_ARTS(0xFF8B00FF);

    private final int color;
    public static final com.mojang.serialization.Codec<SpellCategory> CODEC = StringRepresentable.fromValues(SpellCategory::values);

    SpellCategory(int color) {
        this.color = color;
    }

    public int getColor() {
        return color;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase();
    }
}
