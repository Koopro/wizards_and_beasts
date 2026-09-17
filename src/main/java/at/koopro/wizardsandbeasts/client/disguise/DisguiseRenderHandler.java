package at.koopro.wizardsandbeasts.client.disguise;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import org.jspecify.annotations.NullMarked;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Drawing a wizard as somebody else.
 *
 * <p>The same trick {@code PetrifyRenderHandler} uses, and for the same reason: {@code AvatarRenderer}
 * reads {@code state.skin.body().texturePath()}, so replacing {@code state.skin} makes vanilla render
 * the ordinary player model wearing another face. No render layer, no mixin, no second body to
 * z-fight with the first, and the shadow and lighting stay right. NeoForge runs render-state
 * modifiers <em>after</em> {@code extractRenderState} and {@code finalizeRenderState}, so both writes
 * below land on top of vanilla's rather than under them.
 *
 * <h2>Where the skin comes from, and why it used not to arrive</h2>
 * <p>Three sources, in order, and the ordering is the whole design:
 * <ol>
 *   <li><b>{@link PlayerInfo}</b>, when the target is online on this client. Their tab-list entry
 *       carries the signed {@code textures} property, and vanilla has already fetched and cached the
 *       skin from it. Free, and correct.</li>
 *   <li><b>A profile this class fetched itself</b>, for a target who is offline, on another server,
 *       or has never been seen here. This is the case the feature exists for.</li>
 *   <li><b>{@link DefaultPlayerSkin}</b> for the target's UUID, while a fetch is in flight and
 *       forever if it finds nothing. Never the <em>wearer's</em> own skin: a body that was still
 *       yours under somebody else's nameplate would be the one failure that looks like a working
 *       disguise.</li>
 * </ol>
 *
 * <p>The second source is what the Polyjuice version was missing. It handed
 * {@code SkinManager.createLookup} a bare {@code new GameProfile(id, name)}; {@code SkinManager.get}
 * asks {@code sessionService().getPackedTextures(profile)}, authlib answers
 * {@code profile.properties().get("textures")}, a bare profile has no properties, and the whole chain
 * therefore resolved to {@code MinecraftProfileTextures.EMPTY} → {@code DefaultPlayerSkin.get(uuid)}.
 * Every disguise rendered a default skin picked by hashing the target's UUID. The fix is not to send
 * textures from the server — see below — but to fetch the real profile, properties and all.
 *
 * <h2>Why the client fetches rather than the server sending</h2>
 * <p>A server could resolve the full profile and put the {@code textures} property on the wire. That
 * is rejected: it is larger, and it turns this packet into a way for any server to push arbitrary
 * texture URLs at connected clients. The client asking Mojang about a UUID it was given is the same
 * thing it already does for every player in the tab list, and it keeps the packet a pair of
 * identifiers.
 *
 * <h2>What is deliberately not changed</h2>
 * <p>Only the skin and the nameplate. Not the hitbox, not the model type beyond what the skin itself
 * declares, and nothing on the server. A player disguised as somebody with a slim skin renders slim
 * because that is what the fetched {@link PlayerSkin} says — which is exactly as much as a disguise
 * should decide, and why no {@code slim} flag is carried.
 *
 * <p>The wearer's own <b>first-person arms</b> are also unchanged. They are drawn from
 * {@code Minecraft.player}'s own skin rather than from a render state, so they would need a separate
 * hook; leaving them honest is also arguably right, since the one person a disguise need not fool is
 * the person wearing it.
 */
@NullMarked
public final class DisguiseRenderHandler {

