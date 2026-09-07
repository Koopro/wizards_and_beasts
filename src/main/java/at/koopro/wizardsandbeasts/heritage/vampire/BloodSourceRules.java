package at.koopro.wizardsandbeasts.heritage.vampire;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NullMarked;

/**
 * What has blood in it, how much a bite is worth, and how long a bitten thing stays bitten.
 *
 * <p>Split out of {@link VampireBloodHandler} because these are three separate judgements a server owner
 * might want to reason about on their own, and because the handler is otherwise a wall of event
 * plumbing with the actual design decisions buried in it.
 *
 * <p><b>The tag is a deny-list, not an allow-list.</b> {@code wizards_and_beasts:bloodless} names the
 * things a vampire gets nothing out of; everything else is fair game. An allow-list would have meant
 * every mob from every other mod silently yielding nothing, which reads as a broken feature rather than
 * as a considered one — and the failure would be invisible, because a modded cow looks exactly like a
 * vanilla one.
 */
@NullMarked
public final class BloodSourceRules {

    /**
     * Creatures with nothing a vampire wants: constructs, elementals, the already-dead.
     *
     * <p>Hand-authored in {@code src/main/resources/data} rather than generated, following the split
     * {@code MinistryLicenceTags} documents — membership is a judgement about the fiction, not something
     * derivable from the registry.
     */
    public static final TagKey<EntityType<?>> BLOODLESS = TagKey.create(
            Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "bloodless"));

    private BloodSourceRules() {}

    /**
     * True when {@code target} is something a vampire could, in principle, drink from.
     *
     * <p>Says nothing about cooldowns, the drained window, or whether the vampire is already full — those
     * are timing, and they belong to the handler. This is only "does this thing contain blood".
     *
     * <p>Undead are excluded by vanilla's own {@code minecraft:undead} tag as well as by the mod's, so a
     * modded zombie that joined the vanilla tag is covered without anyone editing this mod's file.
     */
    public static boolean hasBlood(LivingEntity target) {
        if (!target.isAlive() || target.isInvulnerable()) {
            return false;
        }
        EntityType<?> type = target.getType();
        return !type.is(BLOODLESS) && !type.is(EntityTypeTags.UNDEAD);
    }

    /**
     * Blood a single feed on this creature is worth.
     *
     * <p>Scaled by maximum health so a cow is worth more than a chicken and a ravager more than either,
     * multiplied for something that fights back, then clamped at both ends. The upper clamp is the one
     * that matters: a feed that could fill the pool would delete the thirst stages, because a player
     * would simply never be seen below {@link ThirstStage#SATED}.
     */
    public static float yieldFrom(LivingEntity target) {
        float raw = target.getMaxHealth() * VampireBloodConfig.feedPerMaxHealth;
        if (target instanceof Enemy) {
            raw *= VampireBloodConfig.hostileFeedMultiplier;
        }
        return Mth.clamp(raw, VampireBloodConfig.feedMin, VampireBloodConfig.feedMax);
    }

    /** True when this creature was fed on recently enough to still be empty. */
    public static boolean isRecentlyDrained(LivingEntity target, long gameTime) {
        return gameTime < target.getData(ModAttachments.BLOOD_DRAINED_UNTIL.get());
    }

    /** Marks a creature as drained until {@code gameTime + vampireBloodDrainedImmunityTicks}. */
    public static void markDrained(LivingEntity target, long gameTime) {
        target.setData(ModAttachments.BLOOD_DRAINED_UNTIL.get(),
                gameTime + VampireBloodConfig.drainedImmunityTicks);
    }

    /**
     * Whether this particular target is off limits for policy reasons rather than biological ones.
     *
     * <p>Only players, and only because feeding on one is a PvP mechanic with no consent step yet. Kept
     * separate from {@link #hasBlood} so the refusal message can say something true: a wizard is not
     * bloodless, they are protected.
     */
    public static boolean isProtected(LivingEntity target) {
        return target instanceof Player && !VampireBloodConfig.allowFeedingOnPlayers;
    }
}
