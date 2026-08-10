package at.koopro.wizardsandbeasts.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * The obscurial's dark form: a churning column of smoke standing where the player was.
 *
 * <p>Structurally this is the {@code obscurus} mob rig rebuilt in vanilla model parts — a dense core,
 * three shells of vapour at different radii, a plume rising off the top, and six wisps curling off the
 * sides — so the transformed player and the creature read as the same phenomenon. It shares that rig's
 * texture palette through {@code tools/obscurus_model.py}, which generates
 * {@code textures/entity/form/obscurial_dark.png} alongside the mob's skin.
 *
 * <p>What this replaces was seven concentric boxes described in its own javadoc as "smoky placeholder
 * geometry", and it was <em>dead code</em>: {@code FormModelRenderer}'s {@code SHADOW} branch was an
 * empty block on the grounds that the form was "represented by particles and overlays", so the model
 * had no caller and the form rendered nothing at all.
 *
 * <h2>Two conventions worth stating</h2>
 * <ul>
 *   <li><b>+Y is down.</b> Vanilla entity models are authored with the origin at the neck and Y
 *       increasing toward the feet ({@code FormModelRenderer.MODEL_Y_OFFSET} and its
 *       {@code scale(-1, -1, 1)} are what flip them upright). So the plume — the part that rises —
 *       lives at <em>negative</em> Y, above the head.</li>
 *   <li><b>Every box uses {@code texOffs(0, 0)}</b> and samples the same corner of a deliberately
 *       uniform noise sheet. A hand-written model with fifty boxes is exactly where UV bookkeeping
 *       goes wrong; a field texture makes any rectangle cut from it read as smoke.</li>
 * </ul>
 */
public final class ObscurialDarkModel {

    /**
     * Translucent white: the colour lives entirely in the texture. The previous constant baked a
     * violet into the vertex colour as well as the art, so the two had to be kept in step by hand.
     */
    public static final int COLOR = 0xC8FFFFFF;

    /** Player-space extents, in vanilla model units (+Y down, 0 at the neck). */
    private static final float HEAD_TOP = -8.0f;
    private static final float FEET = 24.0f;

    private static final int WISPS = 6;
    private static final int WISP_SEGMENTS = 3;

    private final ModelPart root;
    private final ModelPart core;
    private final List<ModelPart> shells = new ArrayList<>();
    private final List<ModelPart> wispRoots = new ArrayList<>();
    /** Each wisp's baked aim (x, y, z), so the lash can be added to it rather than replace it. */
    private final List<float[]> wispAim = new ArrayList<>();

    public ObscurialDarkModel() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition rootDef = mesh.getRoot();

