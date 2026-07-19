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

    // ── Withers ──
    // One per component, each changing exactly one field. Callers used to re-list all 18
    // constructor arguments positionally for every mutation; with four adjacent booleans and
    // three adjacent ints in the layout, a single transposed argument compiled cleanly and
    // silently corrupted persisted player data. These are generated from the component list so
    // the positional order is written correctly exactly once.

    public PlayerAbilityData withApparitionUnlocked(boolean value) {
        return new PlayerAbilityData(value, apparitionLicensed(), apparitionCooldownTicks(), splinchSeverity(), splinchTicksRemaining(), occlumencyLevel(), legilimencyCooldownTicks(), animagusUnlocked(), animagusFormId(), animagusRegistered(), currentlyTransformed(), wolfsbaneActive(), parseltongueSpeaker(), parseltongueSource(), metamorphmagus(), currentDisguiseFormId(), wandlessCastingLevel(), abilityFlags());
    }

    public PlayerAbilityData withApparitionLicensed(boolean value) {
        return new PlayerAbilityData(apparitionUnlocked(), value, apparitionCooldownTicks(), splinchSeverity(), splinchTicksRemaining(), occlumencyLevel(), legilimencyCooldownTicks(), animagusUnlocked(), animagusFormId(), animagusRegistered(), currentlyTransformed(), wolfsbaneActive(), parseltongueSpeaker(), parseltongueSource(), metamorphmagus(), currentDisguiseFormId(), wandlessCastingLevel(), abilityFlags());
    }

    public PlayerAbilityData withApparitionCooldownTicks(int value) {
        return new PlayerAbilityData(apparitionUnlocked(), apparitionLicensed(), value, splinchSeverity(), splinchTicksRemaining(), occlumencyLevel(), legilimencyCooldownTicks(), animagusUnlocked(), animagusFormId(), animagusRegistered(), currentlyTransformed(), wolfsbaneActive(), parseltongueSpeaker(), parseltongueSource(), metamorphmagus(), currentDisguiseFormId(), wandlessCastingLevel(), abilityFlags());
    }

    public PlayerAbilityData withSplinchSeverity(int value) {
        return new PlayerAbilityData(apparitionUnlocked(), apparitionLicensed(), apparitionCooldownTicks(), value, splinchTicksRemaining(), occlumencyLevel(), legilimencyCooldownTicks(), animagusUnlocked(), animagusFormId(), animagusRegistered(), currentlyTransformed(), wolfsbaneActive(), parseltongueSpeaker(), parseltongueSource(), metamorphmagus(), currentDisguiseFormId(), wandlessCastingLevel(), abilityFlags());
    }

    public PlayerAbilityData withSplinchTicksRemaining(int value) {
        return new PlayerAbilityData(apparitionUnlocked(), apparitionLicensed(), apparitionCooldownTicks(), splinchSeverity(), value, occlumencyLevel(), legilimencyCooldownTicks(), animagusUnlocked(), animagusFormId(), animagusRegistered(), currentlyTransformed(), wolfsbaneActive(), parseltongueSpeaker(), parseltongueSource(), metamorphmagus(), currentDisguiseFormId(), wandlessCastingLevel(), abilityFlags());
    }

    public PlayerAbilityData withOcclumencyLevel(float value) {
        return new PlayerAbilityData(apparitionUnlocked(), apparitionLicensed(), apparitionCooldownTicks(), splinchSeverity(), splinchTicksRemaining(), value, legilimencyCooldownTicks(), animagusUnlocked(), animagusFormId(), animagusRegistered(), currentlyTransformed(), wolfsbaneActive(), parseltongueSpeaker(), parseltongueSource(), metamorphmagus(), currentDisguiseFormId(), wandlessCastingLevel(), abilityFlags());
    }

    public PlayerAbilityData withLegilimencyCooldownTicks(int value) {
        return new PlayerAbilityData(apparitionUnlocked(), apparitionLicensed(), apparitionCooldownTicks(), splinchSeverity(), splinchTicksRemaining(), occlumencyLevel(), value, animagusUnlocked(), animagusFormId(), animagusRegistered(), currentlyTransformed(), wolfsbaneActive(), parseltongueSpeaker(), parseltongueSource(), metamorphmagus(), currentDisguiseFormId(), wandlessCastingLevel(), abilityFlags());
    }

    public PlayerAbilityData withAnimagusUnlocked(boolean value) {
        return new PlayerAbilityData(apparitionUnlocked(), apparitionLicensed(), apparitionCooldownTicks(), splinchSeverity(), splinchTicksRemaining(), occlumencyLevel(), legilimencyCooldownTicks(), value, animagusFormId(), animagusRegistered(), currentlyTransformed(), wolfsbaneActive(), parseltongueSpeaker(), parseltongueSource(), metamorphmagus(), currentDisguiseFormId(), wandlessCastingLevel(), abilityFlags());
    }

    public PlayerAbilityData withAnimagusFormId(@Nullable String value) {
        return new PlayerAbilityData(apparitionUnlocked(), apparitionLicensed(), apparitionCooldownTicks(), splinchSeverity(), splinchTicksRemaining(), occlumencyLevel(), legilimencyCooldownTicks(), animagusUnlocked(), value, animagusRegistered(), currentlyTransformed(), wolfsbaneActive(), parseltongueSpeaker(), parseltongueSource(), metamorphmagus(), currentDisguiseFormId(), wandlessCastingLevel(), abilityFlags());
    }

    public PlayerAbilityData withAnimagusRegistered(boolean value) {
        return new PlayerAbilityData(apparitionUnlocked(), apparitionLicensed(), apparitionCooldownTicks(), splinchSeverity(), splinchTicksRemaining(), occlumencyLevel(), legilimencyCooldownTicks(), animagusUnlocked(), animagusFormId(), value, currentlyTransformed(), wolfsbaneActive(), parseltongueSpeaker(), parseltongueSource(), metamorphmagus(), currentDisguiseFormId(), wandlessCastingLevel(), abilityFlags());
    }

    public PlayerAbilityData withCurrentlyTransformed(boolean value) {
        return new PlayerAbilityData(apparitionUnlocked(), apparitionLicensed(), apparitionCooldownTicks(), splinchSeverity(), splinchTicksRemaining(), occlumencyLevel(), legilimencyCooldownTicks(), animagusUnlocked(), animagusFormId(), animagusRegistered(), value, wolfsbaneActive(), parseltongueSpeaker(), parseltongueSource(), metamorphmagus(), currentDisguiseFormId(), wandlessCastingLevel(), abilityFlags());
    }

    public PlayerAbilityData withWolfsbaneActive(boolean value) {
        return new PlayerAbilityData(apparitionUnlocked(), apparitionLicensed(), apparitionCooldownTicks(), splinchSeverity(), splinchTicksRemaining(), occlumencyLevel(), legilimencyCooldownTicks(), animagusUnlocked(), animagusFormId(), animagusRegistered(), currentlyTransformed(), value, parseltongueSpeaker(), parseltongueSource(), metamorphmagus(), currentDisguiseFormId(), wandlessCastingLevel(), abilityFlags());
    }

    public PlayerAbilityData withParseltongueSpeaker(boolean value) {
        return new PlayerAbilityData(apparitionUnlocked(), apparitionLicensed(), apparitionCooldownTicks(), splinchSeverity(), splinchTicksRemaining(), occlumencyLevel(), legilimencyCooldownTicks(), animagusUnlocked(), animagusFormId(), animagusRegistered(), currentlyTransformed(), wolfsbaneActive(), value, parseltongueSource(), metamorphmagus(), currentDisguiseFormId(), wandlessCastingLevel(), abilityFlags());
    }

    public PlayerAbilityData withParseltongueSource(@Nullable String value) {
        return new PlayerAbilityData(apparitionUnlocked(), apparitionLicensed(), apparitionCooldownTicks(), splinchSeverity(), splinchTicksRemaining(), occlumencyLevel(), legilimencyCooldownTicks(), animagusUnlocked(), animagusFormId(), animagusRegistered(), currentlyTransformed(), wolfsbaneActive(), parseltongueSpeaker(), value, metamorphmagus(), currentDisguiseFormId(), wandlessCastingLevel(), abilityFlags());
    }

    public PlayerAbilityData withMetamorphmagus(boolean value) {
        return new PlayerAbilityData(apparitionUnlocked(), apparitionLicensed(), apparitionCooldownTicks(), splinchSeverity(), splinchTicksRemaining(), occlumencyLevel(), legilimencyCooldownTicks(), animagusUnlocked(), animagusFormId(), animagusRegistered(), currentlyTransformed(), wolfsbaneActive(), parseltongueSpeaker(), parseltongueSource(), value, currentDisguiseFormId(), wandlessCastingLevel(), abilityFlags());
    }

    public PlayerAbilityData withCurrentDisguiseFormId(@Nullable String value) {
        return new PlayerAbilityData(apparitionUnlocked(), apparitionLicensed(), apparitionCooldownTicks(), splinchSeverity(), splinchTicksRemaining(), occlumencyLevel(), legilimencyCooldownTicks(), animagusUnlocked(), animagusFormId(), animagusRegistered(), currentlyTransformed(), wolfsbaneActive(), parseltongueSpeaker(), parseltongueSource(), metamorphmagus(), value, wandlessCastingLevel(), abilityFlags());
    }

    public PlayerAbilityData withWandlessCastingLevel(float value) {
        return new PlayerAbilityData(apparitionUnlocked(), apparitionLicensed(), apparitionCooldownTicks(), splinchSeverity(), splinchTicksRemaining(), occlumencyLevel(), legilimencyCooldownTicks(), animagusUnlocked(), animagusFormId(), animagusRegistered(), currentlyTransformed(), wolfsbaneActive(), parseltongueSpeaker(), parseltongueSource(), metamorphmagus(), currentDisguiseFormId(), value, abilityFlags());
    }

    public PlayerAbilityData withAbilityFlags(Set<String> value) {
        return new PlayerAbilityData(apparitionUnlocked(), apparitionLicensed(), apparitionCooldownTicks(), splinchSeverity(), splinchTicksRemaining(), occlumencyLevel(), legilimencyCooldownTicks(), animagusUnlocked(), animagusFormId(), animagusRegistered(), currentlyTransformed(), wolfsbaneActive(), parseltongueSpeaker(), parseltongueSource(), metamorphmagus(), currentDisguiseFormId(), wandlessCastingLevel(), value);
    }
}
