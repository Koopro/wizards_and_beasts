package at.koopro.wizardsandbeasts.client.wand;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.beam.WandTipTracker;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.client.spell.protego.ClientProtegoChargeState;
import at.koopro.wizardsandbeasts.spell.protego.ProtegoTier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import at.koopro.wizardsandbeasts.wand.WandAppearance;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.customization.WandConfiguration;
import at.koopro.wizardsandbeasts.wand.customization.WandModule;
import at.koopro.wizardsandbeasts.wand.customization.WandModuleRegistry;
import at.koopro.wizardsandbeasts.wand.customization.WandSlot;
import at.koopro.wizardsandbeasts.wand.visual.WandBondLook;
import at.koopro.wizardsandbeasts.wand.visual.WandLook;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.base.BoneSnapshots;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.RenderPassInfo;

import java.util.UUID;

/**
 * Draws every wand: the modular rig ({@code tools/wand_model.py}) dressed in its {@link WandLook}, or the
 * Elder Wand's own model.
 *
 * <p>A look is resolved once per make ({@link WandLookCache}) and applied in three places, none of which
 * builds geometry: the main pass picks the wood's grain sheet and multiplies in its tint; bone snapshots
 * show the selected handle/shaft/tip and lengthen, thin, bow and turn the shaft (the tip and the spell
 * attachment ride on it); two {@link WandOverlayLayer}s draw the untinted trim and the core's light.
 * See {@code documentation/WAND_MODEL.md}, "Looks".
 */
public class WandRenderer extends GeoItemRenderer<WandItem> {

    /** DataTicket carrying the per-stack WandConfiguration through the render pipeline. */
    public static final DataTicket<WandConfiguration> WAND_CONFIG =
            DataTicket.create("wand_configuration", WandConfiguration.class);

    public static final DataTicket<Boolean> IS_ELDER_WAND =
            DataTicket.create("is_elder_wand", Boolean.class);

    /**
     * Entity id of whoever holds this wand; absent when nobody does (inventory icon, item frame,
     * dropped item).
     *
     * <p>The beam anchor has to know whose tip it just saw, for every caster on screen and not only
     * our own. GeckoLib's {@code SpecialModelWrapper} mixin passes vanilla's item owner through to
     * {@link GeoItemRenderer.RenderData}, and vanilla sets that owner to the holding entity for
     * third-person and first-person hands alike.
     */
    public static final DataTicket<Integer> HOLDER_ID =
            DataTicket.create("wand_holder_id", Integer.class);

    /** The wand's resolved look; absent only for a draw that was never captured, which gets the base wand. */
    public static final DataTicket<WandLook> WAND_LOOK = DataTicket.create("wand_look", WandLook.class);

    /** Whether this draw is a slot, frame, shelf or the ground, where the wand is shown whole rather than gripped. */
    private static final DataTicket<Boolean> CENTRED = DataTicket.create("wand_centred", Boolean.class);
    private static final DataTicket<Identifier> TRIM_SHEET = DataTicket.create("wand_trim_sheet", Identifier.class);
    private static final DataTicket<Integer> TRIM_COLOUR = DataTicket.create("wand_trim_colour", Integer.class);
    private static final DataTicket<Identifier> GLINT_SHEET = DataTicket.create("wand_glint_sheet", Identifier.class);
    private static final DataTicket<Integer> GLINT_COLOUR = DataTicket.create("wand_glint_colour", Integer.class);

    private static final Identifier WAND_TEXTURE = texture("wand");
    private static final Identifier ELDER_TEXTURE = texture("elder_wand");
    private static final Identifier TRIM_TEXTURE = texture("wand_trim");
    private static final Identifier GLINT_TEXTURE = texture("wand_glint");
    private static final Identifier GLINT_BONDED_TEXTURE = texture("wand_glint_bonded");
    private static final Identifier ELDER_GLINT_TEXTURE = texture("elder_wand_glint");
    private static final Identifier ELDER_GLINT_BONDED_TEXTURE = texture("elder_wand_glint_bonded");

    /**
     * Where a slot icon's core light sits in its rhythm. A slot icon is drawn into the GUI's item atlas
     * and not redrawn every frame, so it shows the light at one fixed moment rather than whichever
     * instant it happened to be cached at.
     */
    private static final float STILL_SECONDS = 0.0f;

