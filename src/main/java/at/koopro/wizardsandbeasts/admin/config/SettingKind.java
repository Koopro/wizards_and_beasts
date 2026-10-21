package at.koopro.wizardsandbeasts.admin.config;

import org.jspecify.annotations.NullMarked;

/**
 * The shape of a setting's value as the UI sees it — which control draws it.
 *
 * <p>Sent on the wire by {@link #name()}. A future custom {@link SettingType} picks the kind whose control
 * suits it (a colour could be {@link #STRING} with a pattern until it earns its own kind); the client falls
 * back to a text field for anything it cannot draw, so adding a kind never breaks an older screen.
 */
@NullMarked
public enum SettingKind {
    BOOLEAN,
    INTEGER,
    DOUBLE,
    ENUM,
    STRING;

    public static SettingKind byName(String name) {
        for (SettingKind kind : values()) {
            if (kind.name().equals(name)) {
                return kind;
            }
        }
        return STRING;
    }
}
