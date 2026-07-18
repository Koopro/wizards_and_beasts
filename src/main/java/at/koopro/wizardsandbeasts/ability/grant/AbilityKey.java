package at.koopro.wizardsandbeasts.ability.grant;

import com.mojang.serialization.Codec;

import java.util.Locale;

/**
 * A namespaced identifier for a granted capability, independent of who grants it. Wraps a normalized
 * lowercase string so legacy free-string ability flags ({@code "duelist_spell_power"},
 * {@code "niffler_friend"}, heritage tags like {@code "no_wand"}) and future authored keys share one
 * key space. Purely a value object — no registry, no Minecraft bootstrap, so it is safe in unit tests
 * and in codec class-init.
 */
public record AbilityKey(String id) {

    /** Serializes as the bare normalized string. */
    public static final Codec<AbilityKey> CODEC = Codec.STRING.xmap(AbilityKey::of, AbilityKey::id);

    public AbilityKey {
        id = normalize(id);
    }

    public static AbilityKey of(String raw) {
        return new AbilityKey(raw);
    }

    private static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return id;
    }
}
