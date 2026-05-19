package at.koopro.wizardsandbeasts.wand.recipe;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class WandmakingRecipeSerializer implements RecipeSerializer<WandmakingRecipe> {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, WizardsAndBeastsMod.MODID);

    public static final DeferredHolder<RecipeSerializer<?>, WandmakingRecipeSerializer> INSTANCE =
            RECIPE_SERIALIZERS.register("wandmaking", WandmakingRecipeSerializer::new);

    private static final MapCodec<WandmakingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.fieldOf("wood_key").forGetter(WandmakingRecipe::woodKey),
            Identifier.CODEC.fieldOf("core_key").forGetter(WandmakingRecipe::coreKey),
            Codec.FLOAT.fieldOf("minimum_bench_tier").forGetter(WandmakingRecipe::minimumBenchTier),
            Codec.FLOAT.fieldOf("result_length_min").forGetter(WandmakingRecipe::resultLengthMin),
            Codec.FLOAT.fieldOf("result_length_max").forGetter(WandmakingRecipe::resultLengthMax),
            Codec.FLOAT.fieldOf("result_integrity").forGetter(WandmakingRecipe::resultIntegrity)
    ).apply(instance, WandmakingRecipe::new));

    private static final StreamCodec<RegistryFriendlyByteBuf, WandmakingRecipe> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, WandmakingRecipe::woodKey,
            Identifier.STREAM_CODEC, WandmakingRecipe::coreKey,
            ByteBufCodecs.FLOAT, WandmakingRecipe::minimumBenchTier,
            ByteBufCodecs.FLOAT, WandmakingRecipe::resultLengthMin,
            ByteBufCodecs.FLOAT, WandmakingRecipe::resultLengthMax,
            ByteBufCodecs.FLOAT, WandmakingRecipe::resultIntegrity,
            WandmakingRecipe::new
    );

    @Override
    public MapCodec<WandmakingRecipe> codec() {
        return CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, WandmakingRecipe> streamCodec() {
        return STREAM_CODEC;
    }
}
