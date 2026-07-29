package at.koopro.wizardsandbeasts.wand.customization;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * A named whole-wand configuration — every slot filled in one pick.
 *
 * <p>The module system is per-slot by design, which is right for building a wand but poor for
 * recognising one: reproducing a wand from the films means knowing that it is a talon grip on a
 * barked shaft with a clawed tip, and nobody wants to look that up three times. A preset is that
 * combination under the name people actually use for it.
 *
 * <p>A preset stores a {@link WandConfiguration} rather than its own slot fields, so applying one is
 * the same write the config command already performs and nothing downstream needs to know presets
 * exist. It carries a {@link Codec} for the same reason {@link WandModule} does — datapacks can add
 * their own without a code change.
 */
public record WandPreset(
        @NonNull Identifier id,
        @NonNull String displayName,
        @NonNull WandConfiguration configuration
) {

    public static final Codec<WandPreset> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Identifier.CODEC.fieldOf("id").forGetter(WandPreset::id),
            Codec.STRING.fieldOf("display_name").forGetter(WandPreset::displayName),
            WandConfiguration.CODEC.fieldOf("configuration").forGetter(WandPreset::configuration)
    ).apply(inst, WandPreset::new));
}
