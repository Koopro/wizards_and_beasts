package at.koopro.wizardsandbeasts.client;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer;

/**
 * Factory for simple GeoEntityRenderers that use DefaultedEntityGeoModel
 * with no custom render logic. Eliminates one-class-per-entity for simple mobs.
 */
public final class GeoRendererHelper {

    private GeoRendererHelper() {}

    /**
     * Creates a renderer factory for a simple GeckoLib entity.
     * <p>
     * Usage in ClientSetup:
     * <pre>
     * event.registerEntityRenderer(ModEntities.NIFFLER.get(),
     *     GeoRendererHelper.simple("niffler"));
     * </pre>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T extends Entity & GeoEntity> EntityRendererProvider<T> simple(String modelName) {
        return context -> new GeoEntityRenderer(context,
                new DefaultedEntityGeoModel<>(
                        Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, modelName)));
    }

    /**
     * Like {@link #simple}, but adds an {@link AutoGlowingGeoLayer} so emissive pixels
     * (from {@code <modelName>_glowmask.png}) glow full-bright. Used for the Thestral's eyes.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T extends Entity & GeoEntity> EntityRendererProvider<T> withGlow(String modelName) {
        return context -> {
            GeoEntityRenderer renderer = new GeoEntityRenderer(context,
                    new DefaultedEntityGeoModel<>(
                            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, modelName)));
            renderer.withRenderLayer(new AutoGlowingGeoLayer(renderer));
            return renderer;
        };
    }

    /**
     * Block-entity counterpart of {@link #simple}: a {@code GeoBlockRenderer} backed by a
     * {@code DefaultedBlockGeoModel}, no custom render logic. First user is the tent preview harness — see
     * {@code AUDIT_PUNCHLIST.md} "Tent Models" for why this mirrors the entity factory exactly rather than
     * inventing a different block-render convention.
     *
     * <p>{@code renderMargin} inflates the render bounding box by that many blocks in every direction, so a
     * model wider than its single-block hitbox isn't frustum-culled the instant the anchor block itself
     * leaves view. In this NeoForge version that hook is {@code IBlockEntityRendererExtension.
     * getRenderBoundingBox(T)}, a default method on the renderer (via {@code BlockEntityRenderer}) — not on
     * {@code BlockEntity} — hence the anonymous override here rather than in the block entity class.
     */
    public static <T extends BlockEntity & GeoBlockEntity, S extends BlockEntityRenderState & software.bernie.geckolib.renderer.base.GeoRenderState>
            BlockEntityRendererProvider<T, S> simpleBlock(String modelName, double renderMargin) {
        return context -> new GeoBlockRenderer<T, S>(
                new DefaultedBlockGeoModel<>(
                        Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, modelName))) {
            @Override
            public AABB getRenderBoundingBox(T blockEntity) {
                return new AABB(blockEntity.getBlockPos()).inflate(renderMargin);
            }
        };
    }
}
