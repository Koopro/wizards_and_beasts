package at.koopro.wizardsandbeasts.client.render.outline;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.render.outline.OutlineEntry;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The blocks this client has been told to highlight, grouped the way the server sent them.
 *
 * <p>Purely server-driven. A highlight exists from its first {@code ADD} page until the server says
 * {@code REMOVE} or {@code CLEAR}, or until this client leaves the world or the dimension — the server drops
 * its record on those same events, so neither side waits on a packet the other will never send. The expiry
 * each highlight carries is read only to fade its last second, never to remove it.
 *
 * <p>Main thread only. Payloads arrive through {@code enqueueWork} and rendering reads on the same thread,
 * so plain maps are enough.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class ClientBlockOutlineState {

    /** Insertion-ordered so that where highlights overlap, the newer one's colour is the one drawn. */
    private static final Map<Integer, Highlight> HIGHLIGHTS = new LinkedHashMap<>();

    /** Every highlighted block with its merged outline; rebuilt lazily after any change, read every frame. */
    private static @Nullable Map<BlockPos, OutlineEntry> merged;

    private ClientBlockOutlineState() {}

    /** Adds positions to a highlight, creating it on its first page. Later pages keep the first page's outline. */
    public static void add(int highlightId, OutlineEntry outline, List<BlockPos> positions) {
        HIGHLIGHTS.computeIfAbsent(highlightId, id -> new Highlight(outline, new ArrayList<>()))
                .positions().addAll(positions);
        merged = null;
    }

    public static void remove(int highlightId) {
        if (HIGHLIGHTS.remove(highlightId) != null) {
            merged = null;
        }
    }

    public static void clear() {
        HIGHLIGHTS.clear();
        merged = null;
    }

    public static boolean isEmpty() {
        return HIGHLIGHTS.isEmpty();
    }

    /**
     * Each highlighted block once: the newest covering highlight's colour, and the latest covering expiry.
     *
     * <p>Once, because drawing overlapping highlights twice would stack their translucent fill into a darker
     * patch that means nothing. Latest expiry, because that is when the block actually stops being marked —
     * the older highlight is still live underneath — so the fade is timed to the real end, not the first one.
     */
    public static Map<BlockPos, OutlineEntry> blocks() {
        Map<BlockPos, OutlineEntry> view = merged;
        if (view == null) {
            Map<BlockPos, OutlineEntry> built = new LinkedHashMap<>();
            for (Highlight highlight : HIGHLIGHTS.values()) {
                OutlineEntry outline = highlight.outline();
                for (BlockPos pos : highlight.positions()) {
                    OutlineEntry older = built.get(pos);
                    long until = older == null ? outline.expiresAt() : Math.max(older.expiresAt(), outline.expiresAt());
                    built.put(pos, new OutlineEntry(outline.argb(), until));
                }
            }
            view = Collections.unmodifiableMap(built);
            merged = view;
        }
        return view;
    }

    static int highlightCount() {
        return HIGHLIGHTS.size();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    /**
     * {@code Clone} fires on every respawn; only a change of dimension invalidates positions. A death that
     * respawns in the same world keeps its highlights, as it does on the server.
     */
    @SubscribeEvent
    public static void onClone(ClientPlayerNetworkEvent.Clone event) {
        if (!event.getOldPlayer().level().dimension().equals(event.getNewPlayer().level().dimension())) {
            clear();
        }
    }

    private record Highlight(OutlineEntry outline, List<BlockPos> positions) {}
}
