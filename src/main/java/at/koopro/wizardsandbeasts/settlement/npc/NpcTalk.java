package at.koopro.wizardsandbeasts.settlement.npc;

import at.koopro.wizardsandbeasts.artefact.erised.ErisedService;
import at.koopro.wizardsandbeasts.artefact.erised.Gazer;
import at.koopro.wizardsandbeasts.location.LocationDefinition;
import at.koopro.wizardsandbeasts.location.LocationPresence;
import at.koopro.wizardsandbeasts.location.LocationProgression;
import at.koopro.wizardsandbeasts.location.LocationView;
import at.koopro.wizardsandbeasts.location.Locations;
import at.koopro.wizardsandbeasts.settlement.SettlementNames;
import at.koopro.wizardsandbeasts.util.PlayerScopedState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * What a settlement's people say: one greeting and a few topics at the head of their location's board. Conditions are
 * read on the server every time, so the client is only ever shown what it would be allowed to ask.
 *
 * <p>Heritage and the like change what is said and nothing else. A service is never refused for what someone is.
 */
@NullMarked
public final class NpcTalk {

    /** The most topics shown at once, so the board stays a shop with a person in it. */
    public static final int MAX_TOPICS = 4;

    /** The topic last asked, as {@code location|topic}: the reply stays on the board until the player moves on. */
    private static final PlayerScopedState<String> ASKED = PlayerScopedState.create("settlement_topic");

    private NpcTalk() {}

    /** A greeting {@code listener} can hear, by weight and stable for the day; null when nothing applies. */
    public static NpcRole.@Nullable Line greet(NpcRole role, Gazer listener, long seed) {
        List<NpcRole.Line> eligible = role.greetings().stream().filter(line -> line.heardBy(listener)).toList();
        if (eligible.isEmpty()) {
            return null;
        }
        int total = eligible.stream().mapToInt(NpcRole.Line::weight).sum();
        int roll = new Random(seed).nextInt(total);
        for (NpcRole.Line line : eligible) {
            roll -= line.weight();
            if (roll < 0) {
                return line;
            }
        }
        return eligible.get(eligible.size() - 1);
    }

    /** The topics {@code listener} may ask, the first {@link #MAX_TOPICS} that apply. */
    public static List<NpcRole.Topic> topics(NpcRole role, Gazer listener) {
        return role.topics().stream().filter(topic -> topic.openTo(listener)).limit(MAX_TOPICS).toList();
    }

    /** The talk section of a board, or {@link LocationView.Talk#NONE} when the host is not one of the people. */
    public static LocationView.Talk section(ServerPlayer player, Identifier location, LocationDefinition def) {
        Entity host = LocationPresence.host(player, def);
        if (!(host instanceof SettlementNpcEntity npc) || npc.role() == null) {
            return LocationView.Talk.NONE;
        }
        NpcRole role = npc.role();
        Gazer listener = ErisedService.gazer(player);
        Component site = SettlementNames.of(player, npc);
        long seed = player.getUUID().getLeastSignificantBits() ^ (npc.level().getGameTime() / 24000L);
        NpcRole.Line greeting = greet(role, listener, seed);
        List<LocationView.TopicLine> lines = new ArrayList<>();
        for (NpcRole.Topic topic : topics(role, listener)) {
            lines.add(new LocationView.TopicLine(topic.id(), Component.translatable(topic.label())));
        }
        Component reply = Component.empty();
        String asked = ASKED.get(player.getUUID());
        if (asked != null) {
            for (NpcRole.Topic topic : topics(role, listener)) {
                if (asked.equals(location + "|" + topic.id())) {
                    reply = Component.translatable(topic.reply(), site);
                }
            }
        }
        return new LocationView.Talk(npc.getDisplayName().copy(),
                greeting == null ? Component.empty() : Component.translatable(greeting.text(), site), lines, reply);
    }

    /**
     * A player asked about {@code topicId}. Everything is judged again here: an ineligible or unknown topic, or a
     * player no longer at the counter, gets the board as it stands and no reply.
     */
    public static void ask(ServerPlayer player, Identifier location, String topicId) {
        LocationDefinition def = Locations.open(location);
        if (def == null) {
            return;
        }
        ASKED.remove(player.getUUID());
        if (LocationPresence.host(player, def) instanceof SettlementNpcEntity npc && npc.role() != null) {
            for (NpcRole.Topic topic : topics(npc.role(), ErisedService.gazer(player))) {
                if (topic.id().equals(topicId)) {
                    ASKED.put(player.getUUID(), location + "|" + topicId);
                }
            }
        }
        LocationProgression.sync(player, location, false);
    }
}
