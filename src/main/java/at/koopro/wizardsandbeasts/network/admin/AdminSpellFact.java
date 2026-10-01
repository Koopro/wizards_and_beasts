package at.koopro.wizardsandbeasts.network.admin;

import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import io.netty.buffer.ByteBuf;
import org.jspecify.annotations.NullMarked;

/**
 * One read-only line of the spell detail panel: a label and the value the server read off the live spell.
 *
 * @param labelKey          {@code admin.wizards_and_beasts.spell_fact.<name>}
 * @param value             the value as text; a lang key when {@code valueTranslatable}
 * @param valueTranslatable whether {@link #value} is a lang key the client should resolve
 */
@NullMarked
public record AdminSpellFact(String labelKey, String value, boolean valueTranslatable) {

    public static void write(ByteBuf buf, AdminSpellFact fact) {
        PacketCodecUtils.writeString(buf, fact.labelKey);
        PacketCodecUtils.writeString(buf, fact.value);
        buf.writeBoolean(fact.valueTranslatable);
    }

    public static AdminSpellFact read(ByteBuf buf) {
        return new AdminSpellFact(PacketCodecUtils.readString(buf), PacketCodecUtils.readString(buf), buf.readBoolean());
    }
}
