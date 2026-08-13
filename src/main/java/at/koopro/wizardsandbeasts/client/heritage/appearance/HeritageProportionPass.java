package at.koopro.wizardsandbeasts.client.heritage.appearance;

import at.koopro.wizardsandbeasts.client.pose.PlayerModelPart;
import at.koopro.wizardsandbeasts.client.pose.PoseBuilder;
import at.koopro.wizardsandbeasts.client.pose.PoseContext;
import at.koopro.wizardsandbeasts.client.pose.PosePass;
import at.koopro.wizardsandbeasts.client.pose.PoseTarget;
import at.koopro.wizardsandbeasts.heritage.appearance.Proportion;
import com.mojang.logging.LogUtils;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mechanism A — applies a heritage's proportion arm to the vanilla player model.
 *
 * <p>Scale goes on the virtual {@link PlayerModelPart#BODY}, which resolves against the pose stack,
 * because scaling six limbs individually scales each about its own pivot and pulls the model apart.
 * Bone offsets go on the real parts.
 *
 * <p><b>The player's own skin is untouched.</b> This pass emits translation and scale ops and has no
 * way to address a texture — which is the distinction between a proportion pass and a form
 * replacement, enforced by construction rather than by discipline.
 *
 * <p><b>No attribute is written.</b> Hitbox, reach, step height and knockback resistance stay
 * wherever {@code SizeSystemAPI} left them. That does mean a datapack scale with no matching size
 * profile is visual-only and will not agree with the collision box; the same horizontal mismatch
 * already exists in the shipped profiles and is logged as a BLOCKER in {@code AUDIT_PUNCHLIST.md}.
 *
 * <p>Registered in the {@code TRANSFORM} band. Proportion is the most structural thing that can
 * happen to a body, so it resolves after posture, locomotion and casting have had their say — a
 * half-giant's walk cycle is a walk cycle, then the whole result is made large.
 */
@NullMarked
public final class HeritageProportionPass implements PosePass {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Bottom of the TRANSFORM band. Form replacement, when it poses at all, belongs above this. */
    public static final int PRIORITY = 300;

    /**
     * Bone names a datapack may address, and the part each resolves to.
     *
     * <p>{@code body} is the torso — {@link PlayerModelPart#CHEST} — and not the virtual whole-avatar
     * part, which is not offered at all. The two use different units (a {@code ModelPart} is in model
     * sixteenths, the pose stack is in blocks), and one map holding both would silently mean two
     * different things depending on the key.
     */
    private static final Map<String, PlayerModelPart> BONES = Map.of(
            "head", PlayerModelPart.HEAD,
            "body", PlayerModelPart.CHEST,
            "right_arm", PlayerModelPart.RIGHT_ARM,
            "left_arm", PlayerModelPart.LEFT_ARM,
            "right_leg", PlayerModelPart.RIGHT_LEG,
            "left_leg", PlayerModelPart.LEFT_LEG);

    /** Unknown bone names already reported. A render-path warning has to fire once, not per frame. */
    private static final Set<String> REPORTED_UNKNOWN_BONES = ConcurrentHashMap.newKeySet();

    @Override
    public int priority() {
        return PRIORITY;
    }

    @Override
    public String name() {
        return "heritage_proportion";
    }

    @Override
    public void pose(PoseBuilder builder, PoseContext context) {
        HeritageAppearanceRenderState.AppearanceData data =
                HeritageAppearanceRenderState.get(context.state());
        if (data == null) {
            return;
        }
        apply(builder, data.proportion());
    }

    /**
     * The op emission, split out from {@link #pose} so it can be exercised without a render state.
     *
     * <p>Package-visible rather than private for that reason alone; nothing else calls it.
     */
    static void apply(PoseBuilder builder, @Nullable Proportion proportion) {
        if (proportion == null || proportion.isIdentity()) {
            return;
        }

        if (proportion.scale() != 1.0f) {
            float scale = proportion.scale();
            builder.get(PlayerModelPart.BODY)
                    .set(PoseTarget.X_SCALE, scale)
                    .set(PoseTarget.Y_SCALE, scale)
                    .set(PoseTarget.Z_SCALE, scale);
        }

        for (Map.Entry<String, Proportion.Offset> entry : proportion.boneOffsets().entrySet()) {
            Proportion.Offset offset = entry.getValue();
            if (offset.isIdentity()) {
                continue;
            }
            PlayerModelPart part = resolveBone(entry.getKey());
            if (part == null) {
                continue;
            }
            builder.get(part)
                    .add(PoseTarget.X, offset.x())
                    .add(PoseTarget.Y, offset.y())
                    .add(PoseTarget.Z, offset.z());
        }
    }

    /**
     * An unknown bone name is ignored rather than fatal. A name that means something to a rig this
     * pack will ship later should not stop the rest of the entry from drawing today.
     */
    private static @Nullable PlayerModelPart resolveBone(String name) {
        PlayerModelPart part = BONES.get(name.toLowerCase(Locale.ROOT));
        if (part == null && REPORTED_UNKNOWN_BONES.add(name)) {
            LOGGER.warn("[W&B] Heritage appearance addresses unknown bone '{}'. Known: {}. Ignored.",
                    name, BONES.keySet());
        }
        return part;
    }

    /** Test seam, and a reset point for {@code /reload}. */
    public static void clearUnknownBoneLog() {
        REPORTED_UNKNOWN_BONES.clear();
    }
}
