package at.koopro.wizardsandbeasts.pocket;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum PocketAccessMode implements StringRepresentable {
    PRIVATE("private"),
    SHARED_WHITELIST("shared_whitelist"),
    PUBLIC_ACCESS("public_access");

    public static final Codec<PocketAccessMode> CODEC =
            StringRepresentable.fromValues(PocketAccessMode::values);

    private final String serializedName;

    PocketAccessMode(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public PocketAccessMode next() {
        PocketAccessMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
