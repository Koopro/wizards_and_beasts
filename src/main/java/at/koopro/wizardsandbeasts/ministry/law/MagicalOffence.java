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
 * <p>{@link Remedy} names how each offence is answered, and exactly one applies: an arrestable offence is
 * settled in Azkaban and carries <b>no</b> fine, a paperwork offence is settled with money and never sends
 * anyone, and a caution is a formal warning on the file. Nothing is punished twice, and no offence is punished
 * not at all — {@code MinistryFineTest} pins the invariant so a new offence cannot fall between them.
 */
@NullMarked
public enum MagicalOffence implements StringRepresentable {

    /** Killing curse. The single worst thing on the books. */
    AVADA_KEDAVRA("avada_kedavra", 45.0f, Remedy.SENTENCE, 0),
    /** Torture. */
    CRUCIO("crucio", 32.0f, Remedy.SENTENCE, 0),
    /** Seizing another's will. */
    IMPERIO("imperio", 28.0f, Remedy.SENTENCE, 0),
    /**
     * Transforming while unregistered — a fine and a file, not a manhunt. Ten Galleons: heavy enough that
     * an unregistered Animagus who transforms habitually feels it, cheap enough that registering (free)
     * is obviously the better deal.
     */
    UNREGISTERED_ANIMAGUS("unregistered_animagus", 6.0f, Remedy.FINE, 10 * CurrencyHelper.KNUTS_PER_GALLEON),
    /**
     * Apparating without a licence. Illegal, not impossible — the trio do it all through
     * <i>Deathly Hallows</i> — so it goes on the file and nothing is dispatched. Rated a third of
     * unregistered Animagi because the two are different kinds of offence, not different sizes of one:
     * an unregistered Animagus is concealing a capability, while an unlicensed Apparator has merely
     * skipped the paperwork for one everybody knows they have. A fine, not Azkaban — two Galleons, the
     * size of a parking ticket, because the trio do it all through <i>Deathly Hallows</i> and it should
     * stay affordable enough to be worth doing.
     */
    UNLICENSED_APPARITION("unlicensed_apparition", 2.0f, Remedy.FINE, 2 * CurrencyHelper.KNUTS_PER_GALLEON),
    /** Being somewhere in Azkaban you have no business being. */
    AZKABAN_TRESPASS("azkaban_trespass", 15.0f, Remedy.SENTENCE, 0),
    /** Walking out of a sentence. Raises the stakes rather than settling them. */
    AZKABAN_BREAKOUT("azkaban_breakout", 40.0f, Remedy.SENTENCE, 0),
    /** Fighting back against the Aurors sent for you. */
    AUROR_ASSAULT("auror_assault", 20.0f, Remedy.SENTENCE, 0),
    /**
     * Walking into Ministry premises without the papers for it. Paperwork, not a manhunt — one Galleon,
     * the smallest fine on the books, because the punishment for wandering into the Atrium should be a
     * clerk with a form and not a cell.
     */
    MINISTRY_TRESPASS("ministry_trespass", 1.5f, Remedy.FINE, CurrencyHelper.KNUTS_PER_GALLEON),
    /**
     * Presenting a forged Ministry licence, and being caught at it.
     *
     * <p>Arrestable, unlike every other paperwork offence here, and deliberately so: skipping a form is
     * an omission, but a forged warrant is a document manufactured to deceive the state, and the
     * difference between the two is the whole reason forging is worth a fifteen-percent risk per
     * dealing. Rated above unregistered Animagi and below the Unforgivables.
     */
    FORGED_DOCUMENTS("forged_documents", 18.0f, Remedy.SENTENCE, 0),
    /**
     * Passing devalued foreign coin. Fineable, at three Galleons — above skipping a form and below
     * concealing what you are, because the wizard who does this took something from whoever accepted
     * the coin, which the paperwork offences above do not.
     *
     * <p>Filing it here rather than inventing a shopkeeper-reputation counter is deliberate: every
     * offence already flows through {@code TraceService.report} into the standing system's
     * {@code OFFENCE} deed trigger, so a bad coin moves the same needle a bad curse does.
     */
    PASSING_DEVALUED_COIN("passing_devalued_coin", 4.0f, Remedy.FINE, 3 * CurrencyHelper.KNUTS_PER_GALLEON),
    /**
     * Magic worked by a wizard under seventeen outside school, in breach of the Decree for the Reasonable
     * Restriction of Underage Sorcery. A caution: canon answers it with a warning letter, and a second one
     * with a hearing ("a second offence would lead to expulsion", <i>Chamber of Secrets</i> ch. 2) — never a
     * bill. Filed only when a hearing rules on it, not when the Trace first notices.
     */
    UNDERAGE_MAGIC("underage_magic", 1.0f, Remedy.CAUTION, 0),
    /**
     * Magic Muggles saw, found against the wizard at a hearing. Five Galleons: dearer than any paperwork,
     * because the Obliviators had to be sent. Gameplay figure; canon names the breach, not a tariff.
     */
    STATUTE_OF_SECRECY_BREACH("statute_of_secrecy_breach", 5.0f, Remedy.FINE, 5 * CurrencyHelper.KNUTS_PER_GALLEON),
    /**
     * Dark magic or a dangerous creature loosed in front of Muggles. Arrestable: the breach and the danger
     * together are no longer a matter for a bill.
     */
    GRAVE_SECRECY_BREACH("grave_secrecy_breach", 25.0f, Remedy.SENTENCE, 0),
    /**
     * Selling or buying a Ministry-controlled substance (item tag {@code restricted_ingredient}) outside a licensed
     * counter. The cheapest of the trade offences: the goods are legal to hold, only the deal skipped the paperwork.
     * Filed when a fence's deal reaches the Ministry ({@code market.IllicitDealing}).
     */
    TRAFFICKING_RESTRICTED("trafficking_restricted", 5.0f, Remedy.FINE, 4 * CurrencyHelper.KNUTS_PER_GALLEON),
    /**
     * Handling a wand another wizard is master of, or treasure from a Gringotts-claimed site that was never
     * surrendered to the bank. Dearer than a restricted sale: someone else was robbed.
     */
    HANDLING_STOLEN_GOODS("handling_stolen_goods", 8.0f, Remedy.FINE, 6 * CurrencyHelper.KNUTS_PER_GALLEON),
    /**
     * Paying for magic the law forbids: a memory charm on the witnesses to a crime, a forged endorsement. The
     * service is the dealer's crime; buying it is this one.
     */
    PROCURING_ILLEGAL_MAGIC("procuring_illegal_magic", 10.0f, Remedy.FINE, 8 * CurrencyHelper.KNUTS_PER_GALLEON),
    /**
     * Trading goods nobody may trade (item tag {@code contraband}): dark artefacts, basilisk parts, a Hand of
     * Glory. Still a fine rather than a sentence, because the goods are the danger and the fine follows the
     * money — but the heaviest one, and twice in quick succession puts a wizard on the Aurors' list.
     */
    DEALING_CONTRABAND("dealing_contraband", 14.0f, Remedy.FINE, 12 * CurrencyHelper.KNUTS_PER_GALLEON),
    /**
     * Clause Three of the Code of Wand Use: a non-human carrying a wand, seen by a Ministry official
     * ({@code CodeOfWandUse}). Winky's offence under the Dark Mark (GoF ch.9). A fine, not a sentence: the wand is
     * confiscated in canon and nobody went to Azkaban for holding one.
     */
    NON_HUMAN_WAND("non_human_wand", 3.0f, Remedy.FINE, 2 * CurrencyHelper.KNUTS_PER_GALLEON),
    /**
     * Breeding an XXXX-class beast without a Dangerous Creatures licence, in an official's sight
     * ({@code ministry.licence.DangerousCreatureLicensing}). Six Galleons: dearer than skipping a form, because a
     * dragon raised without the Ministry knowing is a danger to the neighbours, not just a filing error.
     */
    UNLICENSED_DANGEROUS_CREATURE("unlicensed_dangerous_creature", 4.0f, Remedy.FINE, 6 * CurrencyHelper.KNUTS_PER_GALLEON),
    /**
     * Paying a Ministry clerk to lose a case file, and being caught at it ({@code FenceServices} {@code bribe_clerk}).
     * The heaviest fine on the books, fifteen Galleons, and the case it was meant to bury goes straight to a summons:
     * a bribe that fails costs more than the hearing it tried to avoid. A fine rather than a sentence — corrupting
     * an official is serious, but it is not a curse.
     */
    BRIBERY_OF_OFFICIAL("bribery_of_official", 12.0f, Remedy.FINE, 15 * CurrencyHelper.KNUTS_PER_GALLEON),
    /**
     * Killing a protected species (unicorn, golden snidget, phoenix, thestral, occamy -- the entity tag
     * {@code protected_species}) in an official's sight ({@code Poaching}). Eight Galleons: dearer than breeding a
     * dangerous beast without a licence, because this one is dead. A fine, not a sentence: canon's Ministry regulates
     * beasts through licences and the Beast Division, not Azkaban.
     */
    POACHING("poaching", 7.0f, Remedy.FINE, 8 * CurrencyHelper.KNUTS_PER_GALLEON);

