package at.koopro.wizardsandbeasts.admin.player;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The world's record of what administrators did to players: player, action, administrator, time, result. Kept across
 * restarts, bounded ({@link #CAPACITY}, oldest dropped), newest first. Every attempt by an authorised administrator is
 * written — applied, refused or failed and rolled back. Attempts by someone without authority go to the server log
 * only, so nobody without authority can push real entries out of this one.
 */
@NullMarked
public final class PlayerActionLog extends SavedData {

    public static final int CAPACITY = 2000;

    /**
     * One entry.
     *
     * @param adminId  null for the console
     * @param argument what the action was given (a spell id, an amount); empty when nothing
     * @param result   {@code ok}, {@code refused:<reason>} or {@code failed:<reason>} (rolled back)
     * @param detail   what changed, in a few words ("3 effects cleared", "galleons 10 → 15")
     */
    public record Entry(long sequence, long timeMillis, @Nullable UUID adminId, String adminName, UUID playerId,
                        String playerName, String action, String argument, String result, String detail) {

        public boolean ok() {
            return "ok".equals(result);
        }

        private static final Codec<UUID> UUID_TEXT = Codec.STRING.comapFlatMap(text -> {
            try {
                return com.mojang.serialization.DataResult.success(UUID.fromString(text));
            } catch (IllegalArgumentException e) {
                return com.mojang.serialization.DataResult.error(() -> "bad uuid " + text);
            }
        }, UUID::toString);

        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("seq").forGetter(Entry::sequence),
                Codec.LONG.fieldOf("time").forGetter(Entry::timeMillis),
                UUID_TEXT.optionalFieldOf("admin_id").forGetter(e -> Optional.ofNullable(e.adminId())),
                Codec.STRING.fieldOf("admin").forGetter(Entry::adminName),
                UUID_TEXT.fieldOf("player_id").forGetter(Entry::playerId),
                Codec.STRING.fieldOf("player").forGetter(Entry::playerName),
                Codec.STRING.fieldOf("action").forGetter(Entry::action),
                Codec.STRING.optionalFieldOf("argument", "").forGetter(Entry::argument),
                Codec.STRING.fieldOf("result").forGetter(Entry::result),
                Codec.STRING.optionalFieldOf("detail", "").forGetter(Entry::detail)
        ).apply(i, (seq, time, admin, adminName, player, playerName, action, argument, result, detail) ->
                new Entry(seq, time, admin.orElse(null), adminName, player, playerName, action, argument, result, detail)));
    }

    public static final SavedDataType<PlayerActionLog> TYPE = new SavedDataType<>(
            WizardsAndBeastsMod.MODID + "_admin_player_log",
            PlayerActionLog::new,
            RecordCodecBuilder.create(i -> i.group(
                    Entry.CODEC.listOf().optionalFieldOf("entries", List.of()).forGetter(d -> d.entries),
                    Codec.LONG.optionalFieldOf("next", 1L).forGetter(d -> d.next)
            ).apply(i, PlayerActionLog::new)));

    /** Newest first. */
    private final List<Entry> entries = new ArrayList<>();
    private long next = 1;

    private PlayerActionLog() {
    }

    private PlayerActionLog(List<Entry> stored, long next) {
        entries.addAll(stored.subList(0, Math.min(stored.size(), CAPACITY)));
        this.next = next;
    }

    public static PlayerActionLog get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    Entry append(@Nullable UUID adminId, String adminName, UUID playerId, String playerName, String action,
                 String argument, String result, String detail) {
        Entry entry = new Entry(next++, System.currentTimeMillis(), adminId, clip(adminName, 64), playerId,
                clip(playerName, 64), action, clip(argument, 128), clip(result, 128), clip(detail, 256));
        entries.add(0, entry);
        while (entries.size() > CAPACITY) {
            entries.remove(entries.size() - 1);
        }
        setDirty();
        return entry;
    }

    /** Newest first, at most {@code limit}; only {@code player}'s when it is given. */
    public List<Entry> recent(@Nullable UUID player, int limit) {
        List<Entry> out = new ArrayList<>();
        for (Entry entry : entries) {
            if (player == null || entry.playerId().equals(player)) {
                out.add(entry);
                if (out.size() >= limit) {
                    break;
                }
            }
        }
        return out;
    }

    private static String clip(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
