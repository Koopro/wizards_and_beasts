package at.koopro.wizardsandbeasts.wand.customization;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * JSON-parsed representation of a datapack wand module.
 * The module id is NOT present in the JSON body — it is derived from the file path by WandModuleLoader.
 */
public record WandModuleSpec(
        @NonNull WandSlot slot,
        @NonNull String boneVariant,
        @NonNull Optional<Identifier> textureOverride,
        @NonNull Optional<Integer> tintOverride
) {
    /** Accepts hex strings ("0xFFAA00") or decimal integers for tint_override. */
    private static final Codec<Integer> HEX_OR_INT_CODEC = Codec.STRING.comapFlatMap(
            s -> {
                try {
                    String stripped = s.startsWith("0x") || s.startsWith("0X") ? s.substring(2) : s;
                    return DataResult.success((int) Long.parseLong(stripped, 16));
                } catch (NumberFormatException e) {
                    return DataResult.error(() -> "Invalid hex color: " + s);
                }
            },
            i -> "0x" + String.format("%06X", i & 0xFFFFFF)
    );

    public static final Codec<WandModuleSpec> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            WandSlot.CODEC.fieldOf("slot").forGetter(WandModuleSpec::slot),
            Codec.STRING.fieldOf("bone_variant").forGetter(WandModuleSpec::boneVariant),
            Identifier.CODEC.optionalFieldOf("texture_override").forGetter(WandModuleSpec::textureOverride),
            HEX_OR_INT_CODEC.optionalFieldOf("tint_override").forGetter(WandModuleSpec::tintOverride)
    ).apply(inst, WandModuleSpec::new));
}
