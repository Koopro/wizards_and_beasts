package at.koopro.wizardsandbeasts.ability.trigger;

import at.koopro.wizardsandbeasts.ability.def.AbilityDefinition;
import at.koopro.wizardsandbeasts.ability.def.AbilityInput;
import at.koopro.wizardsandbeasts.ability.def.AbilityTargeting;
import at.koopro.wizardsandbeasts.ability.def.AbilityType;
import at.koopro.wizardsandbeasts.ability.trigger.behavior.AnimagusFormAbilityBehavior;
import at.koopro.wizardsandbeasts.ability.trigger.behavior.ApparitionAbilityBehavior;
import at.koopro.wizardsandbeasts.ability.trigger.behavior.LegilimencyAbilityBehavior;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the adapter seam of the three migrated abilities: a framework trigger must reach the pre-existing
 * server entry point with the client's pick intact, and must <b>not</b> reach it when the pick is missing.
 * The behaviors are constructed with recording invokers, so the seam is exercised without a live player —
 * the adapters never dereference the player, they only forward it.
 */
class MigratedAbilityBehaviorSeamTest {

    private static AbilityDefinition def(String path, AbilityType type, AbilityTargeting targeting, double range) {
        return new AbilityDefinition(
                Identifier.fromNamespaceAndPath("wizards_and_beasts", path),
                type,
                Identifier.fromNamespaceAndPath("wizards_and_beasts", "textures/ability/placeholder.png"),
                "ability.wizards_and_beasts." + path,
                "ability.wizards_and_beasts." + path + ".desc",
                Identifier.fromNamespaceAndPath("wizards_and_beasts", "player_abilities"),
                0,
                0,
                new AbilityInput(targeting, 20, range));
    }

    // ── Apparition ──

    @Test
    void apparitionForwardsTheBlockTargetToTheServerLogic() {
        AtomicReference<BlockPos> seenBlock = new AtomicReference<>();
        AtomicReference<Vec3> seenPos = new AtomicReference<>();
        ApparitionAbilityBehavior behavior = new ApparitionAbilityBehavior((caster, blockPos, position) -> {
            seenBlock.set(blockPos);
            seenPos.set(position);
        });

        BlockPos blockPos = new BlockPos(12, 64, -7);
        Vec3 position = new Vec3(12.5, 64.0, -6.5);
        boolean fired = behavior.onActivate(null, def("apparition", AbilityType.ACTIVE, AbilityTargeting.BLOCK, 32.0),
                AbilityTarget.ofBlock(blockPos, position));

        assertTrue(fired);
        assertEquals(blockPos, seenBlock.get());
        assertEquals(position, seenPos.get());
    }

    @Test
    void apparitionWithoutADestinationIsASoftNoOp() {
        AtomicInteger calls = new AtomicInteger();
        ApparitionAbilityBehavior behavior =
                new ApparitionAbilityBehavior((caster, blockPos, position) -> calls.incrementAndGet());

        boolean fired = behavior.onActivate(null, def("apparition", AbilityType.ACTIVE, AbilityTargeting.BLOCK, 32.0),
                AbilityTarget.NONE);

        assertFalse(fired, "no destination must not consume a cooldown");
        assertEquals(0, calls.get());
    }

    // ── Legilimency ──

    @Test
    void legilimencyForwardsThePickedEntityId() {
        AtomicInteger seen = new AtomicInteger(-1);
        LegilimencyAbilityBehavior behavior =
                new LegilimencyAbilityBehavior((caster, entityId) -> seen.set(entityId));

        boolean fired = behavior.onActivate(null, def("legilimency", AbilityType.ACTIVE, AbilityTargeting.ENTITY, 8.0),
                AbilityTarget.ofEntity(4321));

        assertTrue(fired);
        assertEquals(4321, seen.get());
    }

