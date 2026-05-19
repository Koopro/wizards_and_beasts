package at.koopro.wizardsandbeasts.client.spell;

import at.koopro.wizardsandbeasts.entity.spell.PatronusEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** Minimal Patronus renderer (particles carry most of the look). */
public class PatronusRenderer extends EntityRenderer<PatronusEntity, EntityRenderState> {

    public PatronusRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }
}
