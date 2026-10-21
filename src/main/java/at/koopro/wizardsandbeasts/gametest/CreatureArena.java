package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.creature.ecology.EcologyBrain;
import at.koopro.wizardsandbeasts.creature.ecology.EcologyGoal;
import at.koopro.wizardsandbeasts.creature.ecology.EcologyIntent;
import at.koopro.wizardsandbeasts.creature.ecology.EcologySensing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A sealed lane for testing one creature behaviour at a time, against the real world, without waiting for AI to
 * wander into it.
 *
 * <p>The ecology layer is split so this is possible: {@code EcologyBrain} decides from a {@code Perception} (unit
 * tested with {@code EcologyScenario}), and {@code EcologyGoal.think()} builds that perception from the live world
 * and decides. An arena spawns creatures frozen ({@code noAi}, no gravity) at fixed offsets on its own height, so
 * nothing moves between setup and the look; {@link #think} takes one look on demand; {@link #expect} asserts the
 * decision. A scenario that wants real movement unfreezes a creature and ticks.
 *
 * <pre>{@code
 * CreatureArena arena = CreatureArena.at(helper, 70);
 * PathfinderMob unicorn = arena.creature("unicorn", 0, 0);
 * arena.creature("acromantula", 6, 0);
 * arena.expect(unicorn, EcologyIntent.FLEE_PREDATOR);
 * arena.clear();
 * }</pre>
 */
public final class CreatureArena {

    private final GameTestHelper helper;
    private final int y;
    private final List<Entity> spawned = new ArrayList<>();

    private CreatureArena(GameTestHelper helper, int y) {
        this.helper = helper;
        this.y = y;
    }

    /** An arena on its own height, so its creatures cannot sense another scenario's. */
    public static CreatureArena at(GameTestHelper helper, int y) {
        WizardTestSupport.forceChunks(helper, new BlockPos(-2, y, -2), new BlockPos(24, y, 4));
        return new CreatureArena(helper, y);
    }

    /** A mod creature by id, frozen at ({@code x}, {@code z}) on the arena floor. */
    public PathfinderMob creature(String id, int x, int z) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, id));
        return (PathfinderMob) spawn(type, x, z);
    }

    /** Any entity, frozen at ({@code x}, {@code z}). */
    public <E extends Entity> E spawn(EntityType<E> type, int x, int z) {
        E entity = helper.spawn(type, new BlockPos(x, y, z));
        if (entity instanceof Mob mob) {
            mob.setNoAi(true);
        }
        entity.setNoGravity(true);
        spawned.add(entity);
        return entity;
    }

    /** One look around and decision, now — exactly what the goal does every {@link EcologySensing#INTERVAL} ticks. */
    public EcologyBrain.Decision think(PathfinderMob mob) {
        return new EcologyGoal(mob, Set.of()).think();
    }

    public void expect(PathfinderMob mob, EcologyIntent intent) {
        EcologyBrain.Decision decision = think(mob);
        WizardTestSupport.check(helper, decision.intent() == intent,
                () -> mob.getType().getDescriptionId() + " decided " + decision + ", expected " + intent
                        + " from " + EcologySensing.state(mob).perception());
    }

    public BlockPos absolute(int x, int z) {
        return helper.absolutePos(new BlockPos(x, y, z));
    }

    public void clear() {
        spawned.forEach(Entity::discard);
        spawned.clear();
    }
}
