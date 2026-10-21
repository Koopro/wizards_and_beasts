package at.koopro.wizardsandbeasts.treasure;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * The curses goblins lay on what they bury, and the spell that lifts each.
 *
 * <p>Bill Weasley's trade, made a skill: a curse-breaker who knows the curse ({@code Revelio}, or a Gringotts
 * appraisal) casts its counter with the artefact in the other hand, one layer per cast. The wrong counter makes
 * the curse flare. Every counter is a spell the mod already casts; none is invented for this.
 *
 * <p>Each kind also has a hazard while it is carried with the curse unbroken ({@link CurseBreaking#tickHazards}), which
 * is what makes carrying a hoard home a decision rather than a formality.
 */
@NullMarked
public enum CurseKind implements StringRepresentable {
    /** A plain jinx: the bearer's limbs grow heavy. Lifted by Finite Incantatem. */
    JINX("finite_incantatem"),
    /** The Flagrante Curse of the Lestrange vault: the treasure burns whoever holds it. Quenched by Glacius. */
    FLAGRANTE("glacius"),
    /** A sealing hex: the casket will not open and saps the hand that tries. Undone by Alohomora. */
    SEALING("alohomora"),
    /** A withering curse, Gaunt-ring kin: it eats at the bearer. Its broken ward is mended with Reparo. */
    WITHERING("reparo");

    public static final Codec<CurseKind> CODEC = StringRepresentable.fromEnum(CurseKind::values);

    private final String counterSpell;

    CurseKind(String counterSpell) {
        this.counterSpell = counterSpell;
    }

    /** The bare spell id that lifts one layer. */
    public String counterSpell() {
        return counterSpell;
    }

    /** {@code wizards_and_beasts:flagrante} — the subject of a {@code curse_broken} activity. */
    public Identifier id() {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, getSerializedName());
    }

    public Component displayName() {
        return Component.translatable("subject." + WizardsAndBeastsMod.MODID + "." + getSerializedName());
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The curse a spell counters, or {@code null} for a spell that counters none. */
    public static @Nullable CurseKind counteredBy(String spellId) {
        String bare = bare(spellId);
        for (CurseKind kind : values()) {
            if (kind.counterSpell.equals(bare)) {
                return kind;
            }
        }
        return null;
    }

    /** Spell ids arrive bare or namespaced; curse-breaking compares the bare path. */
    public static String bare(String spellId) {
        int colon = spellId.indexOf(':');
        return colon < 0 ? spellId : spellId.substring(colon + 1);
    }
}
