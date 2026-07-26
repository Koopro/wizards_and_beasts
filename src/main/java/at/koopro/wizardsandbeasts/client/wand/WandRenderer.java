package at.koopro.wizardsandbeasts.client.wand;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.customization.WandConfiguration;
import at.koopro.wizardsandbeasts.wand.customization.WandModule;
import at.koopro.wizardsandbeasts.wand.customization.WandModuleRegistry;
import at.koopro.wizardsandbeasts.wand.customization.WandSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
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

        // Variant bones are flat siblings in the model (not children of container bones).
        // Enumerate via registry so we know every bone name; handle <name>_anchor siblings too.
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

    // ── Tip position caching (unchanged from original) ───────────────────────

    @Override
    public void preRenderPass(RenderPassInfo<GeoRenderState> info, SubmitNodeCollector collector) {
        super.preRenderPass(info, collector);
        Minecraft mc = Minecraft.getInstance();
        GeoRenderState state = info.renderState();
        ItemDisplayContext perspective = state.getGeckolibData(DataTickets.ITEM_RENDER_PERSPECTIVE);
        if (perspective == null) return;

        boolean cameraFirstPerson = mc.options.getCameraType().isFirstPerson();
        boolean contextFirstPerson = perspective.firstPerson();
        if (cameraFirstPerson != contextFirstPerson) return;

        // Master model: the visible tip anchor is <selected tip variant>_anchor (the fx subtree is
        // skipRender'd, so fx_tip_anchor never yields a position). Elder-wand skin: dedicated
        // wand_tip bone. Listen on both — only the bone present and visible in the active model fires.
        WandConfiguration config = state.getOrDefaultGeckolibData(WAND_CONFIG, WandConfiguration.DEFAULT);
        config.getModule(WandSlot.TIP)
                .flatMap(WandModuleRegistry::get)
                .map(module -> module.boneName() + "_anchor")
                .ifPresent(anchorBone -> info.addBonePositionListener(anchorBone, (worldPos, modelPos, preRenderPos) -> {
                    if (worldPos != null) {
                        WandTipWorldCache.setWorldTip(worldPos);
                        captureBeamAnchor(mc, worldPos);
                    }
                }));
        info.addBonePositionListener(WandTipWorldCache.WAND_TIP_BONE, (worldPos, modelPos, preRenderPos) -> {
            if (worldPos != null) {
                WandTipWorldCache.setWorldTip(worldPos);
                captureBeamAnchor(mc, worldPos);
            }
        });
    }

    /**
     * Feeds the same bone position to the beam system's per-caster anchor cache.
     *
     * <p>An item render state carries no owner, so there is no way to tell <em>whose</em> wand just
     * rendered. In third person every nearby player's wand comes through here too, and booking all
     * of them onto the local player would make the local beam start at whichever wand was drawn
     * last. So only capture in first person, where the wand on screen is necessarily our own.
     *
     * <p>Other casters fall back to {@code WandTipTracker.resolve}'s body-rotation approximation.
     * At the distance you see someone else's beam from, tip-exact and shoulder-approximate are
     * indistinguishable — a wrong anchor on your own beam is not.
     */
    private static void captureBeamAnchor(Minecraft mc, net.minecraft.world.phys.Vec3 worldPos) {
        if (mc.player != null && mc.options.getCameraType().isFirstPerson()) {
            at.koopro.wizardsandbeasts.client.beam.WandTipTracker.capture(mc.player.getId(), worldPos);
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
