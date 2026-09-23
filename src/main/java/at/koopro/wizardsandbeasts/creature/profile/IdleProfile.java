package at.koopro.wizardsandbeasts.creature.profile;

import at.koopro.wizardsandbeasts.creature.BodyPlan;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Map;

/**
 * What a creature does when nothing is happening.
 *
 * <p>Before this, all ninety-six data-driven creatures shared one idle: wander, look at the player,
 * look around. Competent, and identical for a Bowtruckle and a Nundu. The bespoke classes show what
 * the difference is worth — the Mooncalf has {@code GatherToDanceGoal} and {@code StayInBurrowGoal},
 * the Thestral has {@code ThestralGrazeGoal} — but each of those is a Java class, which is not a
 * route that scales to ninety-six.
 *
 * <p>The vocabulary here is deliberately small and is <b>drawn from what the entity architecture can
 * already do</b>, not invented. Every action is a one-shot animation clip played on the existing
 * {@code BEAST_ACTION_CONTROLLER}, through the existing {@code triggerDeclared} gate. That gate is
 * what makes this safe to hand to every creature at once: GeckoLib throws inside the render pass when
 * asked for a clip a file does not define, so {@code triggerDeclared} checks the datapack's declared
 * clip list first and does nothing when the rig has no such clip. An idle profile naming actions a
 * creature has no animation for is therefore inert rather than broken.
 *
 * <p>Position and gaze idling — roaming, watching the player, looking around — are deliberately
 * <em>not</em> in this vocabulary. They are already vanilla goals that {@code GenericBeastEntity}
 * installs for everyone, and duplicating them here would give two systems an opinion about the same
 * behaviour.
 *
 * @param actions   the clips this creature may play when idle, in preference order
 * @param minDelay  minimum ticks between idle actions
 * @param maxDelay  maximum ticks between idle actions
 */
@NullMarked
public record IdleProfile(List<String> actions, int minDelay, int maxDelay) {

    /** Long enough that an idle action reads as punctuation rather than a nervous tic. */
    public static final int DEFAULT_MIN_DELAY = 140;
    public static final int DEFAULT_MAX_DELAY = 400;

    public static final IdleProfile NONE = new IdleProfile(List.of(), DEFAULT_MIN_DELAY, DEFAULT_MAX_DELAY);

    public static final Codec<IdleProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.listOf().optionalFieldOf("actions", List.of()).forGetter(IdleProfile::actions),
            Codec.INT.optionalFieldOf("minDelay", DEFAULT_MIN_DELAY).forGetter(IdleProfile::minDelay),
            Codec.INT.optionalFieldOf("maxDelay", DEFAULT_MAX_DELAY).forGetter(IdleProfile::maxDelay)
    ).apply(instance, IdleProfile::new));

    public IdleProfile {
        minDelay = Math.max(20, minDelay);
        maxDelay = Math.max(minDelay + 20, maxDelay);
    }

    /**
     * A starting idle for a creature whose datapack says nothing, chosen from its body plan.
     *
     * <p>These name clips that mostly do not exist yet, which is the intended state: the profile
     * describes what the creature <em>would</em> do, and the moment a rig gains a {@code preen} clip
     * the avians start preening with no code or data change. Until then every entry is a no-op and
     * behaviour is exactly what it was.
     *
     * <p>Two clip names here do already exist on rigs today — {@code call} and {@code song} — and are
     * left out on purpose: {@code playAmbientSound} already fires those on vanilla's ambient beat, and
     * naming them here would double them up.
     */
    public static IdleProfile forBodyPlan(BodyPlan bodyPlan) {
        List<String> actions = BODY_PLAN_ACTIONS.getOrDefault(bodyPlan, List.of());
        return actions.isEmpty() ? NONE : new IdleProfile(actions, DEFAULT_MIN_DELAY, DEFAULT_MAX_DELAY);
    }

    private static final Map<BodyPlan, List<String>> BODY_PLAN_ACTIONS = Map.of(
            BodyPlan.AVIAN, List.of("preen", "ruffle", "stretch"),
            BodyPlan.INSECTOID_FLYER, List.of("hover", "flit"),
            BodyPlan.WINGED_QUADRUPED, List.of("stretch", "shake", "graze"),
            BodyPlan.QUADRUPED, List.of("graze", "sniff", "shake", "scratch"),
            BodyPlan.SERPENTINE, List.of("coil", "taste_air"),
            BodyPlan.ARTHROPOD_MULTILEG, List.of("groom", "skitter"),
            BodyPlan.BIPED_HUMANOID, List.of("peek", "scratch"),
            BodyPlan.LARGE_HUMANOID, List.of("stretch", "grunt"),
            BodyPlan.AQUATIC, List.of("drift", "roll"),
            BodyPlan.BLOB_SPHERE, List.of("squirm", "settle"));
}
