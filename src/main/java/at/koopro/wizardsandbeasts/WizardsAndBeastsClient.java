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
import at.koopro.wizardsandbeasts.client.render.outline.ClientOutlineState;
import at.koopro.wizardsandbeasts.client.render.outline.EntityOutlines;
import at.koopro.wizardsandbeasts.client.spell.SpellClientInputHandler;
import at.koopro.wizardsandbeasts.client.spell.SpellKeyBindings;
import at.koopro.wizardsandbeasts.client.particle.ModParticleProviders;
import at.koopro.wizardsandbeasts.client.form.hud.FormDebugOverlay;
import at.koopro.wizardsandbeasts.client.hud.MobEffectFullscreenOverlays;
import at.koopro.wizardsandbeasts.client.spell.render.CrucioScreenRenderer;
import at.koopro.wizardsandbeasts.client.hud.ObscurusOverlay;
import at.koopro.wizardsandbeasts.client.hud.FirewhiskyBurnOverlay;
import at.koopro.wizardsandbeasts.client.hud.SneakoscopeAlarmOverlay;
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
        modEventBus.addListener(at.koopro.wizardsandbeasts.client.brew.BrewTintSource::register);
        modEventBus.addListener(BroomRiderRenderer::registerModifiers);
        modEventBus.addListener(FormRenderStateModifier::registerModifiers);
        modEventBus.addListener(EntityOutlines::registerModifiers);
        modEventBus.addListener(at.koopro.wizardsandbeasts.client.petrify.PetrifyRenderHandler::registerModifiers);
        modEventBus.addListener(
                at.koopro.wizardsandbeasts.client.polyjuice.PolyjuiceRenderHandler::registerModifiers);
        modEventBus.addListener(
                at.koopro.wizardsandbeasts.client.heritage.appearance.HeritageAppearanceRenderState::registerModifiers);
        modEventBus.addListener(
                at.koopro.wizardsandbeasts.client.trinket.SneakoscopeSpinProperty::register);
        modEventBus.addListener(SpellKeyBindings::register);
        modEventBus.addListener(at.koopro.wizardsandbeasts.client.ability.AbilityFrameworkKeyBindings::register);
        modEventBus.addListener(at.koopro.wizardsandbeasts.client.armor.WardrobeKeyBindings::register);
        modEventBus.addListener(this::registerGuiLayers);
        modEventBus.addListener(WizardsAndBeastsClient::registerMenus);
        modEventBus.addListener(WizardsAndBeastsClient::registerMapStyleListeners);
        if (Config.enableDebugTools) {
            modEventBus.addListener(DebugKeyBindings::register);
        }

        NeoForge.EVENT_BUS.addListener(SpellClientInputHandler::onClientTick);
        NeoForge.EVENT_BUS.addListener(at.koopro.wizardsandbeasts.client.armor.WardrobeInputHandler::onClientTick);
        NeoForge.EVENT_BUS.addListener(SpellClientInputHandler::onScroll);
        NeoForge.EVENT_BUS.addListener(BeamClientEvents::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(BeamClientEvents::onEntityLeaveLevel);
        // One more rule for the shared outline layer: anything hiding near a wizard who has
        // eaten a Dirigible Plum. Per-viewer by construction -- see WrackspurtOutlineProvider.
        at.koopro.wizardsandbeasts.client.wrackspurt.WrackspurtOutlineProvider.register();
        // And one for a beast's nose -- Animagus and werewolf alike, keyed on the marker effect the
        // server grants, so the rule is not duplicated across the network boundary.
        at.koopro.wizardsandbeasts.client.form.sense.FormScentOutlineProvider.register();
        NeoForge.EVENT_BUS.addListener(ClientOutlineState::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(ApparitionClientController::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(LegilimencyVisionRenderer::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(ObscurialClientViewHandler::onRenderHand);
        NeoForge.EVENT_BUS.addListener(ObscurialClientViewHandler::onRenderGuiLayer);
        // Takes the drumsticks away from anyone whose nutrition is not hunger. Paired with the blood
        // meter registered below -- see NutritionHudHandler for why neither is correct on its own.
        NeoForge.EVENT_BUS.addListener(
                at.koopro.wizardsandbeasts.client.heritage.hud.NutritionHudHandler::onRenderGuiLayer);
        // The client half of werewolf loss of control: the keyboard stops answering and container
        // screens refuse to open. Enforcement is server-side -- see heritage.werewolf.FeralController.
        NeoForge.EVENT_BUS.addListener(
                at.koopro.wizardsandbeasts.client.heritage.WerewolfClientControlHandler::onMovementInput);
        NeoForge.EVENT_BUS.addListener(
                at.koopro.wizardsandbeasts.client.heritage.WerewolfClientControlHandler::onScreenOpening);
        NeoForge.EVENT_BUS.addListener(AnimagusClientViewHandler::onRenderHand);
        // After the form handlers on purpose: a beast in a card-holding pose is not a thing, and
        // a cancelled RenderHandEvent stops here, so theirs must get the first say.
        NeoForge.EVENT_BUS.addListener(
                at.koopro.wizardsandbeasts.client.trinket.WizardCardHandRenderer::onRenderHand);
        NeoForge.EVENT_BUS.addListener(AnimagusClientViewHandler::onRenderNameTag);
        NeoForge.EVENT_BUS.addListener(InventoryScreenInjector::onScreenInit);
        NeoForge.EVENT_BUS.addListener(CharacterSheetKeyHandler::onClientTick);
        NeoForge.EVENT_BUS.addListener(StatHudOverlay::onClientTick);
        NeoForge.EVENT_BUS.addListener(at.koopro.wizardsandbeasts.client.ability.AbilityWheelController::onClientTick);
        NeoForge.EVENT_BUS.addListener(at.koopro.wizardsandbeasts.client.spell.wheel.SpellWheelController::onClientTick);
        // The in-world debug panel polls only while the server says debug mode is on, so this costs
        // an int comparison a tick for everyone else. See DebugPanelClient.
        NeoForge.EVENT_BUS.addListener(
                at.koopro.wizardsandbeasts.client.debug.DebugPanelClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(
                at.koopro.wizardsandbeasts.client.debug.DebugPanelClient::onLoggingOut);
        // Damage numbers are anchored to world points, so any that survived a disconnect would be
        // drawn at those coordinates in the next world joined.
        NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) ->
                        at.koopro.wizardsandbeasts.client.dummy.DamageNumberOverlay.clear());
    }

    private static void registerMenus(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.WANDMAKERS_BENCH.get(), WandmakersBenchScreen::new);
        event.register(ModMenuTypes.OLLIVANDER_TRIAL.get(), OllivanderTrialScreen::new);
        event.register(ModMenuTypes.POCKET_CONFIGURATOR.get(), PocketConfiguratorScreen::new);
        event.register(ModMenuTypes.NIFFLER_POUCH.get(), NifflerPouchScreen::new);
        event.register(ModMenuTypes.HERMIONES_BAG.get(), HermionesBagScreen::new);
    }

    /**
     * The Marauder's Map's look, on the <em>resource</em> reload cycle rather than the datapack one.
     *
     * <p>Which tile sprite a biome gets and what ink a marker is drawn in are appearance, so a
     * texture pack must be able to change them without a datapack and without the server agreeing.
     * It also keeps art off the wire entirely: the server only ever sends a biome id and a marker
     * type id, and this is where those become pixels.
     */
    private static void registerMapStyleListeners(
            net.neoforged.neoforge.client.event.AddClientReloadListenersEvent event) {
        event.addListener(
                net.minecraft.resources.Identifier.fromNamespaceAndPath(
                        WizardsAndBeastsMod.MODID, "map_biome_style_reload_listener"),
                new at.koopro.wizardsandbeasts.client.map.style.MapStyleLoaders.Biomes());
        event.addListener(
                net.minecraft.resources.Identifier.fromNamespaceAndPath(
                        WizardsAndBeastsMod.MODID, "map_marker_style_reload_listener"),
                new at.koopro.wizardsandbeasts.client.map.style.MapStyleLoaders.Markers());
    }

    private void registerGuiLayers(RegisterGuiLayersEvent event) {
        // registerAbove(FOOD_LEVEL), not registerAboveAll: this layer stands in for the hunger bar and
        // has to draw in the hunger bar's place in the order, under the overlays and vignettes below.
        event.registerAbove(
                net.neoforged.neoforge.client.gui.VanillaGuiLayers.FOOD_LEVEL,
                at.koopro.wizardsandbeasts.client.heritage.hud.BloodBarRenderer.ID,
                at.koopro.wizardsandbeasts.client.heritage.hud.BloodBarRenderer::render);
        event.registerAboveAll(SpellDiamondOverlay.ID, SpellDiamondOverlay::render);
        event.registerAboveAll(ObscurusOverlay.ID, ObscurusOverlay::render);
        event.registerAboveAll(MobEffectFullscreenOverlays.ID, MobEffectFullscreenOverlays::render);
        event.registerAboveAll(CrucioScreenRenderer.ID, CrucioScreenRenderer::render);
        event.registerAboveAll(TransitionEffectRenderer.ID, TransitionEffectRenderer::render);
        // Hearth before transit: the wash for standing in a fire has to draw under the spin for
        // travelling through one, or a hop that begins in a grate flashes green over its own swirl.
        event.registerAboveAll(at.koopro.wizardsandbeasts.client.floo.FlooHearthOverlay.ID,
                at.koopro.wizardsandbeasts.client.floo.FlooHearthOverlay::render);
        event.registerAboveAll(at.koopro.wizardsandbeasts.client.floo.FlooTransitOverlay.ID,
                at.koopro.wizardsandbeasts.client.floo.FlooTransitOverlay::render);
        event.registerAboveAll(StatHudOverlay.ID, StatHudOverlay::render);
        event.registerAboveAll(SneakoscopeAlarmOverlay.ID, SneakoscopeAlarmOverlay::render);
        event.registerAboveAll(FirewhiskyBurnOverlay.ID, FirewhiskyBurnOverlay::render);
        // Numbers off a duelling dummy. Above the rest so a burst is not hidden behind a vignette,
        // and unconditional: the layer draws nothing until a payload arrives, and payloads only
        // arrive when the server's dummyDamageNumbers setting says this player should see them.
        event.registerAboveAll(at.koopro.wizardsandbeasts.client.dummy.DamageNumberOverlay.ID,
                at.koopro.wizardsandbeasts.client.dummy.DamageNumberOverlay::render);
        // Not behind Config.enableDebugTools: this layer draws nothing at all unless the *server*
        // has put the player in debug mode, and a client-side switch that also has to be on is how
        // an operator ends up staring at a pot wondering why the panel they just enabled is absent.
        event.registerAboveAll(at.koopro.wizardsandbeasts.client.debug.WorldDebugPanel.ID,
                at.koopro.wizardsandbeasts.client.debug.WorldDebugPanel::render);
        if (Config.enableDebugTools) {
            event.registerAboveAll(FormDebugOverlay.ID, FormDebugOverlay::render);
            event.registerAboveAll(DebugHudRenderer.ID, DebugHudRenderer::render);
        }
    }
}
