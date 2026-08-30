package at.koopro.wizardsandbeasts.broom;

/*
 AUDIT FINDINGS (pre-implementation)
 - BroomEntity hardcoded physics usage location:
   - BroomEntity delegates motion/tilt into BroomMovement.tickMovement/updateTilt.
 - Constants currently driving broom physics in BroomTuning/BroomMovement:
   - MAX_SPEED = 1.15f
   - ACCELERATION = 0.11f
   - DECELERATION = 0.075f
   - BOOST_MULTIPLIER = 1.45f
   - WEAK_GRAVITY = 0.012f
   - VERTICAL_SPEED = 0.12f
   - VERTICAL_RESPONSE = 0.18f
   - LOW_SPEED_TURN_RATE = 10.0f / HIGH_SPEED_TURN_RATE = 4.0f
   - TILT_SMOOTHING = 0.35f
   - MAX_ROLL_TILT = 35f / MAX_FORWARD_LEAN = 20f
 - BroomItem spawning behavior:
   - BroomItem.use() creates BroomEntity, copies held ItemStack into entity via setBroomStack(stack.copy()).
   - Persistence uses BroomEntity.readAdditionalSaveData/addAdditionalSaveData with key "BroomStack" and ItemStack.CODEC.
 - Existing broom variant concept:
   - No existing BroomVariant/BroomType enum/record found in codebase.
 - Existing synced entity data on BroomEntity:
   - defineSynchedData(...) currently empty; no EntityDataAccessor entries are registered.
*/

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public record BroomDefinition(
        Identifier id,
        Component displayName,
        BroomTier tier,
        float maxSpeed,
        float acceleration,
        float deceleration,
        float boostMultiplier,
        int boostDurationTicks,
        int boostCooldownTicks,
        float weakGravity,
        float lerpFactor,
        float turnSpeed,
        float ascentSpeed,
        float descentSpeed,
        float handlingRating,
        float stabilityRating,
        int durability,
        TagKey<Item> repairMaterial,
        List<Component> loreLines,
        Map<BroomSlot, Identifier> modelSlots,
        int woodTint,
        BroomAssets assets,
        BroomHandling handling,
        BroomAudio audio,
        BroomSeat seat) {

    /** Tint that multiplies to no change — the value a broom carries when it authors no wood_tint. */
    public static final int UNTINTED = 0xFFFFFFFF;

    /**
     * Where a subpath-form {@code texture} is rooted, under {@code textures/entity/}.
     *
     * <p>A broom that names no sheet draws {@code textures/entity/broom.png}, whose handle is
     * painted greyscale so {@link #woodTint()} can colour it. A broom that names one draws that
     * sheet in final colour and must leave {@code wood_tint} alone — the two are alternatives, not
     * layers, and setting both multiplies the colour in twice.
     */
    public static final String TEXTURE_ROOT = "broom/";

    public static final Codec<BroomDefinition> CODEC = Codec.of(
            BroomDefinition::encode,
            BroomDefinition::decode);

    private static final Codec<BroomSlot> SLOT_KEY_CODEC = Codec.STRING.comapFlatMap(
            id -> BroomSlot.byId(id)
                    .map(DataResult::success)
                    .orElseGet(() -> DataResult.error(() -> "Unknown broom slot: " + id)),
            BroomSlot::slotId);

    private static final Codec<Map<BroomSlot, Identifier>> MODEL_SLOTS_CODEC =
            Codec.unboundedMap(SLOT_KEY_CODEC, Identifier.CODEC);

    private static final Codec<BroomTier> TIER_CODEC = Codec.STRING.comapFlatMap(
            name -> {
                try {
                    return DataResult.success(BroomTier.valueOf(name));
                } catch (IllegalArgumentException ex) {
                    return DataResult.error(() -> "Invalid tier '" + name + "'");
                }
            },
            BroomTier::name);

    /**
     * A repair-material tag, written with or without the {@code #} a tag reference usually carries.
     * The leading hash is what every other tag field in the mod's data uses, so it is accepted here
     * even though the value is parsed as a bare tag id.
     */
    private static final Codec<TagKey<Item>> REPAIR_MATERIAL_CODEC = Codec.STRING.comapFlatMap(
            raw -> {
                if (raw.isBlank()) {
                    return DataResult.error(() -> "repairMaterial must not be blank");
                }
                String value = raw.startsWith("#") ? raw.substring(1) : raw;
                try {
                    return DataResult.success(TagKey.create(Registries.ITEM, Identifier.parse(value)));
                } catch (Exception ex) {
                    return DataResult.error(() -> "Invalid repairMaterial value: " + raw);
                }
            },
            tag -> "#" + tag.location());

    /**
     * Hex-string colour, the convention the creature {@code tint} ability already uses:
     * {@code "#RRGGBB"} is opaque, {@code "#AARRGGBB"} is taken as written.
     */
    private static final Codec<Integer> WOOD_TINT_CODEC = Codec.STRING.comapFlatMap(
            BroomDefinition::parseHex,
            argb -> String.format(Locale.ROOT, "#%08X", argb));

    /** True when this broom paints its own sheet and must therefore not also be tinted. */
    public boolean hasOwnTexture() {
        return assets.texture().isPresent();
    }

    /** The variant chosen for {@code slot}, or empty when the slot is unset (so nothing is drawn). */
    public Optional<Identifier> modelSlot(BroomSlot slot) {
        return Optional.ofNullable(modelSlots.get(slot));
    }

    /**
     * Reads one definition, reporting every fault it finds rather than only the first.
     *
     * <p>This used to be a nested {@code flatMap} pyramid 22 levels deep, because
     * {@code RecordCodecBuilder.group} caps at 16 fields and this record has more. The pyramid
     * worked, but it short-circuited: a datapack with four bad values reported one, so fixing it
     * took four reload cycles. {@link BroomFields} collects instead, which also means adding a field
     * is one line here rather than one more level of indentation for every line below it.
     *
     * <p>Every optional field falls back to the value the broom had before that field existed, so a
     * definition written against any earlier version of this schema decodes to the same broom it
     * always described. The one deliberate exception is {@code yawDrift} — see
     * {@link HandlingProfile}.
     */
    private static <T> DataResult<Pair<BroomDefinition, T>> decode(DynamicOps<T> ops, T input) {
        BroomFields<T> f = new BroomFields<>(new Dynamic<>(ops, input));

        Identifier id = f.required("id", Identifier.CODEC);
        Component displayName = f.required("displayName", ComponentSerialization.CODEC);
        BroomTier tier = f.optional("tier", TIER_CODEC, null);
        if (tier == null && !f.has("tier")) {
            f.fault("Missing required field: tier");
        }

        float maxSpeed = f.rangedFloat("maxSpeed", 0.1f, 2.0f);
        float acceleration = f.rangedFloat("acceleration", 0.005f, 0.15f);
        float deceleration = f.rangedFloat("deceleration", 0.005f, 0.05f);
        float boostMultiplier = f.rangedFloat("boostMultiplier", 1.0f, 3.0f);
        int boostDurationTicks = f.rangedInt("boostDurationTicks", 20, 200);
        int boostCooldownTicks = f.rangedInt("boostCooldownTicks", 40, 400);
        float weakGravity = f.rangedFloat("weakGravity", 0.001f, 0.02f);
        float lerpFactor = f.rangedFloat("lerpFactor", 0.05f, 0.5f);
        float turnSpeed = f.rangedFloat("turnSpeed", 0.5f, 2.0f);
        float ascentSpeed = f.rangedFloat("ascentSpeed", 0.05f, 0.4f);
        float descentSpeed = f.rangedFloat("descentSpeed", 0.05f, 0.4f);
        float handlingRating = f.rangedFloat("handlingRating", 0.0f, 1.0f);
        float stabilityRating = f.rangedFloat("stabilityRating", 0.0f, 1.0f);
        int durability = f.rangedInt("durability", 50, 2000);

        TagKey<Item> repairMaterial = f.required("repairMaterial", REPAIR_MATERIAL_CODEC);
        List<Component> loreLines =
                f.optional("loreLines", ComponentSerialization.CODEC.listOf(), List.of());
        // Both optional: a broom JSON written before the master model existed still decodes, and
        // gets the plain default silhouette with no tint rather than an empty model.
        Map<BroomSlot, Identifier> modelSlots =
                f.optional("model_slots", MODEL_SLOTS_CODEC, BroomSlot.defaults());
        int woodTint = f.optional("wood_tint", WOOD_TINT_CODEC, UNTINTED);

        BroomAssets assets = BroomAssets.decode(f);
        BroomHandling handling = BroomHandling.decode(f);
        BroomAudio audio = BroomAudio.decode(f);
        BroomSeat seat = BroomSeat.decode(f);

        Optional<String> failure = f.failure();
        if (failure.isPresent()) {
            String message = failure.get();
            return DataResult.error(() -> message);
        }

        BroomDefinition definition = new BroomDefinition(id, displayName, tier,
                maxSpeed, acceleration, deceleration, boostMultiplier, boostDurationTicks,
                boostCooldownTicks, weakGravity, lerpFactor, turnSpeed, ascentSpeed, descentSpeed,
                handlingRating, stabilityRating, durability, repairMaterial, loreLines,
                modelSlots, woodTint, assets, handling, audio, seat);
        return DataResult.success(Pair.of(definition, input));
    }

    private static <T> DataResult<T> encode(BroomDefinition definition, DynamicOps<T> ops, T prefix) {
        com.mojang.serialization.RecordBuilder<T> builder = ops.mapBuilder();
        builder.add("id", Identifier.CODEC.encodeStart(ops, definition.id()).result().orElseThrow());
        builder.add("displayName", ComponentSerialization.CODEC.encodeStart(ops, definition.displayName()).result().orElseThrow());
        builder.add("tier", Codec.STRING.encodeStart(ops, definition.tier().name()).result().orElseThrow());
        builder.add("maxSpeed", Codec.FLOAT.encodeStart(ops, definition.maxSpeed()).result().orElseThrow());
        builder.add("acceleration", Codec.FLOAT.encodeStart(ops, definition.acceleration()).result().orElseThrow());
        builder.add("deceleration", Codec.FLOAT.encodeStart(ops, definition.deceleration()).result().orElseThrow());
        builder.add("boostMultiplier", Codec.FLOAT.encodeStart(ops, definition.boostMultiplier()).result().orElseThrow());
        builder.add("boostDurationTicks", Codec.INT.encodeStart(ops, definition.boostDurationTicks()).result().orElseThrow());
        builder.add("boostCooldownTicks", Codec.INT.encodeStart(ops, definition.boostCooldownTicks()).result().orElseThrow());
        builder.add("weakGravity", Codec.FLOAT.encodeStart(ops, definition.weakGravity()).result().orElseThrow());
        builder.add("lerpFactor", Codec.FLOAT.encodeStart(ops, definition.lerpFactor()).result().orElseThrow());
        builder.add("turnSpeed", Codec.FLOAT.encodeStart(ops, definition.turnSpeed()).result().orElseThrow());
        builder.add("ascentSpeed", Codec.FLOAT.encodeStart(ops, definition.ascentSpeed()).result().orElseThrow());
        builder.add("descentSpeed", Codec.FLOAT.encodeStart(ops, definition.descentSpeed()).result().orElseThrow());
        builder.add("handlingRating", Codec.FLOAT.encodeStart(ops, definition.handlingRating()).result().orElseThrow());
        builder.add("stabilityRating", Codec.FLOAT.encodeStart(ops, definition.stabilityRating()).result().orElseThrow());
        builder.add("durability", Codec.INT.encodeStart(ops, definition.durability()).result().orElseThrow());
        builder.add("repairMaterial", TagKey.codec(Registries.ITEM).encodeStart(ops, definition.repairMaterial()).result().orElseThrow());
        if (!definition.loreLines().isEmpty()) {
            builder.add("loreLines", ComponentSerialization.CODEC.listOf().encodeStart(ops, definition.loreLines()).result().orElseThrow());
        }
        // Written only when they differ from the decode-time defaults, so re-encoding a broom that
        // authored neither field does not inject keys its JSON never had.
        if (!definition.modelSlots().equals(BroomSlot.defaults())) {
            builder.add("model_slots", MODEL_SLOTS_CODEC.encodeStart(ops, definition.modelSlots()).result().orElseThrow());
        }
        if (definition.woodTint() != UNTINTED) {
            builder.add("wood_tint", WOOD_TINT_CODEC.encodeStart(ops, definition.woodTint()).result().orElseThrow());
        }
        definition.assets().encode(builder, ops);
        definition.handling().encode(builder, ops);
        definition.audio().encode(builder, ops);
        definition.seat().encode(builder, ops);
        return builder.build(prefix);
    }

    /** {@code "#RRGGBB"} → opaque ARGB, {@code "#AARRGGBB"} → as written. */
    private static DataResult<Integer> parseHex(String raw) {
        String body = raw.startsWith("#") ? raw.substring(1) : raw;
        if (body.length() != 6 && body.length() != 8) {
            return DataResult.error(() -> "wood_tint must be #RRGGBB or #AARRGGBB: " + raw);
        }
        try {
            long value = Long.parseLong(body, 16);
            return DataResult.success(body.length() == 6 ? (int) (value | 0xFF000000L) : (int) value);
        } catch (NumberFormatException ex) {
            return DataResult.error(() -> "Invalid wood_tint hex value: " + raw);
        }
    }

}
