package at.koopro.wizardsandbeasts.creature.ecology;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.creature.bond.BondableBeast;
import at.koopro.wizardsandbeasts.creature.schedule.AiCadence;
import at.koopro.wizardsandbeasts.creature.schedule.AiMetrics;
import at.koopro.wizardsandbeasts.creature.schedule.AiScratch;
import at.koopro.wizardsandbeasts.creature.wildlife.WildlifeWorld;
import at.koopro.wizardsandbeasts.entity.niffler.NifflerTreasure;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Builds a creature's {@link Perception}. The only place the ecology layer touches the world.
 *
 * <p><b>Cost</b> (the MEDIUM tier, documentation/CREATURE_AI_SCHEDULING.md). One bounded entity query
 * ({@code sense_radius}, at most 32 blocks) per creature every {@link #INTERVAL} ticks, staggered by entity id so a
 * herd does not look around on the same tick, plus at most one more per event wake. Everything — kin, threats, prey,
 * people, things on the ground — is classified in that one pass, into a reused list ({@link AiScratch}), comparing
 * squared distances. Profile lookups go through a per-type cache, never the registry. No block scans, except once
 * per creature life for a tree-dweller's home trunk (a 13×9×13 box). Between looks the goal acts on the last
 * decision. Nothing is sent to clients.
 */
@NullMarked
public final class EcologySensing {

    /** Ticks between looks around. */
    public static final int INTERVAL = 20;
    /** How long a reaction stays in force. */
    public static final int STIMULUS_TICKS = 100;

    /** Vanilla animals the mod's predators may hunt (sheep for a dragon, rabbits for a hippogriff). */
    public static final TagKey<EntityType<?>> PREY_TAG = tag("ecology_prey");
    /** Vanilla hunters the mod's prey run from (wolves, foxes). */
    public static final TagKey<EntityType<?>> PREDATOR_TAG = tag("ecology_predators");
    /** What a fear-sensitive creature runs from. */
    public static final TagKey<EntityType<?>> DREAD_TAG = tag("dementors");

    private EcologySensing() {}

    private static TagKey<EntityType<?>> tag(String path) {
        return TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path));
    }

    /** Whether this creature looks around on this tick: the MEDIUM tier. */
    public static boolean due(Entity creature) {
        return AiCadence.MEDIUM.due(creature);
    }

    /** Every entity, for the typed query that appends into a caller's list; made once. */
    private static final EntityTypeTest<Entity, Entity> ANY = EntityTypeTest.forClass(Entity.class);

    public static EcologyState state(PathfinderMob mob) {
        return mob.getData(ModAttachments.ECOLOGY_STATE.get());
    }

    /** Looks taken since start, for the performance guard test and the debug panel. */
    private static final java.util.concurrent.atomic.AtomicLong LOOKS = new java.util.concurrent.atomic.AtomicLong();

    public static long looksTaken() {
        return LOOKS.get();
    }

    public static Perception perceive(PathfinderMob mob, EcologyProfile profile) {
        LOOKS.incrementAndGet();
        AiMetrics.inc(AiMetrics.Metric.ECOLOGY_LOOK);
        ServerLevel level = (ServerLevel) mob.level();
        EcologyState state = state(mob);
        long now = level.getGameTime();
        if (state.home == null) {
            state.home = profile.has(EcologyCapability.TREE_DWELLING) ? treeNear(mob) : mob.position();
        }

        boolean night = level.isDarkOutside();
        boolean exposed = !night && level.canSeeSky(mob.blockPosition().above());
        float health = mob.getHealth() / Math.max(1.0f, mob.getMaxHealth());
        boolean bonded = mob instanceof BondableBeast beast && beast.bondOwner() != null;
        boolean canAttack = mob.getAttribute(Attributes.ATTACK_DAMAGE) != null
                && mob.getAttributeValue(Attributes.ATTACK_DAMAGE) > 0;

        double radius = profile.senseRadius();
        List<Entity> around = AiScratch.borrow();
        try {
            level.getEntities(ANY, mob.getBoundingBox().inflate(radius),
                    e -> e != mob && e.isAlive() && (e instanceof LivingEntity || e instanceof ItemEntity),
                    around, Integer.MAX_VALUE);
            return classify(mob, profile, state, now, night, exposed, health, bonded, canAttack, around);
        } finally {
            AiScratch.release(around);
        }
    }

    private static Perception classify(PathfinderMob mob, EcologyProfile profile, EcologyState state,
                                       long now, boolean night, boolean exposed, float health, boolean bonded,
                                       boolean canAttack, List<Entity> around) {
        Vec3 here = mob.position();
        // Squared distances throughout; the four nearest are square-rooted once, at the end.
        double far = Perception.FAR * Perception.FAR;
        double threatD = far, preyD = far, itemD = far, playerD = far;
        Vec3 threatAt = null, preyAt = null, itemAt = null;
        Entity prey = null;
        Player person = null;
        int kin = 0;
        double kx = 0, ky = 0, kz = 0;

        for (Entity e : around) {
            double d = e.distanceToSqr(mob);
            if (e instanceof ItemEntity item) {
                if (d < itemD && wants(profile, item.getItem())) {
                    itemD = d;
                    itemAt = item.position();
                }
                continue;
            }
            if (e instanceof Player player) {
                if (player.isSpectator() || (!profile.has(EcologyCapability.ILLUSION_AWARE) && perceivedAsAbsent(player))) {
                    continue;
                }
                if (d < playerD) {
                    playerD = d;
                    person = player;
                }
                continue;
            }
            LivingEntity other = (LivingEntity) e;
            if (other.getType() == mob.getType()) {
                kin++;
                kx += other.getX();
                ky += other.getY();
                kz += other.getZ();
                continue;
            }
            if (d < threatD && threatens(profile, mob, other)) {
                threatD = d;
                threatAt = other.position();
            }
            if (canAttack && d < preyD && isPrey(profile, mob, other)) {
                preyD = d;
                preyAt = other.position();
                prey = other;
            }
        }

        Perception.Kin kinView = kin == 0 ? Perception.Kin.NONE : kinView(here, kin, kx, ky, kz);
        Perception.PlayerView playerView = person == null ? Perception.PlayerView.NONE : new Perception.PlayerView(
                Math.sqrt(playerD), person.position(),
                WildlifeWorld.letsNear(person, mob.getType(),
                        profile.has(EcologyCapability.MAGICAL_CORRUPTION_SENSITIVE), ""),
                profile.has(EcologyCapability.MAGICAL_CORRUPTION_SENSITIVE) && WildlifeWorld.shuns(person, ""),
                person.isShiftKeyDown());

        state.preyId = prey == null ? null : prey.getUUID();
        state.playerId = person == null ? null : person.getUUID();
        Perception.Stimulus stimulus = state.stimulus != null && now < state.stimulusUntil ? state.stimulus : null;
        Perception perception = new Perception(night, exposed, health, bonded, state.fedAt(now),
                state.home.distanceTo(here), sighting(Math.sqrt(threatD), threatAt), sighting(Math.sqrt(preyD), preyAt),
                kinView, playerView, sighting(Math.sqrt(itemD), itemAt), stimulus);
        state.perception = perception;
        return perception;
    }

    private static Perception.Kin kinView(Vec3 here, int count, double x, double y, double z) {
        Vec3 centre = new Vec3(x / count, y / count, z / count);
        return new Perception.Kin(count, centre.distanceTo(here), centre);
    }

    private static Perception.Sighting sighting(double distance, @Nullable Vec3 at) {
        return at == null ? Perception.Sighting.NONE : new Perception.Sighting(distance, at);
    }

    /** Invisible, without a tell: an ordinary creature does not know they are there. */
    private static boolean perceivedAsAbsent(Player player) {
        return player.isInvisible();
    }

    /** What this creature runs from: what hunts it, or — if it feels dread — a Dementor. */
    static boolean threatens(EcologyProfile profile, PathfinderMob self, LivingEntity other) {
        if (profile.has(EcologyCapability.FEAR_SENSITIVE) && other.getType().is(DREAD_TAG)) {
            return true;
        }
        if (!profile.has(EcologyCapability.PREY) && !profile.has(EcologyCapability.COWARDLY)) {
            return false;
        }
        if (other.getType().is(PREDATOR_TAG)) {
            return true;
        }
        EcologyProfile theirs = EcologyProfileRegistry.of(other.getType());
        return theirs.has(EcologyCapability.PREDATOR) && !(other instanceof BondableBeast b && b.bondOwner() != null);
    }

    /**
     * What this predator hunts: declared prey, or the vanilla prey tag, no larger than itself, never kept animals —
     * a bonded creature or a tamed pet belongs to someone, and a predator that ate them would make keeping anything
     * near one a trap.
     */
    static boolean isPrey(EcologyProfile profile, PathfinderMob self, LivingEntity other) {
        if (!profile.has(EcologyCapability.PREDATOR)) {
            return false;
        }
        if (other instanceof BondableBeast b && b.bondOwner() != null) {
            return false;
        }
        if (other instanceof TamableAnimal tame && tame.isTame()) {
            return false;
        }
        if (other.getBbWidth() * other.getBbHeight() > self.getBbWidth() * self.getBbHeight() * 1.2f) {
            return false;
        }
        return other.getType().is(PREY_TAG) || EcologyProfileRegistry.of(other.getType()).has(EcologyCapability.PREY);
    }

    static boolean wants(EcologyProfile profile, ItemStack stack) {
        if (profile.has(EcologyCapability.TREASURE_SEEKER)
                && (stack.is(NifflerTreasure.HIGH) || stack.is(NifflerTreasure.MEDIUM))) {
            return true;
        }
        return profile.has(EcologyCapability.SCAVENGER) && (stack.is(ItemTags.MEAT) || stack.is(ItemTags.FISHES));
    }

    /** A tree-dweller's home: the nearest log within a few blocks of where it was first seen, else where it stands. */
    private static Vec3 treeNear(PathfinderMob mob) {
        BlockPos origin = mob.blockPosition();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-6, -4, -6), origin.offset(6, 4, 6))) {
            if (mob.level().getBlockState(pos).is(BlockTags.LOGS)) {
                double d = pos.distSqr(origin);
                if (d < bestD) {
                    bestD = d;
                    best = pos.immutable();
                }
            }
        }
        return best == null ? mob.position() : Vec3.atBottomCenterOf(best);
    }
}
