package at.koopro.wizardsandbeasts.ability.trigger.behavior;

import at.koopro.wizardsandbeasts.ability.def.AbilityDefinition;
import at.koopro.wizardsandbeasts.ability.trigger.AbilityBehavior;
import at.koopro.wizardsandbeasts.ability.trigger.AbilityTarget;
import at.koopro.wizardsandbeasts.apparition.ApparitionServerLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

/**
 * Thin adapter: unpacks the wheel's block target and hands it to the untouched
 * {@link ApparitionServerLogic#handleRequest}. Every gameplay rule — the licence/test/heritage gates, the
 * Three Ds focus roll, splinching, anti-Apparition wards, side-along pickup, the bespoke Apparition
 * cooldown — stays exactly where it was; this class adds no checks and consumes no framework cooldown
 * (the definition ships {@code cooldownTicks: 0}).
 */
@NullMarked
public final class ApparitionAbilityBehavior implements AbilityBehavior {

    /** The server entry point, injectable so the adapter seam is testable without a live player. */
    @FunctionalInterface
    public interface Invoker {
        void apparate(ServerPlayer caster, BlockPos targetBlockPos, Vec3 targetPosition);
    }

    public static final ApparitionAbilityBehavior INSTANCE =
            new ApparitionAbilityBehavior(ApparitionServerLogic::handleRequest);

    private final Invoker invoker;

    public ApparitionAbilityBehavior(Invoker invoker) {
        this.invoker = invoker;
    }

    @Override
    public boolean onActivate(ServerPlayer player, AbilityDefinition def, AbilityTarget target) {
        BlockPos blockPos = target.blockPos();
        Vec3 position = target.position();
        if (blockPos == null || position == null) {
            return false; // no destination — soft no-op, nothing consumed
        }
        invoker.apparate(player, blockPos, position);
        return true;
    }
}
