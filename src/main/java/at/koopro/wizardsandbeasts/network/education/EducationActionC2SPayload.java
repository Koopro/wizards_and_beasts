package at.koopro.wizardsandbeasts.network.education;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.education.EducationService;
import at.koopro.wizardsandbeasts.education.ExamSittings;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.network.owl.RequestOWLExamPacket;
import at.koopro.wizardsandbeasts.owl.Qualification;
import at.koopro.wizardsandbeasts.util.PlayerScopedState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * C→S: a button on the Education tab or the examination desk. Carries an intent only; every check — the module,
 * the desk within reach, the resit wait, the seat, the clock — is made here, where it is honoured.
 *
 * @param answers the written paper's chosen options, for {@link Action#ANSWER}; empty otherwise
 */
@NullMarked
public record EducationActionC2SPayload(Action action, List<Integer> answers) implements CustomPacketPayload {

    public enum Action { VIEW, BEGIN_OWL, BEGIN_NEWT, ANSWER, HAND_IN }

    /** More than any paper sets; a longer list is not an answer sheet and is refused at decode. */
    private static final int MAX_ANSWERS = 8;
    /** Ticks between handled actions per player: a held mouse button is not a stream of sittings. */
    private static final long MIN_TICKS_BETWEEN = 4;
    private static final PlayerScopedState<Long> LAST_ACTION = PlayerScopedState.create("education_action");

    public static final Type<EducationActionC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "education_action"));

    public static final StreamCodec<ByteBuf, EducationActionC2SPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public EducationActionC2SPayload decode(ByteBuf buf) {
            int ordinal = buf.readByte();
            Action[] actions = Action.values();
            Action action = ordinal >= 0 && ordinal < actions.length ? actions[ordinal] : Action.VIEW;
            int size = ByteBufCodecs.VAR_INT.decode(buf);
            if (size < 0 || size > MAX_ANSWERS) {
                throw new IllegalArgumentException("answer sheet too long: " + size);
            }
            List<Integer> answers = new ArrayList<>(size);
            for (int i = 0; i < size; i++) answers.add((int) buf.readByte());
            return new EducationActionC2SPayload(action, answers);
        }

        @Override
        public void encode(ByteBuf buf, EducationActionC2SPayload payload) {
            buf.writeByte(payload.action().ordinal());
            int size = Math.min(payload.answers().size(), MAX_ANSWERS);
            ByteBufCodecs.VAR_INT.encode(buf, size);
            for (int i = 0; i < size; i++) buf.writeByte(payload.answers().get(i));
        }
    };

    public static EducationActionC2SPayload of(Action action) {
        return new EducationActionC2SPayload(action, List.of());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(EducationActionC2SPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            long now = player.level().getGameTime();
            Long last = LAST_ACTION.get(player);
            if (last != null && now - last < MIN_TICKS_BETWEEN && now >= last && payload.action() != Action.VIEW) {
                return;
            }
            LAST_ACTION.put(player, now);
            switch (payload.action()) {
                case VIEW -> EducationService.sync(player);
                case BEGIN_OWL -> begin(player, Qualification.OWL);
                case BEGIN_NEWT -> begin(player, Qualification.NEWT);
                case ANSWER -> ExamSittings.answer(player, payload.answers());
                case HAND_IN -> {
                    // Handing in early is done at the desk, like opening the paper.
                    if (RequestOWLExamPacket.atExaminationDesk(player)) {
                        ExamSittings.finish(player);
                    }
                }
            }
        });
    }

    private static void begin(ServerPlayer player, Qualification level) {
        ExamSittings.Refusal refusal = ExamSittings.begin(player, level, true);
        if (refusal != null) {
            PlayerFeedback.toast(player, NoticeKind.FAIL, Component.translatable(refusal.translationKey()));
            EducationService.sync(player);
        }
    }
}
