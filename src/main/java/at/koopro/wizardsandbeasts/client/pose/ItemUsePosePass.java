package at.koopro.wizardsandbeasts.client.pose;

import at.koopro.wizardsandbeasts.item.broom.BroomPolishItem;
import at.koopro.wizardsandbeasts.item.lore.LoreTomeItem;
import at.koopro.wizardsandbeasts.item.trinket.DarkMarkItem;
import at.koopro.wizardsandbeasts.item.trinket.FamousWizardCardItem;
import at.koopro.wizardsandbeasts.item.trinket.FoeGlassItem;
import at.koopro.wizardsandbeasts.item.trinket.OmniocularsItem;
import at.koopro.wizardsandbeasts.item.trinket.TimeTurnerItem;
import at.koopro.wizardsandbeasts.item.wand.WandBlankItem;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The one pass for items whose hold or use is a two-handed gesture.
 *
 * <p>Posture band, because these are stances rather than actions: a wizard reading a card while
 * gliding should keep the glide, and a cast must read over the top of whatever is in the other hand.
 *
 * <p>Six items, four gestures — {@link ItemUsePoseConstants#CARVE} and {@code POLISH} for work,
 * {@code WIND} for the Time-Turner, {@code BOOK} for reading and the optic pose for the two things
 * you raise to your eyes.
 *
 * <h2>Why one pass and not one per item</h2>
 *
 * <p>The held item is exclusive. Every pose in this family addresses the same two arms, and only one
 * can apply at a time, so a pass per item would be a stack of near-identical classes competing for
 * the same parts — the situation {@link PoseBand} exists to make visible and this design avoids
 * outright. The branch below <em>is</em> the arbitration, and it is one thing to read.
 *
 * <h2>Holds and actions are gated differently</h2>
 *
 * <p>An <b>action</b> — a carve, a rub, a wind — poses whatever the player is carrying, because the
 * off arm is steadying the work rather than displaying anything. Vanilla treats a crossbow the same
 * way.
 *
 * <p>A <b>two-handed hold</b> — a book, a card, an optic — is refused unless the off hand is empty.
 * That is vanilla's rule for the map, and the rule {@code WizardCardHandRenderer} already applies to
 * the card in first person. It matters for more than consistency: a wizard with a wand in the off
 * hand needs to see the wand, and a two-handed hold would hide it. With something in the other hand
 * the card holds one-handed and the Omnioculars keep vanilla's single-arm spyglass pose.
 *
 * <h2>Third person only, deliberately</h2>
 *
 * <p>The layer reaches this pass with {@link FirstPersonContext#NONE} and nothing else today —
 * {@code PlayerPoseHandler.applyPose} is its only caller and passes {@code NONE} — so the guard
 * below is a statement of intent rather than something that fires now. It is worth stating, because
 * two of these items are unposable in first person for reasons that are not about poses at all.
 * While {@code isScoping()} holds, {@code ItemInHandRenderer#renderArmWithItem} returns before
 * rendering any hand, so an Omnioculars pose would apply to nothing; and
 * {@code WizardCardHandRenderer} cancels {@code RenderHandEvent} to draw the card in the two-handed
 * map pose itself, so a first-person contribution here would fight a renderer that has already won.
 *
 * <p>The first-person arm is safe from the third-person branch for a separate reason worth knowing:
 * {@code AvatarRenderer#renderHand} calls {@code resetPose()} on the arm and never runs
 * {@code setupAnim}, so the hook this layer hangs off does not fire for it at all.
 */
@NullMarked
public final class ItemUsePosePass extends ProceduralPosePass {

    /** Posture band. Below locomotion and casting, both of which should read over a hold. */
    public static final int PRIORITY = 50;

    public ItemUsePosePass() {
        super("item_use", PRIORITY);
    }

    @Override
    public void pose(PoseBuilder builder, PoseContext context) {
        if (context.firstPerson().firstPerson()) {
            return;
        }

        AvatarRenderState state = context.state();
        HumanoidArm holding = state.mainArm;
        ItemStack held = state.getMainHandItemStack();
        boolean using = usingArm(state) == holding;

        // Working poses first, and without the free-hand rule below. A carve or a rub is an action
        // rather than a hold: the off arm steadies the work whatever it happens to be carrying, and
        // vanilla does the same for a crossbow.
        if (using && held.getItem() instanceof WandBlankItem) {
            poseWork(builder, state, holding, ItemUsePoseConstants.CARVE);
            return;
        }
        if (using && held.getItem() instanceof BroomPolishItem) {
            poseWork(builder, state, holding, ItemUsePoseConstants.POLISH);
            return;
        }
        if (using && held.getItem() instanceof TimeTurnerItem) {
            poseWind(builder, state, holding, ItemUsePoseConstants.WIND);
            return;
        }
        if (using && held.getItem() instanceof DarkMarkItem) {
            poseReach(builder, holding, ItemUsePoseConstants.BRAND);
            return;
        }

        // Two-handed holds from here down, and they all need the other hand free — see the class
        // note. A wizard with a wand in the off hand keeps the wand and the vanilla one-armed pose.
        if (!offHand(state, holding).isEmpty()) {
            return;
        }
        if (held.getItem() instanceof OmniocularsItem || held.getItem() instanceof FoeGlassItem) {
            if (using) {
                poseOptic(builder, context, state);
            }
            return;
        }
        if (using && held.getItem() instanceof LoreTomeItem) {
            poseTwoHanded(builder, ItemUsePoseConstants.BOOK);
            return;
        }
        if (held.getItem() instanceof FamousWizardCardItem) {
            poseTwoHanded(builder, ItemUsePoseConstants.BOOK);
        }
    }

    /**
     * Both hands at the chest, the main one turning the chain.
     *
     * <p>Separate from {@link #poseWork} because a wind is not a stroke. The roll goes round and
     * round rather than back and forth, which changes both the curve and the operation it has to be
     * written with — see {@link #spinDegrees}.
     */
    private void poseWind(PoseBuilder builder, AvatarRenderState state, HumanoidArm turning,
                          ItemUsePoseConstants.WindPose pose) {
        for (HumanoidArm arm : HumanoidArm.values()) {
            builder.get(part(arm))
                    .setRotDeg(PoseTarget.X_ROT, pose.armXRot())
                    .setRotDeg(PoseTarget.Y_ROT, pose.armYRot() * mirror(arm));
        }

        // ADD, never SET_SHORTEST. The angle wraps past 360 back to 0 on every turn, and
        // SET_SHORTEST resolves an angle by the shorter arc: asked to travel 359 degrees it goes one
        // degree backwards instead, so a continuous spin written that way stalls on the spot. ADD
        // resolves exactly here (the multiplier is 1, so nothing interpolates), and a model rotation
        // of 360 degrees renders identically to one of 0 — so the wrap itself is invisible.
        builder.get(part(turning))
                .addRotDeg(PoseTarget.Z_ROT,
                        spinDegrees(state.ticksUsingItem(turning), pose.spinTicks()) * mirror(turning));
    }

    /**
     * One arm held out at whatever is in front of the player; the other hangs.
     *
     * <p>Static, with no stroke and no spin. A brand is a press and a hold — the drama is that it
     * takes time and the victim can walk out of it, not that the arm is doing anything.
     */
    private void poseReach(PoseBuilder builder, HumanoidArm reaching,
                           ItemUsePoseConstants.ReachPose pose) {
        builder.get(part(reaching))
                .setRotDeg(PoseTarget.X_ROT, pose.reachXRot())
                .setRotDeg(PoseTarget.Y_ROT, pose.reachYRot() * mirror(reaching))
                .setRotDeg(PoseTarget.Z_ROT, pose.reachZRot() * mirror(reaching));

        builder.get(part(reaching.getOpposite()))
                .setRotDeg(PoseTarget.X_ROT, pose.idleXRot())
                .setRotDeg(PoseTarget.Z_ROT, 0f);

        builder.get(PlayerModelPart.CHEST).addRotDeg(PoseTarget.X_ROT, pose.chestXRot());
    }

    /**
     * How far round the chain has been wound, in degrees, wrapped to a single turn.
     *
     * <p>Wrapped rather than left to accumulate: the Time-Turner's channel runs to
     * {@code MAX_USE_TICKS}, which is an hour, and an unbounded angle would be tens of thousands of
     * degrees by the end — enough that single-precision starts visibly quantising the rotation.
     */
    static float spinDegrees(float ticks, int spinTicks) {
        return (ticks / spinTicks * 360.0f) % 360.0f;
    }

    /**
     * A repetitive two-handed job: one arm strokes on a fixed period, the other steadies.
     *
     * <p>The stroke is driven from {@code ticksUsingItem} rather than from {@code ageInTicks}, which
     * matters twice. A world clock would put the arm at an arbitrary point in its cycle on every use,
     * and it would drift out of step with the item's own sound and particles over the channel.
     *
     * <p><b>{@code -cos}, not {@code sin}.</b> The items fire their sound and chips on
     * {@code elapsed % strokeTicks == 0}, so the arm has to be at the far end of its travel — the
     * moment the knife or the rag actually bites — at exactly those ticks. {@code sin} is zero there,
     * which puts the arm mid-swing and lands every chip between two strokes; {@code -cos} is at
     * {@code -1}, fully forward, and the two read as one motion.
     */
    private void poseWork(PoseBuilder builder, AvatarRenderState state, HumanoidArm working,
                          ItemUsePoseConstants.WorkPose pose) {
        float phase = strokePhase(state.ticksUsingItem(working), pose.strokeTicks());

        builder.get(part(working))
                .setRotDeg(PoseTarget.X_ROT, pose.workXRot() + pose.strokeXRot() * phase)
                .setRotDeg(PoseTarget.Y_ROT, 0f)
                .setRotDeg(PoseTarget.Z_ROT, pose.workZRot() * mirror(working));

        HumanoidArm steady = working.getOpposite();
        builder.get(part(steady))
                .setRotDeg(PoseTarget.X_ROT, pose.steadyXRot())
                .setRotDeg(PoseTarget.Y_ROT, pose.steadyYRot() * mirror(steady))
                .setRotDeg(PoseTarget.Z_ROT, 0f);

        // Added, so the stoop rides on whatever the player is really doing rather than replacing it.
        builder.get(PlayerModelPart.CHEST)
                .addRotDeg(PoseTarget.Y_ROT, pose.chestYRot() * phase * mirror(working))
                .addRotDeg(PoseTarget.X_ROT, pose.chestXRot());
    }

    /**
     * Both arms up to the eyes, on vanilla's own spyglass numbers.
     *
     * <p><b>Both, not just the free one.</b> The Omnioculars keep {@code ItemUseAnimation.SPYGLASS},
     * so vanilla has already posed their holding arm by the time the layer runs, and this writes the
     * same values over the top — a deliberate no-op on that arm. The Foe Glass reports
     * {@code NONE} and gets no vanilla pose at all, so for it this is the only thing posing either
     * arm. One branch that always poses both is simpler than two that each pose different halves,
     * and the duplication argument against it was already spent: {@link ItemUsePoseConstants} has
     * had to carry vanilla's constants since the moment the free arm needed to match the other one.
     *
     * <p>Read off the <em>model's</em> head rather than the render state's yaw, because the model
     * head is what vanilla measured its own arm against — the two arms must agree to the radian or
     * the Omnioculars sit crooked.
     */
    private void poseOptic(PoseBuilder builder, PoseContext context, AvatarRenderState state) {
        ModelPart head = context.model().head;
        float xRot = opticPitch(head.xRot, state.isCrouching);

        for (HumanoidArm arm : HumanoidArm.values()) {
            builder.get(part(arm))
                    // SET_SHORTEST, not SET: both values are derived from a live look direction, and
                    // a raw lerp across the yaw wrap would swing the arm the long way round through
                    // the player's back.
                    .op(PoseTarget.X_ROT, PoseOpType.SET_SHORTEST, xRot)
                    .op(PoseTarget.Y_ROT, PoseOpType.SET_SHORTEST, opticYaw(head.yRot, arm))
                    // Vanilla suppresses the idle bob for the arm it poses (HumanoidModel, guarded
                    // on ArmPose.SPYGLASS) but not for the other one, and the bob writes zRot.
                    // Without this the free arm would tremble against a steady holding arm.
                    .setRotDeg(PoseTarget.Z_ROT, 0f);
        }
    }

    /** Applies a symmetric hold to both arms, plus the head and torso inclination that sells it. */
    private void poseTwoHanded(PoseBuilder builder, ItemUsePoseConstants.TwoHandPose pose) {
        for (HumanoidArm arm : HumanoidArm.values()) {
            builder.get(part(arm))
                    .setRotDeg(PoseTarget.X_ROT, pose.armXRot())
                    .setRotDeg(PoseTarget.Y_ROT, pose.armYRot() * mirror(arm))
                    .setRotDeg(PoseTarget.Z_ROT, pose.armZRot() * mirror(arm));
        }

        // Added, not set: the reader keeps aiming where they are aiming and inclines toward the page,
        // rather than having their look direction replaced by a constant.
        builder.get(PlayerModelPart.HEAD).addRotDeg(PoseTarget.X_ROT, pose.headXRot());
        builder.get(PlayerModelPart.CHEST).addRotDeg(PoseTarget.X_ROT, pose.chestXRot());
    }

    /**
     * Where in its stroke a working arm is: {@code -1} fully forward, {@code +1} drawn back.
     *
     * <p>Static and free of the render state so the phasing can be tested against the tick numbers
     * the items actually fire their sound and particles on. Getting this out of step is invisible in
     * code review and obvious the moment anyone watches it.
     *
     * @param ticks       ticks the item has been in use, interpolated by the partial tick
     * @param strokeTicks period of one full stroke
     */
    static float strokePhase(float ticks, int strokeTicks) {
        return -Mth.cos(ticks / strokeTicks * Mth.TWO_PI);
    }

    /**
     * Shoulder pitch for an arm raised to the eye, from the head's own pitch.
     *
     * <p>Static and free of the render state so the clamp can be tested: the bounds are what stop a
     * player looking at their boots from folding the arm through their chest, and they are the part
     * of this pose most likely to be wrong without anyone noticing at a normal look angle.
     *
     * @param headXRot  the model head's pitch, radians, as vanilla left it
     * @param crouching lowers the arms to meet a lowered head
     */
    static float opticPitch(float headXRot, boolean crouching) {
        float crouch = crouching ? ItemUsePoseConstants.OPTIC_CROUCH_PITCH : 0f;
        return Mth.clamp(headXRot - ItemUsePoseConstants.OPTIC_ARM_PITCH_OFFSET - crouch,
                ItemUsePoseConstants.OPTIC_PITCH_MIN, ItemUsePoseConstants.OPTIC_PITCH_MAX);
    }

    /** Shoulder yaw for an arm raised to the eye — the head's yaw, splayed toward the midline. */
    static float opticYaw(float headYRot, HumanoidArm arm) {
        return headYRot + ItemUsePoseConstants.OPTIC_ARM_YAW_SPLAY * mirror(arm);
    }

    /** The arm actually using an item, or null when nothing is being used. */
    private static @Nullable HumanoidArm usingArm(AvatarRenderState state) {
        if (!state.isUsingItem) {
            return null;
        }
        return state.useItemHand == InteractionHand.MAIN_HAND
                ? state.mainArm : state.mainArm.getOpposite();
    }

    private static ItemStack offHand(AvatarRenderState state, HumanoidArm mainArm) {
        return mainArm == HumanoidArm.RIGHT ? state.leftHandItemStack : state.rightHandItemStack;
    }

    private static PlayerModelPart part(HumanoidArm arm) {
        return arm == HumanoidArm.RIGHT ? PlayerModelPart.RIGHT_ARM : PlayerModelPart.LEFT_ARM;
    }

    /** See {@link ItemUsePoseConstants} — poses are authored right-handed and mirrored by this. */
    private static float mirror(HumanoidArm arm) {
        return arm == HumanoidArm.RIGHT ? -1f : 1f;
    }
}
