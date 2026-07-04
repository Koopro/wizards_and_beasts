package at.koopro.wizardsandbeasts.trunk;

import com.mojang.serialization.Codec;

public enum TrunkTier {
    TIER_1(12, 1, "tier_1"),
    TIER_2(20, 3, "tier_2"),
    TIER_3(32, 7, "tier_3");

    /** Name-based codec (TIER_1/TIER_2/TIER_3) for {@code TrunkBlock} serialization. */
    public static final Codec<TrunkTier> CODEC = Codec.STRING.xmap(TrunkTier::valueOf, Enum::name);

    private final int maxRadius;
    private final int lockCount;
    private final String keySuffix;

    TrunkTier(int maxRadius, int lockCount, String keySuffix) {
        this.maxRadius = maxRadius;
        this.lockCount = lockCount;
        this.keySuffix = keySuffix;
    }

    public int maxRadius() { return maxRadius; }
    public int lockCount() { return lockCount; }
    public String getTranslationKey() { return "trunk.tier.wizards_and_beasts." + keySuffix; }
}
