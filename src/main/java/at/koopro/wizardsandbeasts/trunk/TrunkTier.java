package at.koopro.wizardsandbeasts.trunk;

public enum TrunkTier {
    TIER_1(12, 1, "tier_1"),
    TIER_2(20, 3, "tier_2"),
    TIER_3(32, 7, "tier_3");

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
