package at.koopro.wizardsandbeasts.broom;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The six swappable parts of the master broom model, mirroring {@code WandSlot}.
 *
 * <p>Each slot owns a family of bones in {@code broom.geo.json} named {@code <bonePrefix><variant>}
 * — {@code bristles_birch}, {@code footrest_brass} — of which exactly one is visible at a time, or
 * none for an optional slot left unset. A datapack assembles a broom by naming one variant per slot
 * in {@code model_slots}, so a new broom needs no new art.
 *
 * <p>{@link #SHAFT} and {@link #BRISTLES} are required: a broom missing either is not a broom, and
 * the renderer falls back to the default variant rather than drawing a gap. The other four are
 * genuinely absent on some brooms — Cleansweep-tier carries no footrest, and only the hero brooms
 * carry an accent plate.
 */
public enum BroomSlot implements StringRepresentable {
    /** Handle profile, thickness and taper. */
    SHAFT("shaft_", "shaft", true, "plain", List.of("plain", "oak", "swept", "racing", "heavy_oak")),
    /** Butt-end finial or cap. */
    TAIL_CAP("tail_cap_", "tail_cap", false, "plain", List.of("plain", "finial")),
    /** Cord or metal band at the bristle join. */
    BINDING("binding_", "binding", false, "cord",
            List.of("cord", "brass_band", "collar_ring", "iron_rings")),
    /** Twig bundle silhouette. */
    BRISTLES("bristles_", "bristles", true, "ragged",
            List.of("ragged", "teardrop", "blade", "streamlined", "swept", "heavy")),
    /**
     * The hanging leather strap and toggle bead every prop carries, not a tier-gated
     * platform. Default-present: the reference shows it on training brooms too.
     */
    FOOTSTRAP("footstrap_", "footstrap", false, "leather", List.of("leather", "iron_peg")),
    /** Nameplate, maker stamp, runic collar. */
    ACCENT("accent_", "accent", false, "none",
            List.of("none", "nameplate", "maker_mark", "runic_band"));

    public static final Codec<BroomSlot> CODEC = StringRepresentable.fromEnum(BroomSlot::values);

    /** Required slots first, then optional — the same contract {@code WandSlot.renderOrder()} states. */
    private static final List<BroomSlot> RENDER_ORDER = buildRenderOrder();

    /**
     * What a broom looks like when its JSON names nothing. Only the slots with a default variant
     * appear, so an unconfigured broom is a plain birch-bristled broomstick with no footrest and no
     * nameplate — deliberately the humblest thing the model can draw.
     */
    private static final Map<BroomSlot, Identifier> DEFAULTS = buildDefaults();

    private final String bonePrefix;
    private final String slotId;
    private final boolean required;
    private final String defaultVariant;
    private final List<String> variants;

    BroomSlot(String bonePrefix, String slotId, boolean required, String defaultVariant,
              List<String> variants) {
        this.bonePrefix = bonePrefix;
        this.slotId = slotId;
        this.required = required;
        this.defaultVariant = defaultVariant;
        this.variants = variants;
    }

    public String bonePrefix() {
        return bonePrefix;
    }

    @Override
    public String getSerializedName() {
        return slotId;
    }

    public String slotId() {
        return slotId;
    }

    public boolean isRequired() {
        return required;
    }

    /** Bone name for a variant in this slot: {@code BRISTLES.boneName("birch")} → {@code bristles_birch}. */
    public String boneName(String variantPath) {
        return bonePrefix + variantPath;
    }

    /**
     * Every variant this slot ships, and therefore every bone the renderer has to hide to show one.
     *
     * <p>This exists because GeckoLib's {@code BoneSnapshots} cannot be enumerated — it offers
     * {@code get(String)} and {@code ifPresent(String, …)} and nothing else — so showing one variant
     * means naming all the others to hide them. The wand solves the same problem with
     * {@code WandModuleRegistry.getAllForSlot}; a broom variant is not a registry entry, so the list
     * lives here instead.
     *
     * <p>It duplicates what {@code tools/broom_model.py} emits, which is a real risk: add a bone
     * there and forget here and the new variant is invisible, because nothing ever unhides it.
     * {@code BroomModelParityTest} holds the two against each other so the drift fails the build.
     */
    public List<String> variants() {
        return variants;
    }

    /** The variant drawn when nothing is configured, or empty for a slot that defaults to absent. */
    public Optional<Identifier> defaultVariant() {
        return Optional.ofNullable(defaultVariant)
                .map(v -> Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, v));
    }

    /** Slot id used by broom JSON before the rename, kept so existing datapacks still load. */
    private static final String LEGACY_FOOTREST_ID = "footrest";

    /**
     * Resolves a {@code model_slots} key.
     *
     * <p>Accepts the pre-rename {@code footrest} key as an alias for {@link #FOOTSTRAP}. The slot
     * was renamed because it models a hanging strap rather than a rigid platform, and a datapack
     * written against the old name would otherwise fail to decode rather than degrade — the codec
     * rejects unknown slot ids by design, so silence was never an option here.
     */
    public static Optional<BroomSlot> byId(String id) {
        if (LEGACY_FOOTREST_ID.equals(id)) {
            return Optional.of(FOOTSTRAP);
        }
        for (BroomSlot slot : values()) {
            if (slot.slotId.equals(id)) return Optional.of(slot);
        }
        return Optional.empty();
    }

    /** Canonical render order: required slots first, then optional. */
    public static List<BroomSlot> renderOrder() {
        return RENDER_ORDER;
    }

    /** The slot → variant map used by any broom whose definition omits {@code model_slots}. */
    public static Map<BroomSlot, Identifier> defaults() {
        return DEFAULTS;
    }

    private static List<BroomSlot> buildRenderOrder() {
        List<BroomSlot> order = new ArrayList<>();
        for (BroomSlot slot : values()) {
            if (slot.required) order.add(slot);
        }
        for (BroomSlot slot : values()) {
            if (!slot.required) order.add(slot);
        }
        return List.copyOf(order);
    }

    private static Map<BroomSlot, Identifier> buildDefaults() {
        Map<BroomSlot, Identifier> map = new LinkedHashMap<>();
        for (BroomSlot slot : values()) {
            slot.defaultVariant().ifPresent(id -> map.put(slot, id));
        }
        return Map.copyOf(map);
    }
}