    /** How an offence is answered once it is on the file. Exactly one per offence. */
    public enum Remedy {
        /** Settled in Azkaban; Aurors may be sent. */
        SENTENCE,
        /** Settled with money from the offender's vault. */
        FINE,
        /** A formal warning on the file, and nothing else. */
        CAUTION
    }

    public static final Codec<MagicalOffence> CODEC = StringRepresentable.fromEnum(MagicalOffence::values);

    private final String serializedName;
    private final float notoriety;
    private final Remedy remedy;
    private final int fineKnuts;

    MagicalOffence(String serializedName, float notoriety, Remedy remedy, int fineKnuts) {
        this.serializedName = serializedName;
        this.notoriety = notoriety;
        this.remedy = remedy;
        this.fineKnuts = fineKnuts;
    }

    /** How this offence is answered. */
    public Remedy remedy() {
        return remedy;
    }

    /** Heat added on a first offence, before the repeat-offender multiplier. */
    public float notoriety() {
        return notoriety;
    }

    /** Whether committing this is enough to have Aurors dispatched. */
    public boolean arrestable() {
        return remedy == Remedy.SENTENCE;
    }

    /**
     * The base fine in Knuts before priors are counted, or {@code 0} for offences answered with a
     * sentence or a caution instead. Positive exactly when {@link #remedy()} is {@link Remedy#FINE}.
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
