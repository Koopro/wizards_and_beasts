package at.koopro.wizardsandbeasts.spell.core;

import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.spell.cast.CastContext;
import at.koopro.wizardsandbeasts.spell.cast.SpellExecutor;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.spell.proficiency.SpellScalingProfile;
import at.koopro.wizardsandbeasts.entity.spell.SpellProjectileEntity;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import net.minecraft.resources.Identifier;
import at.koopro.wizardsandbeasts.spell.cast.SpellPower;
import at.koopro.wizardsandbeasts.spell.proficiency.ProficiencyScaler;
import at.koopro.wizardsandbeasts.wand.cast.WandStats;
import at.koopro.wizardsandbeasts.wand.cast.WandStatsResolver;
import at.koopro.wizardsandbeasts.util.WandHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;
import java.util.function.Supplier;

public abstract class Spell {

    private final String id;
    private final String displayName;
    private final SpellCategory category;
    private final int baseCooldownTicks;
    private final float baseDamage;
    private final int color;
    private SpellProperties properties;
    private SpellRequirement requirement;
    private @Nullable String requiredSkillId;

    protected Spell(String id, String displayName, SpellCategory category,
                    int baseCooldownTicks, float baseDamage, int color) {
        this.id = id;
        this.displayName = displayName;
        this.category = category;
        this.baseCooldownTicks = baseCooldownTicks;
        this.baseDamage = baseDamage;
        this.color = color;
    }

    protected abstract SpellProperties buildProperties();

    protected abstract SpellRequirement buildRequirement();

    /**
     * Called after all spells are registered in {@link Spells#init()}.
     * Two-phase init so that {@link #buildRequirement()} can safely reference other spells.
     */
    public void init() {
        this.properties = buildProperties();
        this.requirement = buildRequirement();
    }

    /**
     * Execute this spell. Override in subclasses for unique behavior.
     * The default delegates to {@link SpellExecutor#executeGeneric}.
     */
    public void execute(ServerLevel level, ServerPlayer caster, ItemStack wandStack) {
        SpellExecutor.executeGeneric(this, level, caster, wandStack);
    }

    /**
     * Cast-dispatch hook, invoked by {@link SpellExecutor#executeGeneric(CastContext, ServerLevel)}
     * AFTER the modifier pipeline (corruption, misfire, Gamp, scaling) has run. The default performs
     * the generic {@code castType} dispatch. Override for custom behavior (entity spawns, etc.) so it
     * runs on a normal wand cast with the fully-enriched {@link CastContext}.
     */
    public void executeCast(CastContext ctx, ServerLevel level) {
        SpellExecutor.dispatchGeneric(ctx, level);
    }

    // ── Getters ─────────────────────────────────────────────────────────

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public SpellCategory getCategory() { return category; }
    public int getBaseCooldownTicks() { return baseCooldownTicks; }
    public float getBaseDamage() { return baseDamage; }
    public int getColor() { return color; }
    public int getBaseEffectDurationTicks() { return 0; }
    public float getProjectileSpeed() { return 1.5f; }
    public float getProjectileSpread() { return 0.0f; }
    public float getBaseKnockback() { return 0.0f; }

    @Nullable
    public SpellProperties getProperties() { return properties; }

    public SpellRequirement getRequirement() {
        return requirement != null ? requirement : SpellRequirement.NONE;
    }

    // ── Helper methods for subclass overrides ────────────────────────────

    /**
     * Gets the caster's proficiency level for this spell.
     */
    public Proficiency getProficiency(ServerPlayer caster) {
        PlayerSpellData data = caster.getData(ModAttachments.SPELL_DATA.get());
        return Proficiency.fromCastCount(data.getSuccessfulHits(id));
    }

    /**
     * Calculates skill-and-proficiency adjusted damage for this spell.
     * Equivalent to {@link #getDamageForCaster(ServerPlayer, ItemStack)} resolving
     * the caster's currently held wand via {@link WandHelper#getWandStack(net.minecraft.world.entity.player.Player)}.
     * Use the two-arg overload when the wand stack is already in scope.
     */
    public float getDamageForCaster(ServerPlayer caster) {
        return getDamageForCaster(caster, WandHelper.getWandStack(caster));
    }

