package at.koopro.wizardsandbeasts.wand.customization;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Pure visual/structural descriptor for one variant within one wand slot.
 * No gameplay properties — those belong to a future WandModuleProperties system.
 */
public record WandModule(
        @NonNull Identifier id,
        @NonNull WandSlot slot,
        @NonNull String boneVariant,
        @NonNull Optional<Identifier> textureOverride,
        @NonNull Optional<Integer> tintOverride
) {
    public static final Codec<WandModule> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Identifier.CODEC.fieldOf("id").forGetter(WandModule::id),
            WandSlot.CODEC.fieldOf("slot").forGetter(WandModule::slot),
            Codec.STRING.fieldOf("bone_variant").forGetter(WandModule::boneVariant),
            Identifier.CODEC.optionalFieldOf("texture_override").forGetter(WandModule::textureOverride),
            Codec.INT.optionalFieldOf("tint_override").forGetter(WandModule::tintOverride)
    ).apply(inst, WandModule::new));

    /** Bone name as it appears in the geo.json (prefix + variant). */
    public String boneName() {
        return slot.bonePrefix() + boneVariant;
    }

    /** Factory for code-defined built-in modules (no texture or tint override). */
    public static WandModule builtin(@NonNull Identifier id, @NonNull WandSlot slot, @NonNull String boneVariant) {
        return new WandModule(id, slot, boneVariant, Optional.empty(), Optional.empty());
    }
}
