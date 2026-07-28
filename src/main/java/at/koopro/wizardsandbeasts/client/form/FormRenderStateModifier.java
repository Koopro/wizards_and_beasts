package at.koopro.wizardsandbeasts.client.form;

import at.koopro.wizardsandbeasts.client.form.state.ClientFormDataState;
import at.koopro.wizardsandbeasts.form.FormRegistry;
import at.koopro.wizardsandbeasts.form.ModelType;
import at.koopro.wizardsandbeasts.form.PlayerForm;
import at.koopro.wizardsandbeasts.form.RenderFlag;
import at.koopro.wizardsandbeasts.form.SizeProfile;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

/**
 * Attaches form metadata to player render states, following the same pattern
 * as {@link at.koopro.wizardsandbeasts.client.broom.BroomRiderRenderer}. This data is consumed by
 * {@link FormRenderHandler} and by {@code LivingEntityRendererMixin}.
 *
 * <p>The data rides on the render state via {@link net.minecraft.client.renderer.entity.state.EntityRenderState#setRenderData},
 * not in a side map keyed by state. It used to be an {@code IdentityHashMap<Object, FormRenderData>}
 * cleared only after a successful non-humanoid render, which meant every HUMANOID form and every early
 * return stranded an entry — and because {@code EntityRenderer.createRenderState} allocates a fresh state
 * each frame, those entries accumulated for the lifetime of the session. There is now nothing to clear:
 * a state with no entry is simply a player who is not in a form.
 */
public class FormRenderStateModifier {

    /** Present on the render state of a player in a form; absent otherwise. */
    public static final ContextKey<FormRenderData> FORM_DATA =
            new ContextKey<>(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "player_form"));

    public static @Nullable FormRenderData getFormData(LivingEntityRenderState state) {
        return state.getRenderData(FORM_DATA);
    }

    @SuppressWarnings("unchecked")
    public static void registerModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier(
                (Class) LivingEntityRenderer.class,
                (entity, renderState) -> {
                    if (entity instanceof Player player
                            && renderState instanceof LivingEntityRenderState livingState) {
                        ClientFormDataState.FormData data =
                                ClientFormDataState.get(player.getUUID());
                        if (data == null) return;

                        PlayerForm form = FormRegistry.get(data.formId());
                        if (form == null) return;

                        livingState.setRenderData(FORM_DATA, new FormRenderData(
                                player.getUUID(),
                                data.formId(),
                                form.modelType(),
                                form.texturePath(),
                                data.sizeProfile(),
                                data.renderFlags()));

                        // For HUMANOID forms: override state.scale with lerped value
                        // so vanilla applies smooth scale transitions.
                        // Non-HUMANOID forms are handled entirely by the Mixin.
                        if (form.modelType() == ModelType.HUMANOID) {
                            float pt = Minecraft.getInstance()
                                    .getDeltaTracker().getGameTimeDeltaPartialTick(false);
                            float lerpedScale = SizeLerpTracker.getVisualScale(
                                    player.getUUID(), data.sizeProfile().modelScale(), pt);
                            if (lerpedScale > 0) {
                                livingState.scale = lerpedScale;
                            }
                        }
                    }
                });
    }

    public record FormRenderData(
            java.util.UUID playerUUID,
            String formId,
            ModelType modelType,
            net.minecraft.resources.Identifier texturePath,
            SizeProfile sizeProfile,
            EnumSet<RenderFlag> renderFlags
    ) {}
}
