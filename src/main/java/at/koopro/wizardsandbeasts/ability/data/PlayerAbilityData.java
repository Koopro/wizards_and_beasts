package at.koopro.wizardsandbeasts.ability.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.datafixers.util.Pair;
import org.jspecify.annotations.Nullable;

import java.util.Set;

public record PlayerAbilityData(
        boolean apparitionUnlocked,
        boolean apparitionLicensed,
        int apparitionCooldownTicks,
        int splinchSeverity,
        int splinchTicksRemaining,
        float occlumencyLevel,
        int legilimencyCooldownTicks,
        boolean animagusUnlocked,
        @Nullable String animagusFormId,
        boolean animagusRegistered,
        boolean currentlyTransformed,
        boolean wolfsbaneActive,
        boolean parseltongueSpeaker,
        @Nullable String parseltongueSource,
        boolean metamorphmagus,
        @Nullable String currentDisguiseFormId,
        float wandlessCastingLevel,
        Set<String> abilityFlags
) {
    public static final PlayerAbilityData DEFAULT = new PlayerAbilityData(
            false,
            false,
            0,
            0,
            0,
            0.0f,
            0,
            false,
            null,
            false,
            false,
            false,
            false,
            null,
            false,
            null,
            0.0f,
            Set.of());

    private record FieldsA(
            boolean apparitionUnlocked,
            boolean apparitionLicensed,
            int apparitionCooldownTicks,
            int splinchSeverity,
            int splinchTicksRemaining,
            float occlumencyLevel,
            int legilimencyCooldownTicks,
            boolean animagusUnlocked,
            @Nullable String animagusFormId
    ) {
        private static final MapCodec<FieldsA> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("apparitionUnlocked", false).forGetter(FieldsA::apparitionUnlocked),
                Codec.BOOL.optionalFieldOf("apparitionLicensed", false).forGetter(FieldsA::apparitionLicensed),
                Codec.INT.optionalFieldOf("apparitionCooldownTicks", 0).forGetter(FieldsA::apparitionCooldownTicks),
                Codec.INT.optionalFieldOf("splinchSeverity", 0).forGetter(FieldsA::splinchSeverity),
                Codec.INT.optionalFieldOf("splinchTicksRemaining", 0).forGetter(FieldsA::splinchTicksRemaining),
                Codec.FLOAT.optionalFieldOf("occlumencyLevel", 0.0f).forGetter(FieldsA::occlumencyLevel),
                Codec.INT.optionalFieldOf("legilimencyCooldownTicks", 0).forGetter(FieldsA::legilimencyCooldownTicks),
                Codec.BOOL.optionalFieldOf("animagusUnlocked", false).forGetter(FieldsA::animagusUnlocked),
                Codec.STRING.optionalFieldOf("animagusFormId").forGetter(v -> java.util.Optional.ofNullable(v.animagusFormId))
        ).apply(instance, (apparitionUnlocked, apparitionLicensed, apparitionCooldownTicks, splinchSeverity, splinchTicksRemaining,
                            occlumencyLevel, legilimencyCooldownTicks, animagusUnlocked, animagusFormId) -> new FieldsA(
                apparitionUnlocked,
                apparitionLicensed,
                apparitionCooldownTicks,
                splinchSeverity,
                splinchTicksRemaining,
                occlumencyLevel,
                legilimencyCooldownTicks,
                animagusUnlocked,
                animagusFormId.orElse(null)
        )));
    }

    private record FieldsB(
            boolean animagusRegistered,
            boolean currentlyTransformed,
            boolean wolfsbaneActive,
            boolean parseltongueSpeaker,
            @Nullable String parseltongueSource,
            boolean metamorphmagus,
            @Nullable String currentDisguiseFormId,
            float wandlessCastingLevel,
            Set<String> abilityFlags
    ) {
        private static final MapCodec<FieldsB> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("animagusRegistered", false).forGetter(FieldsB::animagusRegistered),
                Codec.BOOL.optionalFieldOf("currentlyTransformed", false).forGetter(FieldsB::currentlyTransformed),
                Codec.BOOL.optionalFieldOf("wolfsbaneActive", false).forGetter(FieldsB::wolfsbaneActive),
                Codec.BOOL.optionalFieldOf("parseltongueSpeaker", false).forGetter(FieldsB::parseltongueSpeaker),
                Codec.STRING.optionalFieldOf("parseltongueSource").forGetter(v -> java.util.Optional.ofNullable(v.parseltongueSource)),
                Codec.BOOL.optionalFieldOf("metamorphmagus", false).forGetter(FieldsB::metamorphmagus),
                Codec.STRING.optionalFieldOf("currentDisguiseFormId").forGetter(v -> java.util.Optional.ofNullable(v.currentDisguiseFormId)),
                Codec.FLOAT.optionalFieldOf("wandlessCastingLevel", 0.0f).forGetter(FieldsB::wandlessCastingLevel),
                Codec.STRING.listOf().xmap(Set::copyOf, java.util.List::copyOf).optionalFieldOf("abilityFlags", Set.of()).forGetter(FieldsB::abilityFlags)
        ).apply(instance, (animagusRegistered, currentlyTransformed, wolfsbaneActive, parseltongueSpeaker,
                            parseltongueSource, metamorphmagus, currentDisguiseFormId, wandlessCastingLevel, abilityFlags) -> new FieldsB(
                animagusRegistered,
                currentlyTransformed,
                wolfsbaneActive,
                parseltongueSpeaker,
                parseltongueSource.orElse(null),
                metamorphmagus,
                currentDisguiseFormId.orElse(null),
                wandlessCastingLevel,
                abilityFlags
        )));
    }

    public static final Codec<PlayerAbilityData> CODEC = Codec.mapPair(
            FieldsA.MAP_CODEC,
            FieldsB.MAP_CODEC
    ).codec().xmap(
            pair -> new PlayerAbilityData(
                    pair.getFirst().apparitionUnlocked(),
                    pair.getFirst().apparitionLicensed(),
                    Math.max(0, pair.getFirst().apparitionCooldownTicks()),
                    Math.max(0, Math.min(2, pair.getFirst().splinchSeverity())),
                    Math.max(0, pair.getFirst().splinchTicksRemaining()),
                    clamp01(pair.getFirst().occlumencyLevel()),
                    Math.max(0, pair.getFirst().legilimencyCooldownTicks()),
                    pair.getFirst().animagusUnlocked(),
                    pair.getFirst().animagusFormId(),
                    pair.getSecond().animagusRegistered(),
                    pair.getSecond().currentlyTransformed(),
                    pair.getSecond().wolfsbaneActive(),
                    pair.getSecond().parseltongueSpeaker(),
                    pair.getSecond().parseltongueSource(),
                    pair.getSecond().metamorphmagus(),
                    pair.getSecond().currentDisguiseFormId(),
                    clamp01(pair.getSecond().wandlessCastingLevel()),
                    pair.getSecond().abilityFlags()
            ),
            data -> Pair.of(
                    new FieldsA(
                            data.apparitionUnlocked(),
                            data.apparitionLicensed(),
                            data.apparitionCooldownTicks(),
                            data.splinchSeverity(),
                            data.splinchTicksRemaining(),
                            data.occlumencyLevel(),
                            data.legilimencyCooldownTicks(),
                            data.animagusUnlocked(),
                            data.animagusFormId()
                    ),
                    new FieldsB(
                            data.animagusRegistered(),
                            data.currentlyTransformed(),
                            data.wolfsbaneActive(),
                            data.parseltongueSpeaker(),
                            data.parseltongueSource(),
                            data.metamorphmagus(),
                            data.currentDisguiseFormId(),
                            data.wandlessCastingLevel(),
                            data.abilityFlags()
                    )
            )
    );

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
