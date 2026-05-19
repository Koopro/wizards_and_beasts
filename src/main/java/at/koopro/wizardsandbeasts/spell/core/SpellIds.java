package at.koopro.wizardsandbeasts.spell.core;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;

public final class SpellIds {
    private SpellIds() {}

    public static boolean matches(String id, String baseId) {
        if (id == null || baseId == null) return false;
        return baseId.equals(id) || (WizardsAndBeastsMod.MODID + ":" + baseId).equals(id);
    }

    public static String namespaced(String baseId) {
        return WizardsAndBeastsMod.MODID + ":" + baseId;
    }
}
