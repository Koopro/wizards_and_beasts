package at.koopro.wizardsandbeasts.bestiary;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * How much of an entry a player has earned. {@link #UNDISCOVERED} is the absence of an entry rather
 * than a stored value — {@code BestiaryDataHelper} never writes it.
 *
 * <p>Advancement is decided in one place, {@link EncounterRule}; this enum only orders the tiers and
 * names their copy.
 */
public enum DiscoveryTier {
    UNDISCOVERED(0),
    SIGHTED(1),
    ENCOUNTERED(2),
    STUDIED(3),
    MASTERED(4);

    /**
     * Serializes as the constant name. {@code comapFlatMap} rather than {@code xmap}: {@code valueOf}
     * throws on an unknown name, and a codec that throws takes the whole datapack reload down with it
     * instead of reporting one bad file. That is reachable from real content — a harvest rule's
     * {@code minTier} and a saved player record both parse through here — so a typo has to come back as
     * a parse error, not a crash.
     */
    public static final Codec<DiscoveryTier> CODEC = Codec.STRING.comapFlatMap(
            raw -> {
                for (DiscoveryTier tier : values()) {
                    if (tier.name().equals(raw)) {
                        return com.mojang.serialization.DataResult.success(tier);
                    }
                }
                return com.mojang.serialization.DataResult.error(
                        () -> "Unknown discovery tier: " + raw);
            },
            DiscoveryTier::name);

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
     * What the player must still do to move past this tier.
     *
     * <p>Translated rather than the hardcoded English sentence this used to hold: it is the copy a
     * new player reads first, and it was the only player-facing string in the Bestiary that could
     * not be localised. {@link #MASTERED} has a hint too — it says the entry is complete, because a
     * blank line under a full page reads as a rendering fault.
     */
    public Component unlockHint() { return Component.translatable(hintKey); }

    public DiscoveryTier next() { return values()[Math.min(values().length - 1, ordinal() + 1)]; }
}
