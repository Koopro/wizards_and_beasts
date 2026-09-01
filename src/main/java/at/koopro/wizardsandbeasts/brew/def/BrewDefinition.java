package at.koopro.wizardsandbeasts.brew.def;

import org.jspecify.annotations.Nullable;

import at.koopro.wizardsandbeasts.brew.Brew;
import at.koopro.wizardsandbeasts.brew.effect.BrewEffect;
import at.koopro.wizardsandbeasts.brew.effect.BrewEffectEntry;
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
        Optional<String> flavorText,
        Optional<String> silverVariant,
        List<BrewEffectEntry> components) {

    public static final Codec<BrewDefinition> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("displayName").forGetter(BrewDefinition::displayName),
            Codec.INT.fieldOf("color").forGetter(BrewDefinition::color),
            // Optional now. A brew that declares components does not need an effects list, and
            // requiring an empty array to say so would be noise in every componentised file.
            EffectEntry.CODEC.listOf().optionalFieldOf("effects", List.of())
                    .forGetter(BrewDefinition::effects),
            Codec.STRING.optionalFieldOf("flavorText").forGetter(BrewDefinition::flavorText),
            // Optional, so every brew written before silver existed still parses. A brew that omits
            // it is simply not silver-based, which is the correct reading of an absent field here.
            Codec.STRING.optionalFieldOf("silverVariant").forGetter(BrewDefinition::silverVariant),
            BrewEffectEntry.CODEC.listOf().optionalFieldOf("components", List.of())
                    .forGetter(BrewDefinition::components)
    ).apply(inst, BrewDefinition::new));

    /** Five-argument form for callers that predate components. */
    public BrewDefinition(String displayName, int color, List<EffectEntry> effects,
                          Optional<String> flavorText, Optional<String> silverVariant) {
        this(displayName, color, effects, flavorText, silverVariant, List.of());
    }

    /**
     * Bakes this definition into a {@link Brew}, dropping any effect whose
     * mob-effect id is unknown to the live registry. Returns {@code null} if
     * every effect was dropped (a brew with zero effects is rejected; if
     * that's intentional, declare {@code [{ "id": "minecraft:luck", ... }]}).
     */
    public @Nullable Brew toBrew(String fullId) {
        List<Brew.EffectSpec> specs = effects.stream()
                .map(EffectEntry::resolve)
                .filter(java.util.Objects::nonNull)
                .toList();

        // A legacy effects list becomes an apply_effects component. This is what makes the migration
        // opt-in per brew: nothing downstream has to know whether a brew was authored before or after
        // components existed, because by the time it leaves here every brew is a component list.
        List<BrewEffectEntry> resolved = new java.util.ArrayList<>(components);
        if (!specs.isEmpty()) {
            resolved.add(BrewEffectEntry.onDrink(new BrewEffect.ApplyEffects(
                    effects.stream()
                            .map(e -> new BrewEffect.ApplyEffects.EffectSpec(
                                    e.id(), e.duration(), e.amplifier(), e.ambient()))
                            .toList())));
        }

        // Rejected only when it would do nothing at all. It used to be rejected for an empty effects
        // list alone, which would now throw away every brew whose behaviour is entirely in its
        // components — the exact case this class was extended to allow.
        if (resolved.isEmpty()) return null;

        return new Brew(fullId, displayName, color, specs, flavorText.orElse(null),
                silverVariant.orElse(null), resolved);
    }

    /**
     * The inverse of {@link #toBrew(String)}: turns a loaded brew back into its definition form.
     *
     * <p>Needed because the reload listener keeps only the baked {@link Brew} and the sync payload sends
     * definitions — reusing the JSON codec on the wire rather than maintaining a second encoding of the
     * same data. Effects that failed to resolve at load time are already gone, so what this produces is
     * what the server actually has, not what the JSON asked for.
     */
    public static BrewDefinition fromBrew(Brew brew) {
        List<EffectEntry> entries = brew.effects().stream()
                .map(spec -> new EffectEntry(
                        BuiltInRegistries.MOB_EFFECT.getKey(spec.effect().value()),
                        spec.baseDuration(),
                        spec.amplifier(),
                        spec.ambient()))
                .toList();
        // The components that were AUTHORED, not the resolved list. toBrew appends an apply_effects
        // component derived from the effects list, and sending that back alongside the effects it was
        // derived from would double every effect the moment a client round-tripped a brew.
        List<BrewEffectEntry> authored = brew.components().stream()
                .filter(entry -> !(entry.component() instanceof BrewEffect.ApplyEffects))
                .toList();
        return new BrewDefinition(brew.displayName(), brew.color(), entries,
                Optional.ofNullable(brew.flavorText()), Optional.ofNullable(brew.silverVariant()),
                authored);
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
