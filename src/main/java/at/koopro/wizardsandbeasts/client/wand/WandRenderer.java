package at.koopro.wizardsandbeasts.client.wand;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.beam.WandTipTracker;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.client.spell.protego.ClientProtegoChargeState;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.spell.protego.ProtegoTier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import at.koopro.wizardsandbeasts.wand.WandAppearance;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.customization.WandConfiguration;
import at.koopro.wizardsandbeasts.wand.customization.WandModule;
import at.koopro.wizardsandbeasts.wand.customization.WandModuleRegistry;
import at.koopro.wizardsandbeasts.wand.customization.WandSlot;
import net.minecraft.client.Minecraft;
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

public class WandRenderer extends GeoItemRenderer<WandItem> {

    /** DataTicket carrying the per-stack WandConfiguration through the render pipeline. */
    public static final DataTicket<WandConfiguration> WAND_CONFIG =
            DataTicket.create("wand_configuration", WandConfiguration.class);

    public static final DataTicket<Boolean> IS_ELDER_WAND =
            DataTicket.create("is_elder_wand", Boolean.class);

    /**
     * ARGB tint for the wand's wood, from {@link WandAppearance}.
     *
     * <p>Read through render-state rather than off the stack at render time, matching the
     * GeckoLib-5 contract the rest of this renderer already follows.
     */
    public static final DataTicket<Integer> WOOD_TINT =
            DataTicket.create("wand_wood_tint", Integer.class);

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

    public WandRenderer() {
        super(new WandModel());
    }

    // ── State extraction (main-thread safe) ──────────────────────────────────

    @Override
    public void captureDefaultRenderState(WandItem animatable,
                                          GeoItemRenderer.RenderData renderData,
                                          GeoRenderState renderState,
                                          float partialTick) {
        super.captureDefaultRenderState(animatable, renderData, renderState, partialTick);
        ItemStack stack = renderData.itemStack();
        WandConfiguration config = stack.getOrDefault(
                WandComponents.WAND_CONFIGURATION.get(), WandConfiguration.DEFAULT);
        renderState.addGeckolibData(WAND_CONFIG, config);
        renderState.addGeckolibData(IS_ELDER_WAND, ModDataComponents.isElderWand(stack));
        // The Elder Wand keeps its own art and is never tinted: it is one specific wand, not a
        // sample of elder wood, and washing its texture with a wood colour would flatten the one
        // wand in the game that already looks like itself.
        renderState.addGeckolibData(WOOD_TINT, ModDataComponents.isElderWand(stack)
                ? WandAppearance.UNTINTED
                : WandAppearance.woodTint(stack));
        LivingEntity holder = renderData.itemOwner() == null ? null : renderData.itemOwner().asLivingEntity();
        if (holder != null) {
            renderState.addGeckolibData(HOLDER_ID, holder.getId());
        }
    }

    /**
     * Multiplies the wood tint into the wand sprite.
     *
     * <p>This is what makes two wands distinguishable in an inventory at all. Before it, ten woods
     * and three cores shared one texture and the only way to tell two wands apart was to hover both
     * and compare tooltips.
     */
    @Override
    public int getRenderColor(WandItem animatable, GeoItemRenderer.RenderData renderData,
                              float partialTick) {
        int base = super.getRenderColor(animatable, renderData, partialTick);
        int tint = WandAppearance.woodTint(renderData.itemStack());
        if (tint != WandAppearance.UNTINTED && !ModDataComponents.isElderWand(renderData.itemStack())) {
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
        boolean inHand = perspective.firstPerson()
                || perspective == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || perspective == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        if (!inHand || !ClientProtegoChargeState.isCharging()) {
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

    // ── Bone visibility (render thread) ─────────────────────────────────────

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<GeoRenderState> info, BoneSnapshots bones) {
        super.adjustModelBonesForRender(info, bones);
        WandConfiguration config = info.getOrDefaultGeckolibData(WAND_CONFIG, WandConfiguration.DEFAULT);
        applyBoneVisibility(bones, config);
    }

    private static final String FX_BONE = "fx";

    private static void applyBoneVisibility(BoneSnapshots bones, WandConfiguration config) {
        // Hide entire FX subtree; spell rendering will show bones selectively.
        bones.ifPresent(FX_BONE, snap -> {
            snap.skipRender(true);
            snap.skipChildrenRender(true);
        });

        // Variant bones are children of their slot's container bone — handle_gnarled under handle,
        // tip_pointed under tip — and a tip variant's _anchor is a child of the variant itself.
        // Addressing bones by name works regardless of that hierarchy, which is why this loop does
        // not walk it: the registry is the only thing that knows every variant bone name.
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
                // tip variants have a paired <variant>_anchor bone (also flat sibling)
                bones.ifPresent(boneName + "_anchor", snap -> {
                    snap.skipRender(!visible);
                    snap.skipChildrenRender(!visible);
                });
            }
        }
    }

    // ── Tip position capture ─────────────────────────────────────────────────

    /** GeckoLib bone that marks the wand tip on the Elder Wand skin. */
    private static final String WAND_TIP_BONE = "wand_tip";

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
        // Master model: the visible tip anchor is <selected tip variant>_anchor (the fx subtree is
        // skipRender'd, so fx_tip_anchor never yields a position). Elder-wand skin: dedicated
        // wand_tip bone. Listen on both — only the bone present and visible in the active model fires.
        WandConfiguration config = state.getOrDefaultGeckolibData(WAND_CONFIG, WandConfiguration.DEFAULT);
        config.getModule(WandSlot.TIP)
                .flatMap(WandModuleRegistry::get)
                .map(module -> module.boneName() + "_anchor")
                .ifPresent(anchorBone -> info.addBonePositionListener(anchorBone, anchor));
        info.addBonePositionListener(WAND_TIP_BONE, anchor);
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

        @Override
        public Identifier getTextureResource(GeoRenderState renderState) {
            boolean elder = Boolean.TRUE.equals(renderState.getGeckolibData(IS_ELDER_WAND));
            return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID,
                    elder ? "textures/item/elder_wand.png" : "textures/item/wand.png");
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
