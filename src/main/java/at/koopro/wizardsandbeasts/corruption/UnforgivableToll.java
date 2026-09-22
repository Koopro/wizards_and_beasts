package at.koopro.wizardsandbeasts.corruption;

import at.koopro.wizardsandbeasts.spell.core.SpellIds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;

import java.util.Set;

/**
 * The price of casting an Unforgivable Curse.
 *
 * <p>The mod already tracked Dark Corruption, but only from dark <i>artefacts</i> — a worn Horcrux, the
 * Resurrection Stone, ink in Riddle's diary. The Unforgivables themselves cost nothing, which inverts the
 * lore: these three curses are the defining corrupting acts, the ones you have to genuinely mean before
 * they work at all. Casting one now stains the caster.
 *
 * <p>Weighted by what the curse actually does: killing outright costs the most, torture next, and bending
 * another person's will the least of the three — still far above any artefact's slow drip.
 */
@NullMarked
public final class UnforgivableToll {

    private static final float AVADA_KEDAVRA = 12.0f;
    private static final float CRUCIO = 8.0f;
    private static final float IMPERIO = 6.0f;

    /** Corruption at which the caster starts being told what it is costing them. */
    private static final float WARN_THRESHOLD = 40.0f;
    private static final float SEVERE_THRESHOLD = 75.0f;

    private UnforgivableToll() {}

    /** Corruption cost of {@code spellId}, or 0 if it is not an Unforgivable. */
    public static float tollFor(String spellId) {
        if (SpellIds.matches(spellId, "avada_kedavra")) {
            return AVADA_KEDAVRA;
        }
        if (SpellIds.matches(spellId, "crucio")) {
            return CRUCIO;
        }
        if (SpellIds.matches(spellId, "imperio")) {
            return IMPERIO;
        }
        return 0.0f;
    }

    public static boolean isUnforgivable(String spellId) {
        return tollFor(spellId) > 0.0f;
    }

    /**
     * Unforgivables that are paid for where the curse actually lands, not where the wand moves.
     *
     * <p>These two were being charged twice: the flat toll here at cast, and again from their own
     * logic — Imperio another 8 when the control seizes, Crucio 5 per channel tick scaled by intent.
     * Halving the numbers would have missed the point, because the two charges are not two helpings
     * of one price; they are prices on two different things, and only one of them is the act.
     *
     * <p>For both, the moment that means something is the second one. Imperio's weight is in
     * dominating another will, not in pointing a wand — charging at cast billed a wizard for a curse
     * that missed. Crucio's whole design is escalation: {@code crucioHoldTicks} makes holding the
     * curse on one victim worse the longer it runs, and a flat charge at cast flatly contradicts
     * that, pricing a curse released at once the same as twenty seconds of torture.
     *
     * <p>So the cast-time toll is skipped for these and their own call sites pay instead, through
     * {@link #charge}. Avada Kedavra is not here: it is one act with one outcome, and the cast is
     * the only moment it has.
     */
    private static final Set<String> PAID_WHERE_THE_CURSE_LANDS = Set.of("crucio", "imperio");

    /**
     * Charges the caster for an Unforgivable they just successfully cast. No-op for every other spell, so
     * this is safe to call on the common cast path.
     *
     * <p>Also a no-op for the curses in {@link #PAID_WHERE_THE_CURSE_LANDS}, which bill themselves.
     */
    public static void onCast(ServerPlayer caster, String spellId) {
        for (String paidElsewhere : PAID_WHERE_THE_CURSE_LANDS) {
            if (SpellIds.matches(spellId, paidElsewhere)) {
                return;
            }
        }
        charge(caster, tollFor(spellId));
    }

    /**
     * The one way an Unforgivable stains its caster.
     *
     * <p>Every call site goes through here so that the vocation scaling, the 0–100 clamp, the
     * persisted attachment and the mirrored display attribute all move together — the two curse call
     * sites used to write the attachment raw, which left the character sheet showing a stale number
     * until the next login because only {@link DarkCorruptionService} mirrors to the attribute.
     *
     * <p>{@code baseAmount} is pre-scaling: {@link DarkCorruptionService#accrue} applies the vocation
     * hook itself, so a caller that scales first would scale twice.
     */
    public static void charge(ServerPlayer caster, float baseAmount) {
        if (baseAmount <= 0.0f) {
            return;
        }
        float before = DarkCorruptionService.get(caster);
        float after = DarkCorruptionService.accrue(caster, baseAmount);
        if (after <= before) {
            return; // already at the ceiling — nothing further to say
        }

        if (before < SEVERE_THRESHOLD && after >= SEVERE_THRESHOLD) {
            PlayerFeedback.toast(caster, NoticeKind.WARN,
                    Component.translatable("corruption.wizards_and_beasts.hollow.title"),
                    Component.translatable("corruption.wizards_and_beasts.hollow.body"));
        } else if (before < WARN_THRESHOLD && after >= WARN_THRESHOLD) {
            PlayerFeedback.toast(caster, NoticeKind.WARN,
                    Component.translatable("corruption.wizards_and_beasts.residue.title"),
                    Component.translatable("corruption.wizards_and_beasts.residue.body"));
        }
    }
}
