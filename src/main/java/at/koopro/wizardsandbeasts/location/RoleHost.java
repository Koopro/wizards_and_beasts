package at.koopro.wizardsandbeasts.location;

import org.jspecify.annotations.NullMarked;

/**
 * An NPC that keeps a place by the role it plays rather than by its entity type: one entity type can then keep many
 * places, each naming its {@code host_role} in {@link LocationDefinition.Presence}.
 */
@NullMarked
public interface RoleHost {

    /** The role's name, matched against a location's {@code host_role}. */
    String hostRole();
}
