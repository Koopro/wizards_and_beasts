package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.jspecify.annotations.NonNull;

/**
 * Reusable, datapack-driven ability layer for data-configured creatures. A typed list of polymorphic
 * abilities lives on {@code CreatureDefinition} ({@code abilities}); each entry is one server-side
 * behaviour contract that every future beast ability extends. This mirrors {@code SkillNodeEffect}'s
 * sealed-interface + dispatch-{@link Codec} pattern exactly — the dispatch key is a serialized type
 * string (a stable id), and each variant supplies its own {@link MapCodec}.
 *
 * <p>The framework is the deliverable: it is intentionally general, not fire-specific. The first
 * concrete implementation is {@link FireAffinity}. Hooks are typed against {@link GenericBeastEntity}
 * — the shared base of all data-driven creatures (the dragon entity included) — so an ability can read
 * the bound definition, drive attributes/effects, and read/write its small per-entity scratch state.
 *
 * <p><b>Module gating:</b> the dispatch in {@link GenericBeastEntity} only invokes these hooks while
 * {@code Module.CREATURES} is enabled, and any contributed AI {@code Goal} self-gates on the same module
 * at {@code canUse()} (mirroring {@code DragonBreathGoal}). Codec/interface <i>registration</i> is always
 * present regardless of module state; gating controls <i>access</i>, never registration.
 */
public sealed interface CreatureAbility permits
        FireAffinity, WaterAffinity, StatusOnHit, Enrage, Thorns, BlinkAway, HealAura, DreadAura,
        Bioluminescence, LifeLeech, Constrict, Camouflage, LeapAtTarget, RangedHex, WebSnare,
        NunduPestilence, LethifoldSmother, BoggartDread, ThunderbirdStorm, FwooperSong,
        SpellResist, ExplosiveHorn, DeathCry, Anchor,
        OccamyChoranaptyxis, FlameBurst, EmberTrail, DangerSense,
        Tint, Evasion, PackTactics, DamageReduction, SporeCloud, Frenzy, DiveBomb,
        JarveyJinx, SphinxRiddle, LureDisguise, Duplication, BlockDecay {

    /** Dispatch codec keyed by the variant's {@link Type}, mirroring {@code SkillNodeEffect.CODEC}. */
    Codec<CreatureAbility> CODEC = Type.CODEC.dispatch(CreatureAbility::type, Type::codec);

    @NonNull
    Type type();

    /** Server-side per-tick logic. Invoked only while {@code Module.CREATURES} is enabled. No-op by default. */
    default void tick(@NonNull GenericBeastEntity entity) {
    }

    /** After the beast takes (non-cancelled) damage, server-side, while the module is enabled. No-op by default. */
    default void onHurt(@NonNull GenericBeastEntity entity, @NonNull DamageSource source, float amount) {
    }

    /** After the beast lands a melee hit on a living target, server-side, while the module is enabled. No-op by default. */
    default void onMeleeContact(@NonNull GenericBeastEntity entity, @NonNull LivingEntity target) {
    }

    /** When the beast dies, server-side, while the module is enabled. No-op by default. */
    default void onDeath(@NonNull GenericBeastEntity entity) {
    }

    /**
     * Contribute AI goals at goal-registration time. Called once per entity from
     * {@code GenericBeastEntity.registerGoals}; any goal added here must self-gate on the module at
     * {@code canUse()} so a runtime module toggle takes effect. No-op by default.
     */
    default void registerGoals(@NonNull GenericBeastEntity entity, @NonNull GoalSelector goalSelector) {
    }

    /** Dispatch keys for the ability variants, mirroring {@code SkillNodeEffect.Type}. */
    enum Type implements StringRepresentable {
        FIRE_AFFINITY("fire_affinity", FireAffinity.CODEC),
        WATER_AFFINITY("water_affinity", WaterAffinity.CODEC),
        STATUS_ON_HIT("status_on_hit", StatusOnHit.CODEC),
        ENRAGE("enrage", Enrage.CODEC),
        THORNS("thorns", Thorns.CODEC),
        BLINK_AWAY("blink_away", BlinkAway.CODEC),
        HEAL_AURA("heal_aura", HealAura.CODEC),
        DREAD_AURA("dread_aura", DreadAura.CODEC),
        BIOLUMINESCENCE("bioluminescence", Bioluminescence.CODEC),
        LIFE_LEECH("life_leech", LifeLeech.CODEC),
        CONSTRICT("constrict", Constrict.CODEC),
        CAMOUFLAGE("camouflage", Camouflage.CODEC),
        LEAP("leap", LeapAtTarget.CODEC),
        RANGED_HEX("ranged_hex", RangedHex.CODEC),
        WEB_SNARE("web_snare", WebSnare.CODEC),
        NUNDU_PESTILENCE("nundu_pestilence", NunduPestilence.CODEC),
        LETHIFOLD_SMOTHER("lethifold_smother", LethifoldSmother.CODEC),
        BOGGART_DREAD("boggart_dread", BoggartDread.CODEC),
        THUNDERBIRD_STORM("thunderbird_storm", ThunderbirdStorm.CODEC),
        FWOOPER_SONG("fwooper_song", FwooperSong.CODEC),
        SPELL_RESIST("spell_resist", SpellResist.CODEC),
        EXPLOSIVE_HORN("explosive_horn", ExplosiveHorn.CODEC),
        DEATH_CRY("death_cry", DeathCry.CODEC),
        ANCHOR("anchor", Anchor.CODEC),
        OCCAMY_CHORANAPTYXIS("occamy_choranaptyxis", OccamyChoranaptyxis.CODEC),
        FLAME_BURST("flame_burst", FlameBurst.CODEC),
        EMBER_TRAIL("ember_trail", EmberTrail.CODEC),
        DANGER_SENSE("danger_sense", DangerSense.CODEC),
        TINT("tint", Tint.CODEC),
        EVASION("evasion", Evasion.CODEC),
        PACK_TACTICS("pack_tactics", PackTactics.CODEC),
        DAMAGE_REDUCTION("damage_reduction", DamageReduction.CODEC),
        SPORE_CLOUD("spore_cloud", SporeCloud.CODEC),
        FRENZY("frenzy", Frenzy.CODEC),
        DIVE_BOMB("dive_bomb", DiveBomb.CODEC),
        JARVEY_JINX("jarvey_jinx", JarveyJinx.CODEC),
        SPHINX_RIDDLE("sphinx_riddle", SphinxRiddle.CODEC),
        LURE_DISGUISE("lure_disguise", LureDisguise.CODEC),
        DUPLICATION("duplication", Duplication.CODEC),
        BLOCK_DECAY("block_decay", BlockDecay.CODEC);

        public static final Codec<Type> CODEC = StringRepresentable.fromValues(Type::values);

        private final String serializedName;
        private final MapCodec<? extends CreatureAbility> codec;

        Type(String serializedName, MapCodec<? extends CreatureAbility> codec) {
            this.serializedName = serializedName;
            this.codec = codec;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }

        public MapCodec<? extends CreatureAbility> codec() {
            return codec;
        }
    }
}
