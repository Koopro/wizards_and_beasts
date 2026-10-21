# The wizarding economy

Status 2026-10-13. This is the design the currency follows and what is built of it. Coinage, vaults and the admin
settings are in `ADMIN_WORLD_ECONOMY.md`; places and their boards in `LOCATIONS.md`; the open-problem history in
`KNOWN_ISSUES.md` §5f.

`J/` = `src/main/java/at/koopro/wizardsandbeasts/`, `D/` = `src/main/resources/data/wizards_and_beasts/`.

---

## 1. Why there is money at all

A wizard needs money for the things other people do for them: a wandmaker's hands, the Ministry's ink, a goblin's
ledger, an apothecary's stock. Money is **what you pay for another wizard's skill, time, goods or authority** —
never for what is in your own head, and never for the right to play.

1. **Knowledge is never sold.** Spells are found, read and copied. O.W.L. sittings are free. No price list stands
   between a player and a spell, a skill node or an exam. A *certification fee* (§3) pays for the examining body's
   time once the rank is earned; it never buys the rank.
2. **Money is never required for normal play.** A first wand is free, Floo powder is crafted, brewing needs
   ingredients and a cauldron. Every fee is waived while the Gringotts module is off, and every fee and scale is an
   admin setting. Career ranks beyond the first and fences are not normal play.
3. **Money flows through events, not timers.** You pay because something happened. The only recurring charge is the
   high-security vault, which is optional storage. A player who stops playing for a month owes nothing.
4. **Not every system costs money.** Practising, studying, flying, Apparating, bonding and the skill web stay free.
5. **Scarcity is in materials and authority, not in coins.** Coins are how a player who has rare things trades them
   for time — and a trader's demand for a thing is itself scarce (§4).
6. **Canon coinage is fixed.** 29 Knuts = 1 Sickle, 17 Sickles = 1 Galleon, 493 Knuts = 1 Galleon. Only foreign
   money (Dragots) and trader prices move.
7. **Gringotts is the ledger.** Services and traders bill and pay the vault directly; every movement goes through one
   door (`GringottsBilling`) and is booked.
8. **Crime pays better and costs more.** The black market exists only where the law that punishes it does (the
   Ministry module). It pays more than the law, and every deal may reach the Ministry through the same pipeline as any
   other offence.

---

## 2. The loops

### 2.1 Income

| Category | Where | How it pays | Bounded by |
|---|---|---|---|
| **Creature materials** | Apothecary market (dittany… acromantula venom), Ollivanders market (cores) | Unit price moved by shared pressure (§4) | Supply gates (grooming/shed cooldowns, harvest tiers, bond gifts) and each good's daily ceiling |
| **Ingredients** | Apothecary market and supply contracts | Same | Same |
| **Potions** | Apothecary buys 9 brews (Pepperup 43 K … Felix Felicis 986 K) | Same | Brewing time and ingredients; shallow depth for rare brews |
| **Wand materials** | Ollivanders buys wandwood (5–20 K a log) and cores (29–232 K) | Same | Same |
| **Crafting** | Ollivanders buys finished **unbonded** wands: 290 K × wood rarity (1 / 1.5 / 2.5) × integrity | Same | A wand needs a core and wood; depth 6 |
| **Ministry contracts** | Ministry clerk board: beast census, dangerous-beast record, curse clearance, surrender of contraband | Fixed reward per contract | Daily board, cooldowns, max 2 held |
| **Profession rewards** | Career wage (existing), career contracts (existing) | Daily wage × rank multiplier | Once per day; rank needs certification (§3) |
| **Exploration** | **Finder's fee** from the Beast Division for the first field record (OBSERVED) of each species | 14 K – 290 K by Ministry rating | Once per species per player, ever (journal history) |
| | Treasure (existing): Gringotts finder's fee 50 %+ of an artefact's worth | | Each chest rolls once |
| **Creature care / services** | Magizoologist career wage; supply contracts at the apothecary and Ollivanders | | |
| **Risky: restricted materials** | Lawful counters need the Restricted Substances licence (Ministry on); a fence does not ask | Fence pays ~75 % of the apothecary | Fence deals roll detection |
| **Risky: rare artefacts** | Fence buys any artefact at 85 % of worth (cursed at half) vs Gringotts' 50 %+ | | Filed as *handling stolen goods* (the bank's claim) |
| **Risky: smuggling** | Fence contracts: venom run, hair run, heartstrings, counterfeit gold, the fang job | 493 – 1972 K | Delivery at the fence rolls detection |
| **Criminal: illegal artefacts / forbidden materials** | Fence buys contraband (basilisk fang 986 K, Hand of Glory, blood-pact vial, counterfeit Galleons) | | *Dealing in contraband* (12 G fine) |
| **Criminal: stolen goods** | Fence buys wands whose master is another wizard (348 K × rarity) | | *Handling stolen goods* (6 G) |

