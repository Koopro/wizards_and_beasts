package at.koopro.wizardsandbeasts.broom;

import net.minecraft.network.chat.Component;

public enum BroomTier {
    SCHOOL(0, Component.translatable("broom_tier.wizards_and_beasts.school"), 1),
    STANDARD(1, Component.translatable("broom_tier.wizards_and_beasts.standard"), 2),
    RACING(2, Component.translatable("broom_tier.wizards_and_beasts.racing"), 4),
    ELITE(3, Component.translatable("broom_tier.wizards_and_beasts.elite"), 8),
    LEGENDARY(4, Component.translatable("broom_tier.wizards_and_beasts.legendary"), 12);

    private final int sortIndex;
    private final Component displayName;
    private final int baseRepairCost;

    BroomTier(int sortIndex, Component displayName, int baseRepairCost) {
        this.sortIndex = sortIndex;
        this.displayName = displayName;
        this.baseRepairCost = baseRepairCost;
    }

    public int sortIndex() {
        return sortIndex;
    }

    public Component displayName() {
        return displayName;
    }

    public int baseRepairCost() {
        return baseRepairCost;
    }
}
