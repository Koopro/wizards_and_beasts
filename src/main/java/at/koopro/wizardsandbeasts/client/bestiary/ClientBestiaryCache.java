package at.koopro.wizardsandbeasts.client.bestiary;

import at.koopro.wizardsandbeasts.bestiary.data.PlayerBestiaryData;

import java.util.HashMap;

public final class ClientBestiaryCache {
    private static PlayerBestiaryData data = new PlayerBestiaryData(new HashMap<>());

    private ClientBestiaryCache() {}

    public static PlayerBestiaryData get() { return data; }
    public static void set(PlayerBestiaryData next) { data = next; }
    public static void clear() { data = new PlayerBestiaryData(new HashMap<>()); }
}
