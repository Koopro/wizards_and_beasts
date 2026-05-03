package at.koopro.wizardsandbeasts.client;

import at.koopro.wizardsandbeasts.client.broom.BroomRenderer;
import at.koopro.wizardsandbeasts.client.form.FormMannequinRenderer;
import at.koopro.wizardsandbeasts.client.spell.SpellProjectileRenderer;
import at.koopro.wizardsandbeasts.registry.ModEntities;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public class ClientSetup {

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.BROOM.get(), BroomRenderer::new);
        event.registerEntityRenderer(ModEntities.SPELL_PROJECTILE.get(), SpellProjectileRenderer::new);
        event.registerEntityRenderer(ModEntities.GOBLIN_TELLER.get(), GeoRendererHelper.simple("goblin_teller"));
        event.registerEntityRenderer(ModEntities.NIFFLER.get(), GeoRendererHelper.simple("niffler"));
        event.registerEntityRenderer(ModEntities.FORM_MANNEQUIN.get(), FormMannequinRenderer::new);
    }
}