    /**
     * An <em>estimate</em> of this spell's damage for a caster: base times the skill web, the
     * proficiency curve and the wand.
     *
     * <p>Explicitly not the number a cast produces. A real cast composes the situational channel too
     * ({@code ModifierStack}) and bounds the result through {@link SpellPower}; this is for callers
     * outside a cast — commands, previews, a projectile spawned by something other than the caster.
     * Kept deliberately close to the real formula so the gap is the situational channel and nothing
     * else.
     */
    public float getDamageForCaster(ServerPlayer caster, ItemStack wandStack) {
        WandStats wand = WandStatsResolver.resolve(wandStack, caster.registryAccess());
        return baseDamage * getDamageMultiplierForCaster(caster) * wand.damageFor(this);
    }

    /**
     * Skill web times proficiency, bounded the same way a cast is — the multiplier half of
     * {@link #getDamageForCaster}, without the wand or the base.
     */
    public float getDamageMultiplierForCaster(ServerPlayer caster) {
        return SpellPower.damage(
                1.0f,
                ProficiencyScaler.getProfileForPlayer(caster, Identifier.parse(id)).damageMult(),
                SkillSystemAPI.getSkillDamageMultiplier(caster, this)).total();
    }

    /**
     * Plays this spell's cast sound at the caster's position.
     */
    public void playSound(ServerLevel level, ServerPlayer caster) {
        if (properties == null) return;
        SoundEvent event = resolveCastSoundForPlayback();
        float proficiencyPitch = 1.0f;
        if (ModuleManager.isEnabled(Module.PROFICIENCY)) {
            float proficiency = caster.getData(ModAttachments.SPELL_DATA.get()).getSpellProficiency(id);
            proficiencyPitch = 0.9f + (Math.max(0.0f, Math.min(1.0f, proficiency)) * 0.2f);
        }
        level.playSound(null, caster.blockPosition(), event,
                SoundSource.PLAYERS, properties.getSoundVolume(),
                (properties.getSoundPitch() + level.random.nextFloat() * 0.3f) * proficiencyPitch);
    }

    private SoundEvent resolveCastSoundForPlayback() {
        if (properties == null) {
            return ModSounds.SPELL_CAST_GENERIC.get();
        }
        SoundEvent configured = properties.getCastSound();
        if (configured == ModSounds.PATRONUS_SUMMON.get()) {
            return configured;
        }
        SpellFamily family = SpellFamilies.of(this);
        if (family == SpellFamily.DARK) {
            return ModSounds.SPELL_CAST_DARK.get();
        }
        if (family == SpellFamily.LIGHT) {
            return ModSounds.SPELL_CAST_CHARM.get();
        }
        return configured;
    }

    /**
     * Applies this spell's self-effects to the caster.
     */
    public void applySelfEffects(ServerPlayer caster) {
        applySelfEffects(caster, 1.0f);
    }

    public void applySelfEffects(ServerPlayer caster, float durationMult) {
        if (properties == null) return;
        for (Supplier<MobEffectInstance> factory : properties.getSelfEffects()) {
            MobEffectInstance effect = factory.get();
            if (effect != null && canApplyEffect(effect)) {
                int rawDuration = effect.getDuration();
                int scaledDuration = rawDuration < 0 ? rawDuration
                        : Math.max(1, Math.round(rawDuration * durationMult));
                caster.addEffect(new MobEffectInstance(
                        effect.getEffect(),
                        scaledDuration,
                        effect.getAmplifier(),
                        effect.isAmbient(),
                        effect.isVisible(),
                        effect.showIcon()));
            }
        }
    }

    /**
     * Applies this spell's target-effects to a living entity.
     */
    public void applyTargetEffects(LivingEntity target) {
        applyTargetEffects(target, 1.0f);
    }

    public void applyTargetEffects(LivingEntity target, float durationMult) {
        if (properties == null) return;
        for (Supplier<MobEffectInstance> factory : properties.getTargetEffects()) {
            MobEffectInstance effect = factory.get();
            if (effect != null && canApplyEffect(effect)) {
                int rawDuration = effect.getDuration();
                int scaledDuration = rawDuration < 0 ? rawDuration
                        : Math.max(1, Math.round(rawDuration * durationMult));
                target.addEffect(new MobEffectInstance(
                        effect.getEffect(),
                        scaledDuration,
                        effect.getAmplifier(),
                        effect.isAmbient(),
                        effect.isVisible(),
                        effect.showIcon()));
            }
        }
    }

    private static boolean canApplyEffect(MobEffectInstance effect) {
        if (effect.is(ModEffects.CRUCIATUS_PAIN)
                || effect.is(ModEffects.SECTUMSEMPRA_BLEED)
                || effect.is(ModEffects.BAT_BOGEY)) {
            return ModuleManager.isEnabled(Module.DARK_ARTS);
        }
        if (effect.is(ModEffects.DEMENTOR_CHILL)) {
            return ModuleManager.isEnabled(Module.CREATURES);
        }
        return ModuleManager.isEnabled(Module.WANDS_AND_SPELLS);
    }

