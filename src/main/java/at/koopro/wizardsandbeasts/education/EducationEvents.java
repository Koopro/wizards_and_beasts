package at.koopro.wizardsandbeasts.education;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.NullMarked;

/** Closes sittings whose clock ran out — including one left open across a logout, on the first second back. */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class EducationEvents {

    private EducationEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 20 == 0) {
            ExamSittings.tick(player);
        }
    }
}
