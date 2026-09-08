/**
 * Forced lycanthropy: the moon's claim on a werewolf, and the loss of control that comes with it.
 *
 * <h2>The loss-of-control contract</h2>
 * While {@link at.koopro.wizardsandbeasts.heritage.werewolf.WerewolfState#FLAG_LOSS_OF_CONTROL} is set
 * on a player's heritage data, that player is a passenger. The flag is the <em>only</em> condition any
 * part of the system checks — never "is it a full moon and are they a wolf", which is a derivation that
 * three call sites will eventually disagree about. One switch is set when control is taken and cleared
 * when it is given back, and everything else reads it:
 *
 * <ul>
 *   <li>{@link at.koopro.wizardsandbeasts.heritage.werewolf.FeralController} drives movement, facing
 *       and attacks;</li>
 *   <li>{@code WerewolfControlHandler} refuses item use, block and entity interaction, block breaking,
 *       item tossing, and holds every container shut;</li>
 *   <li>{@code SpellNetworkGuards} refuses every wand packet, so casting is blocked at the network
 *       edge rather than per-spell;</li>
 *   <li>{@link at.koopro.wizardsandbeasts.heritage.werewolf.WerewolfTransformService#requestVoluntaryRevert}
 *       refuses, which is what makes the transformation uncancellable;</li>
 *   <li>{@code WerewolfClientControlHandler} zeroes the client's own movement input and refuses to open
 *       inventory screens.</li>
 * </ul>
 *
 * <h2>Why it is server-authoritative</h2>
 * The client layer is a courtesy: it stops a cooperating client fighting its own server. Enforcement is
 * three server-side mechanisms — velocity overwrite with {@code hurtMarked}, absolute rotation packets,
 * and a drift guard that teleports back a player who has been moving against the drive. A client that
 * ignores every one of them still cannot leave. See
 * {@code documentation/WEREWOLF_LOSS_OF_CONTROL.md}.
 *
 * <h2>Composition, not inheritance</h2>
 * Nothing here subclasses anything. The layer is six small collaborators —
 * {@link at.koopro.wizardsandbeasts.heritage.werewolf.WerewolfRules} (predicates),
 * {@link at.koopro.wizardsandbeasts.heritage.werewolf.WerewolfState} (persistence),
 * {@link at.koopro.wizardsandbeasts.heritage.werewolf.WerewolfAttributes} (the body),
 * {@link at.koopro.wizardsandbeasts.heritage.werewolf.WerewolfEquipment} (the hands),
 * {@link at.koopro.wizardsandbeasts.heritage.werewolf.FeralTargeting} (who) and
 * {@link at.koopro.wizardsandbeasts.heritage.werewolf.FeralController} (how) — sitting on top of the
 * existing form system rather than inside it. {@code FormSystemAPI}, {@code HeritageAPI} and the
 * heritage attachment are used unchanged; the layer adds no new attachment and no new payload, because
 * the heritage data's custom-flag map is already persisted, already {@code copyOnDeath} and already
 * synced to the owning client.
 *
 * <p>The one thing here that is not yet data-driven is the wolf's combat profile: see
 * {@link at.koopro.wizardsandbeasts.heritage.werewolf.WerewolfAttributes} for why, and for what has to
 * exist before it can be.
 */
@org.jspecify.annotations.NullMarked
package at.koopro.wizardsandbeasts.heritage.werewolf;