        // The heart: overlapping lumps rather than one box, so the silhouette is irregular from
        // every angle instead of reading as a barrel.
        rootDef.addOrReplaceChild("core", CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-5.0f, -4.0f, -4.5f, 10.0f, 16.0f, 9.0f)
                        .addBox(-6.5f, 1.0f, -3.5f, 8.0f, 11.0f, 7.0f)
                        .addBox(-1.5f, -1.0f, -5.5f, 7.5f, 12.0f, 8.0f)
                        .addBox(-4.5f, 12.0f, -3.5f, 9.0f, 8.0f, 7.0f),
                PartPose.ZERO);

        // Three shells. Counter-rotated against each other at render time — layers that disagree
        // are the whole read of "churning", and one shell alone just spins.
        shell(rootDef, "shroud_a", 6, 6.5f, 3.6f, 15.0f, 4.5f, -3.0f, 0.0f);
        shell(rootDef, "shroud_b", 6, 8.5f, 3.0f, 11.0f, 4.0f, 2.0f, 30.0f);
        shell(rootDef, "shroud_c", 5, 7.0f, 2.6f, 9.0f, 3.6f, 12.0f, 15.0f);

        // The plume, above the head and tapering: negative Y, because +Y is down here.
        shell(rootDef, "plume_a", 5, 4.5f, 2.8f, 8.0f, 3.2f, HEAD_TOP - 6.0f, 20.0f);
        shell(rootDef, "plume_b", 4, 3.0f, 2.2f, 7.0f, 2.6f, HEAD_TOP - 12.0f, 50.0f);

        // A skirt where the smoke meets the ground, so the form does not end in mid-air.
        shell(rootDef, "skirt", 6, 8.0f, 3.2f, 5.0f, 4.0f, FEET - 6.0f, 45.0f);

        for (int i = 0; i < WISPS; i++) {
            wisp(rootDef, i);
        }

        this.root = LayerDefinition.create(mesh, 64, 64).bakeRoot();
        this.core = root.getChild("core");
        for (String name : new String[]{"shroud_a", "shroud_b", "shroud_c",
                "plume_a", "plume_b", "skirt"}) {
            shells.add(root.getChild(name));
        }
        for (int i = 0; i < WISPS; i++) {
            ModelPart wisp = root.getChild("wisp" + i);
            wispRoots.add(wisp);
            // The aim lives in the baked pose, so the animation has to *add* to it. Assigning
            // yRot outright — the obvious way to write the lash — silently discards the compass
            // bearing and stacks every wisp on the same heading.
            wispAim.add(new float[]{wisp.xRot, wisp.yRot, wisp.zRot});
        }
    }

    /**
     * One shell of vapour: {@code count} plates fanned evenly around the Y axis, each a child part so
     * the parent can spin the whole shell as a rigid unit.
     *
     * <p>Plate dimensions are jittered off the nominal by a hash of the index. Identical plates on a
     * perfect ring build a drum with vertical staves — the same mistake the mob rig made first time.
     */
    private static void shell(PartDefinition parent, String name, int count, float radius,
                              float width, float height, float depth, float y, float phase) {
        PartDefinition shell = parent.addOrReplaceChild(name, CubeListBuilder.create(), PartPose.ZERO);
        for (int i = 0; i < count; i++) {
            float w = width + jitter(name.hashCode(), i, 0.7f);
            float h = height + jitter(name.hashCode(), i + 31, 2.2f);
            float r = radius + jitter(name.hashCode(), i + 61, 1.4f);
            float dy = y + jitter(name.hashCode(), i + 97, 1.6f);
            float yaw = (float) Math.toRadians(phase + i * (360.0f / count));
            shell.addOrReplaceChild(name + "_p" + i,
                    CubeListBuilder.create().texOffs(0, 0)
                            .addBox(-r - w, dy, -depth * 0.5f, w, h, depth),
                    PartPose.rotation(0.0f, yaw, 0.0f));
        }
    }

    /** A tapering three-link wisp, aimed outward and curled back toward the mass at each joint. */
    private static void wisp(PartDefinition parent, int i) {
        float yaw = (float) Math.toRadians(i * (360.0f / WISPS) + 18.0f);
        // Negative pitch lifts the wisp (+Y is down), and every wisp is lifted or level: aiming
        // them downward turns the whole form into something standing on legs.
        float pitch = (float) Math.toRadians(-34.0f + jitter(7, i, 22.0f));

        PartDefinition current = parent.addOrReplaceChild("wisp" + i,
                CubeListBuilder.create(), PartPose.rotation(pitch, yaw, 0.0f));
        float[] lengths = {7.0f, 5.5f, 4.0f};
        float[] thick = {2.6f, 1.6f, 0.9f};
        for (int j = 0; j < WISP_SEGMENTS; j++) {
            float len = lengths[j];
            float t = thick[j];
            // Segments chain along -Z; each joint bends further, so a wisp arcs back toward the
            // mass instead of reading as a straight spike.
            PartPose pose = j == 0
                    ? PartPose.ZERO
                    : PartPose.offsetAndRotation(0.0f, 0.0f, -lengths[j - 1],
                            (float) Math.toRadians(-24.0f), (float) Math.toRadians(20.0f), 0.0f);
            current = current.addOrReplaceChild("wisp" + i + "_s" + j,
                    CubeListBuilder.create().texOffs(0, 0)
                            .addBox(-t * 0.5f, -t * 0.5f, -len, t, t, len),
                    pose);
        }
    }

    private static float jitter(int seed, int i, float spread) {
        int n = (int) (((long) i * 2654435761L + (long) seed * 40503L) & 0x7FFFFFFFL);
        return ((n >> 8) % 2000 / 1000.0f - 1.0f) * spread;
    }

    /**
     * Drives the churn from the client clock. Shells turn at rates that share no common multiple, so
     * the layers never resynchronise into a single apparent object, and the core breathes underneath.
     *
     * @param ageInTicks entity age plus partial tick — anything monotonic works
     */
    public void setupAnim(float ageInTicks) {
        float[] rates = {0.030f, -0.041f, 0.025f, -0.019f, 0.016f, 0.022f};
        for (int i = 0; i < shells.size(); i++) {
            ModelPart shell = shells.get(i);
            shell.yRot = ageInTicks * rates[i];
            // A slight tilt that wanders keeps a shell from reading as a flat turntable.
            shell.zRot = Mth.sin(ageInTicks * 0.021f + i) * 0.06f;
        }
        core.yRot = Mth.sin(ageInTicks * 0.017f) * 0.14f;
        core.xRot = Mth.cos(ageInTicks * 0.013f) * 0.05f;

        for (int i = 0; i < wispRoots.size(); i++) {
            ModelPart wisp = wispRoots.get(i);
            float[] aim = wispAim.get(i);
            float phase = ageInTicks * 0.06f + i * 1.7f;
            wisp.xRot = aim[0] + Mth.sin(phase * 0.8f) * 0.16f;
            wisp.yRot = aim[1] + Mth.cos(phase * 0.7f) * 0.22f;
            wisp.zRot = aim[2] + Mth.sin(phase) * 0.30f;
        }
    }

    public void render(PoseStack poseStack, VertexConsumer consumer, int light, int overlay,
                       float ageInTicks) {
        setupAnim(ageInTicks);
        root.render(poseStack, consumer, light, overlay, COLOR);
    }
}
