package at.koopro.wizardsandbeasts.wand.customization;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public enum WandSlot implements StringRepresentable {
    HANDLE("handle_", "handle", true),
    SHAFT("shaft_", "shaft", true),
    TIP("tip_", "tip", true),
    CORE("core_", "core", false),
    ORNAMENT("ornament_", "ornament", false);

    public static final Codec<WandSlot> CODEC = StringRepresentable.fromEnum(WandSlot::values);

    private static final List<WandSlot> RENDER_ORDER = List.of(values());

    private final String bonePrefix;
    private final String slotId;
    private final boolean required;

    WandSlot(String bonePrefix, String slotId, boolean required) {
        this.bonePrefix = bonePrefix;
        this.slotId = slotId;
        this.required = required;
    }

    public String bonePrefix() {
        return bonePrefix;
    }

    @Override
    public String getSerializedName() {
        return slotId;
    }

    public String slotId() {
        return slotId;
    }

    public boolean isRequired() {
        return required;
    }

    public static Optional<WandSlot> byId(String id) {
        for (WandSlot slot : values()) {
            if (slot.slotId.equals(id)) return Optional.of(slot);
        }
        return Optional.empty();
    }

    /** Canonical render order: required slots first, then optional. */
    public static List<WandSlot> renderOrder() {
        return RENDER_ORDER;
    }
}
