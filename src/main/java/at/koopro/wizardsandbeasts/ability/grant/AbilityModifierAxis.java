package at.koopro.wizardsandbeasts.ability.grant;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * The dimension an {@link at.koopro.wizardsandbeasts.skill.SkillEffect.AbilityRefinement} scales on a
 * granted ability. Deliberately generic and content-free: the refinement mechanism ships with zero
 * consumers, so the axis set is a small forward vocabulary, not a promise of behavior. Wiring an axis
 * to an actual ability implementation is content-era work.
 */
public enum AbilityModifierAxis implements StringRepresentable {
    POTENCY("potency"),
    DURATION("duration"),
    COOLDOWN("cooldown"),
    RANGE("range");

    public static final Codec<AbilityModifierAxis> CODEC = StringRepresentable.fromValues(AbilityModifierAxis::values);

    private final String serializedName;

    AbilityModifierAxis(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public static AbilityModifierAxis byName(String raw) {
        return valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
