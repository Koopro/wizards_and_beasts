package at.koopro.wizardsandbeasts.heritage.appearance;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Mechanism B — the player model is replaced outright while a trigger holds.
 *
 * <p>The player's own skin does not render, which is the correct reading: a transformed werewolf is
 * not a person wearing a wolf costume. Armour and held items are suppressed with it — visually only,
 * the stats are untouched.
 *
 * <p><b>This arm declares an existing form; it does not invent one.</b> {@code formId} names a
 * {@code FormRegistry} entry that already ships, and the optional asset fields override that entry's
 * declared model, texture and animation. That is the whole point of the override-layer ruling: the
 * hardcoded registry stays authoritative as a fallback, and the datapack exists so a mechanism
 * assignment can be changed, and an asset swapped, without a recompile.
 *
 * <p>An entry may reference a form whose trigger does not exist in code. That is deliberate and
 * matches the broom {@code brake}/{@code summon} precedent: the appearance is declared, left unwired,
 * and logged to {@code MIGRATION_DELTAS.md}. Werewolf moon-phase, Veela rage and merfolk water
 * triggers are all absent today.
 *
 * @param formId    the {@code FormRegistry} id this entry declares, e.g. {@code "werewolf_wolf"}
 * @param model     optional override for the form's geometry
 * @param texture   optional override for the form's texture
 * @param animation optional override for the form's animation file
 * @param trigger   the state that must hold for this form to apply, as a free-form key resolved by
 *                  the trigger layer. Absent means "whenever the form is active", which is the only
 *                  reachable behaviour today because the form id is what the server actually syncs
 */
public record FormAppearance(
        String formId,
        Optional<Identifier> model,
        Optional<Identifier> texture,
        Optional<Identifier> animation,
        Optional<String> trigger
) implements AppearanceMechanism {

    public static final MapCodec<FormAppearance> CODEC =
            RecordCodecBuilder.<FormAppearance>mapCodec(instance -> instance.group(
                    Codec.STRING.fieldOf("formId").forGetter(FormAppearance::formId),
                    Identifier.CODEC.optionalFieldOf("model").forGetter(FormAppearance::model),
                    Identifier.CODEC.optionalFieldOf("texture").forGetter(FormAppearance::texture),
                    Identifier.CODEC.optionalFieldOf("animation").forGetter(FormAppearance::animation),
                    Codec.STRING.optionalFieldOf("trigger").forGetter(FormAppearance::trigger)
            ).apply(instance, FormAppearance::new)).validate(FormAppearance::validate);

    @Override
    public Type type() {
        return Type.FORM;
    }

    private static DataResult<FormAppearance> validate(FormAppearance form) {
        if (form.formId.isBlank()) {
            return DataResult.error(() -> "form appearance has a blank formId");
        }
        if (form.trigger.isPresent() && form.trigger.get().isBlank()) {
            return DataResult.error(() -> "form appearance '" + form.formId + "' has a blank trigger; "
                    + "omit the field instead");
        }
        return DataResult.success(form);
    }
}