    private DisguiseRenderHandler() {}

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
        ClientDisguiseState.Disguise disguise = ClientDisguiseState.get(player.getUUID());
        if (disguise == null) {
            return;
        }
        state.skin = skinFor(disguise);
        // The nameplate follows the face. A disguise that rendered somebody else's body over your own
        // name is not a disguise — it is a costume with a label on it, and in multiplayer the label is
        // the only thing most players actually read.
        //
        // Null means vanilla decided not to draw a name here at all (too far, sneaking, hidden). Left
        // null rather than forced: a disguise must not make a nameplate appear where there would not
        // have been one, which would be a way to find players by disguising them.
        if (state.nameTag != null) {
            state.nameTag = Component.literal(disguise.targetName());
        }
    }

    /**
     * Profiles we have asked the session service about, keyed by target UUID.
     *
     * <p>One fetch per target for the life of the session, and a completed-empty future is a cached
     * "no such account" rather than an invitation to ask again — this method runs for every tracked
     * player every frame, so a retry on failure would be a request per frame at a rate limiter.
     */
    private static final Map<UUID, CompletableFuture<Optional<GameProfile>>> PROFILES =
            new ConcurrentHashMap<>();

    /**
     * One skin lookup per impersonated player, kept for the session.
     *
     * <p>{@link SkinManager#createLookup} returns a {@link Supplier} that answers from cache and kicks
     * off a texture download when it has nothing — so calling it is cheap, but <em>building</em> it is
     * not. Caching the supplier rather than the skin is what keeps the download asynchronous: the
     * supplier starts returning the real face the moment it arrives, with nothing here having to poll.
     *
     * <p>Keyed on the target rather than the wearer, so ten players disguised as the same person share
     * one lookup.
     */
    private static final Map<UUID, Supplier<PlayerSkin>> LOOKUPS = new ConcurrentHashMap<>();

    private static PlayerSkin skinFor(ClientDisguiseState.Disguise disguise) {
        UUID id = disguise.targetId();
        Minecraft mc = Minecraft.getInstance();

        ClientPacketListener connection = mc.getConnection();
        if (connection != null) {
            PlayerInfo info = connection.getPlayerInfo(id);
            if (info != null) {
                return info.getSkin();
            }
        }

        SkinManager skins = mc.getSkinManager();
        if (skins == null) {
            return DefaultPlayerSkin.get(id);
        }

        Supplier<PlayerSkin> ready = LOOKUPS.get(id);
        if (ready != null) {
            return ready.get();
        }

        // getNow, never join: this is the render thread. An in-flight fetch shows the default face for
        // a frame or two, which is the correct behaviour rather than a compromise — blocking here to
        // avoid a moment of honesty would be a stutter every time somebody puts a face on.
        Optional<GameProfile> fetched = fetch(mc, id, disguise.targetName()).getNow(null);
        if (fetched != null && fetched.isPresent()) {
            // Built on the render thread on purpose. SkinManager's texture caches are plain hash maps
            // driven by the client executor, so the lookup is constructed where vanilla constructs it.
            return LOOKUPS.computeIfAbsent(id, key -> skins.createLookup(fetched.get(), false)).get();
        }
        return DefaultPlayerSkin.get(id);
    }

    private static CompletableFuture<Optional<GameProfile>> fetch(Minecraft mc, UUID id, String name) {
        return PROFILES.computeIfAbsent(id, key -> CompletableFuture.supplyAsync(
                () -> {
                    try {
                        ProfileResult result = mc.services().sessionService().fetchProfile(key, true);
                        return Optional.ofNullable(result).map(ProfileResult::profile);
                    } catch (RuntimeException noAccountOrNoNetwork) {
                        // An invented name, an offline-mode UUID, a rate limit, or no internet. All of
                        // them mean the same thing to the renderer: draw the default face for this
                        // UUID. Swallowed rather than logged per-frame — the empty answer is cached.
                        return Optional.empty();
                    }
                },
                Util.backgroundExecutor().forName("wandbDisguiseProfileFetch")));
    }

    /** Drop every cached profile and lookup. Called from {@link ClientDisguiseState#clear()}. */
    static void forgetLookups() {
        PROFILES.clear();
        LOOKUPS.clear();
    }
}
