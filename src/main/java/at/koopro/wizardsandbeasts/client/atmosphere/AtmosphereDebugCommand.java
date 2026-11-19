package at.koopro.wizardsandbeasts.client.atmosphere;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;

/**
 * {@code /wbatmos}: a client-only readout of what the atmosphere thinks of where you stand: each place's score,
 * whether it is on, its eased strength and how many of its blocks the sampler remembers. For tuning profile
 * thresholds in a real room; changes nothing.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
@NullMarked
public final class AtmosphereDebugCommand {

    private AtmosphereDebugCommand() {}

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wbatmos").executes(ctx -> {
            var a = AtmosphereDirector.ambience();
            ctx.getSource().sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                    "atmosphere=%s fog=%s sound=%.2f | night=%s rain=%s sky=%s",
                    Config.atmosphere, Config.atmosphereFog, Config.ambientSoundVolume,
                    a.night(), a.raining(), a.skyOpen())));
            for (PlaceSense.Place p : PlaceSense.places()) {
                ctx.getSource().sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                        "%s %s score=%.3f (in %.2f/out %.2f/full %.2f) strength=%.2f anchors=%d%s",
                        p.active() ? "●" : "○", p.id(), p.score(), p.profile().enter(),
                        p.profile().leave(), p.profile().full(), p.strength(), p.anchorCount(),
                        p.gated ? "" : " (gated off)")));
            }
            return 1;
        }));
    }
}
