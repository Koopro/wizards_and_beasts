package at.koopro.wizardsandbeasts.form;

import net.minecraft.resources.Identifier;

import java.util.EnumSet;

/**
 * Defines a visual form that a player can assume.
 * <p>
 * Each form specifies a model type, texture, size profile reference,
 * and optional render flags for visual effects.
 *
 * @param formId          unique identifier (e.g. "werewolf_wolf")
 * @param displayName     human-readable name
 * @param modelType       what kind of model to render
 * @param texturePath     texture resource location
 * @param sizeProfileId   ID of the {@link SizeProfile} to apply
 * @param renderFlags     set of visual effect flags
 * @param firstPersonScale scale adjustment for first-person hand rendering (1.0 = normal)
 */
public record PlayerForm(
        String formId,
        String displayName,
        ModelType modelType,
        Identifier texturePath,
        String sizeProfileId,
        EnumSet<RenderFlag> renderFlags,
        float firstPersonScale
) {

    public boolean hasFlag(RenderFlag flag) {
        return renderFlags.contains(flag);
    }

    public int renderFlagBitmask() {
        return RenderFlag.toBitmask(renderFlags);
    }
}
