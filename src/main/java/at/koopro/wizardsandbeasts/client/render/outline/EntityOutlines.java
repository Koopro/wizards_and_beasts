package at.koopro.wizardsandbeasts.client.render.outline;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * Coloured entity outlines that need neither {@code MobEffects.GLOWING} nor a scoreboard team.
 *
 * <p>Vanilla's outline is not a shader you have to reimplement — it is a post-processing chain keyed
 * off one public field. {@code EntityRenderState.outlineColor} is packed ARGB, {@code appearsGlowing()}
 * is precisely {@code outlineColor != 0}, and the only thing the Glowing effect does is decide whether
 * that field gets written:
 *
 * <pre>
 * EntityRenderer.extractRenderState:
 *     outlineColor = Minecraft.shouldEntityAppearGlowing(e) ? ARGB.opaque(e.getTeamColor()) : 0;
 * </pre>
 *
 * <p>NeoForge runs render-state modifiers in {@code EntityRenderer.createRenderState} <em>after</em>
 * both {@code extractRenderState} and {@code finalizeRenderState}, and {@code LevelRenderer} does not
 * read {@code appearsGlowing()} until afterwards. Writing the field from a modifier therefore lands in
 * the one window where it still counts: the outline turns on, takes our colour, and the effect and the
 * team never enter into it.
 *
 * <p>Registered against {@link EntityRenderer} itself rather than {@code LivingEntityRenderer}, because
 * NeoForge matches modifiers with {@code isAssignableFrom} — one registration covers every entity type,
 * items and boats included.
 *
 * <p>This replaced a renderer that drew three billboarded quads as a soft blob and selected its targets
 * by reading {@code entity.getTags()} on the client. Tags are NBT-only and never synced, so that map was
 * always empty and the effect never appeared on screen at all. Anything added here must take its data
 * from something that actually crosses the wire — see {@link ClientOutlineState}.
 */
@NullMarked
public final class EntityOutlines {

    /** Packed-ARGB value meaning "no outline", matching {@code EntityRenderState.NO_OUTLINE}. */
    public static final int NO_OUTLINE = EntityRenderState.NO_OUTLINE;

    private static final List<OutlineColorProvider> PROVIDERS = new ArrayList<>();

    private EntityOutlines() {}

    static {
        registerProvider(ClientOutlineState::colorFor);
    }

    /**
     * Adds a rule that can put an outline on an entity. Providers are consulted in registration order
     * and the first non-{@link #NO_OUTLINE} answer wins, so register more specific rules first.
     */
    public static void registerProvider(OutlineColorProvider provider) {
        PROVIDERS.add(provider);
    }

    // Raw cast for the same reason FormRenderStateModifier needs one: the signature wants
    // Class<? extends EntityRenderer<? extends E, ? extends S>>, and the un-parameterised base class
    // cannot satisfy that while still matching every renderer, which is the entire point here.
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void registerModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier(
                (Class) EntityRenderer.class,
                (entity, renderState) -> {
                    int argb = resolve((Entity) entity);
                    // Only ever write a colour, never clear one. Vanilla has already put the Glowing
                    // effect's outline in this field, and zeroing it here would make this layer able to
                    // silently cancel a real glow it knows nothing about.
                    if (argb != NO_OUTLINE) {
                        renderState.outlineColor = argb;
                    }
                });
    }

    /** The colour this entity should be outlined in, or {@link #NO_OUTLINE} to leave vanilla alone. */
    public static int resolve(Entity entity) {
        for (OutlineColorProvider provider : PROVIDERS) {
            int argb = provider.colorFor(entity);
            if (argb != NO_OUTLINE) {
                return argb;
            }
        }
        return NO_OUTLINE;
    }

    @FunctionalInterface
    public interface OutlineColorProvider {
        /**
         * @return packed <b>ARGB</b>, or {@link #NO_OUTLINE} for "no opinion". The alpha byte is not
         *         decoration: a colour passed as bare {@code 0xRRGGBB} has alpha 0, which reads back as
         *         "no outline" and fails silently. Pack with {@code ARGB.opaque(rgb)}.
         */
        int colorFor(Entity entity);
    }
}
