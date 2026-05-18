package at.koopro.wizardsandbeasts;

import at.koopro.wizardsandbeasts.client.ClientSetup;
import at.koopro.wizardsandbeasts.apparition.client.ApparitionClientController;
import at.koopro.wizardsandbeasts.bestiary.creature.niffler.client.NifflerPouchScreen;
import at.koopro.wizardsandbeasts.legilimency.client.LegilimencyVisionRenderer;
import at.koopro.wizardsandbeasts.trunk.client.gui.PocketConfiguratorScreen;
import at.koopro.wizardsandbeasts.wand.client.gui.OllivanderTrialScreen;
import at.koopro.wizardsandbeasts.wand.client.gui.WandmakersBenchScreen;
import at.koopro.wizardsandbeasts.registry.ModMenuTypes;
import at.koopro.wizardsandbeasts.broom.client.BroomRiderRenderer;
import at.koopro.wizardsandbeasts.form.client.FormRenderStateModifier;
import at.koopro.wizardsandbeasts.form.client.ObscurialClientViewHandler;
import at.koopro.wizardsandbeasts.form.client.TransitionEffectRenderer;
import at.koopro.wizardsandbeasts.spell.client.ColoredGlowRenderer;
import at.koopro.wizardsandbeasts.spell.client.ProtegoCubeRenderer;
import at.koopro.wizardsandbeasts.spell.client.SpellClientInputHandler;
import at.koopro.wizardsandbeasts.spell.client.SpellKeyBindings;
import at.koopro.wizardsandbeasts.particle.client.ModParticleProviders;
import at.koopro.wizardsandbeasts.wand.client.WandBeamRenderer;
import at.koopro.wizardsandbeasts.form.client.hud.FormDebugOverlay;
import at.koopro.wizardsandbeasts.client.hud.MobEffectFullscreenOverlays;
import at.koopro.wizardsandbeasts.spell.client.render.CrucioScreenRenderer;
import at.koopro.wizardsandbeasts.client.hud.ObscurusOverlay;
import at.koopro.wizardsandbeasts.spell.client.hud.SpellDiamondOverlay;
import at.koopro.wizardsandbeasts.client.debug.DebugHudRenderer;
import at.koopro.wizardsandbeasts.client.debug.DebugKeyBindings;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = WizardsAndBeastsMod.MODID, dist = Dist.CLIENT)
public class WizardsAndBeastsClient {

    public WizardsAndBeastsClient(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        modEventBus.addListener(ClientSetup::registerRenderers);
        modEventBus.addListener(ClientSetup::registerLayers);
        modEventBus.addListener(ModParticleProviders::register);
        modEventBus.addListener(BroomRiderRenderer::registerModifiers);
        modEventBus.addListener(FormRenderStateModifier::registerModifiers);
        modEventBus.addListener(SpellKeyBindings::register);
        modEventBus.addListener(this::registerGuiLayers);
        modEventBus.addListener(WizardsAndBeastsClient::registerMenus);
        if (Config.enableDebugTools) {
            modEventBus.addListener(DebugKeyBindings::register);
        }

        NeoForge.EVENT_BUS.addListener(SpellClientInputHandler::onClientTick);
        NeoForge.EVENT_BUS.addListener(SpellClientInputHandler::onScroll);
        NeoForge.EVENT_BUS.addListener(WandBeamRenderer::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(ColoredGlowRenderer::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(ProtegoCubeRenderer::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(ApparitionClientController::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(LegilimencyVisionRenderer::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(ObscurialClientViewHandler::onRenderHand);
        NeoForge.EVENT_BUS.addListener(ObscurialClientViewHandler::onRenderGuiLayer);
    }

    private static void registerMenus(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.WANDMAKERS_BENCH.get(), WandmakersBenchScreen::new);
        event.register(ModMenuTypes.OLLIVANDER_TRIAL.get(), OllivanderTrialScreen::new);
        event.register(ModMenuTypes.POCKET_CONFIGURATOR.get(), PocketConfiguratorScreen::new);
        event.register(ModMenuTypes.NIFFLER_POUCH.get(), NifflerPouchScreen::new);
    }

    private void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(SpellDiamondOverlay.ID, SpellDiamondOverlay::render);
        event.registerAboveAll(ObscurusOverlay.ID, ObscurusOverlay::render);
        event.registerAboveAll(MobEffectFullscreenOverlays.ID, MobEffectFullscreenOverlays::render);
        event.registerAboveAll(CrucioScreenRenderer.ID, CrucioScreenRenderer::render);
        event.registerAboveAll(TransitionEffectRenderer.ID, TransitionEffectRenderer::render);
        if (Config.enableDebugTools) {
            event.registerAboveAll(FormDebugOverlay.ID, FormDebugOverlay::render);
            event.registerAboveAll(DebugHudRenderer.ID, DebugHudRenderer::render);
        }
    }
}
