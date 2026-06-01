package at.koopro.wizardsandbeasts.stats;

import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class StatMilestones {

    private static final Logger LOGGER = LogManager.getLogger(StatMilestones.class);

    private StatMilestones() {}

    /**
     * Entry point for milestone-triggered stat bumps. All cases are stubs — wiring into gameplay
     * events is deferred to a future prompt.
     */
    public static void onMilestoneTriggered(Player player, MilestoneType type) {
        switch (type) {
            case FIRST_PATRONUS_CORPOREAL -> {
                // TODO: grant milestone bump (e.g. WILLPOWER or POWER) for first corporeal Patronus
                LOGGER.debug("[WizardsAndBeasts] Milestone triggered: FIRST_PATRONUS_CORPOREAL for {}", player.getName().getString());
            }
            case FIRST_IMPERIUS_RESISTED -> {
                // TODO: grant milestone bump (e.g. WILLPOWER) for first Imperius resist
                LOGGER.debug("[WizardsAndBeasts] Milestone triggered: FIRST_IMPERIUS_RESISTED for {}", player.getName().getString());
            }
            case FIRST_OCCLUMENCY_DEFENCE_SUCCESS -> {
                // TODO: grant milestone bump (e.g. WILLPOWER) for first successful Occlumency defence
                LOGGER.debug("[WizardsAndBeasts] Milestone triggered: FIRST_OCCLUMENCY_DEFENCE_SUCCESS for {}", player.getName().getString());
            }
            case FIRST_ANIMAGUS_TRANSFORMATION -> {
                // TODO: grant milestone bump (e.g. PRECISION or POWER) for first Animagus transformation
                LOGGER.debug("[WizardsAndBeasts] Milestone triggered: FIRST_ANIMAGUS_TRANSFORMATION for {}", player.getName().getString());
            }
            case NEAR_DEATH_SURVIVED -> {
                // TODO: grant milestone bump (e.g. POWER or WILLPOWER) for surviving near-death
                LOGGER.debug("[WizardsAndBeasts] Milestone triggered: NEAR_DEATH_SURVIVED for {}", player.getName().getString());
            }
        }
    }
}
