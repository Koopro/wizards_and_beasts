package at.koopro.wizardsandbeasts.client;

import at.koopro.wizardsandbeasts.client.broom.BroomRenderer;
import at.koopro.wizardsandbeasts.client.entity.DementorRenderer;
import at.koopro.wizardsandbeasts.client.bestiary.niffler.NifflerPocketLayer;
import at.koopro.wizardsandbeasts.client.entity.ProtegoShieldRenderer;
import at.koopro.wizardsandbeasts.client.form.FormMannequinRenderer;
import at.koopro.wizardsandbeasts.client.spell.PatronusRenderer;
import at.koopro.wizardsandbeasts.client.spell.SpellProjectileRenderer;
import at.koopro.wizardsandbeasts.registry.ModEntities;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public class ClientSetup {

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.BROOM.get(), BroomRenderer::new);
        event.registerEntityRenderer(ModEntities.SPELL_PROJECTILE.get(), SpellProjectileRenderer::new);
        event.registerEntityRenderer(ModEntities.PATRONUS.get(), PatronusRenderer::new);
        event.registerEntityRenderer(ModEntities.PROTEGO_SHIELD.get(), ProtegoShieldRenderer::new);
        event.registerEntityRenderer(ModEntities.GOBLIN_TELLER.get(), GeoRendererHelper.simple("goblin_teller"));
        event.registerEntityRenderer(ModEntities.NIFFLER.get(), GeoRendererHelper.simple("niffler"));
        event.registerEntityRenderer(ModEntities.BABY_NIFFLER.get(), GeoRendererHelper.simple("niffler"));
        event.registerEntityRenderer(ModEntities.FORM_MANNEQUIN.get(), FormMannequinRenderer::new);
        event.registerEntityRenderer(ModEntities.WIZARDING_THROWN.get(), ThrownItemRenderer::new);
        event.registerEntityRenderer(ModEntities.DEMENTOR.get(), DementorRenderer::new);
        event.registerEntityRenderer(ModEntities.BOWTRUCKLE.get(), GeoRendererHelper.simple("bowtruckle"));
        event.registerEntityRenderer(ModEntities.CORNISH_PIXIE.get(), GeoRendererHelper.simple("cornish_pixie"));
        event.registerEntityRenderer(ModEntities.THESTRAL.get(), GeoRendererHelper.simple("thestral"));
        event.registerEntityRenderer(ModEntities.PHOENIX.get(), GeoRendererHelper.simple("phoenix"));
        event.registerEntityRenderer(ModEntities.AUGUREY.get(), GeoRendererHelper.simple("augurey"));
        event.registerEntityRenderer(ModEntities.MOONCALF.get(), GeoRendererHelper.simple("mooncalf"));
        event.registerEntityRenderer(ModEntities.STREELER.get(), GeoRendererHelper.simple("streeler"));

        // Generic data-driven creatures: one simple GeckoLib renderer per creature, keyed by id.
        for (at.koopro.wizardsandbeasts.registry.ModCreatures.Spec spec
                : at.koopro.wizardsandbeasts.registry.ModCreatures.MANIFEST) {
            event.registerEntityRenderer(
                    at.koopro.wizardsandbeasts.registry.ModCreatures.ENTITIES.get(spec.id()).get(),
                    GeoRendererHelper.simple(spec.id()));
        }
    }

    public static void registerLayers(EntityRenderersEvent.AddLayers event) {
        for (var skin : event.getSkins()) {
            AvatarRenderer<AbstractClientPlayer> renderer = event.getPlayerRenderer(skin);
            if (renderer != null) {
                renderer.addLayer(new NifflerPocketLayer(renderer));
            }
        }
    }
}