    /**
     * Spawns and shoots a projectile from the caster.
     */
    public SpellProjectileEntity spawnProjectile(ServerLevel level, ServerPlayer caster) {
        return spawnProjectile(level, caster, SpellScalingProfile.DEFAULT);
    }

    public SpellProjectileEntity spawnProjectile(ServerLevel level, ServerPlayer caster, SpellScalingProfile profile) {
        return spawnProjectile(level, caster, profile, getDamageMultiplierForCaster(caster));
    }

    /**
     * Spawn this spell's projectile carrying an already-composed damage multiplier.
     *
     * <p>The multiplier is handed over rather than recomputed on impact because the cast site is the
     * only place that knows the whole picture — {@code SpellExecutor} spends the first forty lines of
     * a cast building a {@code ModifierStack} out of wand corruption, allegiance, dark corruption,
     * vocation, Niffler happiness and player stats, and the projectile had no way to see any of it.
     * It called {@code getDamageForCaster} instead, which knows only proficiency, the skill web and
     * the wand, so every projectile spell silently ignored the rest of the pipeline.
     */
    public SpellProjectileEntity spawnProjectile(ServerLevel level, ServerPlayer caster,
                                                 SpellScalingProfile profile, float damageMultiplier) {
        SpellProjectileEntity projectile = new SpellProjectileEntity(level, caster, id);
        projectile.setScalingProfile(profile);
        projectile.setDamageMultiplier(damageMultiplier);
        float baseSpeed = this instanceof JsonSpell jsonSpell ? jsonSpell.definition().projectileSpeed() : getProjectileSpeed();
        float baseSpread = this instanceof JsonSpell jsonSpell ? jsonSpell.definition().projectileSpread() : getProjectileSpread();
        float speed = baseSpeed * profile.controlMult();
        float spread = baseSpread * profile.accuracyMult();
        projectile.shootFromRotation(caster, caster.getXRot(), caster.getYRot(), 0.0f, speed, spread);
        level.addFreshEntity(projectile);
        return projectile;
    }

    /**
     * Convenience: gets the wand ItemStack from the caster.
     */
    public ItemStack getWandStack(ServerPlayer caster) {
        return WandHelper.getWandStack(caster);
    }

    /** 0–1-ish scalar derived from proficiency tier (for formulas that expect a float). */
    public float getProficiencyScalar(ServerPlayer caster) {
        return switch (getProficiency(caster)) {
            case NOVICE -> 0.33f;
            case PROFICIENT -> 0.66f;
            case MASTERED -> 1.0f;
        };
    }

    /**
     * This spell's authored particle look, or its family's default when it authors none.
     *
     * <p>Overridden by {@code JsonSpell} to read the {@code vfx} block. Java spells inherit the
     * family default, which is exactly what every spell in the mod looked like before the block
     * existed — so this changes nothing until a spell opts in.
     */
    public at.koopro.wizardsandbeasts.spell.def.SpellVfx vfx() {
        return at.koopro.wizardsandbeasts.spell.def.SpellVfx.defaultFor(SpellFamilies.of(this));
    }

    /** When true, Protego deflection does not apply (datapack / Java override). */
    public boolean isUnblockable() {
        return false;
    }

    /**
     * Whether this spell's behaviour is actually written. A Java spell exists because somebody wrote
     * its {@code execute}, so the base answer is always yes; only {@code JsonSpell} can answer no, by
     * reading {@code implementationState} off its definition.
     *
     * <p>Read by the cast gate and by
     * {@link at.koopro.wizardsandbeasts.spell.learning.SpellLearningEligibility}. Both are
     * server-side, which is what makes the refusal authoritative — see
     * {@link at.koopro.wizardsandbeasts.spell.def.SpellImplementationState}.
     */
    public boolean isImplemented() {
        return true;
    }

    /**
     * Optional hybrid-learning metadata used by the teacher progression system.
     * Java spells default to no extra gate; datapack spells can provide these.
     */
    @Nullable
    public String getRequiredSkillId() {
        return requiredSkillId;
    }

    @Nullable
    public String getRequiredProfessionId() {
        return null;
    }

    @Nullable
    public String getMasterySourceSpellId() {
        return null;
    }

    public PlayerSpellData.@Nullable MasteryTier getRequiredMasteryTier() {
        return null;
    }
}
