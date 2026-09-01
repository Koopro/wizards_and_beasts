package at.koopro.wizardsandbeasts.client.entity;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Ollivander's pointed hat — the geometry the villager render stack cannot supply.
 *
 * <p>A profession skin only repaints cubes the vanilla villager already has, and the tallest of
 * those is the flat {@code hat_rim} plate the farmer wears as a straw brim. A cone needs its own
 * parts, so this is a standalone mesh baked and submitted by {@link WandmakerHatLayer} in the
 * villager's head space: the head part's own pivot is the origin here, and the head cube runs
 * y -10 (scalp) to y 0 (neck), y counted downward as everywhere in model space.
 *
 * <p>The four crown segments are a chain rather than four siblings so their lean compounds: each
 * one tips a little further back than its parent, which is what turns a stack of shrinking boxes
 * into a hat that slouches instead of a traffic cone. Total lean at the tip is about 37 degrees.
 *
 * <p>The {@code texOffs} below are packed by {@code tools/villager_wandmaker.py} — the same
 * offsets in the same order, restated there as {@code HAT_ISLANDS}. Move one and the other has to
 * move with it.
 */
public final class WandmakerHatModel {

    private final ModelPart root;

    public WandmakerHatModel() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition rootDef = mesh.getRoot();

        // Brim at the base of the crown, wide enough to shade the face; the band is inflated by
        // half a pixel so it grips the scalp instead of z-fighting the head cube.
        rootDef.addOrReplaceChild("brim",
                CubeListBuilder.create().texOffs(0, 0).addBox(-7.0F, -7.5F, -7.0F, 14.0F, 1.0F, 14.0F),
                PartPose.ZERO);
        rootDef.addOrReplaceChild("band",
                CubeListBuilder.create().texOffs(0, 16)
                        .addBox(-4.0F, -10.0F, -4.0F, 8.0F, 3.0F, 8.0F, new CubeDeformation(0.5F)),
                PartPose.ZERO);

        PartDefinition cone1 = rootDef.addOrReplaceChild("cone1",
                CubeListBuilder.create().texOffs(32, 16).addBox(-3.0F, -3.0F, -3.0F, 6.0F, 3.0F, 6.0F),
                PartPose.offsetAndRotation(0.0F, -10.0F, 0.0F, -0.13F, 0.0F, 0.04F));
        PartDefinition cone2 = cone1.addOrReplaceChild("cone2",
                CubeListBuilder.create().texOffs(0, 28).addBox(-2.0F, -3.0F, -2.0F, 4.0F, 3.0F, 4.0F),
                PartPose.offsetAndRotation(0.0F, -3.0F, 0.0F, -0.15F, 0.0F, 0.03F));
        PartDefinition cone3 = cone2.addOrReplaceChild("cone3",
                CubeListBuilder.create().texOffs(16, 28).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F),
                PartPose.offsetAndRotation(0.0F, -3.0F, 0.0F, -0.17F, 0.0F, 0.03F));
        cone3.addOrReplaceChild("tip",
                CubeListBuilder.create().texOffs(24, 28).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 2.0F, 2.0F),
                PartPose.offsetAndRotation(0.0F, -3.0F, 0.0F, -0.20F, 0.0F, 0.02F));

        this.root = LayerDefinition.create(mesh, 64, 64).bakeRoot();
    }

    public ModelPart root() {
        return this.root;
    }
}
