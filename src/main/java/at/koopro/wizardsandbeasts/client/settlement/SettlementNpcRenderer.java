package at.koopro.wizardsandbeasts.client.settlement;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.settlement.npc.SettlementNpcEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

/**
 * Draws a settlement's people on vanilla's player rig, as {@code DuellingDummyRenderer} does, with a skin per role.
 * Robes are painted into the skin: mobs have no Curios, and a robe body is a Curio.
 */
@NullMarked
public class SettlementNpcRenderer extends LivingEntityRenderer<SettlementNpcEntity,
        SettlementNpcRenderer.NpcRenderState, HumanoidModel<SettlementNpcRenderer.NpcRenderState>> {

    public SettlementNpcRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
        this.addLayer(new ItemInHandLayer<>(this));
    }

    @Override
    public Identifier getTextureLocation(NpcRenderState state) {
        return state.skin;
    }

    @Override
    public NpcRenderState createRenderState() {
        return new NpcRenderState();
    }

    @Override
    public void extractRenderState(SettlementNpcEntity npc, NpcRenderState state, float partialTick) {
        super.extractRenderState(npc, state, partialTick);
        HumanoidMobRenderer.extractHumanoidRenderState(npc, state, partialTick, this.itemModelResolver);
        Identifier role = Identifier.tryParse(npc.roleId());
        String name = role == null || role.getPath().isEmpty() ? "keeper" : role.getPath();
        state.skin = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "textures/entity/settlement_npc/" + name + ".png");
    }

    @Override
    protected boolean shouldShowName(SettlementNpcEntity npc, double distanceSq) {
        return npc.hasCustomName() && super.shouldShowName(npc, distanceSq);
    }

    /** The ordinary humanoid state, and whose skin to draw. */
    public static class NpcRenderState extends HumanoidRenderState {
        public Identifier skin = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID,
                "textures/entity/settlement_npc/keeper.png");
    }
}
