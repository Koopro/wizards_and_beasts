package at.koopro.wizardsandbeasts.spell.core;

import at.koopro.wizardsandbeasts.registry.ModSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public class SpellProperties {

    private final CastType castType;
    private final float range;
    private final float knockbackStrength;
    private final boolean ignites;
    private final int igniteDurationSeconds;
    private final boolean explodes;
    private final float explosionPower;
    private final boolean explosionBreaksBlocks;
    private final boolean disarms;
    private final boolean stuns;
    private final boolean repairsItem;
    private final int repairAmount;
    private final boolean opensBlocks;
    private final float pullStrength;
    private final boolean levitatesTarget;
    private final int levitateDurationTicks;
    private final float undeadBonusDamage;
    private final boolean controlsMob;
    private final int controlDurationTicks;
    private final List<Supplier<MobEffectInstance>> targetEffects;
    private final List<Supplier<MobEffectInstance>> selfEffects;
    private final SoundEvent castSound;
    private final float soundVolume;
    private final float soundPitch;

    private SpellProperties(Builder builder) {
        this.castType = builder.castType;
        this.range = builder.range;
        this.knockbackStrength = builder.knockbackStrength;
        this.ignites = builder.ignites;
        this.igniteDurationSeconds = builder.igniteDurationSeconds;
        this.explodes = builder.explodes;
        this.explosionPower = builder.explosionPower;
        this.explosionBreaksBlocks = builder.explosionBreaksBlocks;
        this.disarms = builder.disarms;
        this.stuns = builder.stuns;
        this.repairsItem = builder.repairsItem;
        this.repairAmount = builder.repairAmount;
        this.opensBlocks = builder.opensBlocks;
        this.pullStrength = builder.pullStrength;
        this.levitatesTarget = builder.levitatesTarget;
        this.levitateDurationTicks = builder.levitateDurationTicks;
        this.undeadBonusDamage = builder.undeadBonusDamage;
        this.controlsMob = builder.controlsMob;
        this.controlDurationTicks = builder.controlDurationTicks;
        this.targetEffects = Collections.unmodifiableList(builder.targetEffects);
        this.selfEffects = Collections.unmodifiableList(builder.selfEffects);
        this.castSound = builder.castSound;
        this.soundVolume = builder.soundVolume;
        this.soundPitch = builder.soundPitch;
    }

    // Factory methods for each cast type
    public static Builder projectile() { return new Builder(CastType.PROJECTILE, 0); }
    public static Builder self() { return new Builder(CastType.SELF, 0); }
    public static Builder cone(float range) { return new Builder(CastType.CONE, range); }
    public static Builder targeted(float range) { return new Builder(CastType.TARGETED, range); }
    public static Builder beamLethal(float range) { return new Builder(CastType.BEAM_LETHAL, range); }
    public static Builder beamChannel(float range) { return new Builder(CastType.BEAM_CHANNEL, range); }

    // Getters
    public CastType getCastType() { return castType; }
    public float getRange() { return range; }
    public float getKnockbackStrength() { return knockbackStrength; }
    public boolean ignites() { return ignites; }
    public int getIgniteDurationSeconds() { return igniteDurationSeconds; }
    public boolean explodes() { return explodes; }
    public float getExplosionPower() { return explosionPower; }
    public boolean explosionBreaksBlocks() { return explosionBreaksBlocks; }
    public boolean disarms() { return disarms; }
    public boolean stuns() { return stuns; }
    public boolean repairsItem() { return repairsItem; }
    public int getRepairAmount() { return repairAmount; }
    public boolean opensBlocks() { return opensBlocks; }
    public float getPullStrength() { return pullStrength; }
    public boolean levitatesTarget() { return levitatesTarget; }
    public int getLevitateDurationTicks() { return levitateDurationTicks; }
    public float getUndeadBonusDamage() { return undeadBonusDamage; }
    public boolean controlsMob() { return controlsMob; }
    public int getControlDurationTicks() { return controlDurationTicks; }
    public List<Supplier<MobEffectInstance>> getTargetEffects() { return targetEffects; }
    public List<Supplier<MobEffectInstance>> getSelfEffects() { return selfEffects; }
    public SoundEvent getCastSound() { return castSound; }
    public float getSoundVolume() { return soundVolume; }
    public float getSoundPitch() { return soundPitch; }

    public static class Builder {
        private final CastType castType;
        private float range;
        private float knockbackStrength;
        private boolean ignites;
        private int igniteDurationSeconds;
        private boolean explodes;
        private float explosionPower;
        private boolean explosionBreaksBlocks;
        private boolean disarms;
        private boolean stuns;
        private boolean repairsItem;
        private int repairAmount;
        private boolean opensBlocks;
        private float pullStrength;
        private boolean levitatesTarget;
        private int levitateDurationTicks;
        private float undeadBonusDamage;
        private boolean controlsMob;
        private int controlDurationTicks;
        private final List<Supplier<MobEffectInstance>> targetEffects = new ArrayList<>();
        private final List<Supplier<MobEffectInstance>> selfEffects = new ArrayList<>();
        private SoundEvent castSound = ModSounds.SPELL_CAST_GENERIC.get();
        private float soundVolume = 0.8f;
        private float soundPitch = 1.2f;

        Builder(CastType castType, float range) {
            this.castType = castType;
            this.range = range;
        }

        public Builder targetEffect(Supplier<MobEffectInstance> effect) { targetEffects.add(effect); return this; }
        public Builder selfEffect(Supplier<MobEffectInstance> effect) { selfEffects.add(effect); return this; }
        public Builder knockback(float strength) { knockbackStrength = strength; return this; }
        public Builder ignites(int seconds) { ignites = true; igniteDurationSeconds = seconds; return this; }
        public Builder explodes(float power) { explodes = true; explosionPower = power; return this; }
        public Builder explodes(float power, boolean breaksBlocks) { explodes = true; explosionPower = power; explosionBreaksBlocks = breaksBlocks; return this; }
        public Builder disarms() { disarms = true; return this; }
        public Builder stuns() { stuns = true; return this; }
        public Builder repairsItem(int amount) { repairsItem = true; repairAmount = amount; return this; }
        public Builder opensBlocks() { opensBlocks = true; return this; }
        public Builder pullsTarget(float strength) { pullStrength = strength; return this; }
        public Builder levitatesTarget(int durationTicks) { levitatesTarget = true; levitateDurationTicks = durationTicks; return this; }
        public Builder undeadBonus(float damage) { undeadBonusDamage = damage; return this; }
        public Builder controlsMob(int durationTicks) { controlsMob = true; controlDurationTicks = durationTicks; return this; }
        public Builder sound(SoundEvent event) { castSound = event; return this; }
        public Builder sound(SoundEvent event, float volume, float pitch) { castSound = event; soundVolume = volume; soundPitch = pitch; return this; }

        public SpellProperties build() { return new SpellProperties(this); }
    }
}
