package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.creature.wildlife.SignatureRules;
import at.koopro.wizardsandbeasts.creature.wildlife.SignatureRules.Omen;
import at.koopro.wizardsandbeasts.creature.wildlife.WildlifeWorld;
import at.koopro.wizardsandbeasts.entity.azkaban.DementorEntity;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.event.bestiary.BestiaryDiscoveryHandler;
import at.koopro.wizardsandbeasts.heritage.werewolf.WerewolfRules;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.monster.Enemy;
import org.jspecify.annotations.NonNull;

import java.util.EnumSet;
import java.util.Locale;

/**
 * Signature ability (Centaur): reading the sky. Centaurs are star-gazers and seers; they read the movements of the
 * planets and speak of it in their own way — "Mars is bright tonight" (Philosopher's Stone, Order of the Phoenix).
 *
 * <p>On a clear night under open sky a centaur stops, looks up, and a while later says what it has read to anyone
 * within {@code speak_radius}. What it says is always true of the world at that moment
 * ({@link SignatureRules#centaurOmen}): a dangerous creature abroad within {@code danger_radius} ("Mars is bright"),
 * the full moon, a coming storm, or — most nights — that the stars have nothing to tell a human.
 *
 * <p>It is a reading, not a service: it will not answer questions, and it reads at most once per {@code
 * cooldown_ticks}.
 */
public record StarReading(double speakRadius, double dangerRadius, int cooldownTicks) implements CreatureAbility {

    static final String COOLDOWN = "star_reading";

    public static final MapCodec<StarReading> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("speak_radius", 12.0).forGetter(StarReading::speakRadius),
            Codec.DOUBLE.optionalFieldOf("danger_radius", 48.0).forGetter(StarReading::dangerRadius),
            Codec.INT.optionalFieldOf("cooldown_ticks", 2400).forGetter(StarReading::cooldownTicks)
    ).apply(instance, StarReading::new));

    @Override
    public CreatureAbility.@NonNull Type type() {
        return CreatureAbility.Type.STAR_READING;
    }

    @Override
    public void registerGoals(@NonNull GenericBeastEntity entity, @NonNull GoalSelector goalSelector) {
        goalSelector.addGoal(3, new StargazeGoal(entity));
    }

    /** Whether the sky over this centaur can be read right now. */
    public static boolean canRead(GenericBeastEntity entity) {
        ServerLevel level = (ServerLevel) entity.level();
        return SignatureRules.canStargaze(level.isDarkOutside(), level.canSeeSky(entity.blockPosition().above()),
                level.isRainingAt(entity.blockPosition().above()) || level.isRaining());
    }

    /** What the sky says tonight, read from the world around this centaur. */
    public Omen read(GenericBeastEntity entity) {
        ServerLevel level = (ServerLevel) entity.level();
        boolean danger = !level.getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(dangerRadius),
                e -> e.isAlive() && e != entity && (e instanceof Enemy || e instanceof DementorEntity
                        || (e instanceof GenericBeastEntity beast && beast.isNaturallyHostile()))).isEmpty();
        return SignatureRules.centaurOmen(danger, WerewolfRules.fullMoonNight(level), WildlifeWorld.rainComing(level));
    }

    /**
     * Reads the sky and says so to everyone near enough to hear.
     *
     * @return the omen spoken
     */
    public Omen speak(GenericBeastEntity entity, Omen omen) {
        ServerLevel level = (ServerLevel) entity.level();
        Component words = Component.translatable("entity.wizards_and_beasts.centaur.omen."
                + omen.name().toLowerCase(Locale.ROOT)).withStyle(ChatFormatting.ITALIC);
        Component line = Component.translatable("entity.wizards_and_beasts.centaur.speaks", entity.getDisplayName(), words);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class,
                entity.getBoundingBox().inflate(speakRadius), p -> p.isAlive() && !p.isSpectator())) {
            player.sendSystemMessage(line);
            BestiaryDiscoveryHandler.witnessedSignature(player, entity);
        }
        entity.setCooldown(COOLDOWN, cooldownTicks);
        return omen;
    }

    /** Standing still under the night sky, looking up, then saying what it saw. */
    private final class StargazeGoal extends Goal {
        private final GenericBeastEntity centaur;
        private int ticks;

        StargazeGoal(GenericBeastEntity centaur) {
            this.centaur = centaur;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return ModuleManager.isEnabled(Module.CREATURES) && centaur.getTarget() == null
                    && centaur.getCooldown(COOLDOWN) == 0 && centaur.getRandom().nextInt(200) == 0 && canRead(centaur);
        }

        @Override
        public boolean canContinueToUse() {
            return ticks > 0 && centaur.getTarget() == null && canRead(centaur);
        }

        @Override
        public void start() {
            ticks = 120;
            centaur.getNavigation().stop();
        }

        @Override
        public void tick() {
            centaur.getLookControl().setLookAt(centaur.getX(), centaur.getEyeY() + 12.0, centaur.getZ() + 1.0);
            if (--ticks == 0) {
                speak(centaur, read(centaur));
            }
        }
    }
}
