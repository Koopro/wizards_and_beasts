package at.koopro.wizardsandbeasts.spell.core;

import org.jspecify.annotations.Nullable;

import at.koopro.wizardsandbeasts.spell.def.SpellDefinition;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import org.slf4j.Logger;

/**
 * Spell whose behavior is fully described by a {@link SpellDefinition}.
 * Carries no Java logic of its own — delegates to {@link SpellExecutor}
 * via {@link Spell#execute}, identical to how Java spells without an
 * {@code execute} override behave.
 *
 * <p>Sourced from datapack JSON by {@link at.koopro.wizardsandbeasts.spell.def.SpellReloadListener}.
 * Registered through {@link Spells#registerJson(Spell)} so {@code /reload}
 * can wipe and re-add JSON spells without touching Java spells.
 */
public class JsonSpell extends Spell {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final SpellDefinition def;

    public JsonSpell(String fullId, SpellDefinition def) {
        super(fullId, def.displayName(), def.category(),
                def.cooldownTicks(), def.baseDamage(), def.color());
        this.def = def;
    }

    public SpellDefinition definition() {
        return def;
    }

    @Override
    public String getRequiredSkillId() {
        return def.learning().requiredSkillId().orElse(null);
    }

    @Override
    public String getRequiredProfessionId() {
        return def.learning().requiredProfessionId().orElse(null);
    }

    @Override
    public String getMasterySourceSpellId() {
        return def.learning().masterySpellId().orElse(null);
    }

    @Override
    public at.koopro.wizardsandbeasts.spell.data.PlayerSpellData.MasteryTier getRequiredMasteryTier() {
        return def.learning().minMasteryTier().orElse(null);
    }

    @Override
    public boolean isUnblockable() {
        return def.unblockable();
    }

    @Override
    protected SpellProperties buildProperties() {
        SpellProperties.Builder b = switch (def.castType()) {
            case PROJECTILE -> SpellProperties.projectile();
            case SELF -> SpellProperties.self();
            case CONE -> SpellProperties.cone(def.range());
            case TARGETED -> SpellProperties.targeted(def.range());
            case BEAM_LETHAL -> SpellProperties.beamLethal(def.range());
            case BEAM_CHANNEL -> SpellProperties.beamChannel(def.range());
        };

        float configuredKnockback = def.baseKnockback() != 0.0f ? def.baseKnockback() : def.knockback();
        if (configuredKnockback != 0.0f) b.knockback(configuredKnockback);
        def.igniteSeconds().ifPresent(b::ignites);
        def.explode().ifPresent(e -> b.explodes(e.power(), e.breaksBlocks()));
        if (def.disarms()) b.disarms();
        if (def.opensBlocks()) b.opensBlocks();
        if (def.pullStrength() != 0.0f) b.pullsTarget(def.pullStrength());

        for (SpellDefinition.MobEffectDef e : def.selfEffects()) {
            Holder<MobEffect> holder = resolveMobEffect(e.id());
            if (holder == null) continue;
            final int dur = e.duration();
            final int amp = e.amplifier();
            b.selfEffect(() -> new MobEffectInstance(holder, dur, amp, false, true, true));
        }
        for (SpellDefinition.MobEffectDef e : def.targetEffects()) {
            Holder<MobEffect> holder = resolveMobEffect(e.id());
            if (holder == null) continue;
            final int dur = e.duration();
            final int amp = e.amplifier();
            b.targetEffect(() -> new MobEffectInstance(holder, dur, amp, false, true, true));
        }

        def.sound().ifPresent(s -> {
            SoundEvent ev = resolveSound(s.id());
            b.sound(ev, s.volume(), s.pitch());
        });

        return b.build();
    }

    @Override
    protected SpellRequirement buildRequirement() {
        SpellDefinition.SpellRequirementDef req = def.requirement();
        return switch (req.type()) {
            case NONE -> SpellRequirement.none();
            case KNOWS -> req.prerequisiteId()
                    .map(raw -> SpellRequirement.knows(resolvePrerequisiteId(raw)))
                    .orElseGet(() -> {
                        LOGGER.warn("JsonSpell '{}' requirement.type=knows but prerequisiteId missing; treating as NONE",
                                getId());
                        return SpellRequirement.none();
                    });
            case PROFICIENCY -> {
                Proficiency prof = req.minProficiency().orElse(Proficiency.NOVICE);
                yield req.prerequisiteId()
                        .map(raw -> SpellRequirement.proficiency(resolvePrerequisiteId(raw), prof))
                        .orElseGet(() -> {
                            LOGGER.warn("JsonSpell '{}' requirement.type=proficiency but prerequisiteId missing; treating as NONE",
                                    getId());
                            return SpellRequirement.none();
                        });
            }
        };
    }

    /**
     * Resolves a requirement's prerequisite id to the registered spell's full id when the spell is
     * already present, else falls back to the mod-namespaced form of the raw id. JSON spells register
     * one-by-one during a reload sweep (each inits immediately), so a JSON→JSON prerequisite may not
     * be registered yet when this spell builds its requirement — {@link SpellRequirement}'s id-string
     * form resolves lazily at {@code isMet} time, so the requirement still enforces correctly instead
     * of degrading to NONE on unfavorable load order.
     */
    private static String resolvePrerequisiteId(String raw) {
        Spell resolved = Spells.byId(raw);
        if (resolved != null) {
            return resolved.getId();
        }
        return raw.contains(":")
                ? raw
                : at.koopro.wizardsandbeasts.WizardsAndBeastsMod.MODID + ":" + raw;
    }

    private static @Nullable Holder<MobEffect> resolveMobEffect(Identifier id) {
        MobEffect effect = BuiltInRegistries.MOB_EFFECT.getValue(id);
        if (effect == null) {
            LOGGER.warn("Unknown mob effect id in spell definition: {}", id);
            return null;
        }
        return BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect);
    }

    private static SoundEvent resolveSound(Identifier id) {
        SoundEvent ev = BuiltInRegistries.SOUND_EVENT.getValue(id);
        if (ev == null) {
            LOGGER.warn("Unknown sound id in spell definition: {} (falling back to minecraft:entity.blaze.shoot)", id);
            return SoundEvents.BLAZE_SHOOT;
        }
        return ev;
    }
}
