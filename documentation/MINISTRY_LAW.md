# Ministry law as gameplay

What happens after a wizard breaks the law, as of 2026-10-15. This builds on the evidence-based Trace from
2026-09-17 (KNOWN_ISSUES §5k) and does not replace it. Code lives in `ministry.trace`, `ministry.law`,
`ministry.licence` and `ministry.command`.

## The loop

```
illegal act ─► incident (only if a channel will hear of it, or it is dark enough to stay on the wand)
           ─► reports arrive (Trace, official, Muggle sighting, inquiry) ─► each files EVIDENCE
           ─► evidence ≥ IDENTIFY puts a name to it ─► case ladder: record → warning → investigating → summoned → Aurors
           ─► unnamed but serious: the Auror Office's board ─► an Auror examines the scene, questions witnesses
           ─► summons answered / surrender / capture (procedural search or a player Auror)
           ─► hearing on the evidence: contest or plead ─► ruling ─► appeal
```

## Fairness rules (all pinned by tests)

| Rule | Where | Test |
|---|---|---|
| A cast is never an offence by itself; only an incident with evidence reaches a hearing | `TraceRules`, `MinistryTrace.file` | `TraceRulesTest`, game test `ministry_trace_nobody_saw_it` |
| Evidence is only ever something the server observed. No command or packet carries evidence content | `Evidence`, `MinistryTrace`, `MinistryCasework` | `CaseworkRulesTest` |
| One source, one voice: a witness counts once, residue once | `EvidenceRules.strength` | `EvidenceRulesTest` |
| Players' testimony is capped at 45 and, with nothing the Ministry saw itself, never reaches IDENTIFY (50) | `EvidenceRules` | `EvidenceRulesTest`, game test `ministry_casework_players_alone_cannot_name_anyone` |
| Only a player the server recorded as an eyewitness may testify, once; never about themselves; not if the public despises them | `CaseworkRules.testify` | same |
| Nobody works a case about themselves or one they witnessed; an Auror only adds evidence | `CaseworkRules.take` | `CaseworkRulesTest`, game test `ministry_casework_auror_solves_and_apprehends` |
| Apprehension needs a held case at the Aurors' stage, the suspect within 8 blocks, downed or surrendering | `CaseworkRules.apprehend` | same |
| The graver the charge, the more evidence: 50 / 50 / 60 / 75; appeals +15 | `EvidenceRules.standardFor` | `HearingChoicesTest` |
| An appeal can only help | `AppealRules.review` | `HearingChoicesTest` |
| Aurors are credited for closing a case, never for a conviction; no bounty on cases against players | `MinistryCasework.onCaseClosed` | — |
| Polyjuice cannot frame anyone: an incident records the real caster | `Incident.caster` | — |

## Evidence

| Kind | Weight | Source |
|---|---|---|
| `trace_reading` | 70 | the Trace, on an underage wizard with no adult about |
| `official_report` | 80 | an entity in `ministry_officials` saw it |
| `muggle_account` | 30 | one per Muggle, taken by the inquiry |
| `wizard_testimony` | 30 (all players ≤ 45) | `/wandb ministry testify` |
| `scene_residue` | 35 | an Auror at the scene: `casework examine` or Revelio, within 3 days |
| `residue_trail` | 30 | posted cases: the trail from scene to hideout |
| `witness_interview` | 25 | an Auror sneak-uses a recorded witness |
| `wand_examination` | 100 | the hearing finds it on the wand (last three dark acts) |
| `confession` | 100 | guilty plea (the plea itself is evidence) |

`/wandb ministry dossier` discloses every charge, the evidence on it and the bar it must clear, so the plea is an
informed choice.

## The wizard's choices

| Choice | How | Effect |
|---|---|---|
| Cooperate | `summons answer [contest\|guilty]` | answered in person: conduct +3, character reference counts |
| Plead guilty | `… guilty` | conviction regardless of evidence; −25 % fine, half the confiscation; no appeal; conduct +2 |
| Contest / prove innocence | `… contest` | a charge short of its standard is dismissed (`insufficient_evidence`); danger, licensed practice and Hogwarts excuse as before |
| Surrender | `surrender [contest\|guilty]` after the summons lapsed | treated better than capture: no forced confiscation |
| Evade | keep away from the last report | Aurors search the last known spot; after 10 fruitless searches the case goes **cold** until a new report |
| Appeal | `appeal` within `ministryAppealWindowDays` (3) | fee `ministryAppealFeeKnuts` (5 G), refunded if reduced/overturned; overturn withdraws the offence and its heat, forgives or refunds the fine, returns the wand, lifts suspensions |
| Pay | `fine pay` / automatic vault sweep | unchanged |
| Bribe | fence service `bribe_clerk` (Knockturn, trusted contact) | only while INVESTIGATING and nothing dark; caught → `bribery_of_official` (15 G) and an immediate summons. `ministryBribery` |
| Legal alternatives | licence office, Apparition test, Animagus register, surrender contraband at the Ministry | — |

