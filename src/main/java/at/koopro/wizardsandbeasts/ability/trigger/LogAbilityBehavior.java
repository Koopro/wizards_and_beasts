package at.koopro.wizardsandbeasts.ability.trigger;

import at.koopro.wizardsandbeasts.ability.def.AbilityDefinition;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Debug behavior that only logs — the verification stand-in for real behaviors. Wired to the test ability
 * JSONs when {@code Config.enableDebugTools} is on. Also sends the player an actionbar note so the trigger
 * path is observable in-game without reading logs.
 */
@NullMarked
public final class LogAbilityBehavior implements AbilityBehavior {

    private static final Logger LOGGER = LoggerFactory.getLogger("WizardsAndBeasts/Ability");

    @Override
    public boolean onActivate(ServerPlayer player, AbilityDefinition def) {
        LOGGER.info("[ability-debug] {} activated ACTIVE ability {}", player.getName().getString(), def.id());
        player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("[ability] fired " + def.id()), true);
        return true;
    }

    @Override
    public void onToggle(ServerPlayer player, AbilityDefinition def, boolean nowOn) {
        LOGGER.info("[ability-debug] {} toggled {} -> {}", player.getName().getString(), def.id(), nowOn);
        player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("[ability] " + def.id() + " " + (nowOn ? "ON" : "OFF")),
                true);
    }
}
