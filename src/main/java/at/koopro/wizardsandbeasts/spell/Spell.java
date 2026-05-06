package at.koopro.wizardsandbeasts.spell;

import at.koopro.wizardsandbeasts.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.entity.SpellProjectileEntity;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
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

import javax.annotation.Nullable;
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
    void init() {
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

    // ── Getters ─────────────────────────────────────────────────────────

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public SpellCategory getCategory() { return category; }
    public int getBaseCooldownTicks() { return baseCooldownTicks; }
    public float getBaseDamage() { return baseDamage; }
    public int getColor() { return color; }

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
     * Calculates skill-, proficiency-, and wand-adjusted damage for this spell.
     */
    public float getDamageForCaster(ServerPlayer caster, ItemStack wandStack) {
        WandStats wand = WandStatsResolver.resolve(wandStack);
        return baseDamage
                * SkillSystemAPI.getDamageMultiplier(caster, this)
                * wand.damageFor(this);
    }

    /**
     * Plays this spell's cast sound at the caster's position.
     */
    public void playSound(ServerLevel level, ServerPlayer caster) {
        if (properties == null) return;
        SoundEvent event = resolveCastSoundForPlayback();
        level.playSound(null, caster.blockPosition(), event,
                SoundSource.PLAYERS, properties.getSoundVolume(),
                properties.getSoundPitch() + level.random.nextFloat() * 0.3f);
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
        if (properties == null) return;
        for (Supplier<MobEffectInstance> factory : properties.getSelfEffects()) {
            MobEffectInstance effect = factory.get();
            if (effect != null && canApplyEffect(effect)) {
                caster.addEffect(effect);
            }
        }
    }

    /**
     * Applies this spell's target-effects to a living entity.
     */
    public void applyTargetEffects(LivingEntity target) {
        if (properties == null) return;
        for (Supplier<MobEffectInstance> factory : properties.getTargetEffects()) {
            MobEffectInstance effect = factory.get();
            if (effect != null && canApplyEffect(effect)) {
                target.addEffect(effect);
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
        SpellProjectileEntity projectile = new SpellProjectileEntity(level, caster, id);
        projectile.shootFromRotation(caster, caster.getXRot(), caster.getYRot(), 0.0f, 2.0f, 0.5f);
        level.addFreshEntity(projectile);
        return projectile;
    }

    /**
     * Convenience: gets the wand ItemStack from the caster.
     */
    public ItemStack getWandStack(ServerPlayer caster) {
        return WandHelper.getWandStack(caster);
    }
}
