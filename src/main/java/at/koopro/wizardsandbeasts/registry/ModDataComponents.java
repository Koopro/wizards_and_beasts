package at.koopro.wizardsandbeasts.registry;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.wand.stat.WandCore;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import net.minecraft.core.BlockPos;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import at.koopro.wizardsandbeasts.wand.stat.WandLength;
import at.koopro.wizardsandbeasts.item.wand.ExpelliarmusDropTag;
import at.koopro.wizardsandbeasts.wand.stat.WandWood;
import at.koopro.wizardsandbeasts.trunk.TrunkAccessMode;
import at.koopro.wizardsandbeasts.trunk.TrunkArchetype;
import at.koopro.wizardsandbeasts.wand.cast.WandAllegiance;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import net.minecraft.nbt.CompoundTag;
import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

public class ModDataComponents {

    private static final Identifier ELDER_WOOD_KEY =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "elder");
    private static final Identifier THESTRAL_TAIL_HAIR_KEY =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "thestral_tail_hair");

    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, WizardsAndBeastsMod.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<WandWood>> WAND_WOOD =
            DATA_COMPONENTS.register("wand_wood_legacy", () ->
                    DataComponentType.<WandWood>builder()
                            .persistent(WandWood.CODEC)
                            .networkSynchronized(WandWood.STREAM_CODEC)
                            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<WandCore>> WAND_CORE =
            DATA_COMPONENTS.register("wand_core_legacy", () ->
                    DataComponentType.<WandCore>builder()
                            .persistent(WandCore.CODEC)
                            .networkSynchronized(WandCore.STREAM_CODEC)
                            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<WandLength>> WAND_LENGTH =
            DATA_COMPONENTS.register("wand_length_legacy", () ->
                    DataComponentType.<WandLength>builder()
                            .persistent(WandLength.CODEC)
                            .networkSynchronized(WandLength.STREAM_CODEC)
                            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<WandFlexibility>> WAND_FLEXIBILITY =
            DATA_COMPONENTS.register("wand_flexibility_legacy", () ->
                    DataComponentType.<WandFlexibility>builder()
                            .persistent(WandFlexibility.CODEC)
                            .networkSynchronized(WandFlexibility.STREAM_CODEC)
                            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<WandAllegiance>> WAND_ALLEGIANCE =
            DATA_COMPONENTS.register("wand_allegiance_legacy", () ->
                    DataComponentType.<WandAllegiance>builder()
                            .persistent(WandAllegiance.CODEC)
                            .networkSynchronized(WandAllegiance.STREAM_CODEC)
                            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> CREATION_TICK =
            DATA_COMPONENTS.register("creation_tick", () ->
                    DataComponentType.<Long>builder()
                            .persistent(com.mojang.serialization.Codec.LONG)
                            .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_LONG)
                            .build());

    /** Famous wizard card variant id (e.g. "dumbledore"). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> WIZARD_CARD_ID =
            DATA_COMPONENTS.register("wizard_card_id", () ->
                    DataComponentType.<String>builder()
                            .persistent(com.mojang.serialization.Codec.STRING)
                            .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8)
                            .build());

    /** Number of lights currently stored in a Deluminator. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> STORED_LIGHTS =
            DATA_COMPONENTS.register("stored_lights", () ->
                    DataComponentType.<Integer>builder()
                            .persistent(com.mojang.serialization.Codec.INT)
                            .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT)
                            .build());

    /** Portkey destination (world-local). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlockPos>> PORTKEY_TARGET =
            DATA_COMPONENTS.register("portkey_target", () ->
                    DataComponentType.<BlockPos>builder()
                            .persistent(BlockPos.CODEC)
                            .networkSynchronized(BlockPos.STREAM_CODEC)
                            .build());

    /**
     * Fully-qualified id of the {@link at.koopro.wizardsandbeasts.brew.Brew} contained in
     * a brew bottle. Resolved at consumption time via
     * {@link at.koopro.wizardsandbeasts.brew.Brews#byId(String)}.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> BREW_ID =
            DATA_COMPONENTS.register("brew_id", () ->
                    DataComponentType.<String>builder()
                            .persistent(com.mojang.serialization.Codec.STRING)
                            .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8)
                            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Identifier>> BROOM_DEFINITION =
            DATA_COMPONENTS.register("broom_definition", () ->
                    DataComponentType.<Identifier>builder()
                            .persistent(Identifier.CODEC)
                            .networkSynchronized(ByteBufCodecs.STRING_UTF8.map(
                                    Identifier::parse,
                                    Identifier::toString))
                            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> POCKET_CASE_ID =
            DATA_COMPONENTS.register("pocket_case_id", () ->
                    DataComponentType.<UUID>builder()
                            .persistent(net.minecraft.core.UUIDUtil.CODEC)
                            .networkSynchronized(net.minecraft.core.UUIDUtil.STREAM_CODEC)
                            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> POCKET_ID =
            DATA_COMPONENTS.register("pocket_id", () ->
                    DataComponentType.<UUID>builder()
                            .persistent(net.minecraft.core.UUIDUtil.CODEC)
                            .networkSynchronized(net.minecraft.core.UUIDUtil.STREAM_CODEC)
                            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<TrunkArchetype>> POCKET_ARCHETYPE =
            DATA_COMPONENTS.register("pocket_archetype", () ->
                    DataComponentType.<TrunkArchetype>builder()
                            .persistent(TrunkArchetype.CODEC)
                            .networkSynchronized(ByteBufCodecs.STRING_UTF8.map(
                                    value -> TrunkArchetype.valueOf(value.toUpperCase(java.util.Locale.ROOT)),
                                    TrunkArchetype::getSerializedName))
                            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<TrunkAccessMode>> POCKET_ACCESS_MODE =
            DATA_COMPONENTS.register("pocket_access_mode", () ->
                    DataComponentType.<TrunkAccessMode>builder()
                            .persistent(TrunkAccessMode.CODEC)
                            .networkSynchronized(ByteBufCodecs.STRING_UTF8.map(
                                    value -> TrunkAccessMode.valueOf(value.toUpperCase(java.util.Locale.ROOT)),
                                    TrunkAccessMode::getSerializedName))
                            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> POCKET_TEMPLATE_ID =
            DATA_COMPONENTS.register("pocket_template_id", () ->
                    DataComponentType.<String>builder()
                            .persistent(com.mojang.serialization.Codec.STRING)
                            .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                            .build());

    /**
     * Lore: the Elder Wand (and similar artifacts) cannot be mended by Reparo.
     * Set on stacks that represent such wands; absent means false.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> WAND_ELDER_WAND =
            DATA_COMPONENTS.register("wand_elder_wand", () ->
                    DataComponentType.<Boolean>builder()
                            .persistent(com.mojang.serialization.Codec.BOOL)
                            .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.BOOL)
                            .build());

    /** Stable id for a physical wand instance (disarm log / allegiance). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<java.util.UUID>> WAND_INSTANCE_ID =
            DATA_COMPONENTS.register("wand_instance_id", () ->
                    DataComponentType.<java.util.UUID>builder()
                            .persistent(net.minecraft.core.UUIDUtil.CODEC)
                            .networkSynchronized(net.minecraft.core.UUIDUtil.STREAM_CODEC)
                            .build());

    /** True when the trunk is in Muggle-Worthy mode — displays as a mundane suitcase, blocks non-owner entry. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> POCKET_MUGGLE_WORTHY =
            DATA_COMPONENTS.register("pocket_muggle_worthy", () ->
                    DataComponentType.<Boolean>builder()
                            .persistent(com.mojang.serialization.Codec.BOOL)
                            .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.BOOL)
                            .build());

    /** True when the case has been locked from outside — players inside cannot exit via hatch or door. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> POCKET_LOCKED_EXTERNALLY =
            DATA_COMPONENTS.register("pocket_locked_externally", () ->
                    DataComponentType.<Boolean>builder()
                            .persistent(com.mojang.serialization.Codec.BOOL)
                            .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.BOOL)
                            .build());

    /** True when all latches are secured. Set false on drop; set true on normal exit or external lock. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> POCKET_LATCH_SECURED =
            DATA_COMPONENTS.register("pocket_latch_secured", () ->
                    DataComponentType.<Boolean>builder()
                            .persistent(com.mojang.serialization.Codec.BOOL)
                            .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.BOOL)
                            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ExpelliarmusDropTag>> EXPELLIARMUS_DROP =
            DATA_COMPONENTS.register("expelliarmus_drop", () ->
                    DataComponentType.<ExpelliarmusDropTag>builder()
                            .persistent(ExpelliarmusDropTag.CODEC)
                            .networkSynchronized(ExpelliarmusDropTag.STREAM_CODEC)
                            .build());

    // ── Deathly Hallows ──────────────────────────────────────────

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> RESURRECTION_STONE_USES =
            DATA_COMPONENTS.register("resurrection_stone_uses", () ->
                    DataComponentType.<Integer>builder()
                            .persistent(com.mojang.serialization.Codec.INT)
                            .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT)
                            .build());

    // ── Horcrux Vessels (shared) ─────────────────────────────────

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> SOUL_FRAGMENT_INTACT =
            DATA_COMPONENTS.register("soul_fragment_intact", () ->
                    DataComponentType.<Boolean>builder()
                            .persistent(com.mojang.serialization.Codec.BOOL)
                            .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.BOOL)
                            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Optional<UUID>>> DIARY_POSSESSING =
            DATA_COMPONENTS.register("diary_possessing", () ->
                    DataComponentType.<Optional<UUID>>builder()
                            .persistent(net.minecraft.core.UUIDUtil.CODEC.optionalFieldOf("value").codec())
                            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> RING_STONE_PRESENT =
            DATA_COMPONENTS.register("ring_stone_present", () ->
                    DataComponentType.<Boolean>builder()
                            .persistent(com.mojang.serialization.Codec.BOOL)
                            .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.BOOL)
                            .build());

    // ── Unique Artefacts ─────────────────────────────────────────

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> PHILOSOPHERS_STONE_DESTROYED =
            DATA_COMPONENTS.register("philosophers_stone_destroyed", () ->
                    DataComponentType.<Boolean>builder()
                            .persistent(com.mojang.serialization.Codec.BOOL)
                            .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.BOOL)
                            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> PENSIEVE_MEMORIES_STORED =
            DATA_COMPONENTS.register("pensieve_memories_stored", () ->
                    DataComponentType.<Integer>builder()
                            .persistent(com.mojang.serialization.Codec.INT)
                            .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT)
                            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Optional<UUID>>> MIRROR_PAIR_ID =
            DATA_COMPONENTS.register("mirror_pair_id", () ->
                    DataComponentType.<Optional<UUID>>builder()
                            .persistent(net.minecraft.core.UUIDUtil.CODEC.optionalFieldOf("value").codec())
                            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Optional<String>>> MIRROR_RECIPIENT_NAME =
            DATA_COMPONENTS.register("mirror_recipient_name", () ->
                    DataComponentType.<Optional<String>>builder()
                            .persistent(com.mojang.serialization.Codec.STRING.optionalFieldOf("value").codec())
                            .build());

    // ── Containers & Dark Arts ────────────────────────────────────

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> HAND_OF_GLORY_CANDLE_LIT =
            DATA_COMPONENTS.register("hand_of_glory_candle_lit", () ->
                    DataComponentType.<Boolean>builder()
                            .persistent(com.mojang.serialization.Codec.BOOL)
                            .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.BOOL)
                            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> MOODYS_TRUNK_ACTIVE_LOCK =
            DATA_COMPONENTS.register("moodys_trunk_active_lock", () ->
                    DataComponentType.<Integer>builder()
                            .persistent(com.mojang.serialization.Codec.INT)
                            .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT)
                            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> MOODYS_TRUNK_BASE_ID =
            DATA_COMPONENTS.register("moodys_trunk_base_id", () ->
                    DataComponentType.<UUID>builder()
                            .persistent(net.minecraft.core.UUIDUtil.CODEC)
                            .networkSynchronized(net.minecraft.core.UUIDUtil.STREAM_CODEC)
                            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> HERMIONES_BAG_INVENTORY =
            DATA_COMPONENTS.register("hermiones_bag_inventory", () ->
                    DataComponentType.<CompoundTag>builder()
                            .persistent(CompoundTag.CODEC)
                            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> BLOOD_PACT_BROKEN =
            DATA_COMPONENTS.register("blood_pact_broken", () ->
                    DataComponentType.<Boolean>builder()
                            .persistent(com.mojang.serialization.Codec.BOOL)
                            .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.BOOL)
                            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Optional<UUID>>> PACT_UUID =
            DATA_COMPONENTS.register("pact_uuid", () ->
                    DataComponentType.<Optional<UUID>>builder()
                            .persistent(net.minecraft.core.UUIDUtil.CODEC.optionalFieldOf("value").codec())
                            .networkSynchronized(ByteBufCodecs.optional(net.minecraft.core.UUIDUtil.STREAM_CODEC))
                            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Optional<UUID>>> PACT_PARTNER_UUID =
            DATA_COMPONENTS.register("pact_partner_uuid", () ->
                    DataComponentType.<Optional<UUID>>builder()
                            .persistent(net.minecraft.core.UUIDUtil.CODEC.optionalFieldOf("value").codec())
                            .networkSynchronized(ByteBufCodecs.optional(net.minecraft.core.UUIDUtil.STREAM_CODEC))
                            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> NEWTS_CASE_MUGGLE_WORTHY =
            DATA_COMPONENTS.register("newts_case_muggle_worthy", () ->
                    DataComponentType.<Boolean>builder()
                            .persistent(com.mojang.serialization.Codec.BOOL)
                            .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.BOOL)
                            .build());

    private ModDataComponents() {
    }

    public static boolean isElderWand(ItemStack stack) {
        return Boolean.TRUE.equals(stack.get(WAND_ELDER_WAND.get()));
    }

    /** Lore pairing: elder wood + thestral tail hair (Tales of Beedle the Bard). */
    public static boolean isElderWandWoodAndCorePair(@Nullable Identifier wood, @Nullable Identifier core) {
        return ELDER_WOOD_KEY.equals(wood) && THESTRAL_TAIL_HAIR_KEY.equals(core);
    }

    /**
     * Sets or clears {@link #WAND_ELDER_WAND} from the stack's wand wood/core components.
     * Call after any code path that assigns {@link WandComponents#WAND_WOOD} / {@link WandComponents#WAND_CORE}.
     */
    public static void refreshElderWandMarker(ItemStack stack) {
        if (!(stack.getItem() instanceof WandItem)) {
            return;
        }
        if (isElderWandWoodAndCorePair(WandComponents.getWood(stack), WandComponents.getCore(stack))) {
            stack.set(WAND_ELDER_WAND.get(), true);
        } else {
            stack.remove(WAND_ELDER_WAND.get());
        }
    }
}
