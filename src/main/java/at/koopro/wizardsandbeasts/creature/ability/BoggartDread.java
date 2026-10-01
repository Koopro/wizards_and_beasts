package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.bestiary.BestiaryEntry;
import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import at.koopro.wizardsandbeasts.creature.wildlife.SignatureRules;
import at.koopro.wizardsandbeasts.creature.wildlife.WildlifeWorld;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.entity.creature.Guise;
import at.koopro.wizardsandbeasts.event.bestiary.BestiaryDiscoveryHandler;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * Signature ability (Boggart): it becomes your fear. A Boggart is a shape-shifter that likes dark, enclosed spaces —
 * wardrobes, the gap under a bed, the cupboard under the sink — and nobody knows what it looks like alone, because it
 * takes the shape of whatever the person facing it fears most (Prisoner of Azkaban).
 *
 * <ul>
 *   <li><b>Unwatched</b> it is unseen.</li>
 *   <li><b>Faced by one person</b> it becomes the most frightening creature that person has met
 *       ({@link SignatureRules#boggartForm}: the highest Ministry grade among {@code fears} whose bestiary page they
 *       have at all) — everyone present sees that shape, as Lupin's class saw Ron's spider — and the fear takes hold of
 *       that person: darkness, slowness, weakness, nausea. Someone who has met none of them faces only a shapeless
 *       shadow, which is still frightening.</li>
 *   <li><b>Faced by several</b> it cannot decide ({@link SignatureRules#boggartConfused}): it flickers from one person's
 *       fear to the next and frightens nobody. Never face one alone.</li>
 *   <li><b>Riddikulus</b> (the spell's {@code boggart_banish} effect) finishes it, whatever shape it is in.</li>
 *   <li><b>Where it lives:</b> out in the light it goes looking for somewhere dark and roofed, and once there it stays.
 *       </li>
 * </ul>
 *
 * <p>The shape is synced as a {@link Guise}; the renderer draws the borrowed creature and the movement controller
 * plays its clips. The shape is never saved — it is decided by whoever is looking.
 */
public record BoggartDread(double range, int fearDurationTicks, List<FearForm> fears) implements CreatureAbility {

    /** A shape it can take: a creature's asset name, and the clip that creature moves with. */
    public record FearForm(String form, String walk) {
        public static final Codec<FearForm> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("form").forGetter(FearForm::form),
                Codec.STRING.optionalFieldOf("walk", "walk").forGetter(FearForm::walk)
        ).apply(instance, FearForm::new));
    }

    /** Frightening creatures with a rig of their own, true-sized enough to fill a doorway. */
    public static final List<FearForm> DEFAULT_FEARS = List.of(
            new FearForm("dementor", "float"), new FearForm("acromantula", "walk"), new FearForm("werewolf", "walk"),
            new FearForm("lethifold", "walk"), new FearForm("troll", "walk"), new FearForm("manticore", "walk"),
            new FearForm("nundu", "walk"), new FearForm("maledictus", "walk"), new FearForm("red_cap", "walk"),
            new FearForm("ghoul", "walk"));

    /** A grade for a creature whose bestiary page gives none. */
    static final int DEFAULT_DREAD = 3;

    public static final MapCodec<BoggartDread> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("range", 6.0).forGetter(BoggartDread::range),
            Codec.INT.optionalFieldOf("fear_duration_ticks", 120).forGetter(BoggartDread::fearDurationTicks),
            FearForm.CODEC.listOf().optionalFieldOf("fears", DEFAULT_FEARS).forGetter(BoggartDread::fears)
    ).apply(instance, BoggartDread::new));

    @Override
    public CreatureAbility.@NonNull Type type() {
        return CreatureAbility.Type.BOGGART_DREAD;
    }

    @Override
    public void registerGoals(@NonNull GenericBeastEntity entity, @NonNull GoalSelector goalSelector) {
        goalSelector.addGoal(3, new SeekDarknessGoal(entity));
        goalSelector.addGoal(4, new LurkGoal(entity));
    }

    @Override
    public void tick(@NonNull GenericBeastEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || entity.tickCount % 10 != 0) {
            return;
        }
        confront(entity, level, watchers(entity));
    }

    /** The people looking at it, spectators aside. */
    public List<Player> watchers(@NonNull GenericBeastEntity entity) {
        List<Player> watching = new ArrayList<>();
        for (Player player : AbilitySupport.nearbyPlayers(entity, range)) {
            if (!player.isSpectator() && isLookingAt(player, entity)) {
                watching.add(player);
            }
        }
        return watching;
    }

    /**
     * Takes the shape its watchers call for, and frightens the one person facing it alone.
     *
     * @return the shape it took ({@link Guise#encode}, {@code ""} for its own)
     */
    public String confront(@NonNull GenericBeastEntity entity, ServerLevel level, List<Player> watchers) {
        if (watchers.isEmpty()) {
            entity.setGuise("");
            entity.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, true, false));
            return "";
        }
        entity.removeEffect(MobEffects.INVISIBILITY);
        AbilitySupport.emitAtBody(level, entity, AbilitySupport.Particle.DREAD, 6);
        String shape;
        if (SignatureRules.boggartConfused(watchers.size())) {
            // It tries each of them in turn, and settles on none.
            Player turn = watchers.get((entity.tickCount / 10) % watchers.size());
            shape = shapeFor(turn);
        } else {
            Player alone = watchers.getFirst();
            shape = shapeFor(alone);
            alone.addEffect(new MobEffectInstance(MobEffects.DARKNESS, fearDurationTicks, 0));
            alone.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, fearDurationTicks, 1));
            alone.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, fearDurationTicks, 1));
            alone.addEffect(new MobEffectInstance(MobEffects.NAUSEA, fearDurationTicks, 0));
        }
        if (!shape.equals(entity.getGuise())) {
            entity.setGuise(shape);
            for (Player watcher : watchers) {
                if (watcher instanceof ServerPlayer serverPlayer) {
                    BestiaryDiscoveryHandler.witnessedSignature(serverPlayer, entity);
                }
            }
        }
        return shape;
    }

    /** The shape this person's fear takes: the worst creature they have met among {@code fears}, or {@code ""}. */
    public String shapeFor(Player player) {
        List<SignatureRules.Fear> met = new ArrayList<>();
        for (FearForm fear : fears) {
            Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(
                    Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, fear.form()));
            if (type.isPresent() && WildlifeWorld.tierFor(player, type.get()).ordinal()
                    >= DiscoveryTier.ENCOUNTERED.ordinal()) {
                met.add(new SignatureRules.Fear(new Guise(fear.form(), fear.walk()).encode(), dread(type.get())));
            }
        }
        return SignatureRules.boggartForm(met).orElse("");
    }

    /** How frightening a creature is: the highest Ministry grade on its bestiary pages. */
    static int dread(EntityType<?> type) {
        int grade = 0;
        for (BestiaryEntry entry : BestiaryDiscoveryHandler.entriesFor(type)) {
            grade = Math.max(grade, entry.mmRating().orElse(0));
        }
        return grade > 0 ? grade : DEFAULT_DREAD;
    }

    private static boolean isLookingAt(Player player, GenericBeastEntity target) {
        var look = player.getViewVector(1.0f).normalize();
        var toTarget = target.position().add(0, target.getBbHeight() * 0.5, 0)
                .subtract(player.getEyePosition()).normalize();
        return look.dot(toTarget) > 0.6;
    }

    /** Dark and roofed, the way a wardrobe is. */
    public static boolean settled(GenericBeastEntity entity) {
        BlockPos pos = entity.blockPosition();
        return SignatureRules.boggartSettled(entity.level().getMaxLocalRawBrightness(pos), !entity.level().canSeeSky(pos));
    }

    /** Out in the open or the light, it goes looking for somewhere dark and roofed. */
    private static final class SeekDarknessGoal extends Goal {
        private final GenericBeastEntity boggart;

        SeekDarknessGoal(GenericBeastEntity boggart) {
            this.boggart = boggart;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!ModuleManager.isEnabled(Module.CREATURES) || boggart.getTarget() != null || settled(boggart)
                    || boggart.getRandom().nextInt(40) != 0) {
                return false;
            }
            for (int i = 0; i < 12; i++) {
                Vec3 spot = LandRandomPos.getPos(boggart, 12, 4);
                if (spot != null) {
                    BlockPos pos = BlockPos.containing(spot);
                    if (SignatureRules.boggartSettled(boggart.level().getMaxLocalRawBrightness(pos),
                            !boggart.level().canSeeSky(pos))) {
                        return boggart.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.0);
                    }
                }
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            return !boggart.getNavigation().isDone() && boggart.getTarget() == null;
        }
    }

    /** Somewhere dark and roofed, it stays put — holding the move so nothing wanders it out again. */
    private static final class LurkGoal extends Goal {
        private final GenericBeastEntity boggart;
        private int ticks;

        LurkGoal(GenericBeastEntity boggart) {
            this.boggart = boggart;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return ModuleManager.isEnabled(Module.CREATURES) && boggart.getTarget() == null && settled(boggart);
        }

        @Override
        public void start() {
            ticks = 200 + boggart.getRandom().nextInt(200);
            boggart.getNavigation().stop();
        }

        @Override
        public boolean canContinueToUse() {
            return --ticks > 0 && boggart.getTarget() == null && settled(boggart);
        }
    }
}
