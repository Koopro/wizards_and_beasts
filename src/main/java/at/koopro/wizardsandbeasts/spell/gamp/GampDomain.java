package at.koopro.wizardsandbeasts.spell.gamp;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

public enum GampDomain implements StringRepresentable {
    FOOD_CONJURATION(EnforcementType.SOFT_PENALTY, "food_conjuration"),
    CURRENCY_CONJURATION(EnforcementType.SOFT_PENALTY, "currency_conjuration"),
    LIFE_CREATION(EnforcementType.HARD_REJECT, "life_creation"),
    LOVE_CONJURATION(EnforcementType.HARD_REJECT, "love_conjuration"),
    INFORMATION_GENESIS(EnforcementType.SOFT_PENALTY, "information_genesis");

    public static final com.mojang.serialization.Codec<GampDomain> CODEC =
            StringRepresentable.fromEnum(GampDomain::values);

    private final EnforcementType enforcement;
    private final String serializedName;
    private final Component loreMessage;

    GampDomain(EnforcementType enforcement, String serializedName) {
        this.enforcement = enforcement;
        this.serializedName = serializedName;
        this.loreMessage = Component.translatable("gamps_law.wizards_and_beasts." + serializedName);
    }

    public @NonNull EnforcementType enforcement() {
        return enforcement;
    }

    public @NonNull Component loreMessage() {
        return loreMessage;
    }

    @Override
    public @NonNull String getSerializedName() {
        return serializedName;
    }
}
