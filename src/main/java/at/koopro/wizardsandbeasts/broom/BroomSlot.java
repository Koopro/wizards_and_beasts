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
    /** Handle profile, taper and length silhouette. */
    SHAFT("shaft_", "shaft", true, "straight"),
    /** Butt-end finial or cap. */
    TAIL_CAP("tail_cap_", "tail_cap", false, "plain"),
    /** Cord or metal band at the bristle join. */
    BINDING("binding_", "binding", false, "cord"),
    /** Twig bundle silhouette. */
    BRISTLES("bristles_", "bristles", true, "birch"),
    /** Present on Nimbus/Firebolt-tier brooms, absent on Cleansweep-tier. */
    FOOTREST("footrest_", "footrest", false, null),
    /** Nameplate, lettering, registration mark. */
    ACCENT("accent_", "accent", false, null);

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

    BroomSlot(String bonePrefix, String slotId, boolean required, String defaultVariant) {
        this.bonePrefix = bonePrefix;
        this.slotId = slotId;
        this.required = required;
        this.defaultVariant = defaultVariant;
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

    /** The variant drawn when nothing is configured, or empty for a slot that defaults to absent. */
    public Optional<Identifier> defaultVariant() {
        return Optional.ofNullable(defaultVariant)
                .map(v -> Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, v));
    }

    public static Optional<BroomSlot> byId(String id) {
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
