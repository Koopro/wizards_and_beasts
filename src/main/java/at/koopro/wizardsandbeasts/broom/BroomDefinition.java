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
        List<Component> loreLines) {

    public static final Codec<BroomDefinition> CODEC = Codec.of(
            BroomDefinition::encode,
            BroomDefinition::decode);

    private static <T> DataResult<Pair<BroomDefinition, T>> decode(DynamicOps<T> ops, T input) {
        Dynamic<T> dynamic = new Dynamic<>(ops, input);
        DataResult<Identifier> id = dynamic.get("id").result()
                .map(node -> Identifier.CODEC.parse(ops, node.getValue()))
                .orElse(DataResult.error(() -> "Missing required field: id"));
        DataResult<Component> displayName = dynamic.get("displayName").result()
                .map(node -> ComponentSerialization.CODEC.parse(ops, node.getValue()))
                .orElse(DataResult.error(() -> "Missing required field: displayName"));
        DataResult<BroomTier> tier = dynamic.get("tier").asString().flatMap(name -> {
            try {
                return DataResult.success(BroomTier.valueOf(name));
            } catch (IllegalArgumentException ex) {
                return DataResult.error(() -> "Invalid tier '" + name + "'");
            }
        });
        DataResult<Float> maxSpeed = readRangedFloat(dynamic, "maxSpeed", 0.1f, 2.0f);
        DataResult<Float> acceleration = readRangedFloat(dynamic, "acceleration", 0.005f, 0.15f);
        DataResult<Float> deceleration = readRangedFloat(dynamic, "deceleration", 0.005f, 0.05f);
        DataResult<Float> boostMultiplier = readRangedFloat(dynamic, "boostMultiplier", 1.0f, 3.0f);
        DataResult<Integer> boostDurationTicks = readRangedInt(dynamic, "boostDurationTicks", 20, 200);
        DataResult<Integer> boostCooldownTicks = readRangedInt(dynamic, "boostCooldownTicks", 40, 400);
        DataResult<Float> weakGravity = readRangedFloat(dynamic, "weakGravity", 0.001f, 0.02f);
        DataResult<Float> lerpFactor = readRangedFloat(dynamic, "lerpFactor", 0.05f, 0.5f);
        DataResult<Float> turnSpeed = readRangedFloat(dynamic, "turnSpeed", 0.5f, 2.0f);
        DataResult<Float> ascentSpeed = readRangedFloat(dynamic, "ascentSpeed", 0.05f, 0.4f);
        DataResult<Float> descentSpeed = readRangedFloat(dynamic, "descentSpeed", 0.05f, 0.4f);
        DataResult<Float> handlingRating = readRangedFloat(dynamic, "handlingRating", 0.0f, 1.0f);
        DataResult<Float> stabilityRating = readRangedFloat(dynamic, "stabilityRating", 0.0f, 1.0f);
        DataResult<Integer> durability = readRangedInt(dynamic, "durability", 50, 2000);
        DataResult<TagKey<Item>> repairMaterial = dynamic.get("repairMaterial").result()
                .map(node -> parseRepairMaterialTag(ops, node))
                .orElse(DataResult.error(() -> "Missing required field: repairMaterial"));
        DataResult<List<Component>> loreLines = dynamic.get("loreLines").result()
                .map(node -> ComponentSerialization.CODEC.listOf().parse(ops, node.getValue()))
                .orElse(DataResult.success(List.of()));

        return id.flatMap(vId ->
                displayName.flatMap(vDisplay ->
                        tier.flatMap(vTier ->
                                maxSpeed.flatMap(vMaxSpeed ->
                                        acceleration.flatMap(vAcceleration ->
                                                deceleration.flatMap(vDeceleration ->
                                                        boostMultiplier.flatMap(vBoostMultiplier ->
                                                                boostDurationTicks.flatMap(vBoostDuration ->
                                                                        boostCooldownTicks.flatMap(vBoostCooldown ->
                                                                                weakGravity.flatMap(vWeakGravity ->
                                                                                        lerpFactor.flatMap(vLerp ->
                                                                                                turnSpeed.flatMap(vTurn ->
                                                                                                        ascentSpeed.flatMap(vAscent ->
                                                                                                                descentSpeed.flatMap(vDescent ->
                                                                                                                        handlingRating.flatMap(vHandling ->
                                                                                                                                stabilityRating.flatMap(vStability ->
                                                                                                                                        durability.flatMap(vDurability ->
                                                                                                                                                repairMaterial.flatMap(vRepair ->
                                                                                                                                                        loreLines.map(vLore ->
                                                                                                                                                                new BroomDefinition(vId, vDisplay, vTier,
                                                                                                                                                                        vMaxSpeed, vAcceleration, vDeceleration,
                                                                                                                                                                        vBoostMultiplier, vBoostDuration, vBoostCooldown,
                                                                                                                                                                        vWeakGravity, vLerp, vTurn, vAscent, vDescent,
                                                                                                                                                                        vHandling, vStability, vDurability, vRepair, vLore)
                                                                                                                                                        ))))))))))))))))))).map(def -> Pair.of(def, input));
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
        return builder.build(prefix);
    }

    private static DataResult<Float> readRangedFloat(Dynamic<?> dynamic, String key, float min, float max) {
        java.util.Optional<? extends Dynamic<?>> nodeOpt = dynamic.get(key).result();
        if (nodeOpt.isEmpty()) {
            return DataResult.error(() -> "Missing required field: " + key);
        }
        float v = nodeOpt.get().asFloat(Float.NaN);
        if (Float.isNaN(v)) {
            return DataResult.error(() -> "Invalid float for field: " + key);
        }
        if (v < min || v > max) {
            return DataResult.error(() -> key + " out of range [" + min + ", " + max + "]: " + v);
        }
        return DataResult.success(v);
    }

    private static DataResult<Integer> readRangedInt(Dynamic<?> dynamic, String key, int min, int max) {
        java.util.Optional<? extends Dynamic<?>> nodeOpt = dynamic.get(key).result();
        if (nodeOpt.isEmpty()) {
            return DataResult.error(() -> "Missing required field: " + key);
        }
        int v = nodeOpt.get().asInt(Integer.MIN_VALUE);
        if (v == Integer.MIN_VALUE) {
            return DataResult.error(() -> "Invalid int for field: " + key);
        }
        if (v < min || v > max) {
            return DataResult.error(() -> key + " out of range [" + min + ", " + max + "]: " + v);
        }
        return DataResult.success(v);
    }

    private static <T> DataResult<TagKey<Item>> parseRepairMaterialTag(DynamicOps<T> ops, Dynamic<T> node) {
        return node.asString().flatMap(raw -> {
            if (raw.isBlank()) {
                return DataResult.error(() -> "repairMaterial must not be blank");
            }

            String value = raw.startsWith("#") ? raw.substring(1) : raw;
            Identifier id;
            try {
                id = Identifier.parse(value);
            } catch (Exception ex) {
                return DataResult.error(() -> "Invalid repairMaterial value: " + raw);
            }
            return DataResult.success(TagKey.create(Registries.ITEM, id));
        });
    }
}
