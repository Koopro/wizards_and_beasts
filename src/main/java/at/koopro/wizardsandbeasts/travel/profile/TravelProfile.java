package at.koopro.wizardsandbeasts.travel.profile;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Everything about how a player gets about, worded on the server for the Character Sheet's Travel tab
 * ({@link TravelProfiles#build}): their brooms, their Apparition, their Floo address book, and the flying work in hand.
 *
 * <p>Lines may carry one {@link Action} — forget a destination, memorise this spot, sit the test, set a hearth's
 * toll. The client only echoes an action back ({@code TravelActionC2SPayload}); the server re-checks everything about
 * it, so a line is an offer, never a permission.
 *
 * @param sections lines per section, in {@link Section} order
 */
@NullMarked
public record TravelProfile(Map<Section, List<Line>> sections) {

    public static final TravelProfile EMPTY = new TravelProfile(Map.of());

    public enum Section {
        BROOMS, APPARITION, FLOO, ACTIVITIES;

        public String translationKey() {
            return "gui.wizards_and_beasts.travel_tab." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** How a line reads at a glance. */
    public enum Tone { GOOD, BAD, MIXED, NEUTRAL }

    /** What clicking a line's button asks the server to do. */
    public enum ActionKind {
        NONE, MARK_HERE, FORGET_POINT, SIT_TEST, CYCLE_TOLL;

        public static ActionKind of(int ordinal) {
            ActionKind[] all = values();
            return ordinal >= 0 && ordinal < all.length ? all[ordinal] : NONE;
        }
    }

    /**
     * @param kind  what it does
     * @param arg   which thing it is about (a destination name, a hearth address); empty when none
     * @param label the button's text
     */
    public record Action(ActionKind kind, String arg, Component label) {
        public static final Action NONE = new Action(ActionKind.NONE, "", Component.empty());
    }

    public record Line(Component text, Component detail, Tone tone, Action action) {}

    public List<Line> section(Section section) {
        return sections.getOrDefault(section, List.of());
    }

    public boolean isEmpty() {
        return sections.values().stream().allMatch(List::isEmpty);
    }

    /** Collects lines by section. */
    public static final class Builder {
        private final Map<Section, List<Line>> sections = new EnumMap<>(Section.class);

        public Builder add(Section section, Component text, Component detail, Tone tone, Action action) {
            sections.computeIfAbsent(section, s -> new ArrayList<>()).add(new Line(text, detail, tone, action));
            return this;
        }

        public Builder add(Section section, Component text, Component detail, Tone tone) {
            return add(section, text, detail, tone, Action.NONE);
        }

        public Builder add(Section section, Component text, Tone tone) {
            return add(section, text, Component.empty(), tone, Action.NONE);
        }

        public TravelProfile build() {
            Map<Section, List<Line>> out = new EnumMap<>(Section.class);
            sections.forEach((k, v) -> out.put(k, List.copyOf(v)));
            return new TravelProfile(out);
        }
    }

    // -- the wire ---------------------------------------------------------------------------------------

    private static final StreamCodec<ByteBuf, Component> TEXT = ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC;
    /** A bound on lines per section, so a crafted or buggy profile cannot balloon a client. */
    public static final int MAX_LINES = 48;

    public static final StreamCodec<ByteBuf, TravelProfile> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public TravelProfile decode(ByteBuf buf) {
            Map<Section, List<Line>> sections = new EnumMap<>(Section.class);
            int count = Math.min(buf.readByte(), Section.values().length);
            for (int i = 0; i < count; i++) {
                int ordinal = buf.readByte();
                int lines = Math.min(ByteBufCodecs.VAR_INT.decode(buf), MAX_LINES);
                List<Line> list = new ArrayList<>(lines);
                for (int l = 0; l < lines; l++) {
                    Component text = TEXT.decode(buf);
                    Component detail = TEXT.decode(buf);
                    int tone = buf.readByte();
                    ActionKind kind = ActionKind.of(buf.readByte());
                    String arg = ByteBufCodecs.STRING_UTF8.decode(buf);
                    Component label = TEXT.decode(buf);
                    Tone[] tones = Tone.values();
                    list.add(new Line(text, detail, tone >= 0 && tone < tones.length ? tones[tone] : Tone.NEUTRAL,
                            new Action(kind, arg, label)));
                }
                if (ordinal >= 0 && ordinal < Section.values().length) {
                    sections.put(Section.values()[ordinal], List.copyOf(list));
                }
            }
            return new TravelProfile(sections);
        }

        @Override
        public void encode(ByteBuf buf, TravelProfile profile) {
            buf.writeByte(profile.sections().size());
            for (Map.Entry<Section, List<Line>> entry : profile.sections().entrySet()) {
                buf.writeByte(entry.getKey().ordinal());
                List<Line> lines = entry.getValue().subList(0, Math.min(entry.getValue().size(), MAX_LINES));
                ByteBufCodecs.VAR_INT.encode(buf, lines.size());
                for (Line line : lines) {
                    TEXT.encode(buf, line.text());
                    TEXT.encode(buf, line.detail());
                    buf.writeByte(line.tone().ordinal());
                    buf.writeByte(line.action().kind().ordinal());
                    ByteBufCodecs.STRING_UTF8.encode(buf, line.action().arg());
                    TEXT.encode(buf, line.action().label());
                }
            }
        }
    };
}
