package at.koopro.wizardsandbeasts.network.owl;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.owl.ClientOWLCache;
import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;

import java.util.EnumMap;
import java.util.Map;

/** S→C: syncs the player's OWL grade map and exam state to the client. */
public record OWLDataSyncPayload(
        @NonNull Map<OWLSubject, OWLGrade> grades,
        boolean examTaken
) implements CustomPacketPayload {

    public static final Type<OWLDataSyncPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "owl_data_sync"));

    public static final StreamCodec<ByteBuf, OWLDataSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public OWLDataSyncPayload decode(ByteBuf buf) {
            boolean examTaken = buf.readBoolean();
            int size = buf.readInt();
            Map<OWLSubject, OWLGrade> grades = new EnumMap<>(OWLSubject.class);
            OWLSubject[] subjects = OWLSubject.values();
            OWLGrade[] gradeValues = OWLGrade.values();
            for (int i = 0; i < size; i++) {
                int subjectOrd = buf.readByte();
                int gradeOrd = buf.readByte();
                if (subjectOrd >= 0 && subjectOrd < subjects.length
                        && gradeOrd >= 0 && gradeOrd < gradeValues.length) {
                    grades.put(subjects[subjectOrd], gradeValues[gradeOrd]);
                }
            }
            return new OWLDataSyncPayload(grades, examTaken);
        }

        @Override
        public void encode(ByteBuf buf, OWLDataSyncPayload payload) {
            buf.writeBoolean(payload.examTaken());
            buf.writeInt(payload.grades().size());
            payload.grades().forEach((subject, grade) -> {
                buf.writeByte(subject.ordinal());
                buf.writeByte(grade.ordinal());
            });
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleClient(OWLDataSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientOWLCache.setOWLData(payload.grades(), payload.examTaken()));
    }
}
