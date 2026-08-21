package at.koopro.wizardsandbeasts.spell.def;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

/**
 * Whether a registered spell actually does anything yet.
 *
 * <p>The corpus registers every canon spell with a known incantation, most of which have no cast
 * behaviour written. They are registered rather than withheld so the roster is visible, discoverable
 * and diffable in one place — the alternative, adding each spell to the registry only when its
 * behaviour lands, hides the shape of the work and makes "is this spell known to the mod?"
 * unanswerable without reading a design document.
 *
 * <p>Enforced at exactly two sites, both server-side:
 * <ul>
 *   <li>{@link at.koopro.wizardsandbeasts.spell.cast.SpellCastGate} refuses the cast before any
 *       effect resolves — no cooldown, no proficiency, no particles, no sound.</li>
 *   <li>{@link at.koopro.wizardsandbeasts.spell.learning.SpellLearningEligibility} refuses the
 *       lesson, which greys the teacher's offer row and blocks payment at the same choke point.</li>
 * </ul>
 *
 * <p><b>Orthogonal to {@link at.koopro.wizardsandbeasts.module.ModuleManager}.</b> A
 * {@code COMING_SOON} spell inside a disabled module is refused by the module, not by this state;
 * the two gates stack and neither substitutes for the other.
 *
 * @see SpellDefinition#implementationState()
 */
public enum SpellImplementationState implements StringRepresentable {
    /**
     * Castable. The default for every spell that omits the field, which is deliberately every spell
     * that shipped before the corpus existed — the 27 datapack JSONs stay valid unedited.
     */
    IMPLEMENTED("implemented"),
    /** Registered, visible, and refused at the cast gate and the teacher. */
    COMING_SOON("coming_soon");

    public static final Codec<SpellImplementationState> CODEC =
            StringRepresentable.fromEnum(SpellImplementationState::values);

    private final String serializedName;

    SpellImplementationState(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public @NonNull String getSerializedName() {
        return serializedName;
    }
}
