package at.koopro.wizardsandbeasts;

import at.koopro.wizardsandbeasts.client.ClientSetup;
import at.koopro.wizardsandbeasts.client.apparition.ApparitionClientController;
import at.koopro.wizardsandbeasts.client.beam.BeamClientEvents;
import at.koopro.wizardsandbeasts.client.beam.ModBeamShapes;
import at.koopro.wizardsandbeasts.client.bestiary.niffler.NifflerPouchScreen;
import at.koopro.wizardsandbeasts.client.item.HermionesBagScreen;
import at.koopro.wizardsandbeasts.client.legilimency.LegilimencyVisionRenderer;
import at.koopro.wizardsandbeasts.client.trunk.gui.PocketConfiguratorScreen;
import at.koopro.wizardsandbeasts.client.wand.gui.OllivanderTrialScreen;
import at.koopro.wizardsandbeasts.client.wand.gui.WandmakersBenchScreen;
import at.koopro.wizardsandbeasts.registry.ModMenuTypes;
import at.koopro.wizardsandbeasts.client.broom.BroomRiderRenderer;
import at.koopro.wizardsandbeasts.client.form.FormRenderStateModifier;
import at.koopro.wizardsandbeasts.client.form.AnimagusClientViewHandler;
import at.koopro.wizardsandbeasts.client.form.ObscurialClientViewHandler;
import at.koopro.wizardsandbeasts.client.form.TransitionEffectRenderer;
import at.koopro.wizardsandbeasts.client.spell.ColoredGlowRenderer;
import at.koopro.wizardsandbeasts.client.spell.SpellClientInputHandler;
import at.koopro.wizardsandbeasts.client.spell.SpellKeyBindings;
import at.koopro.wizardsandbeasts.client.particle.ModParticleProviders;
import at.koopro.wizardsandbeasts.client.form.hud.FormDebugOverlay;
import at.koopro.wizardsandbeasts.client.hud.MobEffectFullscreenOverlays;
import at.koopro.wizardsandbeasts.client.spell.render.CrucioScreenRenderer;
import at.koopro.wizardsandbeasts.client.hud.ObscurusOverlay;
import at.koopro.wizardsandbeasts.client.spell.hud.SpellDiamondOverlay;
import at.koopro.wizardsandbeasts.client.debug.DebugHudRenderer;
import at.koopro.wizardsandbeasts.client.debug.DebugKeyBindings;
import at.koopro.wizardsandbeasts.client.event.InventoryScreenInjector;
import at.koopro.wizardsandbeasts.client.hud.stats.StatHudOverlay;
import at.koopro.wizardsandbeasts.client.spell.CharacterSheetKeyHandler;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import at.koopro.wizardsandbeasts.client.gui.config.WizardsConfigScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = WizardsAndBeastsMod.MODID, dist = Dist.CLIENT)
public class WizardsAndBeastsClient {

    public WizardsAndBeastsClient(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                (container, modListScreen) -> new WizardsConfigScreen(modListScreen));

        // Beam shapes are a client-only visual concern, so the registry lives on the client bus.
        ModBeamShapes.init(modEventBus);

        modEventBus.addListener(ClientSetup::registerRenderers);
        modEventBus.addListener(ClientSetup::registerLayers);
        modEventBus.addListener(ModParticleProviders::register);
        modEventBus.addListener(BroomRiderRenderer::registerModifiers);
        modEventBus.addListener(FormRenderStateModifier::registerModifiers);
        modEventBus.addListener(at.koopro.wizardsandbeasts.client.petrify.PetrifyRenderHandler::registerModifiers);
        modEventBus.addListener(SpellKeyBindings::register);
        modEventBus.addListener(at.koopro.wizardsandbeasts.client.ability.AbilityFrameworkKeyBindings::register);
        modEventBus.addListener(this::registerGuiLayers);
        modEventBus.addListener(WizardsAndBeastsClient::registerMenus);
        if (Config.enableDebugTools) {
            modEventBus.addListener(DebugKeyBindings::register);
        }

        NeoForge.EVENT_BUS.addListener(SpellClientInputHandler::onClientTick);
        NeoForge.EVENT_BUS.addListener(SpellClientInputHandler::onScroll);
        NeoForge.EVENT_BUS.addListener(BeamClientEvents::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(BeamClientEvents::onEntityLeaveLevel);
        NeoForge.EVENT_BUS.addListener(ColoredGlowRenderer::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(ApparitionClientController::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(LegilimencyVisionRenderer::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(ObscurialClientViewHandler::onRenderHand);
        NeoForge.EVENT_BUS.addListener(ObscurialClientViewHandler::onRenderGuiLayer);
        NeoForge.EVENT_BUS.addListener(AnimagusClientViewHandler::onRenderHand);
        NeoForge.EVENT_BUS.addListener(AnimagusClientViewHandler::onRenderNameTag);
        NeoForge.EVENT_BUS.addListener(InventoryScreenInjector::onScreenInit);
        NeoForge.EVENT_BUS.addListener(CharacterSheetKeyHandler::onClientTick);
        NeoForge.EVENT_BUS.addListener(StatHudOverlay::onClientTick);
        NeoForge.EVENT_BUS.addListener(at.koopro.wizardsandbeasts.client.ability.AbilityWheelController::onClientTick);
    }

    private static void registerMenus(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.WANDMAKERS_BENCH.get(), WandmakersBenchScreen::new);
        event.register(ModMenuTypes.OLLIVANDER_TRIAL.get(), OllivanderTrialScreen::new);
        event.register(ModMenuTypes.POCKET_CONFIGURATOR.get(), PocketConfiguratorScreen::new);
        event.register(ModMenuTypes.NIFFLER_POUCH.get(), NifflerPouchScreen::new);
        event.register(ModMenuTypes.HERMIONES_BAG.get(), HermionesBagScreen::new);
    }

    private void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(SpellDiamondOverlay.ID, SpellDiamondOverlay::render);
        event.registerAboveAll(ObscurusOverlay.ID, ObscurusOverlay::render);
        event.registerAboveAll(MobEffectFullscreenOverlays.ID, MobEffectFullscreenOverlays::render);
        event.registerAboveAll(CrucioScreenRenderer.ID, CrucioScreenRenderer::render);
        event.registerAboveAll(TransitionEffectRenderer.ID, TransitionEffectRenderer::render);
        event.registerAboveAll(at.koopro.wizardsandbeasts.client.floo.FlooTransitOverlay.ID,
                at.koopro.wizardsandbeasts.client.floo.FlooTransitOverlay::render);
        event.registerAboveAll(StatHudOverlay.ID, StatHudOverlay::render);
        if (Config.enableDebugTools) {
            event.registerAboveAll(FormDebugOverlay.ID, FormDebugOverlay::render);
            event.registerAboveAll(DebugHudRenderer.ID, DebugHudRenderer::render);
        }
    }
}
