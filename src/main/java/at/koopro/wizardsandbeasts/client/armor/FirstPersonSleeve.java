package at.koopro.wizardsandbeasts.client.armor;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.equipment.EquipmentType;
import at.koopro.wizardsandbeasts.equipment.curios.CuriosEquipment;
import at.koopro.wizardsandbeasts.equipment.wardrobe.Outfit;
import at.koopro.wizardsandbeasts.equipment.wardrobe.Wardrobe;
import at.koopro.wizardsandbeasts.item.armor.RobeCutItem;
import at.koopro.wizardsandbeasts.item.armor.WizardArmorItem;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.io.Reader;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The robe's sleeve on the first-person arm (Wardrobe 2.0). Vanilla draws no armour on the arm a player sees in first
 * person, so with an empty hand the arm showed bare skin under every robe.
 *
 * <p>When the empty hand's arm is drawn ({@link RenderArmEvent}), the cubes of the worn body cut's arm bone
 * ({@code armorRightArm}/{@code armorLeftArm}: sleeve, cuff, bracer) are read from its geo file, turned into a model
 * part in vanilla's arm space (the bone pivots on the arm's shoulder, geo y up becomes model y down) and drawn with the
 * cut's livery sheet, exactly where vanilla then draws the arm. Rank and fit groups are child bones and are not drawn.
 * Parsed once per cut and arm; a cut without an arm bone draws nothing.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class FirstPersonSleeve {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, Optional<ModelPart>> PARTS = new ConcurrentHashMap<>();
    private static final Map<Identifier, Boolean> SHEETS = new ConcurrentHashMap<>();

    private FirstPersonSleeve() {}

    @SubscribeEvent
    public static void onRenderArm(RenderArmEvent event) {
        ItemStack[] drawn = {ItemStack.EMPTY};
        CuriosEquipment.forEachRendered(event.getPlayer(), (slotId, index, stack) -> {
            if (slotId.equals(EquipmentType.BODY_CLOTHING.slotId())) {
                drawn[0] = stack;
            }
        });
        if (!(drawn[0].getItem() instanceof WizardArmorItem item) || item.defaultLivery() == null) {
            return;
        }
        String asset = asset(item);
        boolean right = event.getArm() == HumanoidArm.RIGHT;
        ModelPart part = PARTS.computeIfAbsent(asset + (right ? "/r" : "/l"), k -> load(asset, right)).orElse(null);
        if (part == null) {
            return;
        }
        part.resetPose();
        part.zRot = right ? 0.1F : -0.1F;   // as vanilla's renderHand turns the arm
        event.getSubmitNodeCollector().submitModelPart(part, event.getPoseStack(),
                RenderTypes.entityCutoutNoCull(sheet(asset, item, drawn[0])), event.getPackedLight(),
                OverlayTexture.NO_OVERLAY, null);
    }

    /** The cut's asset name: a {@link RobeCutItem}'s own, else the item id without its {@code _chest}. */
    static String asset(WizardArmorItem item) {
        if (item instanceof RobeCutItem cut) {
            return cut.assetName();
        }
        String path = BuiltInRegistries.ITEM.getKey(item).getPath();
        return path.endsWith("_chest") ? path.substring(0, path.length() - "_chest".length()) : path;
    }

    /** The livery sheet in effect, as {@code WizardArmorRenderer} picks it, else the cut's base sheet. */
    private static Identifier sheet(String asset, WizardArmorItem item, ItemStack stack) {
        Outfit outfit = Outfit.of(stack);
        Optional<Identifier> effective = outfit.livery().isPresent()
                ? outfit.livery() : Optional.ofNullable(item.defaultLivery());
        Identifier candidate = Wardrobe.liveryTexture(asset, null, effective);
        if (candidate != null && SHEETS.computeIfAbsent(candidate, id ->
                Minecraft.getInstance().getResourceManager().getResource(id).isPresent())) {
            return candidate;
        }
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "textures/armor/" + asset + ".png");
    }

    private static Optional<ModelPart> load(String asset, boolean right) {
        Identifier geo = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "geckolib/models/armor/" + asset + ".geo.json");
        try (Reader reader = Minecraft.getInstance().getResourceManager().openAsReader(geo)) {
            JsonObject geometry = JsonParser.parseReader(reader).getAsJsonObject()
                    .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
            JsonObject description = geometry.getAsJsonObject("description");
            int texW = description.get("texture_width").getAsInt();
            int texH = description.get("texture_height").getAsInt();
            String boneName = right ? "armorRightArm" : "armorLeftArm";
            for (JsonElement element : geometry.getAsJsonArray("bones")) {
                JsonObject bone = element.getAsJsonObject();
                if (!boneName.equals(bone.get("name").getAsString()) || !bone.has("cubes")) {
                    continue;
                }
                return Optional.of(build(bone, texW, texH, right));
            }
            return Optional.empty();
        } catch (Exception e) {
            LOGGER.warn("First-person sleeve: cannot read {}: {}", geo, e.toString());
            return Optional.empty();
        }
    }

    /** One model part in vanilla arm space from the bone's cubes. */
    static ModelPart build(JsonObject bone, int texW, int texH, boolean right) {
        JsonArray pivot = bone.getAsJsonArray("pivot");
        float px = pivot.get(0).getAsFloat();
        float py = pivot.get(1).getAsFloat();
        float pz = pivot.get(2).getAsFloat();
        CubeListBuilder cubes = CubeListBuilder.create();
        for (JsonElement element : bone.getAsJsonArray("cubes")) {
            JsonObject cube = element.getAsJsonObject();
            JsonArray origin = cube.getAsJsonArray("origin");
            JsonArray size = cube.getAsJsonArray("size");
            JsonArray uv = cube.getAsJsonArray("uv");
            float w = size.get(0).getAsFloat();
            float h = size.get(1).getAsFloat();
            float d = size.get(2).getAsFloat();
            float x = origin.get(0).getAsFloat() - px;
            float y = py - (origin.get(1).getAsFloat() + h);
            float z = origin.get(2).getAsFloat() - pz;
            float grow = cube.has("inflate") ? cube.get("inflate").getAsFloat() : 0f;
            cubes.texOffs(uv.get(0).getAsInt(), uv.get(1).getAsInt())
                    .addBox(x, y, z, w, h, d, new CubeDeformation(grow));
        }
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("sleeve", cubes, PartPose.offset(right ? -5F : 5F, 2F, 0F));
        return LayerDefinition.create(mesh, texW, texH).bakeRoot().getChild("sleeve");
    }
}
