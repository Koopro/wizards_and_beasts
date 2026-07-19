package at.koopro.wizardsandbeasts.corruption;

import at.koopro.wizardsandbeasts.spell.core.SpellIds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

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
     * Charges the caster for an Unforgivable they just successfully cast. No-op for every other spell, so
     * this is safe to call on the common cast path.
     */
    public static void onCast(ServerPlayer caster, String spellId) {
        float toll = tollFor(spellId);
        if (toll <= 0.0f) {
            return;
        }
        float before = DarkCorruptionService.get(caster);
        float after = DarkCorruptionService.accrue(caster, toll);
        if (after <= before) {
            return; // already at the ceiling — nothing further to say
        }

        if (before < SEVERE_THRESHOLD && after >= SEVERE_THRESHOLD) {
            caster.displayClientMessage(Component.literal("Something in you has gone quiet, and does not stir again.")
                    .withStyle(ChatFormatting.DARK_RED), false);
        } else if (before < WARN_THRESHOLD && after >= WARN_THRESHOLD) {
            caster.displayClientMessage(Component.literal("The curse leaves a residue you cannot wash off.")
                    .withStyle(ChatFormatting.DARK_PURPLE), false);
        }
    }
}
