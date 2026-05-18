package at.koopro.wizardsandbeasts.form.client;

import at.koopro.wizardsandbeasts.form.entity.FormMannequinEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/**
 * Placeholder renderer for the Form Mannequin debug entity.
 * Currently renders nothing — will be extended to display form models.
 */
public class FormMannequinRenderer extends EntityRenderer<FormMannequinEntity, EntityRenderState> {

    public FormMannequinRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }
}
