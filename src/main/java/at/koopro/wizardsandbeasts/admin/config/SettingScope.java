package at.koopro.wizardsandbeasts.admin.config;

import org.jspecify.annotations.NullMarked;

/**
 * Where a setting's value takes effect, which decides whether the server may claim authority over it.
 *
 * <p>This mod keeps one COMMON config file, so a key's location in {@code Config} says nothing about which
 * side reads it. {@link #CLIENT} marks the ones read on each player's own game — a cosmetic, an
 * accessibility choice, or a value a client-authoritative system (broom flight) reads locally. Writing the
 * server's copy of those would change nothing on a dedicated server while the panel reported success, so the
 * service refuses it and the UI shows them read-only.
 */
@NullMarked
public enum SettingScope {
    /** Read by server logic. An admin change is authoritative for every player. */
    SERVER,
    /** Read on each player's client. Visible in the panel, never writable through it. */
    CLIENT
}
