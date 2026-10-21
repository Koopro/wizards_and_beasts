package at.koopro.wizardsandbeasts.network.owl;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.EnumMap;
import java.util.Map;

/**
 * S→C: syncs the player's O.W.L. and N.E.W.T. sheets and exam state to the client.
 *
 * @param nextSittingTick game time from which the candidate may sit the O.W.L. (again); 0 when they may sit now
 * @param careerOpen      whether a career may be chosen now
 * @param newtGrades      best N.E.W.T. grades
 * @param goblin          whether the candidate is a goblin — the one heritage a career's requirements depend on,
 *                        so the selection screen applies exactly the server's rule
 */
public record OWLDataSyncPayload(
        @NonNull Map<OWLSubject, OWLGrade> grades,
        boolean examTaken,
        long nextSittingTick,
        boolean careerOpen,
        @NonNull Map<OWLSubject, OWLGrade> newtGrades,
        boolean goblin
) implements CustomPacketPayload {

    public static final Type<OWLDataSyncPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "owl_data_sync"));

    public static final StreamCodec<ByteBuf, OWLDataSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public OWLDataSyncPayload decode(ByteBuf buf) {
            boolean examTaken = buf.readBoolean();
            long nextSittingTick = buf.readLong();
            boolean careerOpen = buf.readBoolean();
            boolean goblin = buf.readBoolean();
            Map<OWLSubject, OWLGrade> grades = readGrades(buf);
            Map<OWLSubject, OWLGrade> newt = readGrades(buf);
            return new OWLDataSyncPayload(grades, examTaken, nextSittingTick, careerOpen, newt, goblin);
        }

        @Override
        public void encode(ByteBuf buf, OWLDataSyncPayload payload) {
            buf.writeBoolean(payload.examTaken());
            buf.writeLong(payload.nextSittingTick());
            buf.writeBoolean(payload.careerOpen());
            buf.writeBoolean(payload.goblin());
            writeGrades(buf, payload.grades());
            writeGrades(buf, payload.newtGrades());
        }
    };

    private static Map<OWLSubject, OWLGrade> readGrades(ByteBuf buf) {
        int size = buf.readByte();
        Map<OWLSubject, OWLGrade> grades = new EnumMap<>(OWLSubject.class);
        OWLSubject[] subjects = OWLSubject.values();
        OWLGrade[] gradeValues = OWLGrade.values();
        if (size < 0 || size > subjects.length) {
            throw new IllegalArgumentException("grade sheet too long: " + size);
        }
        for (int i = 0; i < size; i++) {
            int subjectOrd = buf.readByte();
            int gradeOrd = buf.readByte();
            if (subjectOrd >= 0 && subjectOrd < subjects.length
                    && gradeOrd >= 0 && gradeOrd < gradeValues.length) {
                grades.put(subjects[subjectOrd], gradeValues[gradeOrd]);
            }
        }
        return grades;
    }

    private static void writeGrades(ByteBuf buf, Map<OWLSubject, OWLGrade> grades) {
        buf.writeByte(grades.size());
        grades.forEach((subject, grade) -> {
            buf.writeByte(subject.ordinal());
            buf.writeByte(grade.ordinal());
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
