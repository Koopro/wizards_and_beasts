package at.koopro.wizardsandbeasts.client.heritage.appearance;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.form.state.ClientFormDataState;
import at.koopro.wizardsandbeasts.client.heritage.state.ClientHeritageIdentityState;
import at.koopro.wizardsandbeasts.heritage.appearance.HeritageAppearance;
import at.koopro.wizardsandbeasts.heritage.appearance.HeritageAppearanceRegistry;
import at.koopro.wizardsandbeasts.heritage.appearance.OverlayAppearance;
import at.koopro.wizardsandbeasts.heritage.appearance.Proportion;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Resolves a player's heritage appearance once per frame and parks the answer on their render state.
 *
 * <p>This is the whole reason the render path never touches an entity: the modifier runs during
 * render-state extraction, where the {@code Player} is legitimately available, and everything
 * downstream — {@link HeritageProportionPass} and, later, the overlay layer — reads a plain record
 * off the state. Same mechanism as {@code FormRenderStateModifier.FORM_DATA} and
 * {@code PlayerPoseHandler.BODY_TRANSFORM}.
 *
 * <p>Nothing needs clearing. The render state is allocated fresh per entity per frame, so a state
 * with no entry is simply a player with no appearance entry.
 *
 * <p><b>Resolution order</b>, per the override-layer ruling: a datapack {@link HeritageAppearance}
 * entry wins; absent one, the shipped {@code FormRegistry} / {@code SizeProfileRegistry} path
 * continues to do exactly what it already does, untouched. This modifier contributes nothing in that
 * case rather than duplicating it.
 */
public final class HeritageAppearanceRenderState {

    /** Present on the render state of a player with a resolved appearance entry; absent otherwise. */
    public static final ContextKey<AppearanceData> APPEARANCE =
            new ContextKey<>(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "heritage_appearance"));

    private HeritageAppearanceRenderState() {}

    public static @Nullable AppearanceData get(EntityRenderState state) {
        return state.getRenderData(APPEARANCE);
    }

    @SuppressWarnings("unchecked")
    public static void registerModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier(
                (Class) LivingEntityRenderer.class,
                (entity, renderState) -> {
                    if (!(entity instanceof Player player)
                            || !(renderState instanceof LivingEntityRenderState livingState)) {
                        return;
                    }

                    ClientHeritageIdentityState.Identity identity =
                            ClientHeritageIdentityState.get(player.getUUID());
                    if (identity == null) {
                        return;
                    }

                    HeritageAppearance entry = HeritageAppearanceRegistry
                            .clientResolve(identity.heritage(), identity.variant());
                    if (entry == null || entry.isEmpty()) {
                        return;
                    }

                    livingState.setRenderData(APPEARANCE, new AppearanceData(
                            residualProportion(entry.proportion(), player.getUUID()),
                            entry.overlay()));
                });
    }

    /**
     * The proportion arm with any scale the shipped form system has <em>already</em> applied divided
     * back out.
     *
     * <p>The override-layer contract is that a datapack entry <b>wins</b>, and for scale that has to
     * mean the final size equals the declared one — not the declared one multiplied by whatever else
     * was in play. Two things scale a humanoid player and they compose:
     *
     * <ul>
     *   <li>{@code FormRenderStateModifier} writes {@code livingState.scale} from the active form's
     *       {@code SizeProfile.modelScale}, which vanilla applies outside the pose stack;</li>
     *   <li>{@link HeritageProportionPass} writes a pose-stack scale.</li>
     * </ul>
     *
     * <p>Left alone, a half-giant with the {@code half_giant_default} form active would render at
     * 1.6 × 1.6 = 2.56. So the pass is handed {@code declared / formScale}, and the product lands on
     * {@code declared}. In the common case there is no active form at all — heritage selection does
     * not assign one — {@code formScale} is 1.0 and this is the identity.
     *
     * <p>Read from {@code ClientFormDataState} rather than from the form data on the render state:
     * render-state modifiers have no guaranteed order between them, and this is the same map that one
     * reads from.
     *
     * <p><b>Known imprecision.</b> {@code SizeLerpTracker} eases the form scale over a transition
     * while this divides by the profile's settled value, so a heritage proportion applied <i>during</i>
     * a form change is briefly off. Transitions are short, both values agree at either end, and the
     * alternative is duplicating the lerp here and having two copies drift.
     */
    private static @Nullable Proportion residualProportion(@Nullable Proportion declared, UUID playerUUID) {
        if (declared == null || declared.scale() == 1.0f) {
            return declared;
        }
        ClientFormDataState.FormData formData = ClientFormDataState.get(playerUUID);
        if (formData == null) {
            return declared;
        }
        float residual = residualScale(declared.scale(), formData.sizeProfile().modelScale());
        return residual == declared.scale()
                ? declared
                : new Proportion(residual, declared.boneOffsets());
    }

    /**
     * {@code declared / formScale}, guarded.
     *
     * <p>A pure function, split out so the arithmetic can be tested without a client-side map or a
     * render state. A non-positive form scale is a corrupt sync rather than a design choice; dividing
     * by it would send the player to infinity or turn them inside out, so it is ignored.
     */
    static float residualScale(float declared, float formScale) {
        if (formScale <= 0.0f || formScale == 1.0f) {
            return declared;
        }
        return declared / formScale;
    }

    /**
     * The arms that matter to a renderer, pre-extracted.
     *
     * <p>The {@code form} arm is deliberately absent: form replacement is driven by the player's
     * synced {@code activeFormId} through {@code FormRenderStateModifier} and the renderer mixin,
     * not by re-deriving it here. Two sources for one decision is how they drift apart.
     */
    public record AppearanceData(@Nullable Proportion proportion, @Nullable OverlayAppearance overlay) {}
}
