# Disguise system

Making a player render, to everybody, as a different player — skin and nameplate.

Server-authoritative: the client never decides a disguise, and a disguise never decides anything but
what is drawn.

## The layer, and its callers

`at.koopro.wizardsandbeasts.disguise` owns the whole appearance layer. It has no opinion about
*why* somebody is disguised.

| Piece | What it is |
|---|---|
| `DisguiseState` | attachment record: ticks, target UUID, target name |
| `DisguiseSystemAPI` | the only way in — `apply` / `applyIndefinite` / `clear` / `get` / `tick` / `resyncTo` / `resyncSelf` |
| `DisguiseTargetResolver` | a typed name → somebody to look like, online or not |
| `DisguiseSyncS2CPayload` | `wizards_and_beasts:disguise_sync`, broadcast to trackers **and self** |
| `ClientDisguiseState` | client cache, UUID → disguise |
| `DisguiseRenderHandler` | writes `AvatarRenderState.skin` and `.nameTag` |
| `DisguiseEvents` | tick, death, and the four re-sync points |

Two callers today:

- **`PolyjuiceService`** — adds everything that makes a dose a dose: it needs a sample, it lasts
  five minutes, it refuses to stack, it is refused under Veritaserum, it causes nausea.
- **`DisguiseCommands`** — adds nothing. That is the point of it.

A third (Metamorphmagus, a Boggart, a Confundus illusion) would need nothing added to the layer.

## Debug commands

`/wandb player disguise …`, gated on `WizardsAndBeastsCommandPermissions.ADMIN` — the configured
`adminUuids` allow-list when one is set, operator permission otherwise.

```
/wandb player disguise set <players> <name>   disguise everybody selected as <name>
/wandb player disguise self <name>            disguise yourself
/wandb player disguise clear [players]        take it off (defaults to you)
/wandb player disguise query [player]         who does somebody currently look like
```

`<name>` is a **bare name, not a selector**. That is deliberate: the case the system exists for is
looking like somebody who is not in the room, and an entity selector cannot name them.

An admin disguise is **indefinite** — it has no clock. There is no duration argument, because a
timed disguise is what the potion is for.

`set` never refuses. It overwrites whatever face is already on, so an operator can fix a stuck
disguise without first clearing it. A Polyjuice dose *does* refuse while any disguise is active,
including an admin one, so a dose cannot quietly put a five-minute clock on something meant not to
have one.

Suggestions list only the online players. Everything else is untypeable by definition.

## How a name resolves

`DisguiseTargetResolver`, in order:

1. **Online here** — answered on the server thread, no lookup. Uses the account's own
   capitalisation, not what was typed.
2. **A real Mojang account** — `server.services().profileResolver().fetchByName(name)`, cached ten
   minutes and persisted in `usercache.json`. Note `GameProfileCache` does **not** exist on 1.21.11;
   `ProfileResolver` replaced it.
3. **Nothing** — an invented name, a typo, an offline-mode server, or no internet. Still resolves,
   to `UUIDUtil.createOfflinePlayerUUID(name)`. The nameplate is right and the body is the default
   skin that UUID hashes to. The command says so (`… no such account, so the face is a default one`).

Step 2 is a **blocking HTTP call** and runs on `Util.backgroundExecutor()`; the callback is handed
back through `MinecraftServer#execute`, so `onResolved` is always on the server thread. `set`
therefore reports on dispatch and the per-player confirmation arrives from the callback.

## How the skin is drawn

`DisguiseRenderHandler` replaces `AvatarRenderState.skin`, which is what
`AvatarRenderer.getTextureLocation` reads. No render layer, no mixin, no second body. NeoForge runs
render-state modifiers after `extractRenderState`/`finalizeRenderState`, so the write lands on top.

Skin sources, in order: the target's `PlayerInfo` when they are online on this client (vanilla has
already fetched it), else a profile this handler fetched itself, else `DefaultPlayerSkin.get(uuid)`
while a fetch is in flight and forever if it finds nothing. Never the *wearer's* own skin — their
body under somebody else's nameplate is the one failure that looks like it worked.

> **Historical note.** The Polyjuice version handed `SkinManager.createLookup` a bare
> `new GameProfile(id, name)`. `SkinManager.get` asks `sessionService().getPackedTextures(profile)`,
> authlib answers `profile.properties().get("textures")`, and a bare profile has no properties — so
> every disguise ever rendered resolved to `DefaultPlayerSkin.get(uuid)`, a Steve or Alex picked by
> hashing the target's UUID. It never once showed the target's real skin.

The client fetches the profile rather than the server sending it. A server pushing signed texture
properties at clients would be larger on the wire and a way to push arbitrary texture URLs.

There is **no `slim` flag** anywhere in the system. The model variant is a property of the skin and
the client learns it from the fetched `PlayerSkin` — a real account's own choice, or the default
skin's variant. A boolean on the wire could only contradict that.

### Ordering against other appearance systems

`DisguiseRenderHandler` is registered **before** `PetrifyRenderHandler` in `WizardsAndBeastsClient`,
and that order is load-bearing. Both write `state.skin`, NeoForge runs modifiers in registration
order, and petrify derives its stone from `state.skin.model()`. The other way round, a disguise
would paint over the stone and a petrified player could hide being a statue.

### Non-HUMANOID forms

A disguise is **suspended, not cleared**, while the wearer is in a werewolf / obscurial / animagus
form. `LivingEntityRendererMixin` cancels `LivingEntityRenderer.submit` at HEAD for any non-HUMANOID
form, and `submit` is also what draws the nameplate — so neither the disguise skin nor the disguise
name renders, the form owns the whole body, and the disguise resumes when the form ends. This costs
no code and needs no coordination between the two systems.

## When a disguise ends

| Event | What happens |
|---|---|
| Timer runs out (Polyjuice only) | reverts, with a five-second warning at 100 ticks |
| Death | reverts, silently — otherwise dying would be a free way to reset the timer |
| Veritaserum | reverts; you cannot hold a false face |
| `/wandb player disguise clear` | reverts, no particles |
| Logout | **kept.** The attachment is serialised, so an indefinite disguise survives a restart |

The attachment is deliberately not `copyOnDeath`.

## Re-syncing

The sync goes to whoever is tracking the wearer *at the moment it changes*. Four places re-send it:

- `PlayerEvent.StartTracking` — somebody walked into range or joined
- `PlayerLoggedInEvent`, `PlayerRespawnEvent`, `PlayerChangedDimensionEvent` — **the wearer's own
  client**, because `StartTracking` never fires for a player tracking themselves. Without these the
  wearer is the only person on the server who cannot see their own disguise.

`ClientDisguiseState.clear()` runs on `ClientPlayerNetworkEvent.LoggingOut` so a disguise cannot
follow a UUID into the next world joined.

## Known overlap

`/wandb world polyjuice revert <player>` predates this and now does the same thing as
`/wandb player disguise clear <player>`, with potion-flavoured wording. Left in place; worth
collapsing if the Polyjuice command node is ever revisited.

## Not covered

- **First-person arms.** Drawn from `Minecraft.player`'s own skin rather than a render state, so
  they stay honest. Arguably correct — the one person a disguise need not fool is the wearer.
- Capes, chat names, and anything that would make a disguise *authoritative*. The wearer keeps their
  own UUID, so every permission check, team, claim, vault balance and scoreboard entry still
  resolves to the real person. "And villagers should give you their discounts" has to be built as an
  explicit, enumerated exception — never by making the disguise real.
