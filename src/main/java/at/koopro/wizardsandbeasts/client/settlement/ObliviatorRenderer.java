package at.koopro.wizardsandbeasts.client.settlement;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.ministry.trace.ObliviatorEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

/**
 * The Obliviator on vanilla's player rig, as the settlement people are drawn. She wears an existing robed skin (the
 * reading room's Madam Fenwright) until a Ministry skin is drawn and shown for a verdict.
 */
@NullMarked
public class ObliviatorRenderer extends LivingEntityRenderer<ObliviatorEntity, HumanoidRenderState,
        HumanoidModel<HumanoidRenderState>> {

    private static final Identifier SKIN = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID,
            "textures/entity/settlement_npc/scholar_1.png");

    public ObliviatorRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
    }

    @Override
    public HumanoidRenderState createRenderState() {
        return new HumanoidRenderState();
    }

    @Override
    public void extractRenderState(ObliviatorEntity entity, HumanoidRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        HumanoidMobRenderer.extractHumanoidRenderState(entity, state, partialTick, this.itemModelResolver);
    }

    @Override
    public Identifier getTextureLocation(HumanoidRenderState state) {
        return SKIN;
    }
}