### 2.2 Sinks

| Need | Service | Where | Price |
|---|---|---|---|
| **Wand services** | replacement wand; wand care; evaluation (existing); **wand commission** (§4b) | Ollivanders | 7 G; ≤2 G by wear; 87 K; 3 G × wood rarity |
| **Broom services** | **broom servicing**: full repair + polish | Broom shop (broommaker at a broom rack) | 58 K × tier repair cost (1 School … 12 Legendary) × wear, to the Sickle |
| **Travel** | Floo registration (existing); **Portkey Office**: one registered Portkey a week | Ministry clerk | 3 G (Portkeys had no survival source before) |
| **Licences** | endorsement fee (existing) | — | 2 / 4 / 6 G |
| **Training / certification** | **certification fee** on reaching a career rank (`fee` goal) | Career tracks | Magizoologist 1 / 2 / 4 / 6 G; Curse-Breaker 1 / 3 / 5 G |
| **Ministry fines** | existing, plus the four trade offences (§5) | — | 4 – 12 G |
| **Gringotts services** | appraisal, curse-breaking, vault rent, Dragot commission (existing) | Gringotts | — |
| **Creature care** | **creature treatment**: bonded creatures at the counter healed and cleansed | Apothecary | 29 K a visit + 3 K a health point |
| **Rare materials** | apothecary sells dittany, bezoars, Wiggenweld, unicorn hair, Occamy shell; Ollivanders sells blanks, holly, unicorn hair, heartstrings | Markets | Asking price rises with each unit bought; weekly limits on the rarest |
| **Specialised equipment** | Floo powder, essence of dittany, broom polish, Portkeys; **brooms** (Cleansweep 3 G … Firebolt 50 G; racing brooms tier-gated, one a week) | Markets, broom shop | — |
| **Illicit** | memory charm (heat × 1 Sickle, halves heat), forged endorsement (986 K × rank) | Fence | Also roll detection |

### 2.3 Integration

| System | What the economy does with it |
|---|---|
| **Ministry** | Restricted goods need its licence at a lawful counter; fences file its offences through `TraceService.report` (heat, fine, file, standing deed); its clerk posts contracts and keeps the Portkey Office; the fence's memory charm and forged papers act on its records (`withNotoriety`, `LicenceForgery.forge`) |
| **Professions** | Certification fees on career ranks; wages unchanged; GOODS_TRADED activity can feed tracks |
| **Creatures** | Materials are the bulk of what traders buy; treatment heals bonded creatures |
| **Bestiary** | First field record pays the finder's fee; Ministry contracts count bestiary depth |
| **Wand system** | Ollivanders buys unbonded wands (crafting) and cores, and makes wands to order from the customer's own blank and core; fences buy wands mastered by others (stolen) |
| **Locations** | Every trader is a location: presence, tiers gating goods, board UI, contracts |
| **Crafting** | Wands and brews sell for more than their inputs |
| **Exploration** | Finder's fees; treasure to Gringotts or the fence |
| **O.W.L.s** | Grades gate careers and licences, which gate restricted trade; trusted standing needs a grade: apothecary Potions E, Ollivanders Care of Magical Creatures A, Ministry association DADA A (met while the O.W.L. module is off) |

