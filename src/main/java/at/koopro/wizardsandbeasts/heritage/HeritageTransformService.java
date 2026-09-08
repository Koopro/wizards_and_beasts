package at.koopro.wizardsandbeasts.heritage;

import at.koopro.wizardsandbeasts.form.TransitionManager;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

/**
 * The plain two-form transformation, for the heritages whose change needs no machinery of its own.
 *
 * <p>Veela, Vampire and Merpeople each had a {@code TRANSFORMED} form registered, a size profile, a
 * transition config and a {@link HeritageFormBridge} mapping — and <b>nothing that ever transformed
 * them</b>. Until this class, {@code WerewolfTransformService} was the only place in the entire source
 * tree that set {@link TransformationState#TRANSFORMED}. Three heritages advertised a second shape no
 * player could reach.
 *
 * <h2>Who this serves, and who it must not</h2>
 * Two gates, and the second is the important one:
 * <ul>
 *   <li>the variant must carry the <b>{@code "transformation"} tag</b> — the mod already records which
 *       variants change shape, and it is not all of them. Quarter-Veela, Born Vampire, Dhampir, Merrow
 *       and Siren do not transform, and that was written down long before this class existed;</li>
 *   <li>the heritage must be one of {@link #SERVED}. Werewolf variants also carry the
 *       {@code "transformation"} tag, and Obscurial has its own; both own transform logic far richer
 *       than this — a moon, an exposure counter, loss of control, a stress meter. Routing them through
 *       here would give them a second, simpler way to change shape that knows about none of it.</li>
 * </ul>
 *
 * <p>Transitions go through {@link TransitionManager} rather than setting the form directly. That is the
 * opposite of what the werewolf does, and deliberately: the werewolf needs a configurable delay longer
 * than the manager's 30-tick cap, while these three are ordinary changes whose durations and screen
 * effects are already sitting in {@code TransformationConfigRegistry} unused. Going through the manager
 * is what finally makes those entries do something.
 */
@NullMarked
public final class HeritageTransformService {

    /**
     * Heritages whose transformation this class owns.
     *
     * <p>An allow-list rather than a deny-list on purpose: a heritage added later gets no transformation
     * until somebody decides what its change means, which is the safe direction to fail in.
     */
    public static final Set<Heritage> SERVED =
            EnumSet.of(Heritage.VEELA, Heritage.VAMPIRE, Heritage.MERPEOPLE);

    /** The tag the mod already uses to record "this variant changes shape". */
    public static final String TAG_TRANSFORMATION = "transformation";

    private HeritageTransformService() {}

    public static PlayerHeritageData data(ServerPlayer player) {
        return player.getData(ModAttachments.HERITAGE_DATA.get());
    }

    /** True when this player's heritage <em>and</em> variant both permit a change of shape. */
    public static boolean canTransform(@Nullable PlayerHeritageData data) {
        if (data == null) {
            return false;
        }
        Heritage heritage = data.getSelectedHeritage();
        HeritageVariant variant = data.getSelectedHeritageVariant();
        return heritage != null
                && variant != null
                && SERVED.contains(heritage)
                && variant.hasTag(TAG_TRANSFORMATION);
    }

    public static boolean isTransformed(PlayerHeritageData data) {
        return data.getTransformationState() == TransformationState.TRANSFORMED;
    }

    /**
     * Begins the change into this heritage's transformed shape.
     *
     * <p>The target form is not a parameter: {@link HeritageFormBridge} already maps every
     * heritage/variant/state triple to a form id, and asking it is what keeps this class from becoming a
     * second place that decides what a Veela turns into.
     *
     * @return true if a transition was started
     */
    public static boolean enter(ServerPlayer player, PlayerHeritageData data) {
        return changeTo(player, data, TransformationState.TRANSFORMED);
    }

    /** Begins the change back. Safe to call on an untransformed player — it will find nothing to do. */
    public static boolean exit(ServerPlayer player, PlayerHeritageData data) {
        return changeTo(player, data, TransformationState.NORMAL);
    }

    /**
     * Flips between the two shapes. The voluntary entry point, used by the ability wheel.
     *
     * @return true if a transition was started
     */
    public static boolean toggle(ServerPlayer player) {
        PlayerHeritageData data = data(player);
        return isTransformed(data) ? exit(player, data) : enter(player, data);
    }

    /**
     * Adds this player's voluntary form ability, if their heritage has one they can reach.
     *
     * <p>Called from {@code PlayerStatusAbilityGrantSource}. Merpeople are absent on purpose: a Selkie's
     * change is automatic, driven by whether they are in water, so there is no button to press and
     * offering one would imply a choice they do not have.
     *
     * <p>Gated on the same {@link #canTransform} the toggle enforces, so the wheel can never show a
     * button that would be refused — a Quarter-Veela or a Dhampir sees nothing.
     */
    public static void grantsFor(ServerPlayer player, java.util.List<String> out) {
        PlayerHeritageData data = data(player);
        if (!canTransform(data)) {
            return;
        }
        Heritage heritage = data.getSelectedHeritage();
        if (heritage == Heritage.VEELA) {
            out.add(at.koopro.wizardsandbeasts.ability.AbilityIds.VEELA_FORM.toString());
        } else if (heritage == Heritage.VAMPIRE) {
            out.add(at.koopro.wizardsandbeasts.ability.AbilityIds.VAMPIRE_FORM.toString());
        }
    }

    private static boolean changeTo(ServerPlayer player, PlayerHeritageData data, TransformationState target) {
        if (!canTransform(data)) {
            return false;
        }
        // Never start a second change on top of one already running -- the same guard the werewolf and
        // Obscurial paths both keep.
        if (TransitionManager.isTransitioning(player.getUUID())) {
            return false;
        }
        Heritage heritage = data.getSelectedHeritage();
        HeritageVariant variant = data.getSelectedHeritageVariant();
        if (heritage == null) {
            return false;
        }
        String targetForm = HeritageFormBridge.getDefaultFormId(heritage, variant, target);
        if (targetForm.equals(data.getActiveFormId())) {
            return false;
        }
        if (!TransitionManager.startTransition(player, targetForm)) {
            return false;
        }
        // The form itself lands when the transition completes; the state, the stats and the sync are
        // this class's to set, and they go now so that anything asking mid-transition sees the intent.
        data.setTransformationState(target);
        HeritageAPI.applyStats(player);
        HeritageAPI.syncTransformation(player);
        return true;
    }
}
