package at.koopro.wizardsandbeasts.ministry.law;

import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
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
 *
 * <p>{@code fineKnuts} is the other half of that split, and the two are exclusive by design: an arrestable
 * offence is settled in Azkaban and carries <b>no</b> fine, while a paperwork offence is settled with money
 * and never sends anyone. Nothing is punished twice, and no offence is punished not at all —
 * {@code MinistryFineTest} pins the invariant so a new offence cannot be added that falls between them.
 */
@NullMarked
public enum MagicalOffence implements StringRepresentable {

    /** Killing curse. The single worst thing on the books. */
    AVADA_KEDAVRA("avada_kedavra", 45.0f, true, 0),
    /** Torture. */
    CRUCIO("crucio", 32.0f, true, 0),
    /** Seizing another's will. */
    IMPERIO("imperio", 28.0f, true, 0),
    /**
     * Transforming while unregistered — a fine and a file, not a manhunt. Ten Galleons: heavy enough that
     * an unregistered Animagus who transforms habitually feels it, cheap enough that registering (free)
     * is obviously the better deal.
     */
    UNREGISTERED_ANIMAGUS("unregistered_animagus", 6.0f, false, 10 * CurrencyHelper.KNUTS_PER_GALLEON),
    /**
     * Apparating without a licence. Illegal, not impossible — the trio do it all through
     * <i>Deathly Hallows</i> — so it goes on the file and nothing is dispatched. Rated a third of
     * unregistered Animagi because the two are different kinds of offence, not different sizes of one:
     * an unregistered Animagus is concealing a capability, while an unlicensed Apparator has merely
     * skipped the paperwork for one everybody knows they have. A fine, not Azkaban — two Galleons, the
     * size of a parking ticket, because the trio do it all through <i>Deathly Hallows</i> and it should
     * stay affordable enough to be worth doing.
     */
    UNLICENSED_APPARITION("unlicensed_apparition", 2.0f, false, 2 * CurrencyHelper.KNUTS_PER_GALLEON),
    /** Being somewhere in Azkaban you have no business being. */
    AZKABAN_TRESPASS("azkaban_trespass", 15.0f, true, 0),
    /** Walking out of a sentence. Raises the stakes rather than settling them. */
    AZKABAN_BREAKOUT("azkaban_breakout", 40.0f, true, 0),
    /** Fighting back against the Aurors sent for you. */
    AUROR_ASSAULT("auror_assault", 20.0f, true, 0),
    /**
     * Walking into Ministry premises without the papers for it. Paperwork, not a manhunt — one Galleon,
     * the smallest fine on the books, because the punishment for wandering into the Atrium should be a
     * clerk with a form and not a cell.
     */
    MINISTRY_TRESPASS("ministry_trespass", 1.5f, false, CurrencyHelper.KNUTS_PER_GALLEON),
    /**
     * Presenting a forged Ministry licence, and being caught at it.
     *
     * <p>Arrestable, unlike every other paperwork offence here, and deliberately so: skipping a form is
     * an omission, but a forged warrant is a document manufactured to deceive the state, and the
     * difference between the two is the whole reason forging is worth a fifteen-percent risk per
     * dealing. Rated above unregistered Animagi and below the Unforgivables.
     */
    FORGED_DOCUMENTS("forged_documents", 18.0f, true, 0),
    /**
     * Passing devalued foreign coin. Fineable, at three Galleons — above skipping a form and below
     * concealing what you are, because the wizard who does this took something from whoever accepted
     * the coin, which the paperwork offences above do not.
     *
     * <p>Filing it here rather than inventing a shopkeeper-reputation counter is deliberate: every
     * offence already flows through {@code TraceService.report} into the standing system's
     * {@code OFFENCE} deed trigger, so a bad coin moves the same needle a bad curse does.
     */
    PASSING_DEVALUED_COIN("passing_devalued_coin", 4.0f, false, 3 * CurrencyHelper.KNUTS_PER_GALLEON);

    public static final Codec<MagicalOffence> CODEC = StringRepresentable.fromEnum(MagicalOffence::values);

    private final String serializedName;
    private final float notoriety;
    private final boolean arrestable;
    private final int fineKnuts;

    MagicalOffence(String serializedName, float notoriety, boolean arrestable, int fineKnuts) {
        this.serializedName = serializedName;
        this.notoriety = notoriety;
        this.arrestable = arrestable;
        this.fineKnuts = fineKnuts;
    }

    /** Heat added on a first offence, before the repeat-offender multiplier. */
    public float notoriety() {
        return notoriety;
    }

    /** Whether committing this is enough to have Aurors dispatched. */
    public boolean arrestable() {
        return arrestable;
    }

    /**
     * The base fine in Knuts before priors are counted, or {@code 0} for offences answered with a
     * sentence instead. Always zero exactly when {@link #arrestable()} is true.
     */
    public int fineKnuts() {
        return fineKnuts;
    }

    /** True when this offence is settled with money rather than with time. */
    public boolean fineable() {
        return fineKnuts > 0;
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
