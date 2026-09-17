package at.koopro.wizardsandbeasts.ministry.trace;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.bestiary.BestiaryEntry;
import at.koopro.wizardsandbeasts.bestiary.BestiaryEntryRegistry;
import at.koopro.wizardsandbeasts.map.MapLandmarkTags;
import at.koopro.wizardsandbeasts.ministry.MinistryRecords;
import at.koopro.wizardsandbeasts.ministry.licence.MinistryArea;
import at.koopro.wizardsandbeasts.ministry.licence.MinistryLicenceTags;
import at.koopro.wizardsandbeasts.registry.ModVillager;
import at.koopro.wizardsandbeasts.util.ImmediateDanger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Looks around a cast and writes down what the law could care about. The only place in the Trace that reads
 * the world; everything it produces is a plain {@link CastScene}.
 *
 * <p>A witness must be able to see the caster — line of sight, awake, close enough for magic that visible.
 */
@NullMarked
public final class CastSurvey {

    /** Entities whose sighting of magic is a breach of secrecy. Villagers and wandering traders ship in it. */
    public static final TagKey<EntityType<?>> MUGGLES = TagKey.create(Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "muggles"));

    /** Ministry classification at which a creature is dangerous: XXXX, "dangerous / requires specialist knowledge". */
    public static final int DANGEROUS_RATING = 4;

    private CastSurvey() {}

    public static CastScene survey(ServerPlayer caster, SpellLaw law, long now) {
        ServerLevel level = caster.level();
        long ticksPerYear = MinistryTrace.ticksPerYear();
        boolean underage = WizardingAge.isUnderage(MinistryRecords.get(caster).ageAt(now, ticksPerYear));
        boolean atHogwarts = MinistryArea.isInside(level, caster.blockPosition(), MapLandmarkTags.HOGWARTS);

        double radius = TraceRules.witnessRadius(law.visibility());
        AABB box = caster.getBoundingBox().inflate(radius);
        List<LivingEntity> muggles = level.getEntitiesOfClass(LivingEntity.class, box,
                entity -> isMuggle(entity) && sees(entity, caster, radius));
        int officials = level.getEntitiesOfClass(LivingEntity.class, box,
                entity -> entity.getType().is(MinistryLicenceTags.MINISTRY_OFFICIALS) && sees(entity, caster, radius))
                .size();

        int adultsNearby = 0;
        List<UUID> wizardWitnesses = new ArrayList<>();
        double presence = TraceRules.WIZARD_PRESENCE_RADIUS * TraceRules.WIZARD_PRESENCE_RADIUS;
        for (ServerPlayer other : level.players()) {
            if (other == caster || !other.isAlive() || other.isSpectator()) {
                continue;
            }
            if (other.distanceToSqr(caster) <= presence
                    && !WizardingAge.isUnderage(MinistryRecords.get(other).ageAt(now, ticksPerYear))) {
                adultsNearby++;
            }
            if (sees(other, caster, radius)) {
                wizardWitnesses.add(other.getUUID());
            }
        }

        boolean creatureSeen = !muggles.isEmpty() && dangerousCreatureInView(level, box, muggles);
        return new CastScene(underage, ImmediateDanger.test(caster), atHogwarts, muggles.size(), adultsNearby,
                officials, creatureSeen, wizardWitnesses);
    }

    /** A Muggle by the tag, less the villagers who practise a wizarding trade. */
    public static boolean isMuggle(Entity entity) {
        if (!entity.getType().is(MUGGLES)) {
            return false;
        }
        return !(entity instanceof Villager villager)
                || !villager.getVillagerData().profession().is(ModVillager.WANDMAKER.getKey());
    }

    private static boolean sees(LivingEntity witness, Entity subject, double radius) {
        return witness != subject
                && witness.isAlive()
                && !witness.isSleeping()
                && witness.distanceToSqr(subject) <= radius * radius
                && witness.hasLineOfSight(subject);
    }

    private static boolean dangerousCreatureInView(ServerLevel level, AABB box, List<LivingEntity> muggles) {
        for (LivingEntity creature : level.getEntitiesOfClass(LivingEntity.class, box,
                entity -> entity.isAlive() && !(entity instanceof Player) && ratingOf(entity) >= DANGEROUS_RATING)) {
            for (LivingEntity muggle : muggles) {
                if (muggle.hasLineOfSight(creature)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int ratingOf(Entity entity) {
        Identifier type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        for (BestiaryEntry entry : BestiaryEntryRegistry.getAll()) {
            Optional<Identifier> entityType = entry.entityType();
            if (entityType.isPresent() && entityType.get().equals(type)) {
                return entry.mmRating().orElse(0);
            }
        }
        return 0;
    }
}
