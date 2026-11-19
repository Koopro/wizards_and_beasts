package at.koopro.wizardsandbeasts.client.vfx;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import org.jspecify.annotations.NullMarked;

import java.util.HashMap;
import java.util.Map;

/**
 * The render type for {@link VfxQuads}: a textured, alpha-blended quad lit only by the lightmap value it carries.
 *
 * <p>Derived from vanilla's {@code TEXT} pipeline (position, colour, texture, lightmap; no normal-based shading) with
 * depth writes and back-face culling off. The first version used {@code entityTranslucent(Emissive)}: their shader
 * shades by the vertex normal against the entity light directions, which are not set up for this stage of the level
 * render, so every ring came out dark (a navy Protego, a brown Confringo; capture 2026-11-15). Here a glowing quad
 * passes full-bright light and shows its colour; a mark passes the world's light at its spot and darkens with it.
 *
 * <p>No depth write: a ring must not hide the embers behind its faint outer band.
 */
@NullMarked
public final class VfxRenderTypes {

    public static final RenderPipeline VFX_QUAD_PIPELINE = RenderPipelines.TEXT.toBuilder()
            .withLocation(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "pipeline/vfx_quad"))
            .withDepthWrite(false)
            .withCull(false)
            .build();

    private static final Map<Identifier, RenderType> BY_TEXTURE = new HashMap<>();

    private VfxRenderTypes() {}

    public static RenderType quad(Identifier texture) {
        return BY_TEXTURE.computeIfAbsent(texture, t -> RenderType.create(
                "wab_vfx_quad",
                RenderSetup.builder(VFX_QUAD_PIPELINE)
                        .withTexture("Sampler0", t)
                        .useLightmap()
                        .sortOnUpload()
                        .createRenderSetup()));
    }

    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(VFX_QUAD_PIPELINE);
    }
}
