package at.koopro.wizardsandbeasts.ministry.law;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A thing the Ministry considers a crime, and what it costs you.
 *
 * <p>{@code notoriety} is <b>heat</b> — how hard they are currently looking for you — and decays while you
 * keep your head down. The count of each offence never decays: the Ministry keeps files, and priors push a
 * repeat offender into a higher band faster than a first-timer.
 *
 * <p>{@code arrestable} separates the curses that get Aurors sent after you from the paperwork offences that
 * merely go on your record.
 */
@NullMarked
public enum MagicalOffence implements StringRepresentable {

    /** Killing curse. The single worst thing on the books. */
    AVADA_KEDAVRA("avada_kedavra", 45.0f, true),
    /** Torture. */
    CRUCIO("crucio", 32.0f, true),
    /** Seizing another's will. */
    IMPERIO("imperio", 28.0f, true),
    /** Transforming while unregistered — a fine and a file, not a manhunt. */
    UNREGISTERED_ANIMAGUS("unregistered_animagus", 6.0f, false),
    /**
     * Apparating without a licence. Illegal, not impossible — the trio do it all through
     * <i>Deathly Hallows</i> — so it goes on the file and nothing is dispatched. Rated a third of
     * unregistered Animagi because the two are different kinds of offence, not different sizes of one:
     * an unregistered Animagus is concealing a capability, while an unlicensed Apparator has merely
     * skipped the paperwork for one everybody knows they have. A fine, not Azkaban.
     */
    UNLICENSED_APPARITION("unlicensed_apparition", 2.0f, false),
    /** Being somewhere in Azkaban you have no business being. */
    AZKABAN_TRESPASS("azkaban_trespass", 15.0f, true),
    /** Walking out of a sentence. Raises the stakes rather than settling them. */
    AZKABAN_BREAKOUT("azkaban_breakout", 40.0f, true),
    /** Fighting back against the Aurors sent for you. */
    AUROR_ASSAULT("auror_assault", 20.0f, true);

    public static final Codec<MagicalOffence> CODEC = StringRepresentable.fromEnum(MagicalOffence::values);

    private final String serializedName;
    private final float notoriety;
    private final boolean arrestable;

    MagicalOffence(String serializedName, float notoriety, boolean arrestable) {
        this.serializedName = serializedName;
        this.notoriety = notoriety;
        this.arrestable = arrestable;
    }

    /** Heat added on a first offence, before the repeat-offender multiplier. */
    public float notoriety() {
        return notoriety;
    }

    /** Whether committing this is enough to have Aurors dispatched. */
    public boolean arrestable() {
        return arrestable;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public Component displayName() {
        return Component.translatable("ministry.wizards_and_beasts.offence." + serializedName);
    }

    /** The offence a spell id constitutes, or {@code null} if casting it is perfectly legal. */
    @Nullable
    public static MagicalOffence forSpell(String spellId) {
        if (at.koopro.wizardsandbeasts.spell.core.SpellIds.matches(spellId, "avada_kedavra")) {
            return AVADA_KEDAVRA;
        }
        if (at.koopro.wizardsandbeasts.spell.core.SpellIds.matches(spellId, "crucio")) {
            return CRUCIO;
        }
        if (at.koopro.wizardsandbeasts.spell.core.SpellIds.matches(spellId, "imperio")) {
            return IMPERIO;
        }
        return null;
    }

    @Nullable
    public static MagicalOffence byName(String name) {
        for (MagicalOffence offence : values()) {
            if (offence.serializedName.equals(name)) {
                return offence;
            }
        }
        return null;
    }
}
