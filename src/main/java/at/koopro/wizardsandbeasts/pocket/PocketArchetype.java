package at.koopro.wizardsandbeasts.pocket;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum PocketArchetype implements StringRepresentable {
    SCAMANDER_SANCTUARY("scamander_sanctuary"),
    ROOM_OF_REQUIREMENT("room_of_requirement"),
    CUSTOM_PLAYER_TEMPLATE("custom_player_template");

    public static final Codec<PocketArchetype> CODEC =
            StringRepresentable.fromValues(PocketArchetype::values);

    private final String serializedName;

    PocketArchetype(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public PocketArchetype next() {
        PocketArchetype[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
