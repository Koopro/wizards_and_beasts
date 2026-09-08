package at.koopro.wizardsandbeasts.form.constraint;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NullMarked;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The single question "what may this player not do right now", and the registry of systems that answer it.
 *
 * <p><b>Server-side only, and unbypassable by design.</b> Every source takes a {@link ServerPlayer} and
 * reads server-authoritative state — an attachment the client cannot write, or a form id the server
 * assigned. {@link #denies} called with a client-side {@link Player} answers {@code false}, and no
 * enforcement path ever calls it there: the client's own restrictions (refusing to open a screen,
 * dropping movement input) are cosmetic smoothing, and every one of them is backed by a server rule
 * that holds whether or not the client cooperates. Nothing a client can send — a crafted interact
 * packet, a spell-cast packet, a container click, a movement packet — reaches past
 * {@link FormConstraintEvents} and {@code SpellNetworkGuards}.
 *
 * <h2>Registering a source</h2>
 * Sources are registered once from common setup, keyed by a stable id so a double registration during a
 * reload replaces rather than stacks. Answers are combined with
 * {@link FormConstraintSet#union(FormConstraintSet)}, so a player under two systems gets the strictest
 * reading of both and no source needs to know about any other.
 *
 * <pre>{@code
 * FormConstraints.register("animagus", player ->
 *         AnimagusTransformService.isInBeastForm(player)
 *                 ? FormConstraintSet.BEAST_HANDS
 *                 : FormConstraintSet.NONE);
 * }</pre>
 */
@NullMarked
public final class FormConstraints {

    private static final Map<String, FormConstraintSource> SOURCES = new LinkedHashMap<>();

    private FormConstraints() {}

    /**
     * Registers (or replaces) the source under {@code id}.
     *
     * <p>Keyed rather than appended so that calling {@link #bootstrap()} twice — which a test harness or
     * a hot-reload will do — cannot end up asking the same system twice.
     */
    public static synchronized void register(String id, FormConstraintSource source) {
        SOURCES.put(id, source);
    }

    /** Drops a source. Exists for tests; nothing in the mod unregisters. */
    public static synchronized void unregister(String id) {
        SOURCES.remove(id);
    }

    /**
     * Every constraint on this player, from every source.
     *
     * <p>Cheap enough for a per-event and per-tick call: each source is a couple of attachment reads,
     * and the union short-circuits to an existing instance whenever one set already covers the other,
     * so the common case allocates nothing.
     */
    public static FormConstraintSet of(ServerPlayer player) {
        FormConstraintSet result = FormConstraintSet.NONE;
        // Snapshot-free iteration is safe: register() is synchronized and only ever runs at setup,
        // long before any player exists to ask about.
        for (FormConstraintSource source : SOURCES.values()) {
            result = result.union(source.constraintsFor(player));
        }
        return result;
    }

    /** Whether this player is currently forbidden {@code constraint}. */
    public static boolean denies(ServerPlayer player, FormConstraint constraint) {
        for (FormConstraintSource source : SOURCES.values()) {
            if (source.constraintsFor(player).denies(constraint)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Convenience for event handlers, which are handed a {@link Player} or a
     * {@link net.minecraft.world.entity.LivingEntity} and must not act on the client.
     *
     * <p>Answers {@code false} for anything that is not a {@link ServerPlayer} — a client-side player,
     * a mob, an armour stand — which is what makes every call site a single {@code if}.
     */
    public static boolean denies(Object entity, FormConstraint constraint) {
        return entity instanceof ServerPlayer player && denies(player, constraint);
    }

    /**
     * Installs the mod's own sources. Called once from {@code FMLCommonSetupEvent}.
     *
     * <p>Registration lives here rather than in a static initialiser on each system: a static block only
     * runs when something touches the class, and the whole point of a constraint wall is that it is
     * already standing before anything asks.
     */
    public static void bootstrap() {
        register("animagus", at.koopro.wizardsandbeasts.ability.AnimagusTransformService::constraintsFor);
        register("werewolf", at.koopro.wizardsandbeasts.heritage.werewolf.WerewolfRules::constraintsFor);
        register("obscurial", at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialRules::constraintsFor);
    }
}