## Reputation

| Axis | Kind | Moved by | Read by |
|---|---|---|---|
| Ministry | derived: rank credit − notoriety + conduct × 0.4 | hearings (conduct), appeals won | hearing leniency (`good_standing`), licence office refuses `strong_negative` |
| Public | stored | conviction −2/−5/−10/−25 by verdict, acquittal +2, overturned appeal restores | witness credibility (`strong_negative` is not believed), Auror track's top rank |
| Profession | stored, per profession (resets on a career change) | contracts +2, apprehensions +2, convictions −(×2 for Auror/Hit Wizard/Obliviator), a suspect killed −3 | career promotions (`"type": "standing"` goal) |

Standing re-syncs to the client whenever the Ministry band, rank or conduct moves.

## Licences

New types (appended; wire codec is ordinal): `restricted_magic` (licensed dark practice out of Muggle sight is
not convicted; given to Aurors and Curse-Breakers; office needs E in Defence), `magical_commerce` (lifts the
`ministryUnlicensedSalesKnutsPerWeek` allowance at lawful counters — a refusal, never an offence).
`dangerous_creatures` now has teeth: breeding an XXXX+ beast without it in an official's sight files
`unlicensed_dangerous_creature` (6 G).

**Licence office**: Ministry clerk services `licence_<type>` for every type except Apparition, Animagus and Auror
trainee. Fee `ministryLicenceFeeKnuts` (`MoneyFlow.LICENCE_FEE`); term `ministryLicenceTermDays` (0 = never
expires, the default). Refused while suspended, with an open case, or to a wizard the Ministry is hunting.

**Suspensions** are on the Ministry record (`PlayerMinistryRecord.suspensions`), not the scroll, so they hold
whatever copy is carried — the Apparition flag included. Dark convictions suspend restricted magic and Auror
training; any confiscation suspends Apparition; an Azkaban referral suspends four.

## Aurors

Anyone with the Auror profession or Ministry rank ≥ Auror (`ministryPlayerAurors`). `/wandb ministry casework`:
board, take (hold 3 days, 3 at once), drop, examine, interview, apprehend. Career track `career_tracks/auror.json`
pays in experience for evidence, interviews, identifications, apprehensions and closed cases; reaching `auror`
writes `MinistryRank.AUROR` on the record (`ministry_rank` on a career rank).

**Posted cases** (`ministry_cases/*.json`, `ministryNpcCases`): when an Auror reads the board and fewer than three
are posted, the Office posts one near a village 200–600 blocks away. Examine the scene (bearing and distance to
the hideout), follow the trail, or ask villagers nearby; once identified the culprit is waiting at the hideout,
glowing. A blow that would bring them down from the Auror holding the case takes them alive (bounty,
`MoneyFlow.MINISTRY_BOUNTY`); a stunned culprit within 8 blocks is taken; a dead one fails the case.

## Admin

- Ministry → **Cases** tab and `/wandb admin ministry cases`: every case, unsolved incident and posted case with
  stage, holder, strength against the bar, and every item of evidence with its source.
- Player → Ministry facet: conduct, suspensions, case number/stage/holder, each incident's evidence, last ruling
  and appeal. Actions **Close case** (no hearing, nothing filed) and **Lift suspensions**; the rollback snapshot
  now covers the dossier.
- Settings: appeal window and fee, licence fee and term, unlicensed sales allowance, player Aurors, posted cases,
  bribery.
- Operator commands: `/wandb ministry dossier <player>`, `casework post <template>`.

## Not done

- No Azkaban sentence loop (unchanged decision of 2026-09-17).
- No GUI for casework or the dossier; both are chat with clickable buttons.
- Credit for closing a case is given only if the Auror is online when it closes.
- Ministry clerks are not `ministry_officials`, so officials are still only goblin tellers.
