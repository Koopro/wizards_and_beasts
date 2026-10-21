package at.koopro.wizardsandbeasts.admin.operation;

import org.jspecify.annotations.NullMarked;

/** The operations the mod ships. One line per family; each family's class registers its own. */
@NullMarked
final class BuiltinOperations {

    private BuiltinOperations() {}

    static void registerAll() {
        CreatureOperations.registerAll();
        EconomyOperations.registerAll();
    }
}