---

## 3. Certification (training)

A career rank may require `{ "type": "fee", "knuts": N }`. It is shown on the Career tab like any goal, met while
the vault holds N, and **charged once at the moment of promotion** (`CareerProgression.payCertification`, also used
by location tiers). If the vault was spent in between, the promotion waits for the next activity — never a partial
charge. Free while Gringotts is closed (`CareerContext.vaultKnuts` reads as unlimited).

---

## 4. Supply and demand (`J/market/`)

Every good a trader deals in has a **pressure**: units recently traded across that counter, by everyone on the
server (`MarketBook`, world SavedData). It rises by one per unit and halves every `half_life_days` (computed on read,
so nothing ticks).

```
trader buys  (player sells):  unit = base · depth / (depth + pressure)      floored to a Knut; refuses units worth < 1
trader sells (player buys):   unit = base · (depth + pressure) / depth      ceiled
today's short good:           one bought good per trader per day pays × (1 + demand_bonus)
```

**Why it cannot inflate.** Selling `r` units a day holds pressure at `r·H/ln2` and pays `r·b·d/(d + r·H/ln2)` a day.
That rises with `r` but never past **`b·d·ln2/H`** — the most a good can pay the whole server per day, however hard
it is farmed (`MarketPricingTest.farmingCannotOutrunTheDailyCeiling`). Ten players farming one material saturate one
market, not ten. Shipped ceilings (lawful counters, server-wide, per day):

| Trader | Half-life | Sum of ceilings | Example: 8 a day of… |
|---|---|---|---|
| Apothecary | 2 days | 5,722 K (~11.6 G) | dittany 56 K/day · acromantula venom 411 K/day · Felix 629 K/day |
| Ollivanders | 3 days | 1,661 K (~3.4 G) | holly logs 31 K/day · dragon heartstring 192 K/day |
| *Fence (illicit)* | 3 days | 1,997 K | basilisk fang 431 K/day |

`MarketDataTest.theLawfulFaucetIsBounded` pins the lawful total under 25,000 K/day. Real income is far lower: the
supply is gated (a unicorn grooms once a day, harvest drops need study and cooldowns, Felix takes a brewing cycle).
A career wage is 290–493 K/day for reference.

**No arbitrage.** `MarketDataTest` checks that nothing is sold anywhere for less than the most any trader pays for
it on its best day, and that no delivery contract pays more than buying its goods would cost. Buying a batch and
selling it straight back always loses (`MarketPricingTest.sellingAndBuyingBackIsAlwaysALoss`).

A trader that only sells (the broom shop) has no ceiling: it is a sink. It buys nothing back because every broom is
crafted from cheap stock, and a shop that bought brooms would be a mint bounded only by its ceiling.

**Weekly limits** cap how much of a rare good one player may buy (`PlayerMarketRecord`), so one wealthy player cannot
empty a shallow market for everyone.

---

## 4a. Dragots at the till

A keeper whose entity type is in `dragot/accepts` (the wandering trader ships there) or `dragot/penalty` quotes each
good it sells in Dragots too, and the board offers *Buy 1 for N Dragots* (`MarketTrade.buy(..., Tender.DRAGOTS)`).
Paid from the pocket, worst coins first; the vault is never touched; booked as `DRAGOT_TENDER` (circulation) at the
goods' Knut value.

```
N = ceil(price_knuts × penalty / (base_rate × (1 − 0.03) × 493))        penalty 1.25 for dragot/penalty, else 1
```

