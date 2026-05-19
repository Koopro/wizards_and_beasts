package at.koopro.wizardsandbeasts.wand;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import at.koopro.wizardsandbeasts.wand.customization.WandConfiguration;
import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Optional;
import java.util.UUID;

public final class WandComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, WizardsAndBeastsMod.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Identifier>> WAND_WOOD =
            DATA_COMPONENTS.register("wand_wood", () -> DataComponentType.<Identifier>builder()
                    .persistent(Identifier.CODEC)
                    .networkSynchronized(Identifier.STREAM_CODEC)
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Identifier>> WAND_CORE =
            DATA_COMPONENTS.register("wand_core", () -> DataComponentType.<Identifier>builder()
                    .persistent(Identifier.CODEC)
                    .networkSynchronized(Identifier.STREAM_CODEC)
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<WandFlexibility>> WAND_FLEXIBILITY =
            DATA_COMPONENTS.register("wand_flexibility", () -> DataComponentType.<WandFlexibility>builder()
                    .persistent(WandFlexibility.CODEC)
                    .networkSynchronized(WandFlexibility.STREAM_CODEC)
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Optional<UUID>>> WAND_MASTER =
            DATA_COMPONENTS.register("wand_master", () -> DataComponentType.<Optional<UUID>>builder()
                    .persistent(UUIDUtil.CODEC.optionalFieldOf("wand_master").codec())
                    .networkSynchronized(ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC))
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> WAND_ALLEGIANCE_SCORE =
            DATA_COMPONENTS.register("wand_allegiance_score", () -> DataComponentType.<Float>builder()
                    .persistent(Codec.FLOAT)
                    .networkSynchronized(ByteBufCodecs.FLOAT)
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> WAND_CORRUPTION =
            DATA_COMPONENTS.register("wand_corruption", () -> DataComponentType.<Float>builder()
                    .persistent(Codec.FLOAT)
                    .networkSynchronized(ByteBufCodecs.FLOAT)
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> WAND_INTEGRITY =
            DATA_COMPONENTS.register("wand_integrity", () -> DataComponentType.<Float>builder()
                    .persistent(Codec.FLOAT)
                    .networkSynchronized(ByteBufCodecs.FLOAT)
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> WAND_CAST_COUNT =
            DATA_COMPONENTS.register("wand_cast_count", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> WAND_LENGTH =
            DATA_COMPONENTS.register("wand_length", () -> DataComponentType.<Float>builder()
                    .persistent(Codec.FLOAT)
                    .networkSynchronized(ByteBufCodecs.FLOAT)
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<WandConfiguration>> WAND_CONFIGURATION =
            DATA_COMPONENTS.register("wand_configuration", () -> DataComponentType.<WandConfiguration>builder()
                    .persistent(WandConfiguration.CODEC)
                    .networkSynchronized(WandConfiguration.STREAM_CODEC)
                    .build());

    private WandComponents() {
    }

    public static Identifier getWood(ItemStack stack) {
        return stack.get(WAND_WOOD.get());
    }

    public static Identifier getCore(ItemStack stack) {
        return stack.get(WAND_CORE.get());
    }

    public static WandFlexibility getFlexibility(ItemStack stack) {
        return stack.get(WAND_FLEXIBILITY.get());
    }

    public static Optional<UUID> getMaster(ItemStack stack) {
        return stack.getOrDefault(WAND_MASTER.get(), Optional.empty());
    }

    public static float getAllegianceScore(ItemStack stack) {
        return stack.getOrDefault(WAND_ALLEGIANCE_SCORE.get(), 1.0f);
    }

    public static float getCorruption(ItemStack stack) {
        return stack.getOrDefault(WAND_CORRUPTION.get(), 0.0f);
    }

    public static float getIntegrity(ItemStack stack) {
        return stack.getOrDefault(WAND_INTEGRITY.get(), 1.0f);
    }

    public static int getCastCount(ItemStack stack) {
        return stack.getOrDefault(WAND_CAST_COUNT.get(), 0);
    }

    public static Float getLength(ItemStack stack) {
        return stack.get(WAND_LENGTH.get());
    }
}
