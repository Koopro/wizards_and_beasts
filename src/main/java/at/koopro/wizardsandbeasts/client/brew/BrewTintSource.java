package at.koopro.wizardsandbeasts.client.brew;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.brew.Brew;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import org.jspecify.annotations.Nullable;

/**
 * Paints the liquid in a brew bottle the colour the brew says it is.
 *
 * <h2>Why a tint source and not a colour handler</h2>
 * <p>{@code RegisterColorHandlersEvent.Item} does not exist in 1.21.11. Item tinting became
 * data-driven: a model declares {@code tints} in its {@code items/} definition, each entry naming a
 * registered {@link ItemTintSource}, and the source computes the colour per stack. So this is not a
 * stylistic choice between two available APIs — it is the only one there is. See
 * {@code net.minecraft.client.color.item.Potion} for the vanilla shape this follows.
 *
 * <h2>Why it can read the client store directly</h2>
 * <p>{@link ItemTintSource} is {@code @OnlyIn(Dist.CLIENT)}, so this class only ever exists on a
 * client, and {@link ClientBrewData} is exactly the store a client is supposed to ask. That is the
 * difference between this and the bottle's <em>name</em>, which has to run on both sides and therefore
 * derives its translation key from the id instead ({@code BrewNaming}).
 *
 * <p>Tinting is multiplicative over the greyscale liquid painted by {@code tools/item_models_3d.py},
 * which is why that box is painted near-white: the result is the brew's colour, not the brew's colour
 * times a purple.
 */
public record BrewTintSource(int fallback) implements ItemTintSource {

    /** Registry id; must match the {@code type} written in {@code assets/.../items/brew.json}. */
    public static final Identifier ID =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "brew");

    public static final MapCodec<BrewTintSource> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    ExtraCodecs.RGB_COLOR_CODEC.fieldOf("default").forGetter(BrewTintSource::fallback)
            ).apply(instance, BrewTintSource::new));

    public static void register(RegisterColorHandlersEvent.ItemTintSources event) {
        event.register(ID, MAP_CODEC);
    }

    /**
     * The brew's colour, or {@link #fallback} when there is nothing to go on.
     *
     * <p>Three ways to reach the fallback, all legitimate: an unbottled stack from the creative tab,
     * a brew id this client has never been told about, and the window between joining a server and
     * {@code BrewDataSyncPayload} landing. None of them should render a black bottle, so all three
     * take the generic purple the model was painted for.
     */
    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity holder) {
        String brewId = stack.get(ModDataComponents.BREW_ID.get());
        if (brewId == null || brewId.isBlank()) {
            return ARGB.opaque(fallback);
        }
        Brew brew = ClientBrewData.brew(brewId);
        return ARGB.opaque(brew != null ? brew.color() : fallback);
    }

    @Override
    public MapCodec<BrewTintSource> type() {
        return MAP_CODEC;
    }
}
