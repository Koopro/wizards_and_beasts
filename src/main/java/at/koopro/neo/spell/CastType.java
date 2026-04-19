package at.koopro.neo.spell;

import net.minecraft.util.StringRepresentable;

public enum CastType implements StringRepresentable {
    PROJECTILE,
    SELF,
    CONE,
    TARGETED;

    @Override
    public String getSerializedName() {
        return name().toLowerCase();
    }
}
