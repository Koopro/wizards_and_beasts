package at.koopro.wizardsandbeasts.client.render;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.legilimency.state.ClientLegilimencyVisionState;
import at.koopro.wizardsandbeasts.client.petrify.state.ClientPetrifyState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Drives the fullscreen post-processing chains for states the player is *in* — petrified, or with
 * someone else inside their head.
 *
 * <p><b>No mixin and no custom GLSL.</b> {@code GameRenderer.setPostEffect}/{@code clearPostEffect}
 * are public, and both chains are composed entirely from vanilla's own post shaders
 * ({@code color_convolve}, {@code box_blur}, {@code blit}) with different uniforms, so the whole
 * feature is a pair of JSON files plus this switch. Nothing here can break when Mojang changes a
 * shader's internals, because none of it is our shader.
 *
 * <p><b>Never stomps vanilla's effect.</b> Vanilla sets its own post effect when the camera entity
 * changes — spectating a creeper, a spider, or wearing a pumpkin — via
 * {@code GameRenderer.checkEntityPostEffect}. Clearing unconditionally would delete that. So this
 * remembers what it set and only clears while the active effect is still the one it applied; if
 * anything else took over in between, it leaves well alone and forgets its claim.
 *
 * <p>Priority is fixed rather than blended: two fullscreen colour grades composited on top of each
 * other read as a bug. Petrification wins because it is the more total state — a petrified player
 * cannot act on what Legilimency would be showing them anyway.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
@NullMarked
public final class PostEffectDirector {

    private static final Identifier PETRIFIED =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "petrified");
    private static final Identifier LEGILIMENCY =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "legilimency");

    /** The effect this class last applied, or null if it is not currently claiming one. */
    private static @Nullable Identifier applied;

    private PostEffectDirector() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            forget();
            return;
        }
        apply(desiredEffect(player));
    }

    private static @Nullable Identifier desiredEffect(LocalPlayer player) {
        if (ClientPetrifyState.isPetrified(player.getUUID())) {
            return PETRIFIED;
        }
        if (ClientLegilimencyVisionState.markerPos() != null) {
            return LEGILIMENCY;
        }
        return null;
    }

    private static void apply(@Nullable Identifier wanted) {
        if (wanted != null && wanted.equals(applied)) {
            return;
        }
        var renderer = Minecraft.getInstance().gameRenderer;
        if (wanted == null) {
            // Only ours is ours to clear — see the class note on checkEntityPostEffect.
            if (applied != null && applied.equals(renderer.currentPostEffect())) {
                renderer.clearPostEffect();
            }
            applied = null;
            return;
        }
        renderer.setPostEffect(wanted);
        applied = wanted;
    }

    /** Drops the claim without touching the renderer — for leaving a world, where it is already gone. */
    private static void forget() {
        applied = null;
    }
}
