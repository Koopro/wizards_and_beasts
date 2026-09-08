package at.koopro.wizardsandbeasts.form.sense;

import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Which senses a player's current form grants, and the registry of systems that answer.
 *
 * <p>Same shape as {@link at.koopro.wizardsandbeasts.form.constraint.FormConstraints} on purpose: a
 * keyed registry of pure server-side sources, unioned. A form that grants nothing returns an empty set;
 * a player under two forms gets both.
 */
@NullMarked
public final class FormSenses {

    /** One system's answer to "what can this player's body do that a human's cannot". */
    @FunctionalInterface
    public interface FormSenseSource {
        Set<FormSense> sensesFor(ServerPlayer player);
    }

    private static final Map<String, FormSenseSource> SOURCES = new LinkedHashMap<>();

    private FormSenses() {}

    public static synchronized void register(String id, FormSenseSource source) {
        SOURCES.put(id, source);
    }

    public static synchronized void unregister(String id) {
        SOURCES.remove(id);
    }

    /** Every sense this player's form grants. Empty for an untransformed player. */
    public static Set<FormSense> of(ServerPlayer player) {
        EnumSet<FormSense> result = EnumSet.noneOf(FormSense.class);
        for (FormSenseSource source : SOURCES.values()) {
            result.addAll(source.sensesFor(player));
        }
        return result;
    }

    public static boolean has(ServerPlayer player, FormSense sense) {
        for (FormSenseSource source : SOURCES.values()) {
            if (source.sensesFor(player).contains(sense)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Installs the mod's own sources. Called once from {@code FMLCommonSetupEvent}, beside
     * {@link at.koopro.wizardsandbeasts.form.constraint.FormConstraints#bootstrap()}.
     */
    public static void bootstrap() {
        register("animagus", at.koopro.wizardsandbeasts.ability.AnimagusTransformService::sensesFor);
        register("werewolf", at.koopro.wizardsandbeasts.heritage.werewolf.WerewolfRules::sensesFor);
    }
}
