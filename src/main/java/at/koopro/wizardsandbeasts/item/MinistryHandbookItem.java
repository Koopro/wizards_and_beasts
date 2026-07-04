package at.koopro.wizardsandbeasts.item;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import com.mojang.logging.LogUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;

/**
 * Ministry of Magic handbook. Right-click opens the datapack-driven handbook screen client-side.
 * Registration is unconditional; opening the screen is gated on {@link Module#HANDBOOK}.
 *
 * <p>The screen is opened reflectively (same pattern as {@code BestiaryItem}) so the client-only
 * {@code HandbookScreen} class never loads on a dedicated server.
 */
public final class MinistryHandbookItem extends Item {
    private static final Logger LOGGER = LogUtils.getLogger();

    public MinistryHandbookItem(@NonNull Properties properties) {
        super(properties);
    }

    @Override
    public @NonNull InteractionResult use(@NonNull Level level, @NonNull Player player, @NonNull InteractionHand usedHand) {
        if (!ModuleManager.isEnabled(Module.HANDBOOK)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            try {
                Class<?> hooks = Class.forName("at.koopro.wizardsandbeasts.client.network.ClientScreenHooks");
                hooks.getMethod("openHandbookScreen").invoke(null);
            } catch (ReflectiveOperationException e) {
                LOGGER.warn("[WizardsAndBeasts] Failed to open handbook screen via client hook", e);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
