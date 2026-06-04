package at.koopro.wizardsandbeasts.azkaban.biome;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import org.jspecify.annotations.NonNull;
import terrablender.api.Region;
import terrablender.api.RegionType;
import terrablender.api.VanillaParameterOverlayBuilder;

import java.util.function.Consumer;

import static terrablender.api.ParameterUtils.Continentalness;
import static terrablender.api.ParameterUtils.Depth;
import static terrablender.api.ParameterUtils.Erosion;
import static terrablender.api.ParameterUtils.Humidity;
import static terrablender.api.ParameterUtils.ParameterPointListBuilder;
import static terrablender.api.ParameterUtils.Temperature;
import static terrablender.api.ParameterUtils.Weirdness;

/**
 * TerraBlender region that injects {@link AzkabanBiomes#AZKABAN_SEA} into the
 * overworld biome source as a cold, oceanic, rare biome.
 *
 * <p>A {@code NeoForge BiomeModifier} cannot add a <em>new</em> biome to the
 * biome source — it only edits existing biomes. TerraBlender's parameter overlay
 * is the supported path: this region claims a slice of the cold-ocean climate
 * space, so {@code azkaban_sea} actually generates and {@code /locate biome}
 * resolves it.
 *
 * <p>Rarity is the product of (a) a low region {@code weight} relative to the
 * vanilla overworld region and (b) the narrow cold-deep-ocean parameter window.
 * The Azkaban structure is anchored to this biome, so a rare biome means a rare
 * structure.
 */
public final class AzkabanRegion extends Region {

    /** Low weight vs. the vanilla overworld region (~10) keeps the biome rare. */
    private static final int WEIGHT = 3;

    public AzkabanRegion(@NonNull Identifier name) {
        super(name, RegionType.OVERWORLD, WEIGHT);
    }

    @Override
    public void addBiomes(
            Registry<Biome> registry,
            Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> mapper) {
        VanillaParameterOverlayBuilder builder = new VanillaParameterOverlayBuilder();
        new ParameterPointListBuilder()
                // Cold North-Sea climate.
                .temperature(Temperature.span(Temperature.FROZEN, Temperature.COOL))
                .humidity(Humidity.FULL_RANGE)
                // Open deep/standard ocean only — never on land.
                .continentalness(Continentalness.span(Continentalness.DEEP_OCEAN, Continentalness.OCEAN))
                .erosion(Erosion.FULL_RANGE)
                // Ocean surface + floor.
                .depth(Depth.span(Depth.SURFACE, Depth.FLOOR))
                .weirdness(Weirdness.FULL_RANGE)
                .build()
                .forEach(point -> builder.add(point, AzkabanBiomes.AZKABAN_SEA));

        builder.build().forEach(mapper);
    }
}
