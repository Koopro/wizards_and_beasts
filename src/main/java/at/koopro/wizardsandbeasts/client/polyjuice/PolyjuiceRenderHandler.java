package at.koopro.wizardsandbeasts.client.polyjuice;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Drawing a wizard as somebody else.
 *
 * <p>The same trick {@code PetrifyRenderHandler} uses, and for the same reason: {@code AvatarRenderer}
 * reads {@code state.skin.body().texturePath()}, so replacing {@code state.skin} makes vanilla render
 * the ordinary player model wearing another face. No render layer, no mixin, no second body to
 * z-fight with the first, and the shadow and lighting stay right.
 *
 * <h2>Where the skin comes from</h2>
 * <p>{@code SkinManager} already knows how to fetch and cache a skin for a profile, and it is
 * asynchronous: the first frame after a disguise starts will still show the drinker's own face, and
 * the frame after the fetch completes will not. That is the correct behaviour rather than a
 * compromise — blocking the render thread on a network fetch to avoid one frame of honesty would be a
 * stutter every time somebody drinks.
 *
 * <p>The name is carried on the packet rather than looked up, because the impersonated player may be
 * offline, on another server, or someone this client has never seen.
 *
 * <h2>What is deliberately not changed</h2>
 * <p>Only the skin and the nameplate. Not the hitbox, not the model type beyond what the skin itself
 * declares, and nothing on the server. A player disguised as somebody with a slim skin renders slim
 * because that is what the fetched {@link PlayerSkin} says — which is exactly as much as a disguise
 * should decide.
 */
@NullMarked
public final class PolyjuiceRenderHandler {

    private PolyjuiceRenderHandler() {}

    @SuppressWarnings("unchecked")
    public static void registerModifiers(RegisterRenderStateModifiersEvent event) {
        // Raw-typed like the petrify modifier beside it: AvatarRenderer's own type parameter is not
        // expressible through the event's bounds without a TypeToken literal, and dispatch is on the
        // raw class either way.
        event.registerEntityModifier(
                (Class) AvatarRenderer.class,
                (entity, renderState) -> {
                    if (entity instanceof Player player && renderState instanceof AvatarRenderState state) {
                        modify(player, state);
                    }
                });
    }

    private static void modify(Player player, AvatarRenderState state) {
        ClientPolyjuiceState.Disguise disguise = ClientPolyjuiceState.get(player.getUUID());
        if (disguise == null) {
            return;
        }
        PlayerSkin skin = lookupSkin(disguise);
        if (skin != null) {
            state.skin = skin;
        }
        // The nameplate follows the face. A disguise that rendered somebody else's body over your own
        // name is not a disguise — it is a costume with a label on it, and in multiplayer the label is
        // the only thing most players actually read.
        if (state.nameTag != null) {
            state.nameTag = net.minecraft.network.chat.Component.literal(disguise.targetName());
        }
    }

    /**
     * One skin lookup per impersonated player, kept for the session.
     *
     * <p>{@code SkinManager.createLookup} returns a {@link Supplier} that answers from cache and
     * kicks off a fetch when it has nothing — so calling it is cheap, but <em>building</em> it is
     * not, and this modifier runs for every tracked player every frame. Caching the supplier rather
     * than the skin is what keeps the fetch asynchronous: the supplier starts returning the real face
     * the moment it arrives, with nothing here having to poll for it.
     *
     * <p>Keyed on the target rather than the wearer, so ten players disguised as the same person
     * share one lookup.
     */
    private static final Map<UUID, Supplier<PlayerSkin>> LOOKUPS = new ConcurrentHashMap<>();

    /**
     * The impersonated player's skin, or null before the lookup has anything.
     *
     * <p>{@code insecure} — the second argument — because the client is being told who to draw by its
     * own server, and demanding a signed profile would mean a disguise only worked for players the
     * client had independently authenticated. The consequence is bounded: the worst a server can do
     * is make somebody look like somebody else, which is the entire feature.
     */
    private static @Nullable PlayerSkin lookupSkin(ClientPolyjuiceState.Disguise disguise) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSkinManager() == null) {
            return null;
        }
        Supplier<PlayerSkin> lookup = LOOKUPS.computeIfAbsent(disguise.targetId(), id ->
                mc.getSkinManager().createLookup(
                        new GameProfile(id, disguise.targetName()), false));
        return lookup.get();
    }
}
