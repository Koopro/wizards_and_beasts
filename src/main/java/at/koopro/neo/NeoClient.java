package at.koopro.neo;

import at.koopro.neo.client.ClientSetup;
import at.koopro.neo.client.broom.BroomRiderRenderer;
import at.koopro.neo.client.form.FormRenderStateModifier;
import at.koopro.neo.client.form.TransitionEffectRenderer;
import at.koopro.neo.client.spell.SpellClientInputHandler;
import at.koopro.neo.client.spell.SpellKeyBindings;
import at.koopro.neo.client.wand.WandBeamRenderer;
import at.koopro.neo.client.hud.FormDebugOverlay;
import at.koopro.neo.client.hud.SpellDiamondOverlay;
import at.koopro.neo.client.debug.DebugHudRenderer;
import at.koopro.neo.client.debug.DebugKeyBindings;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = Neo.MODID, dist = Dist.CLIENT)
public class NeoClient {

    public NeoClient(IEventBus modEventBus) {
        modEventBus.addListener(ClientSetup::registerRenderers);
        modEventBus.addListener(BroomRiderRenderer::registerModifiers);
        modEventBus.addListener(FormRenderStateModifier::registerModifiers);
        modEventBus.addListener(SpellKeyBindings::register);
        modEventBus.addListener(DebugKeyBindings::register);
        modEventBus.addListener(this::registerGuiLayers);

        NeoForge.EVENT_BUS.addListener(SpellClientInputHandler::onClientTick);
        NeoForge.EVENT_BUS.addListener(WandBeamRenderer::onRenderLevel);
    }

    private void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(SpellDiamondOverlay.ID, SpellDiamondOverlay::render);
        event.registerAboveAll(TransitionEffectRenderer.ID, TransitionEffectRenderer::render);
        event.registerAboveAll(FormDebugOverlay.ID, FormDebugOverlay::render);
        event.registerAboveAll(DebugHudRenderer.ID, DebugHudRenderer::render);
    }
}
