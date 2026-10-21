package at.koopro.wizardsandbeasts.network.bestiary;

import at.koopro.wizardsandbeasts.network.PayloadBroadcast;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.bestiary.BestiaryEntry;
import at.koopro.wizardsandbeasts.bestiary.BestiaryEntryRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

import java.util.List;

/**
 * Server → client sync of the full bestiary entry list, mirroring {@code SyncHandbookPayload}.
 * The client screen must not read the server-populated {@link BestiaryEntryRegistry} directly —
 * that only works in a shared single-player JVM; on a dedicated server the client-side registry
 * is empty. Clients store the synced list via {@link BestiaryEntryRegistry#setClientEntries}.
 */
public record SyncBestiaryEntriesPayload(List<BestiaryEntry> entries,
                                         at.koopro.wizardsandbeasts.bestiary.journal.JournalLayout layout,
                                         java.util.Map<Identifier, at.koopro.wizardsandbeasts.bestiary.journal.JournalFacts> facts)
        implements CustomPacketPayload {

    /**
     * The entries, the journal's page layout, and each entry's server-derived facts (temperament, ecology, feeding,
     * harvest) — the client holds no creature, ecology, bond or harvest registries to derive them itself.
     */
    public static final com.mojang.serialization.Codec<SyncBestiaryEntriesPayload> CODEC =
            com.mojang.serialization.codecs.RecordCodecBuilder.create(instance -> instance.group(
                    BestiaryEntry.CODEC.listOf().fieldOf("entries").forGetter(SyncBestiaryEntriesPayload::entries),
                    at.koopro.wizardsandbeasts.bestiary.journal.JournalLayout.CODEC.fieldOf("layout")
                            .forGetter(SyncBestiaryEntriesPayload::layout),
                    com.mojang.serialization.Codec.unboundedMap(Identifier.CODEC,
                                    at.koopro.wizardsandbeasts.bestiary.journal.JournalFacts.CODEC)
                            .fieldOf("facts").forGetter(SyncBestiaryEntriesPayload::facts)
            ).apply(instance, SyncBestiaryEntriesPayload::new));

    /** The server's current book: every entry, the layout, and the facts derived for each. */
    public static SyncBestiaryEntriesPayload current() {
        List<BestiaryEntry> all = List.copyOf(BestiaryEntryRegistry.getAll());
        java.util.Map<Identifier, at.koopro.wizardsandbeasts.bestiary.journal.JournalFacts> facts = new java.util.HashMap<>();
        for (BestiaryEntry entry : all) {
            facts.put(entry.id(), at.koopro.wizardsandbeasts.bestiary.journal.JournalFacts.compute(entry));
        }
        return new SyncBestiaryEntriesPayload(all, at.koopro.wizardsandbeasts.bestiary.journal.JournalBook.serverLayout(), facts);
    }
    public static final Type<SyncBestiaryEntriesPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "sync_bestiary_entries"));

    public static final StreamCodec<ByteBuf, SyncBestiaryEntriesPayload> STREAM_CODEC =
            ByteBufCodecs.fromCodec(CODEC);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Fires on player join and on {@code /reload} (server datapack sync), pushing the current
     * entry list. On {@code /reload} {@link OnDatapackSyncEvent#getPlayer()} is {@code null}, so
     * every player is re-synced.
     */
    @EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
    public static final class SyncOnDatapack {
        @SubscribeEvent
        public static void onDatapackSync(OnDatapackSyncEvent event) {
            SyncBestiaryEntriesPayload payload = current();
            PayloadBroadcast.toSyncTarget(event, payload);
        }
    }
}