    public WandRenderer() {
        super(new WandModel());
        withRenderLayer(new WandOverlayLayer(this, WandOverlayLayer.Kind.TRIM, TRIM_SHEET, TRIM_COLOUR));
        withRenderLayer(new WandOverlayLayer(this, WandOverlayLayer.Kind.GLINT, GLINT_SHEET, GLINT_COLOUR));
    }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "textures/item/" + name + ".png");
    }

    // ── State extraction (main-thread safe) ──────────────────────────────────

    @Override
    public void captureDefaultRenderState(WandItem animatable,
                                          GeoItemRenderer.RenderData renderData,
                                          GeoRenderState renderState,
                                          float partialTick) {
        super.captureDefaultRenderState(animatable, renderData, renderState, partialTick);
        ItemStack stack = renderData.itemStack();
        WandLook look = WandLookCache.look(registries(renderData), stack);
        ItemDisplayContext perspective = renderData.renderPerspective();
        renderState.addGeckolibData(WAND_LOOK, look);
        renderState.addGeckolibData(WAND_CONFIG, look.configuration());
        renderState.addGeckolibData(IS_ELDER_WAND, look.elder());
        renderState.addGeckolibData(CENTRED, centred(perspective));
        LivingEntity holder = renderData.itemOwner() == null ? null : renderData.itemOwner().asLivingEntity();
        captureOverlays(renderState, stack, look, holder, perspective);
        if (holder != null) {
            renderState.addGeckolibData(HOLDER_ID, holder.getId());
            // Only a wand drawn in a hand says whether it is being held for a cast. The same stack
            // is drawn again in the hotbar, and a slot copy must not tell the controller the hold
            // ended -- WandItem's controller leaves its clip alone when the ticket is absent.
            if (inHand(perspective)) {
                renderState.addGeckolibData(WandItem.HOLDING_CAST,
                        WandItem.isUsingWand(holder) && holder.getUseItem() == stack);
            }
        }
    }

    /**
     * The trim and the core's light, as sheets and colours for the two overlay layers.
     *
     * <p>The light's brightness is the wand's bond with whoever holds it -- in an inventory, the player
     * the game draws it for, so a won or stolen wand looks sullen in a bag it does not answer to
     * ({@link WandBondLook}).
     */
    private static void captureOverlays(GeoRenderState renderState, ItemStack stack, WandLook look,
                                        @org.jspecify.annotations.Nullable LivingEntity holder,
                                        ItemDisplayContext perspective) {
        if (!look.elder()) {
            renderState.addGeckolibData(TRIM_SHEET, TRIM_TEXTURE);
            renderState.addGeckolibData(TRIM_COLOUR, 0xFFFFFFFF);
        }
        UUID holderId = holder == null ? null : holder.getUUID();
        WandBondLook bond = WandBondLook.of(WandComponents.getMaster(stack),
                WandComponents.getAllegianceScore(stack), holderId);
        float seconds = perspective == ItemDisplayContext.GUI ? STILL_SECONDS : Util.getMillis() / 1000.0f;
        renderState.addGeckolibData(GLINT_COLOUR, look.glintArgb(bond, seconds));
        renderState.addGeckolibData(GLINT_SHEET, look.elder()
                ? (bond.reachesPommel() ? ELDER_GLINT_BONDED_TEXTURE : ELDER_GLINT_TEXTURE)
                : (bond.reachesPommel() ? GLINT_BONDED_TEXTURE : GLINT_TEXTURE));
    }

    /** Displays that show the wand whole rather than in a grip: it is centred on its own length there. */
    private static boolean centred(ItemDisplayContext perspective) {
        return perspective == ItemDisplayContext.GUI || perspective == ItemDisplayContext.FIXED
                || perspective == ItemDisplayContext.GROUND || perspective == ItemDisplayContext.ON_SHELF;
    }

    private static boolean inHand(ItemDisplayContext perspective) {
        return perspective.firstPerson()
                || perspective == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || perspective == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
    }

    /**
     * The client's copy of the synced registries, where the wand woods and cores (and so their colours,
     * grains, silhouettes and lights) live. Null only before a world is joined -- a wand drawn then gets
     * the base look.
     */
    private static HolderLookup.@org.jspecify.annotations.Nullable Provider registries(GeoItemRenderer.RenderData renderData) {
        if (renderData.level() != null) {
            return renderData.level().registryAccess();
        }
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? null : mc.level.registryAccess();
    }

    /**
     * Multiplies the wood tint (with this wand's slight variation, {@link WandLook#tint()}) into the
     * grain sheet.
     *
     * <p>This is what makes two wands distinguishable in an inventory at all. Before it, ten woods
     * and three cores shared one texture and the only way to tell two wands apart was to hover both
     * and compare tooltips.
     */
    @Override
    public int getRenderColor(WandItem animatable, GeoItemRenderer.RenderData renderData,
                              float partialTick) {
        int base = super.getRenderColor(animatable, renderData, partialTick);
        // The Elder Wand's look carries no tint: it is one specific wand, not a sample of elder wood,
        // and washing its own art with a wood colour would flatten the one wand that already looks
        // like itself.
        int tint = WandLookCache.look(registries(renderData), renderData.itemStack()).tint();
        if (tint != WandAppearance.UNTINTED) {
            base = net.minecraft.util.ARGB.multiply(base, tint);
        }
        return applyProtegoCharge(base, renderData.renderPerspective());
    }

    /**
     * A wand gathering a Shield Charm takes on the colour of the shape it is gathering, deepening as
     * the hold climbs.
     *
     * <p>Held perspectives only: this is the local player's own wand in their own hand telling them
     * what a release would buy, and tinting the same item in an inventory slot or on the ground would
     * describe a state that item does not have.
     */
    private static int applyProtegoCharge(int base, ItemDisplayContext perspective) {
        if (!inHand(perspective) || !ClientProtegoChargeState.isCharging()) {
            return base;
        }
        ProtegoTier tier = ClientProtegoChargeState.tier();
        float climb = (tier.index() + (ClientProtegoChargeState.isCapped()
                ? 1.0f : ClientProtegoChargeState.progress())) / ProtegoTier.values().length;
        float strength = Mth.clamp(0.25f + 0.75f * climb, 0.0f, 1.0f);
        // Lerp towards the tier colour rather than multiplying by it: a multiply on dark wandwood
        // darkens the wand instead of lighting it up, which is the opposite of a charge.
        return net.minecraft.util.ARGB.srgbLerp(strength, base, tier.colour());
    }

    // ── Bone visibility and proportions (render thread) ──────────────────────

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<GeoRenderState> info, BoneSnapshots bones) {
        super.adjustModelBonesForRender(info, bones);
        WandConfiguration config = info.getOrDefaultGeckolibData(WAND_CONFIG, WandConfiguration.DEFAULT);
        applyBoneVisibility(bones, config);
        WandLook look = info.getGeckolibData(WAND_LOOK);
        if (look != null && !look.elder()) {
            applyProportions(bones, look, Boolean.TRUE.equals(info.getGeckolibData(CENTRED)));
        }
    }

    /**
     * Shows the one selected variant per slot and hides every other.
     *
     * <p>Variant bones are children of their slot's container bone (handle_briar under handle,
     * tip_hook under tip) and may carry child bones of their own (a curl is a chain of bones);
     * skipChildrenRender takes those along. Addressing variants by name works regardless of that
     * hierarchy, which is why this loop does not walk it: the registry is the only thing that knows
     * every variant bone name. Slot containers and the spell attachment are never touched.
     */
    private static void applyBoneVisibility(BoneSnapshots bones, WandConfiguration config) {
        for (WandSlot slot : WandSlot.renderOrder()) {
            String selectedBoneName = config.getModule(slot)
                    .flatMap(WandModuleRegistry::get)
                    .map(WandModule::boneName)
                    .orElse(null); // null = hide all (optional slot with nothing set)

            for (WandModule module : WandModuleRegistry.getAllForSlot(slot)) {
                String boneName = module.boneName();
                boolean visible = boneName.equals(selectedBoneName);
                bones.ifPresent(boneName, snap -> {
                    snap.skipRender(!visible);
                    snap.skipChildrenRender(!visible);
                });
            }
        }
    }

    /**
     * Length, girth, bow and turn, as transforms on the modular rig's slot bones.
     *
     * <p>The rig hangs the tip off the shaft and the spell attachment off the tip (the "length chain",
     * {@code tools/wand_model.py}), so scaling the shaft carries the point and the place a spell leaves
     * with it, and the beam anchor stays on the drawn point; the tip undoes the shaft's lengthening so
     * only the shaft grows. The bow bends once at the grip and once more at the tip, which reads as a
     * curve on a rig with no joints in between.
     *
     * <p>Held, the grip stays in the hand and a long wand reaches further. Shown whole, the wand is
     * re-centred on its own length so the display transforms (fitted to the model's length) still frame
     * it: the longest wand the resolver allows still fits a slot corner to corner.
     */
    private static void applyProportions(BoneSnapshots bones, WandLook look, boolean centred) {
        float length = look.lengthScale();
        float bow = look.bowDegrees() * Mth.DEG_TO_RAD;
        bones.ifPresent("shaft", shaft -> shaft
                .setScale(look.girth(), length, look.girth())
                .setRotation(0.0f, look.twistDegrees() * Mth.DEG_TO_RAD, bow));
        bones.ifPresent("tip", tip -> tip
                .setScale(1.0f, 1.0f / length, 1.0f)
                .setRotZ(bow));
        if (centred) {
            float offset = (WandLook.MODEL_LENGTH - look.drawnLength()) / 2.0f;
            bones.ifPresent("wand_root", root -> root.setTranslateY(offset));
        }
    }

    // ── Tip position capture ─────────────────────────────────────────────────

    /**
     * Where a spell leaves the wand: one bone in every wand model (the modular wand and the Elder
     * Wand alike) that no variant owns, so it sits at the point whatever handle, shaft and tip are
     * showing -- and, on the modular wand, however long or bowed the look makes the shaft.
     */
    public static final String SPELL_ATTACHMENT_BONE = "wand_spell_attachment";

    @Override
    public void preRenderPass(RenderPassInfo<GeoRenderState> info, SubmitNodeCollector collector) {
        super.preRenderPass(info, collector);
        GeoRenderState state = info.renderState();
        Integer holderId = state.getGeckolibData(HOLDER_ID);
        ItemDisplayContext perspective = state.getGeckolibData(DataTickets.ITEM_RENDER_PERSPECTIVE);
        // Only a wand in a hand anchors a beam. Icons, item frames and dropped wands have no holder or
        // no hand context and stop here.
        if (holderId == null || perspective == null || !(perspective.firstPerson()
                || perspective == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || perspective == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)) {
            return;
        }
        boolean firstPerson = perspective.firstPerson();

        RenderPassInfo.BonePositionListener anchor = (worldPos, modelPos, localPos) -> {
            if (localPos != null) {
                captureBeamAnchor(holderId, firstPerson, info.getPreRenderMatrixState(), localPos,
                        state.getPartialTick());
            }
        };
        info.addBonePositionListener(SPELL_ATTACHMENT_BONE, anchor);
    }

    /**
     * Books a drawn tip onto its holder in the beam system's anchor cache. Built from
     * {@code localPos}, not the listener's {@code worldPos}: GeckoLib leaves that null for items —
     * see {@link WandTipTracker}. A first-person hand is drawn under its own projection, so it takes
     * its own capture path.
     */
    private static void captureBeamAnchor(int holderId, boolean firstPerson, Matrix4fc preRenderPose,
                                          Vec3 localPos, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(holderId) instanceof LivingEntity holder)) {
            return;
        }
        if (firstPerson) {
            WandTipTracker.captureFirstPerson(holder, preRenderPose, localPos);
        } else {
            WandTipTracker.capture(holder, preRenderPose, localPos, partialTick);
        }
    }

    // ── GeoModel ──────────────────────────────────────────────────────────────

    private static final class WandModel extends GeoModel<WandItem> {
        @Override
        public Identifier getModelResource(GeoRenderState renderState) {
            boolean elder = Boolean.TRUE.equals(renderState.getGeckolibData(IS_ELDER_WAND));
            return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID,
                    elder ? "item/elder_wand" : "item/wand");
        }

        /** The Elder Wand's own art, else the wood's grain sheet, else the plain sheet. */
        @Override
        public Identifier getTextureResource(GeoRenderState renderState) {
            if (Boolean.TRUE.equals(renderState.getGeckolibData(IS_ELDER_WAND))) {
                return ELDER_TEXTURE;
            }
            WandLook look = renderState.getGeckolibData(WAND_LOOK);
            Identifier grain = look == null ? null : WandLookCache.grainSheet(look.grain());
            return grain == null ? WAND_TEXTURE : grain;
        }

        @Override
        public Identifier getAnimationResource(WandItem animatable) {
            // WandItem registers a (STOP-state) controller, so a real — if empty — animation file
            // must exist: a null here NPEs inside GeckoLib the moment any animation is triggered.
            // Short form like getModelResource; GeckoLib expands to geckolib/animations/item/wand.animation.json.
            return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "item/wand");
        }
    }
}
