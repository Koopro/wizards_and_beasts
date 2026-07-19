package at.koopro.wizardsandbeasts.ability.trigger.behavior;

import at.koopro.wizardsandbeasts.ability.def.AbilityDefinition;
import at.koopro.wizardsandbeasts.ability.trigger.AbilityBehavior;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

import java.util.function.Consumer;

/**
 * Adapter for an untargeted ACTIVE ability whose whole implementation is "call this server entry point".
 * Used by the Obscurial and Animagus-beast abilities, which take no target and own their cooldowns — so
 * there is nothing to unpack and nothing to decide here.
 *
 * <p>The invoker is a plain {@link Consumer}, which is also the test seam: constructing one with a
 * recording lambda exercises the adapter without a live player.
 */
@NullMarked
public final class SimpleAbilityBehavior implements AbilityBehavior {

    private final Consumer<ServerPlayer> invoker;

    public SimpleAbilityBehavior(Consumer<ServerPlayer> invoker) {
        this.invoker = invoker;
    }

    @Override
    public boolean onActivate(ServerPlayer player, AbilityDefinition def) {
        invoker.accept(player);
        return true;
    }
}