A till values a Dragot at the **bottom** of the teller's ±3 % spread and takes no commission. Anything higher and a
customer buys Dragots at Gringotts on a low quote and spends them for more than they cost; at the bottom, the cheapest
possible Gringotts purchase (a goblin's 3.5 % fee) still costs more than paying from the vault
(`DragotRatesTest.spendingBoughtDragotsNeverBeatsTheVault`). The till is where Dragots found as loot are worth
something without the bank's cut. A devalued coin in the purse rolls the 20 % notice: the sale is off, the keeper
remembers (villager gossip), `PASSING_DEVALUED_COIN` is filed. A keeper in `dragot/only` refuses the vault.

## 4b. Wand commissions

Ollivanders, from Regular: hold a shaped blank (the wood) and a core material, one in each hand. Price = 1,479 K (3 G)
× wood rarity (1 / 1.5 / 2.5), less 5 % per tier above Regular, rounded up to a Sickle; both materials are taken in
the tick the fee is paid. Ready after `3 − tiers above` days (at least 1); one order at a time
(`WandCommissionOrder` attachment, survives death and relog). On collection the wandmaker picks the flexibility and
length in the recipe's range that resonate best with the customer; no workshop tier is needed. The wand still
chooses: below the match threshold it is handed over unbonded. Elder (legendary) wood is refused.

**No loop:** the cheapest commission (1,331 K at the top tier) is above the best price any trader pays for a finished
wand on its best day (348 K × 1.25 at the fence), per unit of rarity, before counting the materials
(`MarketDataTest.aCommissionCostsMoreThanAnyWandSells`).

---

## 5. Legality and risk

What a deal is, is decided per stack on the server (`MarketGoods.classify`):

| Class | What | Lawful counter | Fence | Offence when reported (fine) |
|---|---|---|---|---|
| LEGAL | everything else | yes | yes, no risk | — |
| RESTRICTED | item tag `restricted_ingredient` | with the Restricted Substances licence (Ministry on) | yes | `trafficking_restricted` (4 G) |
| CONTRABAND | item tag `contraband` (basilisk fang, Hand of Glory, blood-pact vial, Dark Mark brand, counterfeit Galleon) | refused | yes | `dealing_contraband` (12 G) |
| STOLEN | a wand another wizard is master of; treasure sold past the Gringotts claim | refused | yes | `handling_stolen_goods` (6 G) |
| SERVICE | memory charm, forged papers | — | yes | `procuring_illegal_magic` (8 G) |

A location with a `"risk"` block deals outside the law and must belong to the Ministry module (validated at load).
Every illicit deal — sale, purchase, smuggling delivery, service — rolls once:

```
p = chance · class weight · (1 + 0.35 ln units) · (1 + 2·officials) · (1 + 0.25·witnesses) · (1 + heat/50)
      · (1 − discretion≤0.6) · server scale            clamped to 0 … 0.95
```

Officials are `ministry_officials` in sight; witnesses are other players and Muggles in sight (never the fence
himself). Volume is sub-linear, so splitting a deal into many small ones is *riskier*, not safer
(`IllicitRiskTest.splittingADealIsRiskierThanMakingItOnce`). A reported deal goes through `TraceService.report`:
heat, the permanent file, a fine billed to the vault (or a debt that freezes heat decay) and the standing deed. The
deal itself stands — the fine is the cost.

**Expected value, quiet deal at the shipped fence (8 %):** selling a 580 K artefact to the fence pays 493 K against
Gringotts' 290 K (+203 K); the chance of a report is 8 % × 0.8 = 6.4 %, costing a 6 G (2,958 K) fine — about −189 K
expected. Roughly break-even for a clean record, worse with heat, priors (×1.15 per prior) or witnesses. That is the
decision the black market exists to create.

---

## 6. Authority, duplication and every currency path

All trade is server-side (`MarketTrade`). The client sends only `good|count` on the board's packet
(`LocationActionC2SPayload`, 4-tick throttle); the server re-checks the location's module, the player's presence and
tier, the stack itself, the law, the price and the vault.

- **Selling** prices first, refuses before anything moves if the vault could not hold the money, then removes the
  goods and credits in the same tick; if the credit is refused anyway the goods are put back.
- **Buying** charges all or nothing first; only a paid charge hands goods over.
- **No double sale:** the first deal removes the stack before a second packet is read (one server thread);
  `economy_market_cannot_sell_twice`.
- **Counts:** ≤ 0 refused; a purchase over 64 is refused, a sale over 2304 is clamped; malformed `good|count` is dropped.

| Path | Door | Flow | Authority |
|---|---|---|---|
| Trader sale / purchase | `GringottsBilling.credit/charge` | `MARKET_SALE` / `MARKET_PURCHASE` | board packet, presence, tier, licence |
| Fence sale / purchase / service / contract | same | `ILLICIT_SALE` / `ILLICIT_PURCHASE` | same + detection roll |
| Location services | `LocationService.Use.charge` | `LOCATION_SERVICE` (or `ILLICIT_PURCHASE` at a fence), `VAULT_RENT` | board packet |
| Location / career contracts | `credit` | `LOCATION_CONTRACT` (or `ILLICIT_SALE`) / `CAREER_CONTRACT` | delivery counted server-side |
| Finder's fees | `credit` | `EXPLORATION_FEE`, `TREASURE_FINDERS_FEE` | earned bestiary path / surrender |
| Certification | `charge` | `CERTIFICATION` | promotion |
| Market purchase in Dragots | pocket Dragots taken, `recordCirculation` | `DRAGOT_TENDER` | board packet, presence, host's `dragot/` tag, notice roll |
| Wand commission | `LocationService.Use.charge` | `WAND_COMMISSION` | board packet, presence, tier; materials taken after the charge |
| Wage, wand, care, respec, Floo, fines, endorsements | (existing, `GringottsBilling`) | — | — |
| Counter deposit / withdrawal | coins ↔ vault, booked with `recordCirculation` | `COUNTER_DEPOSIT` / `COUNTER_WITHDRAWAL` | teller within 8 blocks; now 2-tick throttle |
| Dragot exchange | `charge`/`credit` | `DRAGOT_*` (exchange) | teller |
| Admin money tools, dev kit | coin by coin + `recordAdjustment` | `ADMIN_*` | ADMIN / MONEY capability |
| Loot coins (chests, caches, Nifflers) | items only | seen at deposit (`COUNTER_DEPOSIT`) | worldgen |

**Fixed in this pass (audit 2026-10-08):**
- **Goblin Dragot arbitrage.** The quote re-rolls on every teller click and goblins paid no fee, so buying at the low
  quote and selling at the high one made ~6 % a cycle. Goblins now pay 3.5 % (> the 3 % spread); every round trip
  for every fee loses (`DragotRatesTest.noFeeLetsACustomerPlayTheSpread`).
- **Counter packets** had no rate limit (now 2 ticks). The counter deliberately stays open with Gringotts off, so
  money can always be withdrawn.
- **Dev kit** minted 500 G/S/K without a ledger entry; it now books `ADMIN_DEPOSIT`/`ADMIN_WITHDRAWAL`.
- **Leprechaun gold** never vanished: `inventoryTick` had the pre-1.21.5 signature. It is not money (the counter
  refuses it), but now behaves as canon.
- Counter traffic was invisible; it is now `CIRCULATION`, so loot money entering the bank is measurable.
- **Coins destroyed on a full pocket** (found by code review). `Inventory.add` returns true on a *partial* insert
  and leaves the rest in the stack, so `if (!add) drop` lost the overflow: a counter withdrawal or Dragot purchase
  into a nearly full pocket debited the vault for coins that never existed. All economy hand-overs (counter, Dragots,
  market purchases and refunds, tier and rank rewards) now go through `util/InventoryHandover.giveOrDrop`
  (GameTest `economy_counter_books_circulation` withdraws into a full pocket). Location delivery contracts also check
  the vault can take the reward before the goods are taken, and a trader good naming no real item is refused before
  any charge.

**Fixed 2026-10-13:** the 15 non-economy hand-overs that still used `if (!add) drop` now use
`InventoryHandover.giveOrDrop` (or add, then drop what is left); `PartialInsertPatternTest` refuses the pattern in
`src/main/java`. Market purchases hand over `Item.getDefaultInstance()`, so a bought broom keeps its definition.

**Known, not changed:** vanilla villager trades still use emeralds and take no Dragots (the board does); fines reach only the vault, so pocket coins are fine-proof (deliberate: Gringotts is the
ledger); career contract hand-in needs no presence (careers are a board, not a place); a finder's fee earned while
Gringotts is closed is not paid later (no money exists then — the same rule as every service).

---

## 7. Admin

| What | Where |
|---|---|
| Trader price scale, recovery speed, creature finder's fee | Economy → Rules (`market_price_scale_percent`, `market_recovery_scale_percent`, `exploration_fee_scale_percent`) |
| Black-market detection | Ministry → Rules (`illicit_detection_scale_percent`; warns at 0) |
| Live flows and every trader's book | Economy → **Markets** tab (polls every 2 s while shown) |
| Same, from a console | `/wandb admin economy ledger`, `/wandb admin economy market` |
| Restore list prices | `/wandb admin economy market reset <location|all> confirm` |
| One player's last 32 money movements | `/wandb admin economy journal <player>`; Players → Economy facet |

Traders' books (prices, depth, limits, contracts) are datapack data: `D/locations/{apothecary,ollivanders,ministry,
knockturn_dealer,broom_shop}.json`; the commission fee is the `wand_commission` offer in `ollivanders.json`.
New flows (`DRAGOT_TENDER`, `WAND_COMMISSION`) appear in the ledger, journal and Markets tab on their own. A new trader is one location file with a `market` block and lang lines.

---

## 8. Places

| Place | Host (job site) | Module | Market | Services | Contracts |
|---|---|---|---|---|---|
| Slug & Jiggers Apothecary | villager `apothecary` (any wizarding cauldron) | Gringotts | buys 23 materials/ingredients/brews, sells 8 | creature treatment | supply orders |
| Ollivanders | villager `wandmaker` | Wands | buys wood, cores, unbonded wands; sells blanks, holly, cores | evaluation, wand commission | core and wood supply |
| The Broom Shop | villager `broommaker` (broom rack) | Broom Flight | sells polish, twig bundles, brooms; buys nothing | broom servicing | twig and polish orders |
| Ministry of Magic | villager `ministry_clerk` (examination desk) | Ministry | Portkey Office | — | census, records, curse clearance, surrender |
| A Dealer of No Fixed Address | wandering trader | Ministry | buys artefacts, stolen wands, restricted and contraband goods; sells restricted goods and a fang | memory charm, forged papers | smuggling runs |
| Gringotts | goblin teller | Gringotts | — (contracts buy gold, emeralds, diamonds) | appraisal, curse-breaking, surrender, vault | expeditions |

Sneak-use the keeper to open the board. The board's **Buying** / **For sale** sections show the next unit's price,
what you carry, today's short good, and how saturated a good is; restricted goods are marked ✦, illicit ones ⚠.

---

## 9. Non-goals

- No shop that lists everything; each good is sold by someone, somewhere, for a reason.
- No spell, skill node, exam or progression skip for money.
- No interest, coin decay or per-hop travel toll.
- No emerald exchange (still deliberate, `KNOWN_ISSUES.md` §5f).
- No crime without the Ministry module.

---

## 10. Tests

| Test | Proves |
|---|---|
| `MarketPricingTest` | half-life decay; selling lowers / buying raises; batches priced per unit; round trip always loses; **farming cannot outrun the daily ceiling**; daily short good stable and moving; wood rarity |
| `IllicitRiskTest` | lawful never reported; eyes/heat/volume/class raise risk; caps; splitting is riskier; every illicit class files a fine offence |
| `MarketDataTest` | traders exist and validate; real items and brews; lawful counters list no contraband/stolen/treasure; **no arbitrage across traders**; **no contract pays more than its goods cost**; lawful faucet pinned; lang |
| `MarketStateTest` | pressure, trade record and journal survive a save; weekly limits roll over; journal bounded; malformed goods refused; fee goal reads the vault; finder's fee once |
| `MoneyFlowTest`, `DragotRatesTest`, `AdminWorldEconomyTest`, `CommandTreeShapeTest` | flows classified (circulation apart); no fee lets a customer play the spread; pinned Economy settings; new commands resolve |
| GameTest `economy_market_sell_is_server_priced_and_atomic` | exact goods removed, quoted batch paid, `MARKET_SALE` booked and journaled, price falls |
| GameTest `economy_market_cannot_sell_twice` | two sell-all intents sell once; zero/negative/absurd counts, unknown goods and markets refused |
| GameTest `economy_market_vault_ceiling_keeps_goods` | a sale the vault cannot hold moves nothing |
| GameTest `economy_market_buy_all_or_nothing` | short vault buys nothing; exact charge; no negative coin; weekly limit |
| GameTest `economy_market_needs_presence` | no trade away from the keeper |
| GameTest `economy_market_pressure_is_shared` | one player's sale lowers another's price (multiplayer); recovery |
| GameTest `economy_market_law_at_the_counter` | restricted goods need papers with the Ministry on |
| GameTest `economy_fence_deals_reach_the_ministry` | fence buys what a counter will not; reported deal files the offence and heat; unreported files nothing |
| GameTest `economy_market_price_scale_setting` | price scale moves quotes; detection scale 0 never reports |
| GameTest `economy_exploration_fee_once_per_species` | fee once, not again after an admin lowers the page |
| GameTest `economy_certification_fee_on_promotion` | fee charged once; short vault untouched |
| GameTest `economy_counter_books_circulation` | deposits/withdrawals booked |
| GameTest `economy_services_broom_and_fence` | broom repaired and billed by tier and wear; memory charm halves heat, booked illicit |
| `DragotRatesTest.spendingBoughtDragotsNeverBeatsTheVault`, `aTillAlwaysAsksEnoughDragotsToCoverThePrice` | the till's rate cannot be played against the teller |
| `MarketDataTest.aCommissionCostsMoreThanAnyWandSells` | commissioning to sell loses at every rarity; Elder never priced |
| `PartialInsertPatternTest` | no `if (!add) drop` hand-over in the sources |
| GameTest `economy_dragots_at_the_till` | fence quotes and takes pocket Dragots, vault untouched, tender booked; short purse and the apothecary refuse |
| GameTest `economy_wand_commission` | tier gate; Elder refused free; fee and both materials once; nothing early; one fitted wand when ready; no second collection |
| GameTest `economy_broom_shop` | broommaker keeps the shop; bought broom keeps its definition; tier and weekly limit; buys nothing |
| (existing) `economy_refused_charge_leaves_vault_identical`, `economy_wand_care`, `economy_endorsement_fee`, `economy_ledger_records_flows`, `VaultPaymentTest` | the 2026-10-07 slice |

---

## 11. History

- **2026-10-07** — one billing door (`GringottsBilling`) and the ledger; wand care and endorsement fees.
- **2026-10-08 (locations)** — Gringotts as a place: finder's fees, location contracts and services, vault rent.
- **2026-10-08 (this pass)** — markets with supply and demand, apothecary / Ministry / fence, legal–risky–criminal
  loops, finder's fees for creatures, certification, broom servicing, creature treatment, Portkey Office, the money
  journal, circulation, the Markets admin tab, and the currency-path audit fixes in §6.
- **2026-10-13** — Dragots at the till (§4a), wand commissions (§4b), the broom shop and broom rack, O.W.L. grades on
  trusted standing, the 15 remaining partial-insert hand-overs.
