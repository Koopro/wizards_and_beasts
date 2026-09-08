package at.koopro.wizardsandbeasts.form.constraint;

/**
 * One thing a transformed player may not do.
 *
 * <p>This enum <b>is</b> the contract. Every prohibition the mod places on a player wearing a body
 * that is not their own is named here exactly once, enforced in exactly one place
 * ({@link FormConstraintEvents}, plus the cast guard), and asked about through exactly one method
 * ({@link FormConstraints#denies}). A system that wants a transformed player restricted declares
 * <em>which</em> of these apply and stops there; it never writes its own cancel handler.
 *
 * <p>Before this existed, three systems — Animagus, Obscurial and the werewolf — each carried their own
 * near-identical wall of {@code @SubscribeEvent} cancels. They had already drifted: the Animagus wall
 * blocked right-clicking an item but not the spell-cast packet, so a wizard in a cat's body could still
 * cast from the spell wheel, which is the one thing "a beast holds no wand" was supposed to mean.
 *
 * <h2>What is deliberately NOT here</h2>
 * <ul>
 *   <li><b>Melee.</b> A beast's answer to everything is its teeth. Every transformed player keeps
 *       them — the Animagus javadoc has said so since it was written — so there is no constraint to
 *       express. It is also not implementable as one: NeoForge fires {@code AttackEntityEvent} from
 *       inside {@code Player.attack}, so cancelling it would disarm the werewolf controller's own bite
 *       along with the player's.</li>
 *   <li><b>Movement.</b> Being unable to <em>steer</em> is loss of control, not a constraint: it is a
 *       thing done to a player rather than a thing forbidden them, it needs a tick and a controller
 *       rather than an event cancel, and only the werewolf has it. It lives in {@code FeralController}.</li>
 *   <li><b>Chat, the pause menu, the death screen.</b> Never restricted, by any form, for any reason.
 *       A player who cannot use their hands must always be able to reach the menu that lets them
 *       leave.</li>
 * </ul>
 */
public enum FormConstraint {

    /**
     * Right-clicking an item, and starting a use of one.
     *
     * <p>Covers eating, drinking, bows, shields, spawn eggs and every wand right-click. Enforced on
     * both {@code PlayerInteractEvent.RightClickItem} and {@code LivingEntityUseItemEvent.Start},
     * because a use can begin without an interact event.
     */
    NO_ITEM_USE,

    /** Right-clicking a block: doors, chests, buttons, crafting tables, anything with a menu. */
    NO_BLOCK_INTERACT,

    /** Breaking blocks. A paw does not swing a pickaxe. */
    NO_BLOCK_BREAK,

    /** Right-clicking an entity: trading, saddling, leashing, shearing, naming. */
    NO_ENTITY_INTERACT,

    /**
     * Holding any container open, the player's own inventory included.
     *
     * <p>The server cannot refuse an open — {@code PlayerContainerEvent.Open} is not cancellable — so
     * this is enforced by closing whatever is open on every tick. The client also refuses to open one,
     * but that is a courtesy that keeps the screen from flickering; the server's close is what makes
     * the rule true for a client that does not cooperate.
     */
    NO_INVENTORY,

    /**
     * Throwing items on the ground.
     *
     * <p>Enforced with care: cancelling {@code ItemTossEvent} alone <em>destroys</em> the stack — its
     * own javadoc says so, because the item has already left the inventory when the event fires. The
     * handler puts the stack back first and only cancels if that succeeded.
     */
    NO_ITEM_DROP,

    /**
     * Casting, at the network edge.
     *
     * <p>Enforced in {@code SpellNetworkGuards.canUseWand}, which is the one guard every wand packet
     * passes — casting, assigning a slot, selecting a spell and the Leviosa adjustment alike. Blocking
     * it there rather than per-spell is why a new spell cannot accidentally be castable by a cat.
     */
    NO_SPELLCASTING,

    /**
     * Choosing to change back.
     *
     * <p><b>The one constraint an Animagus never has.</b> An Animagus transformation is voluntary in
     * both directions by definition, and a form that could trap a wizard in a beast's body would be a
     * different mechanic with the same name. This exists for the forced ones: a werewolf under a full
     * moon without Wolfsbane did not choose to be a wolf and cannot choose to stop.
     */
    NO_VOLUNTARY_EXIT
}
