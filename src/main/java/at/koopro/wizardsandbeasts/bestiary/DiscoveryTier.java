package at.koopro.wizardsandbeasts.bestiary;

import com.mojang.serialization.Codec;

public enum DiscoveryTier {
    UNDISCOVERED(0, "Observe this creature to begin your notes."),
    SIGHTED(1, "Observe this creature from within 8 blocks."),
    ENCOUNTERED(2, "Survive a close encounter to expand this entry."),
    STUDIED(3, "Study this creature further to reveal deeper lore."),
    MASTERED(4, "Master knowledge through repeated encounters.");

    public static final Codec<DiscoveryTier> CODEC = Codec.STRING.xmap(DiscoveryTier::valueOf, DiscoveryTier::name);

    private final int tierIndex;
    private final String unlockHint;

    DiscoveryTier(int tierIndex, String unlockHint) {
        this.tierIndex = tierIndex;
        this.unlockHint = unlockHint;
    }

    public int tierIndex() { return tierIndex; }
    public String unlockHint() { return unlockHint; }
    public DiscoveryTier next() { return values()[Math.min(values().length - 1, ordinal() + 1)]; }
}
