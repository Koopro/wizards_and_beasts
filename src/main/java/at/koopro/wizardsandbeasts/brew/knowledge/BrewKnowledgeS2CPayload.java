package at.koopro.wizardsandbeasts.brew.knowledge;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What the client needs to show recipes: the recipes this player knows (basics included) and what each textbook
 * teaches -- so a book's tooltip can list its recipes, and spell out the ingredients of the ones already learned.
 * Sent on join, on every datapack reload, and whenever the player learns something.
 *
 * @param books textbook item path -> recipe ids
 */
@NullMarked
public record BrewKnowledgeS2CPayload(List<String> known, Map<String, List<String>> books, List<Line> lines)
        implements CustomPacketPayload {

    /**
     * One recipe as a tooltip writes it: the brew it makes, the pot it needs, its ingredients as "item*count", and
     * its catalyst as "item@start-end" (fractions of the brew) or empty.
     */
    public record Line(String recipe, String output, String tier, List<String> ingredients, String catalyst) {
        static final StreamCodec<ByteBuf, Line> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Line::recipe,
                ByteBufCodecs.STRING_UTF8, Line::output,
                ByteBufCodecs.STRING_UTF8, Line::tier,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(16)), Line::ingredients,
                ByteBufCodecs.STRING_UTF8, Line::catalyst,
                Line::new);
    }

    public static final Type<BrewKnowledgeS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "brew_knowledge"));

    private static final StreamCodec<ByteBuf, List<String>> IDS = ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(512));

    public static final StreamCodec<ByteBuf, BrewKnowledgeS2CPayload> STREAM_CODEC = StreamCodec.composite(
            IDS, BrewKnowledgeS2CPayload::known,
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, IDS, 64), BrewKnowledgeS2CPayload::books,
            Line.CODEC.apply(ByteBufCodecs.list(512)), BrewKnowledgeS2CPayload::lines,
            BrewKnowledgeS2CPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void sendTo(ServerPlayer player) {
        List<String> known = new ArrayList<>(RecipeSources.recipes(RecipeSources.BASIC));
        known.addAll(BrewKnowledge.book(player).known());
        Map<String, List<String>> books = new HashMap<>();
        RecipeSources.all().forEach((id, source) -> books.put(id.getPath(), source.recipes()));
        List<Line> lines = new ArrayList<>();
        for (at.koopro.wizardsandbeasts.brew.BrewingRecipe recipe : at.koopro.wizardsandbeasts.brew.BrewingRecipes.all()) {
            List<String> ingredients = recipe.ingredients().stream()
                    .map(i -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(i.item()) + "*" + i.count())
                    .toList();
            String catalyst = recipe.catalyst().map(c -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(c.item())
                    + "@" + c.windowStart() + "-" + c.windowEnd()).orElse("");
            lines.add(new Line(recipe.id(), recipe.outputBrewId(), recipe.cauldronTier().getSerializedName(),
                    ingredients, catalyst));
        }
        PacketDistributor.sendToPlayer(player, new BrewKnowledgeS2CPayload(known, books, lines));
    }

    public static void broadcast(MinecraftServer server) {
        server.getPlayerList().getPlayers().forEach(BrewKnowledgeS2CPayload::sendTo);
    }

    public static void handle(BrewKnowledgeS2CPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> KnownRecipesView.accept(payload.known(), payload.books(), payload.lines()));
    }
}
