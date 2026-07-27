package at.koopro.wizardsandbeasts.network.brew;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.brew.def.BrewDefinition;
import at.koopro.wizardsandbeasts.brew.def.BrewingRecipeDefinition;
import at.koopro.wizardsandbeasts.client.brew.ClientBrewData;
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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ships the brew catalogue and the brewing recipes to the client.
 *
 * <p><b>Why this has to exist.</b> Brews and brewing recipes are datapack content loaded by listeners
 * registered on {@code AddServerReloadListenersEvent} — server-side only. Cauldron brewing works fine
 * that way, because every decision it makes happens on the server. But it means that on a dedicated
 * server the client's {@code Brews} and {@code BrewingRecipes} tables are empty, and anything client-side
 * that wants to <em>describe</em> brewing — a recipe viewer, most obviously — has nothing to read. In
 * single player it appears to work only because client and integrated server share the same static maps,
 * which is the kind of accident that hides a multiplayer bug until someone reports it.
 *
 * <p>The wire format reuses the JSON codecs rather than inventing a second encoding for the same data:
 * whatever the datapack could express, this can carry, and there is no parallel format to keep in step.
 * The id travels as the map key because it comes from the file path, not the file body.
 *
 * <p>Sent as a whole snapshot on datapack sync. Brewing content is small (single-digit entries today) and
 * a snapshot cannot drift out of order the way a stream of deltas can — the same reasoning
 * {@code ModuleStateSyncPayload} follows.
 */
@NullMarked
public record BrewDataSyncPayload(Map<String, BrewDefinition> brews,
                                  Map<String, BrewingRecipeDefinition> recipes)
        implements CustomPacketPayload {

    /** A datapack that gets anywhere near this many brews has bigger problems than the packet size. */
    private static final int MAX_ENTRIES = 1024;

    public static final Type<BrewDataSyncPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "brew_data_sync"));

    public static final StreamCodec<ByteBuf, BrewDataSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(LinkedHashMap::new,
                    ByteBufCodecs.STRING_UTF8,
                    ByteBufCodecs.fromCodec(BrewDefinition.CODEC),
                    MAX_ENTRIES),
            BrewDataSyncPayload::brews,
            ByteBufCodecs.map(LinkedHashMap::new,
                    ByteBufCodecs.STRING_UTF8,
                    ByteBufCodecs.fromCodec(BrewingRecipeDefinition.CODEC),
                    MAX_ENTRIES),
            BrewDataSyncPayload::recipes,
            BrewDataSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BrewDataSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientBrewData.accept(payload));
    }

    // ── sending ──────────────────────────────────────────────────────────────────────────────────

    public static void sendTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, snapshot());
    }

    public static void broadcast(MinecraftServer server) {
        if (!server.getPlayerList().getPlayers().isEmpty()) {
            PacketDistributor.sendToAllPlayers(snapshot());
        }
    }

    /**
     * Rebuilds the definition form from what the reload listeners loaded.
     *
     * <p>The listeners keep resolved {@code Brew}/{@code BrewingRecipe} objects, not the definitions they
     * came from, so this converts back. Ingredients whose item id did not resolve on the server were
     * already dropped at load time and are simply absent here — the client is told what the server
     * actually has, not what the JSON asked for.
     */
    private static BrewDataSyncPayload snapshot() {
        Map<String, BrewDefinition> brews = new LinkedHashMap<>();
        at.koopro.wizardsandbeasts.brew.Brews.all()
                .forEach(brew -> brews.put(brew.id(), BrewDefinition.fromBrew(brew)));

        Map<String, BrewingRecipeDefinition> recipes = new LinkedHashMap<>();
        at.koopro.wizardsandbeasts.brew.BrewingRecipes.all()
                .forEach(recipe -> recipes.put(recipe.id(), BrewingRecipeDefinition.fromRecipe(recipe)));

        return new BrewDataSyncPayload(brews, recipes);
    }
}
