package at.koopro.wizardsandbeasts.admin.history;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.AdminRejection;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The world's configuration history, kept across restarts: who changed which setting from what to what, and when.
 * Bounded ({@link #CAPACITY}, oldest dropped), stored newest first. Plain values only — ids, text, numbers.
 */
@NullMarked
public final class AdminHistoryData extends SavedData {

    public static final int CAPACITY = 1000;

    private record Stored(long sequence, String settingId, String kind, String oldValue, String newValue,
                          Optional<String> actorId, String actorName, long time, boolean applied,
                          Optional<String> rejection, boolean undone, Optional<String> group) {

        static final Codec<Stored> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("seq").forGetter(Stored::sequence),
                Codec.STRING.fieldOf("id").forGetter(Stored::settingId),
                Codec.STRING.fieldOf("kind").forGetter(Stored::kind),
                Codec.STRING.fieldOf("old").forGetter(Stored::oldValue),
                Codec.STRING.fieldOf("new").forGetter(Stored::newValue),
                Codec.STRING.optionalFieldOf("actor_id").forGetter(Stored::actorId),
                Codec.STRING.fieldOf("actor").forGetter(Stored::actorName),
                Codec.LONG.fieldOf("time").forGetter(Stored::time),
                Codec.BOOL.fieldOf("applied").forGetter(Stored::applied),
                Codec.STRING.optionalFieldOf("rejection").forGetter(Stored::rejection),
                Codec.BOOL.fieldOf("undone").forGetter(Stored::undone),
                Codec.STRING.optionalFieldOf("group").forGetter(Stored::group)
        ).apply(i, Stored::new));

        static Stored of(AdminChangeRecord r) {
            return new Stored(r.sequence(), r.settingId().toString(), r.kind().name(), r.oldValue(), r.newValue(),
                    Optional.ofNullable(r.actorId()).map(UUID::toString), r.actorName(), r.timestampMillis(),
                    r.applied(), Optional.ofNullable(r.rejection()).map(Enum::name), r.undone(), Optional.ofNullable(r.group()));
        }

        /** Null when the entry no longer reads (an enum renamed since) — dropped rather than guessed. */
        AdminChangeRecord toRecord() {
            Identifier id = Identifier.tryParse(settingId);
            AdminChangeRecord.Kind k;
            try {
                k = AdminChangeRecord.Kind.valueOf(kind);
            } catch (IllegalArgumentException e) {
                return null;
            }
            AdminRejection why = null;
            if (rejection.isPresent()) {
                try {
                    why = AdminRejection.valueOf(rejection.get());
                } catch (IllegalArgumentException e) {
                    why = AdminRejection.CONFLICT;
                }
            }
            UUID actor = null;
            if (actorId.isPresent()) {
                try {
                    actor = UUID.fromString(actorId.get());
                } catch (IllegalArgumentException ignored) {
                    // unreadable actor id: keep the name, drop the uuid
                }
            }
            return id == null ? null : new AdminChangeRecord(sequence, id, k, oldValue, newValue, actor, actorName, time,
                    applied, why, undone, group.orElse(null));
        }
    }

    public static final SavedDataType<AdminHistoryData> TYPE = new SavedDataType<>(
            WizardsAndBeastsMod.MODID + "_admin_history",
            AdminHistoryData::new,
            RecordCodecBuilder.create(i -> i.group(
                    Stored.CODEC.listOf().optionalFieldOf("records", List.of()).forGetter(d -> d.records),
                    Codec.LONG.optionalFieldOf("next", 1L).forGetter(d -> d.next)
            ).apply(i, AdminHistoryData::new)));

    /** Newest first. */
    private final List<Stored> records = new ArrayList<>();
    private long next = 1;

    private AdminHistoryData() {
    }

    private AdminHistoryData(List<Stored> stored, long next) {
        records.addAll(stored.subList(0, Math.min(stored.size(), CAPACITY)));
        this.next = next;
    }

    public static AdminHistoryData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public List<AdminChangeRecord> records() {
        List<AdminChangeRecord> out = new ArrayList<>();
        for (Stored stored : records) {
            AdminChangeRecord record = stored.toRecord();
            if (record != null) {
                out.add(record);
            }
        }
        return out;
    }

    public long next() {
        return next;
    }

    public void append(AdminChangeRecord record) {
        records.add(0, Stored.of(record));
        while (records.size() > CAPACITY) {
            records.remove(records.size() - 1);
        }
        next = Math.max(next, record.sequence() + 1);
        setDirty();
    }

    public void markUndone(long sequence) {
        for (int i = 0; i < records.size(); i++) {
            Stored s = records.get(i);
            if (s.sequence() == sequence) {
                records.set(i, new Stored(s.sequence(), s.settingId(), s.kind(), s.oldValue(), s.newValue(), s.actorId(),
                        s.actorName(), s.time(), s.applied(), s.rejection(), true, s.group()));
                setDirty();
                return;
            }
        }
    }
}