    @Test
    void legilimencyWithoutAMindIsASoftNoOp() {
        AtomicInteger calls = new AtomicInteger();
        LegilimencyAbilityBehavior behavior =
                new LegilimencyAbilityBehavior((caster, entityId) -> calls.incrementAndGet());

        boolean fired = behavior.onActivate(null, def("legilimency", AbilityType.ACTIVE, AbilityTargeting.ENTITY, 8.0),
                AbilityTarget.NONE);

        assertFalse(fired);
        assertEquals(0, calls.get());
    }

    // ── Animagus form ──

    @Test
    void animagusOwnsItsToggleStateAndReadsItLive() {
        boolean[] transformed = {false};
        AnimagusFormAbilityBehavior behavior = new AnimagusFormAbilityBehavior(new AnimagusFormAbilityBehavior.Invoker() {
            @Override
            public void toggleTransform(ServerPlayer player) {
                transformed[0] = !transformed[0];
            }

            @Override
            public boolean isTransformed(ServerPlayer player) {
                return transformed[0];
            }
        });
        AbilityDefinition def = def("animagus_form", AbilityType.TOGGLE, AbilityTargeting.NONE, 0.0);

        assertTrue(behavior.ownsToggleState(), "framework must not keep a second copy of the form bit");
        assertFalse(behavior.isToggledOn(null, def));

        behavior.onToggle(null, def, true);
        assertTrue(behavior.isToggledOn(null, def), "state is read back from the owner, not from the framework");

        behavior.onToggle(null, def, false);
        assertFalse(behavior.isToggledOn(null, def));
    }

    @Test
    void animagusReportsTheOwnersStateEvenWhenTheToggleIsRefused() {
        // An owner that refuses every request (mid-transition, no chosen form, ritual incomplete).
        AnimagusFormAbilityBehavior behavior = new AnimagusFormAbilityBehavior(new AnimagusFormAbilityBehavior.Invoker() {
            @Override
            public void toggleTransform(ServerPlayer player) {
                // refused
            }

            @Override
            public boolean isTransformed(ServerPlayer player) {
                return false;
            }
        });
        AbilityDefinition def = def("animagus_form", AbilityType.TOGGLE, AbilityTargeting.NONE, 0.0);

        behavior.onToggle(null, def, true);
        assertFalse(behavior.isToggledOn(null, def), "a refused toggle must not read back as on");
    }

    // ── Target value semantics the trigger path relies on ──

    @Test
    void targetKindsMatchOnlyTheirOwnTargeting() {
        AbilityTarget block = AbilityTarget.ofBlock(BlockPos.ZERO, Vec3.ZERO);
        AbilityTarget entity = AbilityTarget.ofEntity(7);

        assertTrue(block.matches(AbilityTargeting.BLOCK));
        assertFalse(block.matches(AbilityTargeting.ENTITY));
        assertTrue(entity.matches(AbilityTargeting.ENTITY));
        assertFalse(entity.matches(AbilityTargeting.BLOCK));

        // An untargeted ability accepts anything (the handler collapses it to NONE anyway).
        assertTrue(AbilityTarget.NONE.matches(AbilityTargeting.NONE));
        assertFalse(AbilityTarget.NONE.matches(AbilityTargeting.BLOCK));
        assertNull(AbilityTarget.NONE.blockPos());
        assertSame(AbilityTarget.Kind.NONE, AbilityTarget.NONE.kind());
    }

    @Test
    void definitionsWithoutAnInputBlockDefaultToPressToFire() {
        AbilityDefinition def = new AbilityDefinition(
                Identifier.fromNamespaceAndPath("wizards_and_beasts", "debug_active"),
                AbilityType.ACTIVE,
                Identifier.fromNamespaceAndPath("wizards_and_beasts", "textures/ability/placeholder.png"),
                "n", "d", null, 40, 0, AbilityInput.NONE);

        assertFalse(def.input().requiresCharge());
        assertFalse(def.input().requiresTarget());
    }
}
