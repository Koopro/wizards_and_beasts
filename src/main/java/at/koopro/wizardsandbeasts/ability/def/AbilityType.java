package at.koopro.wizardsandbeasts.ability.def;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NullMarked;

/**
 * Classifies how an {@link AbilityDefinition} is exercised. Framework-level taxonomy only — behavior is
 * bound separately (see {@code at.koopro.wizardsandbeasts.ability.trigger.AbilityBehavior}).
 *
 * <ul>
 *   <li>{@link #PASSIVE} — always-on; never selectable, never triggered. Resolver-visible for future UI.</li>
 *   <li>{@link #CONTEXTUAL} — fires from game context, not the wheel. Resolver-visible, never selectable.</li>
 *   <li>{@link #TOGGLE} — on/off state flipped from the wheel; appears in the wheel.</li>
 *   <li>{@link #ACTIVE} — armed by wheel selection, fired by the use / quick key; appears in the wheel.</li>
 * </ul>
 */
@NullMarked
public enum AbilityType implements StringRepresentable {
    PASSIVE("passive"),
    CONTEXTUAL("contextual"),
    TOGGLE("toggle"),
    ACTIVE("active");

    public static final Codec<AbilityType> CODEC = StringRepresentable.fromEnum(AbilityType::values);

    private final String serializedName;

    AbilityType(String serializedName) {
        this.serializedName = serializedName;
    }

    /** True for the two types that populate the ability wheel and can be selected / triggered. */
    public boolean isWheelEligible() {
        return this == ACTIVE || this == TOGGLE;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
