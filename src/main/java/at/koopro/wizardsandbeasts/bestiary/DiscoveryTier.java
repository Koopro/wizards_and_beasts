package at.koopro.wizardsandbeasts.bestiary;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * How much of a creature's page a player has earned, the way a field naturalist earns it: by seeing, watching,
 * handling and finally understanding the animal — never by killing it. {@link #UNKNOWN} is the absence of an
 * entry rather than a stored value; {@code BestiaryDataHelper} never writes it.
 *
 * <p>How a player moves between tiers is decided in {@link EncounterRule}; this enum only orders the tiers and
 * names their copy.
 *
 * <h2>Renamed 2026-09-17, indices unchanged</h2>
 * The tiers used to be {@code UNDISCOVERED, SIGHTED, ENCOUNTERED, STUDIED, MASTERED}. Each new tier sits at the
 * index of the old one it replaces, so a saved index means the same depth of knowledge before and after. Saves
 * are now written by index ({@code PlayerBestiaryData}), and the old names are read by {@link #fromLegacySave}.
 */
public enum DiscoveryTier {
    /** Never seen. */
    UNKNOWN(0),
    /** Seen with your own eyes, or met in a fight. */
    ENCOUNTERED(1),
    /** Watched calmly for long enough to learn how it lives. */
    OBSERVED(2),
    /** Fed, handled, harvested from or otherwise worked with. */
    STUDIED(3),
    /** Its signature behaviour witnessed, or its trust won. The page is complete. */
    KNOWN(4);

    /**
     * Datapack form (harvest rules, deeds): the tier's name. The old names that are not ambiguous are still
     * accepted, so a pack written before the rename keeps working — {@code UNDISCOVERED}, {@code SIGHTED} and
     * {@code MASTERED}. The old {@code ENCOUNTERED} is ambiguous (it was index 2, and is now the name of index 1)
     * and reads as the new meaning; a pack that meant the old one should say {@code OBSERVED}.
     *
     * <p>{@code comapFlatMap} rather than {@code xmap}: an unknown name must come back as a parse error for one
     * file, not an exception that takes the whole datapack reload down.
     */
    public static final Codec<DiscoveryTier> CODEC = Codec.STRING.comapFlatMap(
            raw -> {
                DiscoveryTier tier = byName(raw);
                return tier != null
                        ? DataResult.success(tier)
                        : DataResult.error(() -> "Unknown discovery tier: " + raw);
            },
            DiscoveryTier::name);

    /** Save form: the index. Stable across renames by construction. */
    public static final Codec<DiscoveryTier> INDEX_CODEC = Codec.INT.comapFlatMap(
            index -> index >= 0 && index < values().length
                    ? DataResult.success(values()[index])
                    : DataResult.error(() -> "Discovery tier index out of range: " + index),
            DiscoveryTier::tierIndex);

    private final int tierIndex;
    private final String translationKey;
    private final String hintKey;

    DiscoveryTier(int tierIndex) {
        this.tierIndex = tierIndex;
        String slug = name().toLowerCase(Locale.ROOT);
        this.translationKey = "bestiary.wizards_and_beasts.tier." + slug;
        this.hintKey = "bestiary.wizards_and_beasts.tier." + slug + ".hint";
    }

    public int tierIndex() { return tierIndex; }

    /** The tier's own name, for the detail pane header. */
    public Component displayName() { return Component.translatable(translationKey); }

    /**
     * What the player must still do to move past this tier. {@link #KNOWN} has a hint too — it says the entry is
     * complete, because a blank line under a full page reads as a rendering fault.
     */
    public Component unlockHint() { return Component.translatable(hintKey); }

    public DiscoveryTier next() { return values()[Math.min(values().length - 1, ordinal() + 1)]; }

    public boolean atLeast(DiscoveryTier other) {
        return ordinal() >= other.ordinal();
    }

    /** A datapack name: current names first, then the unambiguous pre-rename ones. */
    public static @Nullable DiscoveryTier byName(String raw) {
        for (DiscoveryTier tier : values()) {
            if (tier.name().equals(raw)) {
                return tier;
            }
        }
        return switch (raw) {
            case "UNDISCOVERED" -> UNKNOWN;
            case "SIGHTED" -> ENCOUNTERED;
            case "MASTERED" -> KNOWN;
            default -> null;
        };
    }

    /**
     * A tier name from a save written before the rename, where every name meant its old index. Here the old
     * {@code ENCOUNTERED} is not ambiguous: it was index 2 and becomes {@link #OBSERVED}.
     */
    public static @Nullable DiscoveryTier fromLegacySave(String raw) {
        return switch (raw) {
            case "UNDISCOVERED" -> UNKNOWN;
            case "SIGHTED" -> ENCOUNTERED;
            case "ENCOUNTERED" -> OBSERVED;
            case "STUDIED" -> STUDIED;
            case "MASTERED" -> KNOWN;
            default -> null;
        };
    }
}
