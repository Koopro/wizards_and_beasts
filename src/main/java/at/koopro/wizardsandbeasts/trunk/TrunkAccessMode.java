package at.koopro.wizardsandbeasts.trunk;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum TrunkAccessMode implements StringRepresentable {
    SEALED("sealed"),
    KEYED("keyed"),
    OPEN("open");

    public static final Codec<TrunkAccessMode> CODEC =
            StringRepresentable.fromValues(TrunkAccessMode::values);

    private final String serializedName;

    TrunkAccessMode(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public String getTranslationKey() {
        return "item.wizards_and_beasts.trunk_access_mode." + serializedName;
    }

    public TrunkAccessMode next() {
        TrunkAccessMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
