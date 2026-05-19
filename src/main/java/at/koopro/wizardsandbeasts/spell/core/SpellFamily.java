package at.koopro.wizardsandbeasts.spell.core;

import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * Visual particle family for spell FX; orthogonal to {@link SpellCategory} (gameplay).
 */
public enum SpellFamily implements StringRepresentable {
    FIRE,
    ICE,
    ELECTRIC,
    ARCANE,
    DARK,
    LIGHT,
    WATER;

    public static SpellFamily byOrdinal(int ordinal) {
        SpellFamily[] v = values();
        if (ordinal < 0 || ordinal >= v.length) {
            return ARCANE;
        }
        return v[ordinal];
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
