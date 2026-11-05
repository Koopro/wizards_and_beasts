package at.koopro.wizardsandbeasts.creature.profile;

import org.jspecify.annotations.NullMarked;

/**
 * A creature that shows what it is doing ({@link CreatureActivity}). The generic beasts implement it with one synced
 * byte; a bespoke creature can implement it the same way and the ecology goal will drive it.
 */
@NullMarked
public interface HasActivity {

    CreatureActivity activity();

    /** Server side. Setting the same value again costs nothing and sends nothing. */
    void setActivity(CreatureActivity activity);
}
