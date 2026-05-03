package at.koopro.wizardsandbeasts.brew.def;

import at.koopro.wizardsandbeasts.brew.Brew;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;

import java.util.List;
import java.util.Optional;

/**
 * JSON-friendly description of a brew. Loaded by {@link BrewReloadListener}
 * from {@code data/<ns>/<modId>/brews/*.json} and adapted to a {@link Brew}.
 *
 * <p>Mob effects are referenced by registry id and resolved lazily — if the
 * id is unknown the entry is dropped with a warning rather than failing the
 * whole reload.
 */
public record BrewDefinition(
        String displayName,
        int color,
        List<EffectEntry> effects,
        Optional<String> flavorText) {

    public static final Codec<BrewDefinition> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("displayName").forGetter(BrewDefinition::displayName),
            Codec.INT.fieldOf("color").forGetter(BrewDefinition::color),
            EffectEntry.CODEC.listOf().fieldOf("effects").forGetter(BrewDefinition::effects),
            Codec.STRING.optionalFieldOf("flavorText").forGetter(BrewDefinition::flavorText)
    ).apply(inst, BrewDefinition::new));

    /**
     * Bakes this definition into a {@link Brew}, dropping any effect whose
     * mob-effect id is unknown to the live registry. Returns {@code null} if
     * every effect was dropped (a brew with zero effects is rejected; if
     * that's intentional, declare {@code [{ "id": "minecraft:luck", ... }]}).
     */
    public Brew toBrew(String fullId) {
        List<Brew.EffectSpec> specs = effects.stream()
                .map(EffectEntry::resolve)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (specs.isEmpty()) return null;
        return new Brew(fullId, displayName, color, specs, flavorText.orElse(null));
    }

    /** Single mob-effect entry on a brew. */
    public record EffectEntry(Identifier id, int duration, int amplifier, boolean ambient) {

        public static final Codec<EffectEntry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Identifier.CODEC.fieldOf("id").forGetter(EffectEntry::id),
                Codec.INT.fieldOf("duration").forGetter(EffectEntry::duration),
                Codec.INT.optionalFieldOf("amplifier", 0).forGetter(EffectEntry::amplifier),
                Codec.BOOL.optionalFieldOf("ambient", false).forGetter(EffectEntry::ambient)
        ).apply(inst, EffectEntry::new));

        Brew.EffectSpec resolve() {
            MobEffect effect = BuiltInRegistries.MOB_EFFECT.getValue(id);
            if (effect == null) return null;
            Holder<MobEffect> holder = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect);
            return new Brew.EffectSpec(holder, duration, amplifier, ambient);
        }
    }
}
