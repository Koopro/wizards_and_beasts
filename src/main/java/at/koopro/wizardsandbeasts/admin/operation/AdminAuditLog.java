package at.koopro.wizardsandbeasts.admin.operation;

import net.minecraft.core.UUIDUtil;
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
 * The world's record of {@link AdminOperation}s and of the other one-shot admin actions that had only a server log
 * line before (market resets, test-spawn cleanups, beam preset deletes): who, what, on what, when, result. Kept
 * across restarts, bounded ({@link #CAPACITY}, oldest dropped), newest first. Setting changes keep their own
 * history ({@code AdminHistoryData}) and player actions theirs ({@code PlayerActionLog}); the {@code audit} report
 * reads all three as one timeline.
 *
 * <p>Like the player log, only attempts by someone holding the action's authority are written, so nobody without it
 * can push real entries out.
 */
@NullMarked
public final class AdminAuditLog extends SavedData {

    public static final int CAPACITY = 2000;

    /**
     * @param result {@code ok}, {@code refused:<code>} or {@code failed:<reason>}
     */
    public record Entry(long sequence, long timeMillis, @Nullable UUID adminId, String adminName, String section,
                        String operation, String subject, String argument, String result, String detail) {

        public boolean ok() {
            return "ok".equals(result);
        }

        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("seq").forGetter(Entry::sequence),
                Codec.LONG.fieldOf("time").forGetter(Entry::timeMillis),
                UUIDUtil.STRING_CODEC.optionalFieldOf("admin_id").forGetter(e -> Optional.ofNullable(e.adminId())),
                Codec.STRING.fieldOf("admin").forGetter(Entry::adminName),
                Codec.STRING.optionalFieldOf("section", "").forGetter(Entry::section),
                Codec.STRING.fieldOf("operation").forGetter(Entry::operation),
                Codec.STRING.optionalFieldOf("subject", "").forGetter(Entry::subject),
                Codec.STRING.optionalFieldOf("argument", "").forGetter(Entry::argument),
                Codec.STRING.fieldOf("result").forGetter(Entry::result),
                Codec.STRING.optionalFieldOf("detail", "").forGetter(Entry::detail)
        ).apply(i, (seq, time, admin, adminName, section, operation, subject, argument, result, detail) ->
                new Entry(seq, time, admin.orElse(null), adminName, section, operation, subject, argument, result, detail)));
    }

    public static final SavedDataType<AdminAuditLog> TYPE = new SavedDataType<>(
            WizardsAndBeastsMod.MODID + "_admin_audit",
            AdminAuditLog::new,
            RecordCodecBuilder.create(i -> i.group(
                    Entry.CODEC.listOf().optionalFieldOf("entries", List.of()).forGetter(d -> d.entries),
                    Codec.LONG.optionalFieldOf("next", 1L).forGetter(d -> d.next)
            ).apply(i, AdminAuditLog::new)));

    /** Newest first. */
    private final List<Entry> entries = new ArrayList<>();
    private long next = 1;

    private AdminAuditLog() {
    }

    private AdminAuditLog(List<Entry> stored, long next) {
        entries.addAll(stored.subList(0, Math.min(stored.size(), CAPACITY)));
        this.next = next;
    }

    public static AdminAuditLog get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** Records one attempt; returns the entry (its sequence goes back to the caller). */
    public Entry append(@Nullable UUID adminId, String adminName, String section, String operation, String subject,
                        String argument, String result, String detail) {
        Entry entry = new Entry(next++, System.currentTimeMillis(), adminId, clip(adminName, 64), clip(section, 32),
                clip(operation, 64), clip(subject, 96), clip(argument, 128), clip(result, 128), clip(detail, 256));
        entries.add(0, entry);
        while (entries.size() > CAPACITY) {
            entries.remove(entries.size() - 1);
        }
        setDirty();
        return entry;
    }

    /** Newest first, at most {@code limit}. */
    public List<Entry> recent(int limit) {
        return List.copyOf(entries.subList(0, Math.min(limit, entries.size())));
    }

    private static String clip(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
