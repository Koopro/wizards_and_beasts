package at.koopro.wizardsandbeasts.heritage.identity;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * What a character's heritage and condition mean, in words, worded on the server for the Character Sheet's Heritage
 * tab ({@link HeritageProfiles#build}).
 *
 * <p>Six answers, one per {@link Section}: what they are, what they can do, how the world treats them, what it costs
 * them, how they grow, and how they are right now. Every line names something a rule in the game enforces; nothing
 * here is computed on the client, and nothing here is anyone else's — the profile goes only to its owner, so a
 * condition stays private.
 *
 * @param title    heritage, lineage and condition as one heading
 * @param sections lines per section, in {@link Section} order; an empty section is left out on the sheet
 */
@NullMarked
public record HeritageProfile(Component title, Map<Section, List<Line>> sections) {

    public static final HeritageProfile EMPTY = new HeritageProfile(Component.empty(), Map.of());

    /** The six questions, in the order the tab asks them. */
    public enum Section {
        ADVANTAGES, DISADVANTAGES, ACTIVE, SOCIAL, PROGRESSION, STATE;

        public String translationKey() {
            return "gui.wizards_and_beasts.heritage_tab." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** How a line reads at a glance. */
    public enum Tone { GOOD, BAD, MIXED, NEUTRAL }

    /**
     * @param text   the line itself
     * @param detail one sentence behind it, or empty
     * @param tone   its colour
     */
    public record Line(Component text, Component detail, Tone tone) {
        public static Line of(Component text, Component detail, Tone tone) {
            return new Line(text, detail, tone);
        }
    }

    public List<Line> section(Section section) {
        return sections.getOrDefault(section, List.of());
    }

    public boolean isEmpty() {
        return sections.values().stream().allMatch(List::isEmpty);
    }

    // -- building ---------------------------------------------------------------------------------------

    /** Collects lines by section; {@link HeritageProfiles} and its contributors write into one. */
    public static final class Builder {
        private final Map<Section, List<Line>> sections = new EnumMap<>(Section.class);
        private Component title = Component.empty();

        public Builder title(Component title) {
            this.title = title;
            return this;
        }

        public Builder add(Section section, Component text, Component detail, Tone tone) {
            sections.computeIfAbsent(section, s -> new ArrayList<>()).add(new Line(text, detail, tone));
            return this;
        }

        public Builder add(Section section, Component text, Tone tone) {
            return add(section, text, Component.empty(), tone);
        }

        public HeritageProfile build() {
            Map<Section, List<Line>> out = new EnumMap<>(Section.class);
            sections.forEach((k, v) -> out.put(k, List.copyOf(v)));
            return new HeritageProfile(title, out);
        }
    }

    // -- the wire ---------------------------------------------------------------------------------------

    private static final StreamCodec<ByteBuf, Component> TEXT = ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC;
    /** A bound on lines per section, so a crafted or buggy profile cannot balloon a client. */
    public static final int MAX_LINES = 48;

    public static final StreamCodec<ByteBuf, HeritageProfile> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public HeritageProfile decode(ByteBuf buf) {
            Component title = TEXT.decode(buf);
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
                    Tone[] tones = Tone.values();
                    list.add(new Line(text, detail, tone >= 0 && tone < tones.length ? tones[tone] : Tone.NEUTRAL));
                }
                if (ordinal >= 0 && ordinal < Section.values().length) {
                    sections.put(Section.values()[ordinal], List.copyOf(list));
                }
            }
            return new HeritageProfile(title, sections);
        }

        @Override
        public void encode(ByteBuf buf, HeritageProfile profile) {
            TEXT.encode(buf, profile.title());
            buf.writeByte(profile.sections().size());
            for (Map.Entry<Section, List<Line>> entry : profile.sections().entrySet()) {
                buf.writeByte(entry.getKey().ordinal());
                List<Line> lines = entry.getValue().subList(0, Math.min(entry.getValue().size(), MAX_LINES));
                ByteBufCodecs.VAR_INT.encode(buf, lines.size());
                for (Line line : lines) {
                    TEXT.encode(buf, line.text());
                    TEXT.encode(buf, line.detail());
                    buf.writeByte(line.tone().ordinal());
                }
            }
        }
    };
}
