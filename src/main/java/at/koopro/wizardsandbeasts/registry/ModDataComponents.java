package at.koopro.wizardsandbeasts.registry;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.item.wand.WandCore;
import net.minecraft.core.BlockPos;
import at.koopro.wizardsandbeasts.item.wand.WandFlexibility;
import at.koopro.wizardsandbeasts.item.wand.WandLength;
import at.koopro.wizardsandbeasts.item.wand.WandWood;
import at.koopro.wizardsandbeasts.wand.cast.WandAllegiance;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModDataComponents {

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

    private ModDataComponents() {
    }
}
