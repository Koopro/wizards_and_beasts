package at.koopro.wizardsandbeasts.util;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;

/**
 * One-way namespace migration helpers for legacy saved ids.
 */
public final class NamespaceMigration {
    public static final String LEGACY_NAMESPACE = "WizardsAndBeastsMod";

    private NamespaceMigration() {}

    public static String remapLegacyId(String id) {
        if (id == null || id.isBlank()) {
            return id;
        }
        String prefix = LEGACY_NAMESPACE + ":";
        if (id.startsWith(prefix)) {
            return WizardsAndBeastsMod.MODID + ":" + id.substring(prefix.length());
        }
        return id;
    }
}
