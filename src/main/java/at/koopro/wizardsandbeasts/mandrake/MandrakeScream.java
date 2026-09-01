package at.koopro.wizardsandbeasts.mandrake;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The cry of a Mandrake, and who it reaches.
 *
 * <p>A mature Mandrake's scream is fatal in the books and merely awful here — Nausea and Weakness
 * rather than death, because a crop that killed the farmer who harvested it would be a crop nobody
 * ever planted twice. It is still indiscriminate: the wizard doing the pulling is inside the radius
 * along with everything else, which is the entire reason earmuffs exist.
 *
 * <h2>Protection is a tag, not an item</h2>
 * {@link #MUFFLES_SCREAMS} decides who is spared. Earmuffs are the canonical answer and the Blindfold
 * is in there too because the brief asked for it — a blindfold plainly does nothing about sound, so
 * it lives in a datapack tag where a pack that disagrees can take it out without touching code.
 *
 * <h2>Two screams, one implementation</h2>
 * The full-grown pull and a thrown Baby Mandrake differ only in radius and duration. Splitting them
 * into two methods would have been two places for "does this entity have earmuffs on" to drift.
 */
@NullMarked
public final class MandrakeScream {

    /** Blocks a mature Mandrake's scream carries. */
    public static final double ADULT_RADIUS = 8.0;
    /** Ticks of Nausea and Weakness from a mature pull. */
    public static final int ADULT_EFFECT_TICKS = 200;   // 10s

    /**
     * A Baby Mandrake's cry is shorter and carries less far.
     *
     * <p>It has to be worth throwing and not worth farming: half the reach and a third of the
     * duration keeps it a disruption rather than a better version of the harvest.
     */
    public static final double BABY_RADIUS = 4.0;
    public static final int BABY_EFFECT_TICKS = 70;     // 3.5s

    /** Worn on the head, this spares you. */
    public static final TagKey<Item> MUFFLES_SCREAMS = TagKey.create(
            Registries.ITEM,
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "muffles_screams"));

    private MandrakeScream() {}

    /** A mature Mandrake pulled out of the ground. */
    public static int adult(Level level, Vec3 at, @Nullable LivingEntity source) {
        return scream(level, at, ADULT_RADIUS, ADULT_EFFECT_TICKS, source, 1.2f, 0.7f);
    }

    /** A Baby Mandrake hitting something. */
    public static int baby(Level level, Vec3 at, @Nullable LivingEntity source) {
        return scream(level, at, BABY_RADIUS, BABY_EFFECT_TICKS, source, 0.8f, 1.5f);
    }

    /**
     * Screams at {@code at}, afflicting every unprotected living thing in range.
     *
     * <p>{@code source} is excluded from nothing — it is passed only so the effects are attributed —
     * because a Mandrake does not care who pulled it. That is the mechanic.
     *
     * @return how many entities were affected, for feedback and tests
     */
    public static int scream(Level level, Vec3 at, double radius, int effectTicks,
                             @Nullable LivingEntity source, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GHAST_SCREAM, SoundSource.BLOCKS,
                volume, pitch + level.getRandom().nextFloat() * 0.4f);
        if (level.isClientSide()) {
            return 0;
        }
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.NOTE, at.x, at.y + 0.5, at.z,
                    (int) (radius * 3), radius * 0.4, 0.4, radius * 0.4, 0.0);
        }

        AABB box = new AABB(at, at).inflate(radius);
        int afflicted = 0;
        for (LivingEntity heard : level.getEntitiesOfClass(LivingEntity.class, box,
                candidate -> candidate.isAlive() && candidate.distanceToSqr(at) <= radius * radius)) {
            if (isProtected(heard)) {
                continue;
            }
            heard.addEffect(new MobEffectInstance(MobEffects.NAUSEA, effectTicks, 0), source);
            heard.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, effectTicks, 0), source);
            afflicted++;
        }
        return afflicted;
    }

    /** Whether this entity is wearing something that keeps the scream out. */
    public static boolean isProtected(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.HEAD).is(MUFFLES_SCREAMS);
    }
}
