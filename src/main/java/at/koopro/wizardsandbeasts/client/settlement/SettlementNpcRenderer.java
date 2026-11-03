package at.koopro.wizardsandbeasts.client.settlement;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.gui.ui.UiIcons;
import at.koopro.wizardsandbeasts.settlement.npc.SettlementNpcEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Draws a settlement's people on vanilla's player rig, as {@code DuellingDummyRenderer} does, with a skin per person.
 * Robes are painted into the skin: mobs have no Curios, and a robe body is a Curio. The outer garment is on the
 * overlay layers (jacket, sleeves, pants), which the player mesh carries as children, so they draw here too.
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
        state.skin = skin(npc.roleId(), npc.getCustomName());
    }

    /**
     * {@code <role>_<n>.png}, where n is the number the person's name key ends in: a role's names are different
     * people, so Mother Yarrow looks like Mother Yarrow in every hamlet. A role that ships one skin for everyone
     * ({@code <role>.png}) still works.
     */
    static Identifier skin(String roleId, @Nullable Component name) {
        Identifier role = Identifier.tryParse(roleId);
        String path = role == null || role.getPath().isEmpty() ? "keeper" : role.getPath();
        Identifier own = texture(path + "_" + person(name));
        return UiIcons.exists(own) ? own : texture(path);
    }

    /** The number at the end of a translated name's key ({@code npc.<ns>.<role>.name.<n>}), else 0. */
    static int person(@Nullable Component name) {
        if (name == null || !(name.getContents() instanceof TranslatableContents translatable)) {
            return 0;
        }
        String key = translatable.getKey();
        try {
            return Math.max(0, Integer.parseInt(key.substring(key.lastIndexOf('.') + 1)));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "textures/entity/settlement_npc/" + name + ".png");
    }

    @Override
    protected boolean shouldShowName(SettlementNpcEntity npc, double distanceSq) {
        return npc.hasCustomName() && super.shouldShowName(npc, distanceSq);
    }

    /** The ordinary humanoid state, and whose skin to draw. */
    public static class NpcRenderState extends HumanoidRenderState {
        public Identifier skin = texture("keeper_0");
    }
}
