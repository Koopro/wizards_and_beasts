package at.koopro.wizardsandbeasts.client.spell.ui;

import at.koopro.wizardsandbeasts.spell.cast.SpellRejectCodes;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;

/**
 * Turns a stored reject code into a label for a diagnostic surface — the debug reject summary and
 * {@link SpellHudUiModel#topRejectReason()}, which report <em>which refusal a player hits most</em>
 * rather than narrating a single cast.
 *
 * <p>It holds no English of its own. It used to carry fifteen hardcoded sentences that paraphrased the
 * ones the cast path was showing, which is two vocabularies for one set of reasons and exactly the kind
 * of pair that drifts. Every label now resolves through {@link SpellRejectCodes#messageKeys()}, so
 * changing a refusal's wording is one edit in the lang file.
 *
 * <p>Unlike the live reject line, this reads {@code messageKeys()} directly rather than
 * {@link SpellRejectCodes#castRejectMessageKey}: site-owned codes have no <em>live</em> line to draw,
 * but they very much still need a name in a summary of what a player keeps running into.
 */
@NullMarked
public final class SpellRejectReasonFormatter {

    private SpellRejectReasonFormatter() {}

    /**
     * A label for {@code reason}, which may carry a {@code :detail} suffix.
     *
     * <p>Falls back to the bare code for anything unmapped. That is deliberate: an unrecognised code on
     * a debug surface should show the code, not a friendly sentence that hides which one it was.
     */
    public static Component toHudLabel(String reason) {
        if (reason == null || reason.isBlank()) {
            return Component.translatable("wandcraft.cast.reject.none");
        }
        String base = SpellRejectCodes.baseReason(reason);
        String key = SpellRejectCodes.messageKeys().get(base);
        return key == null ? Component.literal(base) : Component.translatable(key);
    }
}
